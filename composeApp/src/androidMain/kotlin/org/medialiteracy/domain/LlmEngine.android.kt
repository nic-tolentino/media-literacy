package org.medialiteracy.domain

import android.content.Context
import com.google.ai.edge.litertlm.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.isActive
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.File
import org.medialiteracy.domain.Logger

/**
 * Android-specific implementation of the [LlmEngine], utilizing the LiteRT-LM SDK.
 * 
 * **CRITICAL ARCHITECTURE NOTE:**
 * This class uses a **Mutex-locked Actor Pattern** to safeguard native C++ resources.
 * LiteRT-LM handles are inherently single-threaded. Concurrent access to the [engine] 
 * or [activeConversation] will cause non-deterministic SIGSEGV (Segmentation Fault) crashes.
 * Always interact with the engine through the [mutex] gated methods.
 */
class AndroidLlmEngine : LlmEngine {
    private var engine: Engine? = null
    
    /** 
     * Persistent handle for the active dialogue.
     * Preserved across [generatePersistentStreaming] calls to maintain context continuity.
     */
    private var activeConversation: Conversation? = null
    
    companion object {
        @Volatile
        private var instance: AndroidLlmEngine? = null
        
        /** Singleton accessor ensuring a single instance of the native engine weights. */
        fun getInstance(): AndroidLlmEngine = instance ?: synchronized(this) {
            instance ?: AndroidLlmEngine().also { instance = it }
        }
    }

    private val initMutex = kotlinx.coroutines.sync.Mutex()

    /**
     * Locates and initializes the LiteRT-LM model weights.
     * Iterates through multiple potential storage paths (Internal, External, and Debug Root).
     * 
     * @param context Must be an Android [Context].
     */
    override suspend fun initialize(context: Any) {
        initMutex.withLock {
            if (engine != null) return
            val appContext = context as Context
            
            // Comprehensive search for the model file
            val potentialLocations = listOf(
                File(appContext.filesDir, "gemma.litertlm"),
                File(appContext.filesDir, "gemma.task"),
                File(appContext.getExternalFilesDir(null), "gemma.task"),
                File(appContext.getExternalFilesDir(null), "gemma.litertlm"),
                File("/data/local/tmp/gemma-2b-it-cpu-int4.bin"),
                File("/data/local/tmp/gemma.task"),
                File("/data/local/tmp/gemma.litertlm")
            )

            val modelFile = potentialLocations.find { it.exists() }
            
            try {
                if (modelFile == null) {
                    val searchedPaths = potentialLocations.joinToString("\n") { "- ${it.absolutePath}" }
            Logger.e("GemmaEngine", "Model not found. Searched:\n$searchedPaths")
                    return 
                }

                val modelPath = modelFile.absolutePath
                Logger.i("GemmaEngine", "Loading model from: $modelPath")

                val cpuConfig = EngineConfig(
                    modelPath = modelPath,
                    backend = Backend.CPU(),
                    maxNumTokens = 4096 
                )
                withContext(Dispatchers.IO) {
                    engine = Engine(cpuConfig).apply { initialize() }
                }
                Logger.i("GemmaEngine", "LiteRT-LM Engine initialized with CPU (4096 context)")
            } catch (e: Exception) {
                Logger.e("GemmaEngine", "Failed to initialize LiteRT-LM: ${e.message}")
            }
        }
    }

    private fun extractText(message: Message): String {
        return message.contents.contents
            .filterIsInstance<Content.Text>()
            .joinToString("") { it.text }
    }

    override suspend fun closeSession() {
        mutex.withLock {
            Logger.i("GemmaEngine", "Closing active session...")
            activeConversation?.close()
            activeConversation = null
        }
    }

    /** Stateless streaming. Creates a fresh conversation for every call. */
    override fun generateStreaming(prompt: String): Flow<String> = kotlinx.coroutines.flow.flow {
        val eng = engine ?: throw Exception("Engine not initialized.")
        
        closeSession()
        val conversation = withContext(Dispatchers.Default) {
            mutex.withLock {
                eng.createConversation()
            }
        }
        
        try {
            conversation.sendMessageAsync(prompt).collect { message ->
                currentCoroutineContext().ensureActive()
                emit(extractText(message))
            }
        } catch (e: Exception) {
            if (e !is kotlinx.coroutines.CancellationException) {
                Logger.e("GemmaEngine", "Streaming error: ${e.message}")
            }
            emit("Error in generation stream.")
        } finally {
            withContext(Dispatchers.Default) {
                conversation.close()
            }
        }
    }

    @OptIn(ExperimentalApi::class)
    override fun generateMultimodalStreaming(content: MultimodalContent): Flow<String> = kotlinx.coroutines.flow.flow {
        val eng = engine ?: throw Exception("Engine not initialized.")
        
        // Fresh conversation for multimodal turns to prevent context pollution (Option A requirement)
        val conversation = withContext(Dispatchers.Default) {
            mutex.withLock {
                eng.createConversation()
            }
        }
        
        try {
            val contentList = mutableListOf<Content>()
            content.text?.let { contentList.add(Content.Text(it)) }
            content.image?.let { contentList.add(Content.ImageBytes(it)) }
            content.audio?.let { contentList.add(Content.AudioBytes(it)) }
            
            val contents = Contents.of(contentList)
            
            conversation.sendMessageAsync(contents).collect { message ->
                currentCoroutineContext().ensureActive()
                emit(extractText(message))
            }
        } catch (e: Exception) {
            if (e !is kotlinx.coroutines.CancellationException) {
                Logger.e("GemmaEngine", "Multimodal streaming error: ${e.message}")
            }
            emit("Error in multimodal stream.")
        } finally {
            withContext(Dispatchers.Default) {
                conversation.close()
            }
        }
    }

    private val mutex = kotlinx.coroutines.sync.Mutex()

    /**
     * Stateful streaming via "Sticky Sessions".
     * Maintains the [activeConversation] handle across multiple calls to ensure 
     * the model remembers previous turns (e.g. Analysis -> Q&A).
     * 
     * @param isFirstTurn If true, resets the current conversation history.
     */
    override fun generatePersistentStreaming(prompt: String, isFirstTurn: Boolean): Flow<String> = kotlinx.coroutines.flow.callbackFlow {
        val eng = engine ?: throw Exception("Engine not initialized")
        
        val convo = withContext(Dispatchers.Default) {
            mutex.withLock {
                if (isFirstTurn || activeConversation == null) {
                    activeConversation?.close()
                    activeConversation = eng.createConversation()
                }
                activeConversation ?: throw Exception("Failed to create conversation")
            }
        }
        var charCount = 0
        var chunkCount = 0
        
        try {
            convo.sendMessageAsync(prompt).collect { message ->
                currentCoroutineContext().ensureActive()
                if (convo.isAlive == false) {
                    this@callbackFlow.close()
                    return@collect
                }
                val text = extractText(message)
                charCount += text.length
                chunkCount++
                
                if (chunkCount % 10 == 0) {
                    Logger.d("GemmaEngine", "Stream Progress: $chunkCount chunks, $charCount chars")
                }
                
                trySend(text)
            }
            Logger.i("GemmaEngine", "Stream complete. Total: $charCount chars")
            this@callbackFlow.close()
        } catch (e: Exception) {
            if (e !is kotlinx.coroutines.CancellationException) {
                Logger.e("GemmaEngine", "Persistent streaming error: ${e.message}")
            }
            this@callbackFlow.close(e)
        }
        
        awaitClose { /* Persist convo across flow closures */ }
    }

    override fun hasActiveConversation(): Boolean = activeConversation != null

    /** Releases all native engine and conversation resources. */
    override suspend fun close() {
        mutex.withLock {
            activeConversation?.close()
            activeConversation = null
            engine?.close()
            engine = null
            Logger.i("GemmaEngine", "Engine and session released.")
        }
    }
}

/** Entry point for the platform-specific actual implementation. */
actual fun getLlmEngine(): LlmEngine = AndroidLlmEngine.getInstance()
