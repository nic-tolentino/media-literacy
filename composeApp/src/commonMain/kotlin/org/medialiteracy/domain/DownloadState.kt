package org.medialiteracy.domain

sealed class DownloadState {
    object Idle : DownloadState()
    object Offline : DownloadState()
    data class Downloading(val progressFraction: Float, val bytesDownloaded: Long, val totalBytes: Long) : DownloadState()
    data class Paused(val progressFraction: Float, val bytesDownloaded: Long, val totalBytes: Long) : DownloadState()
    object Verifying : DownloadState()
    object Complete : DownloadState()
    data class Failed(val reason: String, val isRetryable: Boolean) : DownloadState()
}
