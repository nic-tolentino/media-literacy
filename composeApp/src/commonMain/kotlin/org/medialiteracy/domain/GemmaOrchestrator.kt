package org.medialiteracy.domain

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.delay
import cafe.adriel.voyager.core.model.ScreenModel

class GemmaOrchestrator(
    private val engine: LlmEngine = getLlmEngine()
) : ScreenModel {
    private val _state = MutableStateFlow<InferenceState>(InferenceState.Idle)
    val state: StateFlow<InferenceState> = _state.asStateFlow()

    suspend fun startAnalysis(input: String) {
        _state.value = InferenceState.Thinking("Analyzing input text...")
        delay(2000)
        val result = engine.analyzeMultimodal(input.encodeToByteArray(), InputType.TEXT)
        _state.value = InferenceState.Complete(result)
    }

    suspend fun downloadModel() {
        for (i in 0..100 step 10) {
            _state.value = InferenceState.DownloadingModel(i / 100f)
            delay(500)
        }
        _state.value = InferenceState.Idle
    }
}
