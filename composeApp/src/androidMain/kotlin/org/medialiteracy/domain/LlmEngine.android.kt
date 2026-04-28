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
import java.io.File

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

    /**
     * Locates and initializes the LiteRT-LM model weights.
     * Iterates through multiple potential storage paths (Internal, External, and Debug Root).
     * 
     * @param context Must be an Android [Context].
     */
    override fun initialize(context: Any) {
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
                android.util.Log.e("GemmaEngine", "Model not found. Searched:\n$searchedPaths")
                return 
            }

            val modelPath = modelFile.absolutePath
            android.util.Log.i("GemmaEngine", "Loading model from: $modelPath")

            val config = EngineConfig(
                modelPath = modelPath,
                backend = Backend.CPU(),
                maxNumTokens = 4096 // Context window size for deep-dive analysis.
            )

            engine = Engine(config).apply {
                initialize()
            }
            android.util.Log.i("GemmaEngine", "LiteRT-LM Engine initialized successfully")
        } catch (e: Exception) {
            android.util.Log.e("GemmaEngine", "Failed to initialize LiteRT-LM: ${e.message}")
        }
    }

    private fun extractText(message: Message): String {
        return message.contents.contents
            .filterIsInstance<Content.Text>()
            .joinToString("") { it.text }
    }

    /** Stateless streaming. Creates a fresh conversation for every call. */
    override fun generateStreaming(prompt: String): Flow<String> = kotlinx.coroutines.flow.flow {
        val eng = engine ?: throw Exception("Engine not initialized.")
        
        mutex.withLock {
            activeConversation?.close()
            activeConversation = null
        }
        
        val conversation = eng.createConversation()
        
        try {
            conversation.sendMessageAsync(prompt).collect { message ->
                emit(extractText(message))
            }
        } catch (e: Exception) {
            if (e !is kotlinx.coroutines.CancellationException) {
                android.util.Log.e("GemmaEngine", "Streaming error: ${e.message}")
            }
            emit("Error in generation stream.")
        } finally {
            conversation.close()
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
        
        mutex.withLock {
            if (isFirstTurn || activeConversation == null) {
                activeConversation?.close()
                activeConversation = eng.createConversation()
            }
        }
        
        val convo = activeConversation ?: throw Exception("Failed to create conversation")
        var charCount = 0
        var chunkCount = 0
        
        try {
            convo.sendMessageAsync(prompt).collect { message ->
                if (convo.isAlive == false) {
                    this@callbackFlow.close()
                    return@collect
                }
                val text = extractText(message)
                charCount += text.length
                chunkCount++
                
                if (chunkCount % 10 == 0) {
                    android.util.Log.d("GemmaEngine", "Stream Progress: $chunkCount chunks, $charCount chars")
                }
                
                trySend(text)
            }
            android.util.Log.i("GemmaEngine", "Stream complete. Total: $charCount chars")
            this@callbackFlow.close()
        } catch (e: Exception) {
            if (e !is kotlinx.coroutines.CancellationException) {
                android.util.Log.e("GemmaEngine", "Persistent streaming error: ${e.message}")
            }
            this@callbackFlow.close(e)
        }
        
        awaitClose { /* Persist convo across flow closures */ }
    }

    override fun hasActiveConversation(): Boolean = activeConversation != null

    /** Blocking response generation. */
    override suspend fun generateResponse(prompt: String): String {
        return withContext(Dispatchers.Default) {
            val eng = engine ?: throw Exception("Engine not initialized")
            mutex.withLock {
                activeConversation?.close()
                activeConversation = null
            }
            eng.createConversation().use { conversation ->
                val response = conversation.sendMessage(prompt)
                extractText(response)
            }
        }
    }

    /** Vision-enabled analytical stub for upcoming multimodal support. */
    override suspend fun analyzeMultimodal(input: ByteArray, type: InputType): AnalysisResult {
        throw Exception("Multimodal analysis is coming in a future update...")
    }

    /** Releases all native engine and conversation resources. */
    override fun close() {
        activeConversation?.close()
        engine?.close()
        engine = null
        activeConversation = null
    }
}

/** Entry point for the platform-specific actual implementation. */
actual fun getLlmEngine(): LlmEngine = AndroidLlmEngine.getInstance()
