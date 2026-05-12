package org.medialiteracy.domain

import android.app.ActivityManager
import android.content.Context
import android.os.StatFs
import java.io.File

actual object DeviceCapabilityChecker {
    private lateinit var appContext: Context

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    actual fun recommendedVariant(): ModelVariant {
        if (!::appContext.isInitialized) return ModelVariant.E2B // Fallback

        return try {
            val activityManager = appContext.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            
            // Signal 1: total RAM
            val memInfo = ActivityManager.MemoryInfo()
            activityManager.getMemoryInfo(memInfo)
            val totalRamGb = memInfo.totalMem / (1024.0 * 1024.0 * 1024.0)

            // Signal 2: GPU / OpenCL availability
            val hasGpu = GpuUtils.checkOpenClAvailability()

            // Signal 3: free disk space
            val stat = StatFs(appContext.filesDir.path)
            val freeDiskGb = stat.availableBytes / (1024.0 * 1024.0 * 1024.0)

            if (totalRamGb >= 6.0 && hasGpu && freeDiskGb >= 4.0) {
                ModelVariant.E4B
            } else {
                ModelVariant.E2B
            }
        } catch (e: Exception) {
            ModelVariant.E2B
        }
    }
}
