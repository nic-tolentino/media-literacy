package org.medialiteracy.domain

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import java.io.File

class AndroidModelManager(private val context: Context) : ModelManager {
    
    override fun getAvailableSpace(): Long {
        val stat = android.os.StatFs(context.filesDir.path)
        return stat.availableBytes
    }

    override fun isModelDownloaded(): Boolean {
        return File(context.filesDir, "gemma.task").exists()
    }

    override fun startDownload(url: String) {
        val request = DownloadManager.Request(Uri.parse(url))
            .setTitle("Gemma AI Model")
            .setDescription("Downloading logical reasoning engine...")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalFilesDir(context, null, "gemma.task")
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(false)

        val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        manager.enqueue(request)
    }
}

private var modelManagerInstance: ModelManager? = null

fun initializeModelManager(context: Context) {
    modelManagerInstance = AndroidModelManager(context)
}

actual fun getModelManager(): ModelManager = modelManagerInstance 
    ?: throw IllegalStateException("ModelManager not initialized")
