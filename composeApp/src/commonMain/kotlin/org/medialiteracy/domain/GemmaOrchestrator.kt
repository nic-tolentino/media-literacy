package org.medialiteracy.domain

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * UI Adapter for the centralized Analysis system.
 * Delegates all logic to the [AnalysisCoordinator] and [InferenceService].
 */
class GemmaOrchestrator : ScreenModel {
    
    private val coordinator = ServiceRegistry.analysisCoordinator
    private val inferenceService = ServiceRegistry.inferenceService
    private val repository = AnalysisRepository.getInstance()

    val state: StateFlow<InferenceState> = coordinator.state

    /** 
     * Mapping for the legacy engine-level state if needed by old UI components.
     * Most UI should observe coordinator.state instead.
     */
    val engineState: StateFlow<EngineInternalState> = inferenceService.state

    fun startAnalysis(input: String) {
        coordinator.startAnalysis(input)
    }

    fun restoreAnalysis(text: String, result: AnalysisResult) {
        coordinator.loadResult(text, result)
    }

    fun generateChatResponse(
        userMessage: String,
        onUpdate: (String) -> Unit,
        onComplete: (String) -> Unit
    ) {
        screenModelScope.launch {
            var lastCleaned = ""
            coordinator.sendChat(userMessage).collect { fullResponse ->
                // Clean internal thinking tags for UI
                lastCleaned = fullResponse.replace(Regex("<\\|think\\|>[\\s\\S]*?\\*?\\/\\|think\\|>"), "").trim()
                onUpdate(lastCleaned)
            }
            // Return the final cleaned response text to the completion handler
            onComplete(lastCleaned)
        }
    }

    /** 
     * Manually triggers a resource release (e.g. for backgrounding).
     */
    fun releaseResources() {
        screenModelScope.launch {
            inferenceService.execute(InferenceCommand.ReleaseResources).collect()
        }
    }

    fun cancelActiveInference() {
        screenModelScope.launch {
            inferenceService.execute(InferenceCommand.CancelCurrent).collect()
        }
    }

    fun reset() {
        coordinator.reset()
    }

    fun downloadModel() {
        // Mock download if needed
    }
}
