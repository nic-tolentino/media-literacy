package org.medialiteracy.domain

import android.app.DownloadManager
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Environment
import android.os.StatFs
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import java.io.File

class AndroidModelRepository(
    private val context: Context,
    private val settings: SettingsRepository
) : ModelRepository {

    private val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    
    private val _downloadState = MutableStateFlow<DownloadState>(DownloadState.Idle)
    override val downloadState: StateFlow<DownloadState> = _downloadState.asStateFlow()

    private val _installedVariant = MutableStateFlow<ModelVariant?>(null)
    override val installedVariant: StateFlow<ModelVariant?> = _installedVariant.asStateFlow()

    private var activeDownloadId: Long = -1
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var pollingJob: Job? = null

    init {
        scope.launch {
            _installedVariant.value = installedVariantValue()
        }
    }

    // Track the variant currently being downloaded
    private var pendingVariant: ModelVariant? = null

    override fun startDownload(variant: ModelVariant) {
        if (!isOnline()) {
            _downloadState.value = DownloadState.Offline
            return
        }

        pendingVariant = variant
        // DownloadManager cannot write to internal filesDir.
        // We download to external staging area first, then move it in verifyIntegrity.
        val stagingFile = File(context.getExternalFilesDir(null), variant.fileName + ".part")
        
        val request = DownloadManager.Request(Uri.parse(ModelConfig.urlForVariant(variant)))
            .setTitle("Downloading ${variant.displayName}")
            .setDescription("${variant.approximateSizeGb} GB — required for offline AI analysis")
            .setDestinationUri(Uri.fromFile(stagingFile))
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
            .setAllowedOverMetered(false)
            .setAllowedOverRoaming(false)

        activeDownloadId = downloadManager.enqueue(request)
        startPollingProgress()
    }

    private fun startPollingProgress() {
        pollingJob?.cancel()
        pollingJob = scope.launch {
            while (isActive) {
                val query = DownloadManager.Query().setFilterById(activeDownloadId)
                downloadManager.query(query).use { cursor ->
                    if (cursor.moveToFirst()) {
                        val status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                        val bytesDownloaded = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                        val totalBytes = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
                        val progress = if (totalBytes > 0) bytesDownloaded.toFloat() / totalBytes else 0f

                        when (status) {
                            DownloadManager.STATUS_RUNNING -> {
                                _downloadState.value = DownloadState.Downloading(progress, bytesDownloaded, totalBytes)
                            }
                            DownloadManager.STATUS_PAUSED -> {
                                _downloadState.value = DownloadState.Paused(progress, bytesDownloaded, totalBytes)
                            }
                            DownloadManager.STATUS_SUCCESSFUL -> {
                                _downloadState.value = DownloadState.Verifying
                                val variant = pendingVariant
                                if (variant == null) {
                                    _downloadState.value = DownloadState.Failed("Download restarted after process death — please retry", true)
                                } else {
                                    verifyIntegrity(variant)
                                }
                                return@launch // Exit loop
                            }
                            DownloadManager.STATUS_FAILED -> {
                                val reason = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON))
                                _downloadState.value = DownloadState.Failed("Download failed: error code $reason", true)
                                return@launch // Exit loop
                            }
                        }
                    }
                }
                delay(500)
            }
        }
    }

    private suspend fun verifyIntegrity(variant: ModelVariant) {
        val stagingFile = File(context.getExternalFilesDir(null), variant.fileName + ".part")
        val finalFile = File(context.filesDir, variant.fileName)
        
        try {
            withContext(Dispatchers.IO) {
                // 1. Calculate local hash
                val actualHash = HashUtils.calculateSha256(stagingFile)
                
                // 2. Fetch expected hash
                val expectedHash = try {
                    val url = java.net.URL(ModelConfig.urlForVariant(variant) + ".sha256")
                    val connection = url.openConnection().apply {
                        connectTimeout = 10_000
                        readTimeout = 10_000
                    }
                    connection.getInputStream().bufferedReader().use { it.readText() }.trim().split(" ")[0]
                } catch (e: Exception) {
                    Logger.e("ModelRepository", "Failed to fetch checksum: ${e.message}")
                    null // Fallback: skip hash check if server is unreachable for hash
                }

                if (expectedHash != null && actualHash != expectedHash) {
                    throw IllegalStateException("Checksum mismatch! Expected: $expectedHash, Actual: $actualHash")
                }

                if (stagingFile.exists()) {
                    stagingFile.renameTo(finalFile)
                }

                // Save verified state to persistent settings
                settings.setSelectedVariant(variant)
                settings.setInstalledModelHash(actualHash)
            }
            
            _installedVariant.value = variant
            _downloadState.value = DownloadState.Complete
        } catch (e: Exception) {
            _downloadState.value = DownloadState.Failed("Verification failed: ${e.message}", false)
        }
    }

    override fun cancelDownload() {
        if (activeDownloadId != -1L) {
            downloadManager.remove(activeDownloadId)
            activeDownloadId = -1
            pollingJob?.cancel()
            _downloadState.value = DownloadState.Idle
        }
    }

    override fun pauseDownload() {
        // No-op for V1 as per feedback
    }

    override fun resumeDownload() {
        // No-op for V1 as per feedback
    }

    override fun isOnline(): Boolean {
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    override suspend fun checkForUpdate(): Boolean {
        val variant = installedVariantValue() ?: return false
        val currentHash = settings.getInstalledModelHash().first() ?: return true
        
        return try {
            withContext(Dispatchers.IO) {
                val url = java.net.URL(ModelConfig.BASE_URL + "metadata.json")
                val connection = url.openConnection().apply {
                    connectTimeout = 5000
                    readTimeout = 5000
                }
                val json = connection.getInputStream().bufferedReader().use { it.readText() }
                // Simple parser since we don't have a JSON lib in common yet (or assuming simple format)
                val remoteHash = if (variant == ModelVariant.E4B) {
                    json.substringAfter("\"e4b_hash\":").substringAfter("\"").substringBefore("\"")
                } else {
                    json.substringAfter("\"e2b_hash\":").substringAfter("\"").substringBefore("\"")
                }
                remoteHash != currentHash
            }
        } catch (e: Exception) {
            Logger.e("ModelRepository", "Update check failed: ${e.message}")
            false
        }
    }

    override suspend fun availableDiskBytes(): Long {
        // Check external storage partition as that's where we stage the download
        val externalDir = context.getExternalFilesDir(null) ?: context.filesDir
        val stat = StatFs(externalDir.path)
        return stat.availableBytes
    }

    override suspend fun installedVariantValue(): ModelVariant? {
        return ModelVariant.entries.firstOrNull { variant ->
            File(context.filesDir, variant.fileName).let { it.exists() && it.length() > 0 }
        }
    }

    override suspend fun installedModelPath(): String? {
        val variant = installedVariantValue()
        if (variant != null) return File(context.filesDir, variant.fileName).absolutePath
        
        // Dev fallbacks
        val devFallbacks = listOf("gemma.litertlm", "gemma.task")
        return devFallbacks.map { File(context.filesDir, it) }.firstOrNull { it.exists() && it.length() > 0 }?.absolutePath
    }

    override suspend fun deleteModel(variant: ModelVariant): Boolean {
        val file = File(context.filesDir, variant.fileName)
        val deleted = file.exists() && file.delete()
        if (deleted) {
            _installedVariant.value = installedVariantValue()
        }
        return deleted
    }
}

private var repositoryInstance: ModelRepository? = null

actual fun PlatformModelRepository(): ModelRepository {
    return repositoryInstance ?: throw IllegalStateException("ModelRepository not initialized")
}

fun initModelRepository(
    context: Context,
    settings: SettingsRepository = SettingsRepository.getInstance()
) {
    repositoryInstance = AndroidModelRepository(context, settings)
}
