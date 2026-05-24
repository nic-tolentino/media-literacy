package org.medialiteracy.domain

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.datetime.Clock
import kotlinx.serialization.json.Json
import kotlinx.serialization.decodeFromString

/**
 * Orchestrates the multi-stage analysis pipeline and manages task-level state.
 * This class bridges the raw InferenceService (token generation) and the UI's 
 * need for structured AnalysisResults.
 */
class AnalysisCoordinator(
    private val inferenceService: InferenceService,
    private val repository: AnalysisRepository,
    private val scope: CoroutineScope,
    private val transcriber: ImageTranscriber? = null,
    private val resizer: ImageResizer? = null
) {
    private val _state = MutableStateFlow<InferenceState>(InferenceState.Idle)
    val state: StateFlow<InferenceState> = _state.asStateFlow()

    private val _socraticSession = MutableStateFlow<SocraticSession?>(null)
    val socraticSession: StateFlow<SocraticSession?> = _socraticSession.asStateFlow()

    private var currentArticle: String? = null
    private var currentResult: AnalysisResult? = null
    private var analysisJob: Job? = null

    @Volatile
    var isFreshAnalysis: Boolean = false
        private set

    val currentArticleText: String? get() = currentArticle
    val currentAnalysisResult: AnalysisResult? get() = currentResult

    fun consumeFreshAnalysis() {
        isFreshAnalysis = false
    }

    fun updateSocraticSession(session: SocraticSession?) {
        _socraticSession.value = session
    }

    fun reset(initialMessage: String = "Idle") {
        analysisJob?.cancel()
        currentResult = null
        currentArticle = null
        _socraticSession.value = null
        _state.value = if (initialMessage == "Idle") InferenceState.Idle else InferenceState.Thinking(initialMessage)
    }
    
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /**
     * Loads a previously saved result into the coordinator without triggering inference.
     */
    fun loadResult(article: String, result: AnalysisResult) {
        isFreshAnalysis = false
        currentArticle = article
        currentResult = result
        _state.value = InferenceState.Complete(result)
        
        // Prime the engine with context in the background for chat readiness
        scope.launch {
            try {
                inferenceService.execute(InferenceCommand.Prime(article)).collect()
            } catch (e: Exception) {
                Logger.e("AnalysisCoordinator", "Priming failed: ${e.message}")
            }
        }
    }

    /**
     * Starts the two-stage analysis pipeline.
     */
    fun startAnalysis(article: String) {
        // Enforce V1 Hard Limit
        if (article.length > AppConfig.MAX_ARTICLE_LENGTH) {
            _state.value = InferenceState.SourceTooLarge("This article is too long for a deep-dive analysis. Please try a shorter excerpt (under ${AppConfig.MAX_ARTICLE_LENGTH} characters).")
            return
        }

        isFreshAnalysis = true
        currentArticle = article
        reset("Analyzing document...")
        analysisJob = scope.launch {
            try {
                // Prime the session with the article context
                _state.value = InferenceState.Thinking("Priming context...")
                inferenceService.execute(InferenceCommand.Prime(article)).collect()

                // STAGE 1: Perception (Summary & Highlights)
                _state.value = InferenceState.Thinking("Analyzing document...")
                val summaryPrompt = SummaryStage.buildPrompt()
                
                var summaryResponse = ""
                inferenceService.execute(InferenceCommand.Chat(summaryPrompt)).collect { token ->
                    summaryResponse += token
                    _state.value = InferenceState.Thinking(summaryResponse)
                }
                if (summaryResponse.isBlank()) throw Exception("Engine failed to generate a summary.")

                // Initial result with summary
                val initialResult = SummaryStage.parse(summaryResponse).copy(
                    isSummaryLoading = false,
                    isClaimsLoading = true,
                    isMetricsLoading = true,
                    isFallaciesLoading = true,
                    isSocraticLoading = true,
                    fullTranscript = article
                )
                currentResult = initialResult
                _state.value = InferenceState.Complete(initialResult)

                // STAGE 2: Extraction (Claims)
                val claimsPrompt = ClaimsStage.buildPrompt()
                var claimsResponse = ""
                inferenceService.execute(InferenceCommand.Chat(claimsPrompt)).collect { token ->
                    claimsResponse += token
                }
                if (claimsResponse.isBlank()) throw Exception("Engine failed to extract claims.")

                val claimsData = try {
                    json.decodeFromString<AnalysisResult>(extractJsonObject(claimsResponse))
                } catch (e: Exception) {
                    Logger.e("AnalysisCoordinator", "Text Claims parse failed: ${e.message} | raw: ${claimsResponse.takeLast(500)}")
                    initialResult
                }

                val updatedResultWithClaims = initialResult.copy(
                    keyClaims = claimsData.keyClaims,
                    isClaimsLoading = false
                )
                currentResult = updatedResultWithClaims
                _state.value = InferenceState.Complete(updatedResultWithClaims)

                // STAGE 3: Metrication (Scores)
                val metricsPrompt = MetricsStage.buildPrompt()
                var metricsResponse = ""
                inferenceService.execute(InferenceCommand.Chat(metricsPrompt)).collect { token ->
                    metricsResponse += token
                }
                if (metricsResponse.isBlank()) throw Exception("Engine failed to generate metrics.")
                
                val scoresResult = try {
                    json.decodeFromString<AnalysisResult>(extractJsonObject(metricsResponse))
                } catch (e: Exception) {
                    Logger.e("AnalysisCoordinator", "Text Metrics parse failed: ${e.message} | raw: ${metricsResponse.takeLast(500)}")
                    updatedResultWithClaims
                }

                val updatedResultWithScores = updatedResultWithClaims.copy(
                    objectivityScore = scoresResult.objectivityScore,
                    logicScore = scoresResult.logicScore,
                    evidenceQuality = scoresResult.evidenceQuality,
                    credibilityScore = scoresResult.credibilityScore,
                    credibility = scoresResult.credibility,
                    isMetricsLoading = false
                )
                currentResult = updatedResultWithScores
                _state.value = InferenceState.Complete(updatedResultWithScores)


                // STAGE 4: Socratic Bridge
                var socraticResult = updatedResultWithScores
                try {
                    Logger.i("AnalysisCoordinator", "Initiating Socratic Bridge...")
                    val socraticPrompt = SocraticStage.buildPrompt()
                    var socraticResponse = ""
                    inferenceService.execute(InferenceCommand.Chat(socraticPrompt)).collect { token ->
                        socraticResponse += token
                    }
                    Logger.d("AnalysisCoordinator", "RAW SOCRATIC RESPONSE: $socraticResponse")
                    val extractedJson = extractJsonObject(socraticResponse)
                    Logger.d("AnalysisCoordinator", "EXTRACTED SOCRATIC JSON: $extractedJson")
                    
                    val socraticData = json.decodeFromString<AnalysisResult>(extractedJson)
                    Logger.i("AnalysisCoordinator", "PARSED SOCRATIC QUESTIONS: ${socraticData.socraticQuestions}")
                    
                    socraticResult = updatedResultWithScores.copy(
                        socraticQuestions = socraticData.socraticQuestions,
                        isSocraticLoading = false
                    )
                } catch (e: Exception) {
                    Logger.e("AnalysisCoordinator", "Socratic bridge stage failed: ${e.message}")
                    socraticResult = updatedResultWithScores.copy(isSocraticLoading = false)
                }
                currentResult = socraticResult
                _state.value = InferenceState.Complete(socraticResult)

                // STAGE 5: Fallacies (Deep Scan)
                var finalResult = socraticResult
                try {
                    Logger.i("AnalysisCoordinator", "Starting background deep scan...")
                    val fallacyPrompt = FallacyStage.buildPrompt()
                    var fallacyResponse = ""
                    inferenceService.execute(InferenceCommand.Chat(fallacyPrompt)).collect { token ->
                        fallacyResponse += token
                    }
                    if (fallacyResponse.isBlank()) {
                        Logger.w("AnalysisCoordinator", "Empty fallacy response, skipping fallacy details.")
                        finalResult = socraticResult.copy(isFallaciesLoading = false)
                    } else {
                        Logger.d("AnalysisCoordinator", "RAW FALLACY RESPONSE: ${fallacyResponse.takeLast(500)}")
                        val fallacies = FallacyStage.parse(fallacyResponse)
                        finalResult = socraticResult.copy(
                            fallacies = fallacies,
                            isFallaciesLoading = false
                        )
                    }
                } catch (e: Exception) {
                    Logger.e("AnalysisCoordinator", "Fallacy stage failed: ${e.message}")
                    finalResult = socraticResult.copy(isFallaciesLoading = false)
                }

                currentResult = finalResult
                _state.value = InferenceState.Complete(finalResult)

                // Persist
                repository.saveAnalysis(
                    SavedAnalysis(
                        id = Clock.System.now().toEpochMilliseconds().toString(),
                        timestamp = Clock.System.now().toEpochMilliseconds(),
                        originalArticleText = article,
                        analysisResult = finalResult
                    )
                )

            } catch (e: Exception) {
                if (e !is CancellationException) {
                    _state.value = InferenceState.Error(e.message ?: "Analysis failed")
                }
            }
        }
    }

    /**
     * Sends a chat message to the active session.
     */
    fun sendChat(message: String): Flow<String> = flow {
        var response = ""
        // Apply V1 Verbosity & Expert Guidance framing
        val chatPrompt = "$message\nExpert Guidance (be concise):"
        
        inferenceService.execute(InferenceCommand.Chat(chatPrompt)).collect { token ->
            response += token
            emit(response)
        }
    }

    /**
     * Starts image analysis with **Cactus-Style Intelligent Routing**.
     * 
     * To optimize for mobile efficiency (latency and battery), the coordinator first performs 
     * a lightweight text density scan. 
     * 
     * - **High Text Density (>= 20 words)**: Routes to a high-fidelity native OCR pipeline (ML Kit).
     * - **Low Text Density / Visual Content**: Falls back to a deep multimodal vision path (Gemma 4).
     * 
     * This intelligent routing ensures the most efficient use of device resources while 
     * maintaining high analytical accuracy.
     */
    fun startImageAnalysis(imageBytes: ByteArray) {
        analysisJob?.cancel()
        analysisJob = scope.launch {
            _state.value = InferenceState.Thinking("Scanning image for text...")
            
            // Phase 1: OCR-First (ML Kit)
            val extractedText = transcriber?.transcribe(imageBytes)
            val words = extractedText?.trim()?.split(Regex("\\s+"))?.filter { it.isNotBlank() } ?: emptyList()
            val wordCount = words.size
            
            if (wordCount >= 20) {
                Logger.d("AnalysisCoordinator", "OCR found $wordCount words. Routing to high-fidelity text pipeline.")
                startAnalysis(extractedText!!)
                return@launch
            }
            
            if (wordCount > 0) {
                Logger.d("AnalysisCoordinator", "OCR found only $wordCount words. Falling back to multimodal vision.")
            } else {
                Logger.d("AnalysisCoordinator", "No text found by OCR. Using multimodal vision.")
            }

            // Phase 2: Multimodal Fallback
            _state.value = InferenceState.Thinking("Preparing image for AI analysis...")
            
            // Letterbox to 448x448 for model consistency
            val finalBytes = resizer?.letterbox(imageBytes, 448, 448) ?: imageBytes

            try {
                // STAGE 0: Multimodal Transcription (OCR Fallback)
                _state.value = InferenceState.Thinking("Transcribing image text...")
                val transPrompt = ImageTranscriptionStage.buildPrompt("Analyze this image for logical fallacies or bias.")
                
                var transResponse = ""
                inferenceService.execute(
                    InferenceCommand.AnalyzeMultimodal(
                        type = MultimodalType.IMAGE,
                        data = finalBytes,
                        prompt = transPrompt
                    )
                ).collect { token ->
                    transResponse += token
                    _state.value = InferenceState.Thinking(transResponse)
                }

                Logger.d("AnalysisCoordinator", "RAW TRANSCRIPTION RESPONSE: $transResponse")

                val transData = SummaryStage.parse(transResponse)
                val fullTranscript = if (!transData.fullTranscript.isNullOrBlank()) {
                    transData.fullTranscript
                } else {
                    "AI OBSERVATION: (No clear text detected in image)"
                }

                // Prime the session with the transcribed text so subsequent turns have context
                _state.value = InferenceState.Thinking("Priming context...")
                inferenceService.execute(InferenceCommand.Prime(fullTranscript)).collect()

                // STAGE 1: Summary (Executive Perception)
                _state.value = InferenceState.Thinking("Summarizing content...")
                val summaryPrompt = SummaryStage.buildPrompt()
                
                var summaryResponse = ""
                inferenceService.execute(InferenceCommand.Chat(summaryPrompt)).collect { token ->
                    summaryResponse += token
                    _state.value = InferenceState.Thinking(summaryResponse)
                }

                // Initial result with summary
                val initialResult = SummaryStage.parse(summaryResponse).copy(
                    isSummaryLoading = false,
                    isClaimsLoading = true,
                    isMetricsLoading = true,
                    isSocraticLoading = true,
                    isFallaciesLoading = true,
                    fullTranscript = fullTranscript
                )
                
                currentResult = initialResult
                _state.value = InferenceState.Complete(initialResult)

                // STAGE 2: Extraction (Claims)
                val claimsPrompt = ClaimsStage.buildPrompt()
                var claimsResponse = ""
                inferenceService.execute(InferenceCommand.Chat(claimsPrompt)).collect { token ->
                    claimsResponse += token
                }

                val claimsData = try {
                    json.decodeFromString<AnalysisResult>(extractJsonObject(claimsResponse))
                } catch (e: Exception) {
                    Logger.e("AnalysisCoordinator", "Claims parse failed: ${e.message} | raw: ${claimsResponse.takeLast(500)}")
                    initialResult
                }

                val updatedResultWithClaims = initialResult.copy(
                    keyClaims = claimsData.keyClaims,
                    isClaimsLoading = false
                )
                currentResult = updatedResultWithClaims
                _state.value = InferenceState.Complete(updatedResultWithClaims)

                // STAGE 3: Metrication (Scores)
                val metricsPrompt = MetricsStage.buildPrompt()
                var metricsResponse = ""
                inferenceService.execute(InferenceCommand.Chat(metricsPrompt)).collect { token ->
                    metricsResponse += token
                }
                
                val scoresResult = try {
                    json.decodeFromString<AnalysisResult>(extractJsonObject(metricsResponse))
                } catch (e: Exception) {
                    Logger.e("AnalysisCoordinator", "Metrics parse failed: ${e.message} | raw: ${metricsResponse.takeLast(500)}")
                    updatedResultWithClaims
                }

                val updatedResultWithScores = updatedResultWithClaims.copy(
                    objectivityScore = scoresResult.objectivityScore,
                    logicScore = scoresResult.logicScore,
                    evidenceQuality = scoresResult.evidenceQuality,
                    credibilityScore = scoresResult.credibilityScore,
                    credibility = scoresResult.credibility,
                    isMetricsLoading = false
                )
                currentResult = updatedResultWithScores
                _state.value = InferenceState.Complete(updatedResultWithScores)

                // STAGE 4: Socratic Bridge
                var socraticResult = updatedResultWithScores
                try {
                    Logger.i("AnalysisCoordinator", "Initiating Socratic Bridge for image...")
                    val socraticPrompt = SocraticStage.buildPrompt()
                    var socraticResponse = ""
                    inferenceService.execute(InferenceCommand.Chat(socraticPrompt)).collect { token ->
                        socraticResponse += token
                    }
                    val extractedJson = extractJsonObject(socraticResponse)
                    val socraticData = json.decodeFromString<AnalysisResult>(extractedJson)
                    socraticResult = updatedResultWithScores.copy(
                        socraticQuestions = socraticData.socraticQuestions,
                        isSocraticLoading = false
                    )
                } catch (e: Exception) {
                    Logger.e("AnalysisCoordinator", "Image Socratic bridge stage failed: ${e.message}")
                    socraticResult = updatedResultWithScores.copy(isSocraticLoading = false)
                }
                currentResult = socraticResult
                _state.value = InferenceState.Complete(socraticResult)

                // STAGE 5: Fallacies (Deep Scan)
                var verifiedResult = socraticResult
                try {
                    val fallacyPrompt = FallacyStage.buildPrompt()
                    var fallacyResponse = ""
                    inferenceService.execute(InferenceCommand.Chat(fallacyPrompt)).collect { token ->
                        fallacyResponse += token
                    }
                    val fallacies = FallacyStage.parse(fallacyResponse)
                    verifiedResult = socraticResult.copy(
                        fallacies = fallacies,
                        isFallaciesLoading = false
                    )
                } catch (e: Exception) {
                    Logger.e("AnalysisCoordinator", "Image fallacy stage failed: ${e.message}")
                    verifiedResult = socraticResult.copy(isFallaciesLoading = false)
                }
                
                currentResult = verifiedResult
                _state.value = InferenceState.Complete(verifiedResult)

                // Persist
                repository.saveAnalysis(
                    SavedAnalysis(
                        id = Clock.System.now().toEpochMilliseconds().toString(),
                        timestamp = Clock.System.now().toEpochMilliseconds(),
                        originalArticleText = fullTranscript,
                        analysisResult = verifiedResult
                    )
                )

            } catch (e: Exception) {
                if (e !is CancellationException) {
                    _state.value = InferenceState.Error(e.message ?: "Image analysis failed")
                }
            }
        }
    }

    /**
     * Starts the chunked audio analysis pipeline.
     * 
     * IMPORTANT: Expects raw 16kHz mono 16-bit PCM. Normalize formats before calling.
     */
    fun startAudioAnalysis(audioBytes: ByteArray) {
        val chunker = AudioChunker()
        val chunks = chunker.chunk(audioBytes)
        
        // Enforce V1 Limit (5 minutes @ 16kHz mono 16bit = ~9.6MB)
        if (chunks.size > 12) {
            _state.value = InferenceState.SourceTooLarge("Audio is too long. V1 supports up to 5 minutes of recording.")
            return
        }

        currentArticle = "[Audio Analysis]"
        reset("Analyzing audio segment 1/${chunks.size}...")
        analysisJob = scope.launch {
            try {
                val observations = mutableListOf<ChunkObservation>()
                
                chunks.forEachIndexed { index, chunk ->
                    _state.value = InferenceState.Thinking("Analyzing audio segment ${index + 1}/${chunks.size}...")
                    
                    val prompt = AudioAnalysisStage.buildPrompt(chunk.timestamp)
                    
                    var chunkResponse = ""
                    inferenceService.execute(
                        InferenceCommand.AnalyzeMultimodal(
                            type = MultimodalType.AUDIO,
                            data = AudioProcessor.wrapInWav(chunk.data),
                            prompt = prompt,
                            isFirstTurn = true
                        )
                    ).collect { token ->
                        chunkResponse += token
                        _state.value = InferenceState.Thinking(
                            "Analyzing segment ${index + 1}/${chunks.size}...\n\n$chunkResponse"
                        )
                    }
                    
                    val observation = AudioAnalysisStage.parse(chunkResponse)
                    if (observation != null) {
                        observations.add(observation)
                        val partialTranscript = mergeTranscripts(observations)
                        _state.value = InferenceState.Thinking(partialTranscript)
                        Logger.d("AnalysisCoordinator", "Chunk ${index + 1} done: transcript length=${observation.transcript.length}")
                    } else {
                        Logger.w("AnalysisCoordinator", "Chunk ${index + 1} parsing FAILED. Response: $chunkResponse")
                    }
                }
                
                val finalTranscript = mergeTranscripts(observations)
                Logger.i("AnalysisCoordinator", "Audio processing finished. Collected ${observations.size}/${chunks.size} segments.")
                Logger.i("AnalysisCoordinator", "FINAL AGGREGATED TRANSCRIPT:\n$finalTranscript")

                if (observations.isEmpty()) {
                    _state.value = InferenceState.Error("Could not analyze audio content.")
                    return@launch
                }

                // Combine transcript and observations for the first prompt context
                val obsContext = observations.joinToString("\n") { "Segment ${it.timestamp}: ${it.dominantTone}" }
                val synthesisInput = "TRANSCRIPT:\n$finalTranscript\n\nOBSERVATIONS:\n$obsContext"

                // Prime the session with the transcribed audio text so subsequent turns have context
                _state.value = InferenceState.Thinking("Priming context...")
                inferenceService.execute(InferenceCommand.Prime(synthesisInput)).collect()

                // STAGE 1: Summary (Executive Perception)
                _state.value = InferenceState.Thinking("Summarizing content...")
                val summaryPrompt = SummaryStage.buildPrompt()
                var summaryResponse = ""
                inferenceService.execute(InferenceCommand.Chat(summaryPrompt)).collect { token ->
                    summaryResponse += token
                    _state.value = InferenceState.Thinking(summaryResponse)
                }

                // Initial result with summary
                val initialResult = SummaryStage.parse(summaryResponse).copy(
                    isSummaryLoading = false,
                    isClaimsLoading = true,
                    isMetricsLoading = true,
                    isVocalToneLoading = true,
                    isSocraticLoading = true,
                    isFallaciesLoading = true,
                    fullTranscript = finalTranscript
                )
                currentResult = initialResult
                _state.value = InferenceState.Complete(initialResult)

                // STAGE 2: Extraction (Claims)
                val claimsPrompt = ClaimsStage.buildPrompt()
                var claimsResponse = ""
                inferenceService.execute(InferenceCommand.Chat(claimsPrompt)).collect { token ->
                    claimsResponse += token
                }

                val claimsData = try {
                    json.decodeFromString<AnalysisResult>(extractJsonObject(claimsResponse))
                } catch (e: Exception) {
                    Logger.e("AnalysisCoordinator", "Audio Claims parse failed: ${e.message} | raw: ${claimsResponse.takeLast(500)}")
                    initialResult
                }

                val updatedResultWithClaims = initialResult.copy(
                    keyClaims = claimsData.keyClaims,
                    isClaimsLoading = false
                )
                currentResult = updatedResultWithClaims
                _state.value = InferenceState.Complete(updatedResultWithClaims)

                // STAGE 3: Metrication (Scores)
                val metricsPrompt = MetricsStage.buildPrompt()
                var metricsResponse = ""
                inferenceService.execute(InferenceCommand.Chat(metricsPrompt)).collect { token ->
                    metricsResponse += token
                }
                
                val scoresResult = try {
                    json.decodeFromString<AnalysisResult>(extractJsonObject(metricsResponse))
                } catch (e: Exception) {
                    Logger.e("AnalysisCoordinator", "Audio Metrics parse failed: ${e.message} | raw: ${metricsResponse.takeLast(500)}")
                    updatedResultWithClaims
                }

                val updatedResultWithScores = updatedResultWithClaims.copy(
                    objectivityScore = scoresResult.objectivityScore,
                    logicScore = scoresResult.logicScore,
                    evidenceQuality = scoresResult.evidenceQuality,
                    credibilityScore = scoresResult.credibilityScore,
                    credibility = scoresResult.credibility,
                    isMetricsLoading = false
                )
                currentResult = updatedResultWithScores
                _state.value = InferenceState.Complete(updatedResultWithScores)

                // STAGE 4: Tone Synthesis
                val tonePrompt = ToneStage.buildPrompt()
                var toneResponse = ""
                inferenceService.execute(InferenceCommand.Chat(tonePrompt)).collect { token ->
                    toneResponse += token
                }

                val toneData = try {
                    json.decodeFromString<AnalysisResult>(extractJsonObject(toneResponse))
                } catch (e: Exception) {
                    Logger.e("AnalysisCoordinator", "Audio Tone parse failed: ${e.message} | raw: ${toneResponse.takeLast(500)}")
                    updatedResultWithScores
                }

                val updatedResultWithTone = updatedResultWithScores.copy(
                    vocalTone = toneData.vocalTone,
                    isVocalToneLoading = false
                )
                currentResult = updatedResultWithTone
                _state.value = InferenceState.Complete(updatedResultWithTone)

                // STAGE 5: Socratic Bridge
                var socraticResult = updatedResultWithTone
                try {
                    Logger.i("AnalysisCoordinator", "Initiating Socratic Bridge for audio...")
                    val socraticPrompt = SocraticStage.buildPrompt()
                    var socraticResponse = ""
                    inferenceService.execute(InferenceCommand.Chat(socraticPrompt)).collect { token ->
                        socraticResponse += token
                    }
                    val extractedJson = extractJsonObject(socraticResponse)
                    val socraticData = json.decodeFromString<AnalysisResult>(extractedJson)
                    socraticResult = updatedResultWithTone.copy(
                        socraticQuestions = socraticData.socraticQuestions,
                        isSocraticLoading = false
                    )
                } catch (e: Exception) {
                    Logger.e("AnalysisCoordinator", "Audio Socratic bridge stage failed: ${e.message}")
                    socraticResult = updatedResultWithTone.copy(isSocraticLoading = false)
                }
                currentResult = socraticResult
                _state.value = InferenceState.Complete(socraticResult)

                // STAGE 6: Fallacies (Deep Scan)
                var verifiedResult = socraticResult
                try {
                    val fallacyPrompt = FallacyStage.buildPrompt()
                    var fallacyResponse = ""
                    inferenceService.execute(InferenceCommand.Chat(fallacyPrompt)).collect { token ->
                        fallacyResponse += token
                    }
                    val fallacies = FallacyStage.parse(fallacyResponse)
                    verifiedResult = socraticResult.copy(
                        fallacies = fallacies,
                        isFallaciesLoading = false
                    )
                } catch (e: Exception) {
                    Logger.e("AnalysisCoordinator", "Audio fallacy stage failed: ${e.message}")
                    verifiedResult = socraticResult.copy(isFallaciesLoading = false)
                }
                
                currentResult = verifiedResult
                _state.value = InferenceState.Complete(verifiedResult)

                // Persist to history
                repository.saveAnalysis(
                    SavedAnalysis(
                        id = Clock.System.now().toEpochMilliseconds().toString(),
                        timestamp = Clock.System.now().toEpochMilliseconds(),
                        originalArticleText = finalTranscript.ifBlank { "[Audio Analysis]" },
                        analysisResult = verifiedResult
                    )
                )

            } catch (e: Exception) {
                if (e !is CancellationException) {
                    _state.value = InferenceState.Error(e.message ?: "Audio analysis failed")
                }
            }
        }
    }

    /**
     * Joins transcripts while attempting to remove overlaps caused by chunking.
     * Uses word-window matching to find the best stitch point.
     */
    private fun mergeTranscripts(observations: List<ChunkObservation>): String {
        if (observations.isEmpty()) return ""
        val result = StringBuilder(observations[0].transcript)
        
        for (i in 1 until observations.size) {
            val prev = observations[i-1].transcript
            val curr = observations[i].transcript
            
            val trimmedCurr = removeTranscriptOverlap(prev, curr)
            result.append(" ").append(trimmedCurr)
        }
        return result.toString().trim()
    }

    internal fun removeTranscriptOverlap(prev: String, curr: String): String {
        val prevWords = prev.split(Regex("\\s+")).filter { it.isNotBlank() }
        val currWords = curr.split(Regex("\\s+")).filter { it.isNotBlank() }
        
        if (prevWords.isEmpty() || currWords.isEmpty()) return curr
        
        // Use a window to find the stitch point. 5 words is usually enough for uniqueness
        // while being flexible enough for slight transcription variations.
        val windowSize = 5
        if (prevWords.size < windowSize || currWords.size < windowSize) return curr
        
        // Search back from the end of prev (up to 40 words or the whole chunk)
        val maxLookback = 40.coerceAtMost(prevWords.size)
        val minStartIndex = (prevWords.size - maxLookback).coerceAtLeast(0)
        
        // We search backwards from the end of 'prev' to find the LATEST occurrence 
        // that matches the START of 'curr'. This is the most likely overlap point.
        for (i in (prevWords.size - windowSize) downTo minStartIndex) {
            val window = prevWords.subList(i, i + windowSize)
            
            // Look for this window in the first 40 words of curr
            val lookahead = 40.coerceAtMost(currWords.size - windowSize)
            for (j in 0..lookahead) {
                if (currWords.subList(j, j + windowSize).equalsIgnoringCase(window)) {
                    // Stitch point found! 
                    // i is the start index of the match in prev.
                    // j is the start index of the match in curr.
                    
                    // The number of words in prev from the match start to the end is (prevWords.size - i).
                    // We assume these same words (or their equivalents) exist at the start of curr starting at j.
                    val wordsToSkipInCurr = j + (prevWords.size - i)
                    
                    if (wordsToSkipInCurr < currWords.size) {
                        return currWords.subList(wordsToSkipInCurr, currWords.size).joinToString(" ")
                    } else {
                        return "" // Entire chunk was an overlap
                    }
                }
            }
        }
        
        // Fallback: If no match, just return as is
        return curr
    }

    private fun List<String>.equalsIgnoringCase(other: List<String>): Boolean {
        if (size != other.size) return false
        val regex = Regex("[^a-zA-Z0-9]")
        for (i in indices) {
            val w1 = this[i].replace(regex, "")
            val w2 = other[i].replace(regex, "")
            if (!w1.equals(w2, ignoreCase = true)) return false
        }
        return true
    }

    private fun extractJsonObject(raw: String): String {
        val cleaned = raw.trim()
        val start = cleaned.indexOf("{")
        val end = cleaned.lastIndexOf("}")
        return if (start != -1 && end != -1 && end > start) {
            cleaned.substring(start, end + 1)
        } else {
            cleaned
        }
    }
}
