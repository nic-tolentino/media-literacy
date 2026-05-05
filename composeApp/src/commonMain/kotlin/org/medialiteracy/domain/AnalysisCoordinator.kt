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
    private var analysisJob: Job? = null

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
        analysisJob?.cancel()
        analysisJob = scope.launch {
            try {
                // Stage 1: Summary & Metrics
                _state.value = InferenceState.Thinking("Reading article (Pre-fill)...")
                val summaryPrompt = SummaryStage.buildPrompt(article)
                
                var summaryResponse = ""
                inferenceService.execute(InferenceCommand.Analyze("v1_summary", summaryPrompt)).collect { token ->
                    summaryResponse += token
                    _state.value = InferenceState.Thinking(summaryResponse)
                }

                val result = SummaryStage.parse(summaryResponse).copy(
                    isAnalyzingFallacies = true,
                    fullTranscript = article
                )
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
                
                Logger.i("AnalysisCoordinator", "Analysis verification complete: ${finalResult.fallacies.size} fallacies found.")
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
     * Starts the multimodal image analysis.
     */
    fun startImageAnalysis(imageBytes: ByteArray, description: String = "Analyze this image for logical fallacies or bias.") {
        currentArticle = "[Image Analysis]"
        analysisJob?.cancel()
        analysisJob = scope.launch {
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

                val result = SummaryStage.parse(response).copy(
                    fullTranscript = response // For images, the 'response' often contains the reasoning/extraction
                )
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
        analysisJob?.cancel()
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
                            data = chunk.data,
                            prompt = prompt,
                            isFirstTurn = true
                        )
                    ).collect { token ->
                        chunkResponse += token
                    }
                    
                    val observation = AudioAnalysisStage.parse(chunkResponse)
                    if (observation != null) {
                        observations.add(observation)
                        val partialTranscript = mergeTranscripts(observations)
                        _state.value = InferenceState.Thinking(partialTranscript)
                        Logger.d(\"AnalysisCoordinator\", \"Chunk ${index + 1} done: transcript=${observation.transcript} scores=${observation.objectivityScore}/${observation.logicScore}\")
                    } else {
                        Logger.w(\"AnalysisCoordinator\", \"Chunk ${index + 1} parsing FAILED. Response: $chunkResponse\")
                    }
                }
                
                val finalTranscript = mergeTranscripts(observations)
                Logger.i("AnalysisCoordinator", "Audio processing finished. Collected ${observations.size}/${chunks.size} segments.")
                Logger.i("AnalysisCoordinator", "FINAL AGGREGATED TRANSCRIPT:\n$finalTranscript")

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

                Logger.i("AnalysisCoordinator", "Aggregated full transcript: ${finalTranscript.length} characters (deduplicated).")
                
                val finalResult = SummaryStage.parse(finalResponse).copy(
                    fullTranscript = finalTranscript
                )
                currentResult = finalResult
                _state.value = InferenceState.Complete(finalResult)

                // Persist to history
                repository.saveAnalysis(
                    SavedAnalysis(
                        id = Clock.System.now().toEpochMilliseconds().toString(),
                        timestamp = Clock.System.now().toEpochMilliseconds(),
                        originalArticleText = finalTranscript.ifBlank { "[Audio Analysis]" },
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
}
