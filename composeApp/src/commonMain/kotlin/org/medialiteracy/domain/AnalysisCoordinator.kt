package org.medialiteracy.domain

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.datetime.Clock

/**
 * Orchestrates the multi-stage analysis pipeline and manages task-level state.
 * This class bridges the raw InferenceService (token generation) and the UI's 
 * need for structured AnalysisResults.
 */
class AnalysisCoordinator(
    private val inferenceService: InferenceService,
    private val repository: AnalysisRepository,
    private val scope: CoroutineScope
) {
    private val _state = MutableStateFlow<InferenceState>(InferenceState.Idle)
    val state: StateFlow<InferenceState> = _state.asStateFlow()

    private var currentArticle: String? = null
    private var currentResult: AnalysisResult? = null

    /**
     * Loads a previously saved result into the coordinator without triggering inference.
     */
    fun loadResult(article: String, result: AnalysisResult) {
        currentArticle = article
        currentResult = result
        _state.value = InferenceState.Complete(result)
    }

    /**
     * Starts the two-stage analysis pipeline.
     */
    fun startAnalysis(article: String) {
        // Enforce V1 Hard Limit
        if (article.length > 8000) {
            _state.value = InferenceState.SourceTooLarge("This article is too long for a deep-dive analysis. Please try a shorter excerpt (under 8,000 characters).")
            return
        }

        currentArticle = article
        scope.launch {
            try {
                // Stage 1: Summary & Metrics
                _state.value = InferenceState.Thinking("Reading article (Pre-fill)...")
                val summaryPrompt = SummaryStage.buildPrompt(article)
                
                var summaryResponse = ""
                inferenceService.execute(InferenceCommand.Analyze("v1_summary", summaryPrompt)).collect { token ->
                    summaryResponse += token
                    _state.value = InferenceState.Thinking(summaryResponse)
                }

                val result = SummaryStage.parse(summaryResponse).copy(isAnalyzingFallacies = true)
                currentResult = result
                _state.value = InferenceState.Complete(result)

                // Stage 2: Deep Fallacy Scan
                delay(500) 
                val fallacyPrompt = FallacyStage.buildPrompt()
                var fallacyResponse = ""
                
                inferenceService.execute(InferenceCommand.Chat(fallacyPrompt)).collect { token ->
                    fallacyResponse += token
                }

                val fallacies = FallacyStage.parse(fallacyResponse)
                val finalResult = result.copy(
                    fallacies = fallacies,
                    isAnalyzingFallacies = false
                )
                
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
        val chatPrompt = "<|turn|>user\n$message\nExpert Guidance (be concise):\n<|turn|>model\n"
        
        inferenceService.execute(InferenceCommand.Chat(chatPrompt)).collect { token ->
            response += token
            emit(response)
        }
    }

    /**
     * Starts the multimodal image analysis.
     */
    fun startImageAnalysis(imageBytes: ByteArray, description: String = "Analyze this image for logical fallacies or bias.") {
        currentArticle = "[Image Analysis]"
        scope.launch {
            try {
                _state.value = InferenceState.Thinking("Processing image...")
                val prompt = SummaryStage.buildPrompt("IMAGE ANALYSIS: $description")
                
                var response = ""
                inferenceService.execute(
                    InferenceCommand.AnalyzeMultimodal(
                        type = MultimodalType.IMAGE,
                        data = imageBytes,
                        prompt = prompt
                    )
                ).collect { token ->
                    response += token
                    _state.value = InferenceState.Thinking(response)
                }

                val result = SummaryStage.parse(response)
                currentResult = result
                _state.value = InferenceState.Complete(result)
                
                // Persist to history
                repository.saveAnalysis(
                    SavedAnalysis(
                        id = Clock.System.now().toEpochMilliseconds().toString(),
                        timestamp = Clock.System.now().toEpochMilliseconds(),
                        originalArticleText = "[Image Analysis] $description",
                        analysisResult = result
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
        scope.launch {
            try {
                val observations = mutableListOf<ChunkObservation>()
                
                chunks.forEachIndexed { index, chunk ->
                    _state.value = InferenceState.Thinking("Analyzing audio segment ${index + 1}/${chunks.size}...")
                    
                    val prompt = AudioAnalysisStage.buildPrompt(chunk.timestamp)
                    var chunkResponse = ""
                    
                    inferenceService.execute(
                        InferenceCommand.AnalyzeMultimodal(
                            type = MultimodalType.AUDIO,
                            data = chunk.data,
                            prompt = prompt,
                            isFirstTurn = true // Stateless per chunk
                        )
                    ).collect { token ->
                        chunkResponse += token
                    }
                    
                    AudioAnalysisStage.parse(chunkResponse)?.let { 
                        observations.add(it)
                    }
                }

                if (observations.isEmpty()) {
                    _state.value = InferenceState.Error("Could not analyze audio content.")
                    return@launch
                }

                // Final Synthesis
                _state.value = InferenceState.Thinking("Synthesizing final report...")
                val synthesisPrompt = SynthesisStage.buildPrompt(observations)
                var finalResponse = ""
                
                inferenceService.execute(InferenceCommand.Analyze("audio_synthesis", synthesisPrompt)).collect { token ->
                    finalResponse += token
                    _state.value = InferenceState.Thinking(finalResponse)
                }

                val finalResult = SummaryStage.parse(finalResponse)
                currentResult = finalResult
                _state.value = InferenceState.Complete(finalResult)

                // Persist to history
                repository.saveAnalysis(
                    SavedAnalysis(
                        id = Clock.System.now().toEpochMilliseconds().toString(),
                        timestamp = Clock.System.now().toEpochMilliseconds(),
                        originalArticleText = "[Audio Analysis]",
                        analysisResult = finalResult
                    )
                )

            } catch (e: Exception) {
                if (e !is CancellationException) {
                    _state.value = InferenceState.Error(e.message ?: "Audio analysis failed")
                }
            }
        }
    }
}
