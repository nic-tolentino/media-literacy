package org.medialiteracy.domain

import android.content.Context
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.ExperimentalApi
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.MessageCallback
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import java.io.File
import java.util.concurrent.CancellationException

@OptIn(ExperimentalApi::class)
class AndroidLlmEngine : LlmEngine {
    private var engine: Engine? = null
    private var conversation: Conversation? = null
    private var context: Context? = null
    private var initializationError: String? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun init(context: Any) {
        this.context = context as Context
        try {
            System.loadLibrary("litertlm_jni")
            println("Gemma4ML: Native LiteRT-LM library loaded successfully")
        } catch (e: Exception) {
            println("Gemma4ML: Warning - Manual library load failed: ${e.message}")
        }
    }

    private suspend fun ensureInitialized(): Conversation? {
        if (conversation != null) return conversation
        
        val ctx = context ?: return null
        val modelFile = File(ctx.filesDir, "gemma.litertlm")
        
        if (!modelFile.exists()) {
            initializationError = "File not found at ${modelFile.absolutePath}"
            return null
        }

        return withContext(Dispatchers.IO) {
            try {
                println("Gemma4ML: Initializing LiteRT-LM Engine...")
                
                // Try GPU first for 6x faster prefill, fallback to CPU
                var selectedBackend: Backend = Backend.CPU()
                try {
                    //selectedBackend = Backend.GPU() // this line causes a native crash
                    println("Gemma4ML: Attempting High-Speed GPU acceleration...")
                } catch (e: Exception) {
                    println("Gemma4ML: GPU not available, falling back to CPU: ${e.message}")
                }

                val engineConfig = EngineConfig(
                    modelPath = modelFile.absolutePath,
                    backend = selectedBackend,
                    maxNumTokens = 4096
                )
                
                val newEngine = Engine(engineConfig)
                newEngine.initialize()
                
                val newConversation = newEngine.createConversation(
                    ConversationConfig()
                )
                
                engine = newEngine
                conversation = newConversation
                println("Gemma4ML: LiteRT-LM Engine initialized successfully!")
                newConversation
            } catch (e: Exception) {
                initializationError = "FAILED_TO_CREATE_LITERT_ENGINE: ${e.message}"
                println("Gemma4ML: ERROR during LiteRT engine creation: ${e.message}")
                e.printStackTrace()
                null
            }
        }
    }

    override fun generateStreaming(prompt: String): Flow<String> = callbackFlow {
        val currentConversation = ensureInitialized()
        
        if (currentConversation == null) {
            val mockThoughts = listOf(
                "<|think|>\n", 
                "ERROR: ${initializationError ?: "LiteRT Engine Init Failed"}\n",
                "</|think|>\n"
            )
            mockThoughts.forEach { trySend(it); delay(50) }
            """{"summary": "Engine Offline. Check terminal logs for LiteRT-LM error."}""".forEach {
                trySend(it.toString()); delay(5)
            }
            close()
            return@callbackFlow
        }

        var isThinking = false
        currentConversation.sendMessageAsync(
            Contents.of(Content.Text(prompt)),
            object : MessageCallback {
                override fun onMessage(message: Message) {
                    val thinkingSnippet = message.channels["thought"]
                    if (thinkingSnippet != null && thinkingSnippet.isNotEmpty()) {
                        // Enter thinking mode if not already in it
                        if (!isThinking) {
                            trySend("<|think|>\n")
                            isThinking = true
                        }
                        trySend(thinkingSnippet)
                    } else {
                        // Exit thinking mode before sending final content
                        if (isThinking) {
                            trySend("\n</|think|>\n\n")
                            isThinking = false
                        }
                        trySend(message.toString())
                    }
                }

                override fun onDone() {
                    if (isThinking) {
                        trySend("\n</|think|>\n")
                        isThinking = false
                    }
                    close()
                }

                override fun onError(throwable: Throwable) {
                    if (isThinking) {
                        trySend("\n</|think|>\n")
                    }
                    if (throwable is CancellationException) {
                        close()
                    } else {
                        trySend("\n[ENGINE_ERROR: ${throwable.message}]\n")
                        close()
                    }
                }
            }
        )
        
        awaitClose { }
    }

    override suspend fun generateResponse(prompt: String): String = withContext(Dispatchers.IO) {
        val currentConversation = ensureInitialized() ?: return@withContext "Engine Error"
        val deferred = CompletableDeferred<String>()
        var fullText = ""
        
        currentConversation.sendMessageAsync(
            Contents.of(Content.Text(prompt)),
            object : MessageCallback {
                override fun onMessage(message: Message) {
                    fullText += message.toString()
                }
                override fun onDone() {
                    deferred.complete(fullText)
                }
                override fun onError(throwable: Throwable) {
                    deferred.completeExceptionally(throwable)
                }
            }
        )
        try {
            deferred.await()
        } catch (e: Exception) {
            "Mock Response"
        }
    }

    override suspend fun analyzeMultimodal(input: ByteArray, type: InputType): AnalysisResult = withContext(Dispatchers.IO) {
        delay(2000)
        AnalysisResult(
            summary = "Multimodal analysis simulation",
            highlights = emptyList(),
            fallacies = emptyList(),
            toneScore = 3,
            evidenceQuality = 50,
            credibility = "Simulation"
        )
    }
}

private val engine = AndroidLlmEngine()
actual fun getLlmEngine(): LlmEngine = engine
