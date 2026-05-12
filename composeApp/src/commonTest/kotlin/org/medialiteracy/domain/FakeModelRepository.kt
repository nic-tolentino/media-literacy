package org.medialiteracy.domain

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * In-memory ModelRepository for unit tests.
 * Drive state transitions explicitly via simulate*() methods.
 */
class FakeModelRepository(
    private val onlineStatus: Boolean = true,
    private val availableBytes: Long = 20L * 1024 * 1024 * 1024 // 20 GB default
) : ModelRepository {

    private val _downloadState = MutableStateFlow<DownloadState>(DownloadState.Idle)
    override val downloadState: StateFlow<DownloadState> = _downloadState.asStateFlow()

    private val _installedVariant = MutableStateFlow<ModelVariant?>(null)
    override val installedVariant: StateFlow<ModelVariant?> = _installedVariant.asStateFlow()

    val downloadedVariants = mutableListOf<ModelVariant>()

    // --- Test drivers ---

    fun simulateDownloadStart(variant: ModelVariant) {
        _downloadState.value = DownloadState.Downloading(0f, 0L, (variant.approximateSizeGb * 1_073_741_824).toLong())
    }

    fun simulateProgress(fraction: Float, variant: ModelVariant) {
        val total = (variant.approximateSizeGb * 1_073_741_824).toLong()
        _downloadState.value = DownloadState.Downloading(fraction, (total * fraction).toLong(), total)
    }

    fun simulateVerifying() {
        _downloadState.value = DownloadState.Verifying
    }

    fun simulateComplete(variant: ModelVariant) {
        downloadedVariants += variant
        _installedVariant.value = variant
        _downloadState.value = DownloadState.Complete
    }

    fun simulateFailed(reason: String, retryable: Boolean) {
        _downloadState.value = DownloadState.Failed(reason, retryable)
    }

    fun simulateCancel() {
        _downloadState.value = DownloadState.Idle
    }

    fun simulateOffline() {
        _downloadState.value = DownloadState.Offline
    }

    // --- Business rule helper (mirrors GemmaOrchestrator.canStartDownload) ---
    fun canStartDownload(variant: ModelVariant, freeSpaceGb: Double): Boolean =
        onlineStatus && freeSpaceGb >= variant.approximateSizeGb

    // --- ModelRepository interface ---

    override fun startDownload(variant: ModelVariant) {
        if (!onlineStatus) {
            _downloadState.value = DownloadState.Offline
            return
        }
        simulateDownloadStart(variant)
    }

    override fun cancelDownload() {
        simulateCancel()
    }

    override fun pauseDownload() {}
    override fun resumeDownload() {}

    override fun isOnline(): Boolean = onlineStatus

    override suspend fun availableDiskBytes(): Long = availableBytes

    override suspend fun installedVariantValue(): ModelVariant? = _installedVariant.value

    override suspend fun installedModelPath(): String? =
        _installedVariant.value?.let { "/fake/path/${it.fileName}" }

    override suspend fun deleteModel(variant: ModelVariant): Boolean {
        val removed = downloadedVariants.remove(variant)
        if (removed) _installedVariant.value = downloadedVariants.lastOrNull()
        return removed
    }

    override suspend fun checkForUpdate(): Boolean = false
}
