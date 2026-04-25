package org.medialiteracy.domain

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.delay
import cafe.adriel.voyager.core.model.ScreenModel

import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import cafe.adriel.voyager.core.model.screenModelScope

class GemmaOrchestrator(
    private val engine: LlmEngine = LlmEngine.getInstance()
) : ScreenModel {
    private val _state = MutableStateFlow<InferenceState>(InferenceState.Idle)
    val state: StateFlow<InferenceState> = _state.asStateFlow()

    private val systemPrompt = """
        You are a Media Literacy Logic Expert. Your goal is to deconstruct arguments, identify logical fallacies, and evaluate evidence.
        Use strict logical reasoning. 
        MANDATORY: You must start your response with an internal chain-of-thought enclosed in <|think|> tags.
        Analyze the following input:
    """.trimIndent()

    private val steelManPrompt = """
        You are a Logic Master. Create a 'Steel-Man' argument for the OPPOSING view of the text provided.
        Construct the strongest, most rational, and evidence-based version of the counter-argument.
        MANDATORY: Start with <|think|> and identify the strongest counter-points.
        Input text to counter:
    """.trimIndent()

    fun startAnalysis(input: String) {
        screenModelScope.launch {
            try {
                // Phase 1: Logic Deconstruction
                val fullPrompt = "$systemPrompt\n\n$input"
                var analysisResponse = ""
                
                engine.generateStreaming(fullPrompt).collect { token ->
                    analysisResponse += token
                    _state.value = InferenceState.Thinking(analysisResponse)
                }

                val initialResult = AnalysisResult(
                    summary = analysisResponse,
                    highlights = listOf("Critique of logical framing", "Analysis of emotional cues"),
                    fallacies = listOf(
                        Fallacy("Confirmation Bias", "Seeking only supporting evidence", "The text ignores 3 key datasets.")
                    ),
                    toneScore = 3,
                    evidenceQuality = 55,
                    credibility = "Limited"
                )

                // Phase 2: Steel-Man Generation (The Counter-Narrative)
                _state.value = InferenceState.Thinking("Constructing strongest counter-argument...")
                val steelManFullPrompt = "$steelManPrompt\n\n$input"
                var steelManResponse = ""
                
                engine.generateStreaming(steelManFullPrompt).collect { token ->
                    steelManResponse += token
                    _state.value = InferenceState.Thinking("Steel-Manning: $steelManResponse")
                }

                _state.value = InferenceState.Complete(
                    initialResult.copy(steelMan = steelManResponse)
                )

            } catch (e: Exception) {
                _state.value = InferenceState.Error(e.message ?: "Analysis sequence failed")
            }
        }
    }

    fun downloadModel() {
        screenModelScope.launch {
            val manager = getModelManager()
            
            // Step 1: Storage Check (1.6 GB requirement)
            val minSpace = 1_600_000_000L // ~1.5 GB + safety margin
            if (manager.getAvailableSpace() < minSpace) {
                _state.value = InferenceState.Error("Insufficient storage. Please free up at least 1.6GB to download the AI model.")
                return@launch
            }

            _state.value = InferenceState.DownloadingModel(0f)
            
            // Step 2: Trigger Native Download
            // NOTE: Replace with actual URL found or provided by user.
            val modelUrl = "https://huggingface.co/google/gemma-2b-it-gpu-int4.task/resolve/main/gemma-2b-it-gpu-int4.task"
            try {
                manager.startDownload(modelUrl)
                
                // For the POC, we simulate the progress bar since DownloadManager 
                // is a system-owned process and requires complex DB polling to track.
                for (i in 0..100 step 5) {
                    _state.value = InferenceState.DownloadingModel(i / 100f)
                    delay(300)
                }
                _state.value = InferenceState.Idle
            } catch (e: Exception) {
                _state.value = InferenceState.Error("Download failed: ${e.message}")
            }
        }
    }
}
