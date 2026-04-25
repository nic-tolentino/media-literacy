package org.medialiteracy.domain

interface ModelManager {
    fun isModelDownloaded(): Boolean
    fun getAvailableSpace(): Long
    fun startDownload(url: String)
}

expect fun getModelManager(): ModelManager
