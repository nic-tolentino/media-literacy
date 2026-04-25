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

class AndroidLlmEngine : LlmEngine {
    private var engine: Engine? = null
    private var activeConversation: Conversation? = null
    
    companion object {
        @Volatile
        private var instance: AndroidLlmEngine? = null
        fun getInstance(): AndroidLlmEngine = instance ?: synchronized(this) {
            instance ?: AndroidLlmEngine().also { instance = it }
        }
    }

    override fun initialize(context: Any) {
        if (engine != null) return
        val appContext = context as Context
        
        // Comprehensive search for the model file
        val potentialLocations = listOf(
            File(appContext.filesDir, "gemma.litertlm"), // User's known good path
            File(appContext.filesDir, "gemma.task"), // Internal
            File(appContext.getExternalFilesDir(null), "gemma.task"), // External (ModelManager)
            File(appContext.getExternalFilesDir(null), "gemma.litertlm"),
            File("/data/local/tmp/gemma-2b-it-cpu-int4.bin"), // Manual adb push root
            File("/data/local/tmp/gemma.task"),
            File("/data/local/tmp/gemma.litertlm")
        )

        val modelFile = potentialLocations.find { it.exists() }
        
        try {
            if (modelFile == null) {
                val searchedPaths = potentialLocations.joinToString("\n") { "- ${it.absolutePath}" }
                android.util.Log.e("GemmaEngine", "Model not found. Searched:\n$searchedPaths")
                // We keep engine null, but we'll throw an informative error later
                return 
            }

            val modelPath = modelFile.absolutePath
            android.util.Log.i("GemmaEngine", "Loading model from: $modelPath")

            val config = EngineConfig(
                modelPath = modelPath,
                backend = Backend.CPU(),
                maxNumTokens = 4096 // Restored to user's working configuration
            )

            engine = Engine(config).apply {
                initialize()
            }
            android.util.Log.i("GemmaEngine", "LiteRT-LM Engine initialized successfully with 4096 context window")
        } catch (e: Exception) {
            android.util.Log.e("GemmaEngine", "Failed to initialize LiteRT-LM: ${e.message}")
        }
    }

    private fun extractText(message: Message): String {
        return message.contents.contents
            .filterIsInstance<Content.Text>()
            .joinToString("") { it.text }
    }

    override fun generateStreaming(prompt: String): Flow<String> {
        val eng = engine ?: throw Exception("Engine not initialized. Check if the model exists in the app's files or /data/local/tmp/.")
        val conversation = eng.createConversation()
        
        return conversation.sendMessageAsync(prompt)
            .map { extractText(it) }
            .catch { e -> 
                android.util.Log.e("GemmaEngine", "Streaming error: ${e.message}")
                emit("Error in generation stream.")
            }
    }

    private val mutex = kotlinx.coroutines.sync.Mutex()

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
            android.util.Log.e("GemmaEngine", "Persistent streaming error: ${e.message}")
            this@callbackFlow.close(e)
        }
        
        awaitClose { /* Persist convo */ }
    }

    override suspend fun generateResponse(prompt: String): String {
        return withContext(Dispatchers.Default) {
            val eng = engine ?: throw Exception("Engine not initialized")
            eng.createConversation().use { conversation ->
                val response = conversation.sendMessage(prompt)
                extractText(response)
            }
        }
    }

    override suspend fun analyzeMultimodal(input: ByteArray, type: InputType): AnalysisResult {
        throw Exception("Multimodal analysis is coming in a future update...")
    }

    override fun close() {
        activeConversation?.close()
        engine?.close()
        engine = null
        activeConversation = null
    }
}

actual fun getLlmEngine(): LlmEngine = AndroidLlmEngine.getInstance()
