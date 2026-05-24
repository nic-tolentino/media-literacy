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
import kotlinx.coroutines.asCoroutineDispatcher
import java.util.concurrent.Executors
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
    
    /** Dedicated thread for all native engine interactions to prevent thread-safety crashes. */
    private val engineContext = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
    
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

    private var isMockMode = false

    private val initMutex = kotlinx.coroutines.sync.Mutex()

    /**
     * Locates and initializes the LiteRT-LM model weights.
     * Iterates through multiple potential storage paths (Internal, External, and Debug Root).
     * 
     * @param context Must be an Android [Context].
     */
    override suspend fun initialize(context: Any) {
        initMutex.withLock {
            if (engine != null || isMockMode) return
            val appContext = context as Context
            
            // Comprehensive search for the model file
            val potentialLocations = listOf(
                // Production downloads land here (variant-named)
                File(appContext.filesDir, ModelVariant.E4B.fileName),
                File(appContext.filesDir, ModelVariant.E2B.fileName),
                // Dev fallback: push_model.sh uses the generic name — keep for local dev workflow only
                File(appContext.filesDir, "gemma.litertlm"),
                File(appContext.filesDir, "gemma.task")
            )

            val modelFile = potentialLocations.find { it.exists() }
            
            if (modelFile == null) {
                // Check if TEST variant metadata file is present
                val testFile = File(appContext.filesDir, ModelVariant.TEST.fileName)
                if (testFile.exists() && testFile.length() > 0) {
                    isMockMode = true
                    Logger.i("GemmaEngine", "Mock Mode initialized via Test Connection metadata file.")
                    return
                }
            }
            
            try {
                if (modelFile == null) {
                    val searchedPaths = potentialLocations.joinToString("\n") { "- ${it.absolutePath}" }
                    throw Exception("Model file not found. Searched paths:\n$searchedPaths")
                }

                val modelPath = modelFile.absolutePath
                val fileSizeMB = modelFile.length() / (1024 * 1024)
                Logger.i("GemmaEngine", "Loading model from: $modelPath ($fileSizeMB MB)")

                // Vision/audio adapter sections in the .litertlm model file have a `cpu`
                // backend constraint — using Backend.GPU() for these causes the SDK to
                // skip the vision encoder entirely (VisionExecutorSettings: Not set → SIGSEGV).
                // The main LLM (tf_lite_prefill_decode) has no constraint and can use GPU.
                Logger.i("GemmaEngine", "Env Check: FINGERPRINT=${android.os.Build.FINGERPRINT}, MODEL=${android.os.Build.MODEL}, PRODUCT=${android.os.Build.PRODUCT}, HARDWARE=${android.os.Build.HARDWARE}")
                
                val isEmulator = android.os.Build.FINGERPRINT.contains("generic") ||
                                android.os.Build.FINGERPRINT.contains("unknown") ||
                                android.os.Build.MODEL.contains("google_sdk") ||
                                android.os.Build.MODEL.contains("Emulator") ||
                                android.os.Build.MODEL.contains("Android SDK built for x86") ||
                                android.os.Build.MANUFACTURER.contains("Genymotion") ||
                                (android.os.Build.BRAND.startsWith("generic") && android.os.Build.DEVICE.startsWith("generic")) ||
                                android.os.Build.PRODUCT.contains("sdk_google") ||
                                android.os.Build.PRODUCT.contains("google_sdk") ||
                                android.os.Build.PRODUCT.contains("sdk") ||
                                android.os.Build.PRODUCT.contains("sdk_gphone64_arm64") ||
                                android.os.Build.HARDWARE.contains("goldfish") ||
                                android.os.Build.HARDWARE.contains("ranchu") ||
                                android.os.Build.HARDWARE.contains("virtio")
                
                // Hardware check: If we can't find libOpenCL, GPU inference is guaranteed to fail or crash on startup
                val hasOpenCL = GpuUtils.checkOpenClAvailability()
                
                Logger.d("GemmaEngine", "Hardware Check: isEmulator=$isEmulator, hasOpenCL=$hasOpenCL")
                if (!hasOpenCL) {
                    val libPaths = listOf("/system/vendor/lib64/", "/vendor/lib64/", "/vendor/lib64/egl/")
                    Logger.d("GemmaEngine", "Checking paths: ${libPaths.map { p -> p to File(p).exists() }}")
                }

                val useGpu = !isEmulator && hasOpenCL
                if (!useGpu) {
                    Logger.w("GemmaEngine", "Forcing safe CPU mode.")
                }

                val config = if (!useGpu) {
                    EngineConfig(
                        modelPath = modelPath,
                        backend = Backend.CPU(),
                        visionBackend = Backend.CPU(),
                        audioBackend = Backend.CPU(),
                        maxNumTokens = AppConfig.CPU_MAX_NUM_TOKENS,
                        maxNumImages = 1
                    )
                } else {
                    EngineConfig(
                        modelPath = modelPath,
                        backend = Backend.GPU(),
                        visionBackend = Backend.GPU(),
                        audioBackend = Backend.CPU(),
                        maxNumTokens = AppConfig.GPU_MAX_NUM_TOKENS,
                        maxNumImages = 1
                    )
                }

                withContext(engineContext) {
                    try {
                        val mode = if (useGpu) "GPU" else "CPU"
                        Logger.i("GemmaEngine", "Attempting $mode initialization (LLM + Vision)...")
                        engine = Engine(config).apply { initialize() }
                        Logger.i("GemmaEngine", "LiteRT-LM Engine initialized with $mode")
                    } catch (e: Exception) {
                        Logger.w("GemmaEngine", "Primary initialization failed: ${e.message}. Falling back to full CPU.")
                        val fallbackConfig = EngineConfig(
                            modelPath = modelPath,
                            backend = Backend.CPU(),
                            visionBackend = Backend.CPU(),
                            audioBackend = Backend.CPU(),
                            maxNumTokens = AppConfig.FALLBACK_CPU_MAX_NUM_TOKENS,
                            maxNumImages = 1
                        )
                        try {
                            engine = Engine(fallbackConfig).apply { initialize() }
                            Logger.i("GemmaEngine", "LiteRT-LM Engine initialized with emergency CPU fallback")
                        } catch (fallbackError: Exception) {
                            Logger.e("GemmaEngine", "Emergency CPU fallback failed: ${fallbackError.message}")
                            throw fallbackError // Critical failure
                        }
                    }
                }
            } catch (e: Exception) {
                Logger.e("GemmaEngine", "Failed to initialize LiteRT-LM: ${e.message}")
                throw e // Propagate the error so InferenceService knows it failed
            }
        }
    }

    private fun extractText(message: Message): String {
        return message.contents.contents
            .filterIsInstance<Content.Text>()
            .joinToString("") { it.text }
    }

    override suspend fun closeSession() {
        withContext(engineContext) {
            mutex.withLock {
                Logger.i("GemmaEngine", "Closing active session...")
                activeConversation?.close()
                activeConversation = null
            }
        }
    }

    /** Stateless streaming. Creates a fresh conversation for every call. */
    override fun generateStreaming(prompt: String): Flow<String> = kotlinx.coroutines.flow.callbackFlow {
        if (isMockMode) {
            val response = getMockResponseForPrompt(prompt)
            response.chunked(8).forEach { chunk ->
                send(chunk)
                kotlinx.coroutines.delay(30)
            }
            close()
            return@callbackFlow
        }
        val eng = engine ?: throw Exception("Engine not initialized.")

        closeSession()
        val conversation = withContext(engineContext) {
            mutex.withLock { 
                val c = eng.createConversation()
                activeConversation = c
                c
            }
        }

        try {
            withContext(engineContext) {
                conversation.sendMessageAsync(prompt).collect { message ->
                    currentCoroutineContext().ensureActive()
                    trySend(extractText(message))
                }
            }
            close()
        } catch (e: Exception) {
            if (e !is kotlinx.coroutines.CancellationException) {
                Logger.e("GemmaEngine", "Streaming error: ${e.message}")
            }
            close(e)
        }
        awaitClose()
    }

    @OptIn(ExperimentalApi::class)
    override fun generateMultimodalStreaming(content: MultimodalContent, isFirstTurn: Boolean): Flow<String> = kotlinx.coroutines.flow.callbackFlow {
        if (isMockMode) {
            val response = getMockResponseForPrompt(content.text ?: "")
            response.chunked(8).forEach { chunk ->
                send(chunk)
                kotlinx.coroutines.delay(30)
            }
            close()
            return@callbackFlow
        }
        val eng = engine ?: throw Exception("Engine not initialized.")

        val conversation = withContext(engineContext) {
            mutex.withLock { 
                if (isFirstTurn || activeConversation == null) {
                    activeConversation?.close()
                    activeConversation = eng.createConversation()
                }
                activeConversation!!
            }
        }

        try {
            // Gemma multimodal expects image/audio before the text prompt
            val contentList = mutableListOf<Content>()
            content.image?.let { contentList.add(Content.ImageBytes(it)) }
            content.audio?.let { 
                val wrapped = if (it.size > 4 && it[0] == 'R'.code.toByte() && it[1] == 'I'.code.toByte() && it[2] == 'F'.code.toByte() && it[3] == 'F'.code.toByte()) {
                    it
                } else {
                    AudioProcessor.wrapInWav(it)
                }
                contentList.add(Content.AudioBytes(wrapped)) 
            }
            content.text?.let { contentList.add(Content.Text(it)) }

            val contents = Contents.of(contentList)
            Logger.d("GemmaEngine", "Starting multimodal prefill with ${contentList.size} parts.")

            withContext(engineContext) {
                conversation.sendMessageAsync(contents).collect { message ->
                    currentCoroutineContext().ensureActive()
                    trySend(extractText(message))
                }
            }
            close()
        } catch (e: Exception) {
            if (e !is kotlinx.coroutines.CancellationException) {
                Logger.e("GemmaEngine", "Multimodal streaming error: ${e.message}")
            }
            close(e)
        }
        awaitClose()
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
        if (isMockMode) {
            val response = getMockResponseForPrompt(prompt)
            response.chunked(8).forEach { chunk ->
                send(chunk)
                kotlinx.coroutines.delay(30)
            }
            close()
            return@callbackFlow
        }
        val eng = engine ?: throw Exception("Engine not initialized")
        
        val convo = withContext(engineContext) {
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
            withContext(engineContext) {
                convo.sendMessageAsync(prompt).collect { message ->
                    currentCoroutineContext().ensureActive()
                    if (convo.isAlive == false) {
                        return@collect
                    }
                    val text = extractText(message)
                    charCount += text.length
                    chunkCount++
                    
                    
                    trySend(text)
                }
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

    override fun hasActiveConversation(): Boolean = if (isMockMode) true else activeConversation != null

    /** Releases all native engine and conversation resources. */
    override suspend fun close() {
        withContext(engineContext) {
            mutex.withLock {
                activeConversation?.close()
                activeConversation = null
                engine?.close()
                engine = null
                isMockMode = false
                Logger.i("GemmaEngine", "Engine and session released.")
            }
        }
    }

    private fun getMockResponseForPrompt(prompt: String): String {
        return when {
            prompt.contains("fullTranscript") -> {
                "{\"fullTranscript\": \"[Test Connection Transcription]: This is a test scan image.\"}"
            }
            prompt.contains("summary") -> {
                "{\"summary\": \"This is a mock connection test summary. The download pipeline is verified! Please download Gemma 4 E2B or E4B for real analysis.\"}"
            }
            prompt.contains("keyClaims") -> {
                "{\"keyClaims\": [\"Connection test succeeded\", \"App is running in mock mode\", \"Gemma 4 model needs to be downloaded\"]}"
            }
            prompt.contains("objectivityScore") -> {
                "{\"objectivityScore\": 100, \"logicScore\": 100, \"evidenceQuality\": 100, \"credibilityScore\": 100, \"credibility\": \"Verified\"}"
            }
            prompt.contains("vocalTone") -> {
                "{\"vocalTone\": \"Overall vocal tone is positive and clear.\"}"
            }
            prompt.contains("type") && prompt.contains("evidence") -> {
                "[{\"type\": \"Test Pattern\", \"evidence\": \"Connection verified\", \"description\": \"This is a test fallacy check. Your pipeline is fully functional!\"}]"
            }
            prompt.contains("socraticQuestions") -> {
                "{\"socraticQuestions\": [\"Did the test download work as expected?\", \"Are you ready to download a real Gemma model?\", \"How will you deconstruct news bias?\"]}"
            }
            else -> {
                "This is the Socratic Guide (Test Connection mode). Since you are running the 1 KB connection test, actual AI inference is simulated. Go back and download Gemma 4 E2B or E4B to analyze live articles."
            }
        }
    }
}

/** Entry point for the platform-specific actual implementation. */
actual fun getLlmEngine(): LlmEngine = AndroidLlmEngine.getInstance()
