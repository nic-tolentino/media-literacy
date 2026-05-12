package org.medialiteracy.domain

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class IosModelRepository : ModelRepository {
    private val _downloadState = MutableStateFlow<DownloadState>(DownloadState.Idle)
    override val downloadState: StateFlow<DownloadState> = _downloadState.asStateFlow()

    private val _installedVariant = MutableStateFlow<ModelVariant?>(null)
    override val installedVariant: StateFlow<ModelVariant?> = _installedVariant.asStateFlow()

    override suspend fun checkForUpdate(): Boolean = false
    override suspend fun installedVariantValue(): ModelVariant? = null
    override suspend fun installedModelPath(): String? = null
    override suspend fun availableDiskBytes(): Long = 0
    override fun startDownload(variant: ModelVariant) {}
    override fun cancelDownload() {}
    override fun pauseDownload() {}
    override fun resumeDownload() {}
    override fun isOnline(): Boolean = true
    override suspend fun deleteModel(variant: ModelVariant): Boolean = false
}

actual fun PlatformModelRepository(): ModelRepository = IosModelRepository()
