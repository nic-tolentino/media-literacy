package org.medialiteracy.domain

import kotlinx.coroutines.flow.StateFlow

interface ModelRepository {
    /** Checks if a newer version of the model is available on the server. */
    suspend fun checkForUpdate(): Boolean

    /** Emits the current download state. Persists across recompositions. */
    val downloadState: StateFlow<DownloadState>

    /** Emits the currently installed variant. Reactive way to track model presence. */
    val installedVariant: StateFlow<ModelVariant?>

    /** The variant currently stored on disk, or null if none. */
    suspend fun installedVariantValue(): ModelVariant?

    /** Absolute path to the installed model file, or null. */
    suspend fun installedModelPath(): String?

    /** Available disk space in bytes. */
    suspend fun availableDiskBytes(): Long

    /** Start downloading the given variant. */
    fun startDownload(variant: ModelVariant)

    /** Cancel an in-progress download. */
    fun cancelDownload()

    /** Pause an in-progress download. */
    fun pauseDownload()

    /** Resume a previously paused download. */
    fun resumeDownload()

    /** Returns true if a network connection is available. */
    fun isOnline(): Boolean

    /** Delete a specific installed variant. Returns true on success. */
    suspend fun deleteModel(variant: ModelVariant): Boolean

    companion object {
        fun getInstance(): ModelRepository = PlatformModelRepository()
    }
}

expect fun PlatformModelRepository(): ModelRepository
