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

    private val modelRepository = ModelRepository.getInstance()

    val state: StateFlow<InferenceState> = coordinator.state
    val downloadState: StateFlow<DownloadState> = modelRepository.downloadState
    val installedVariant: StateFlow<ModelVariant?> = modelRepository.installedVariant

    /** 
     * Mapping for the legacy engine-level state if needed by old UI components.
     * Most UI should observe coordinator.state instead.
     */
    val engineState: StateFlow<EngineInternalState> = inferenceService.state

    val currentArticleText: String? get() = coordinator.currentArticleText
    val currentAnalysisResult: AnalysisResult? get() = coordinator.currentAnalysisResult
    val isFreshAnalysis: Boolean get() = coordinator.isFreshAnalysis
    val socraticSession: StateFlow<SocraticSession?> = coordinator.socraticSession

    fun consumeFreshAnalysis() {
        coordinator.consumeFreshAnalysis()
    }

    fun updateSocraticSession(session: SocraticSession?) {
        coordinator.updateSocraticSession(session)
    }

    fun startModelDownload(variant: ModelVariant) {
        modelRepository.startDownload(variant)
    }

    fun cancelDownload() = modelRepository.cancelDownload()
    fun pauseDownload() = modelRepository.pauseDownload()
    fun resumeDownload() = modelRepository.resumeDownload()

    fun isOnline(): Boolean = modelRepository.isOnline()

    suspend fun availableDiskBytes(): Long = modelRepository.availableDiskBytes()

    fun canStartDownload(variant: ModelVariant, freeSpaceGb: Double): Boolean =
        modelRepository.isOnline() && freeSpaceGb >= variant.approximateSizeGb

    suspend fun resetEngine() {
        ServiceRegistry.inferenceService.resetEngine()
    }

    fun deleteModel(variant: ModelVariant, onComplete: (Boolean) -> Unit) {
        screenModelScope.launch {
            val success = modelRepository.deleteModel(variant)
            onComplete(success)
        }
    }

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
            try {
                coordinator.sendChat(userMessage).collect { fullResponse ->
                    // Clean internal thinking tags for UI
                    lastCleaned = fullResponse.replace(Regex("<\\|think\\|>[\\s\\S]*?\\*?\\/\\|think\\|>"), "").trim()
                    onUpdate(lastCleaned)
                }
                onComplete(lastCleaned)
            } catch (e: Exception) {
                val errMsg = "Failed to generate response. ${e.message ?: "Please check if your model is active."}"
                onUpdate(errMsg)
                onComplete(errMsg)
            }
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

}
