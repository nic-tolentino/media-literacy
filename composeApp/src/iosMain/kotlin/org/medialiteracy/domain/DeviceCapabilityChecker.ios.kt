package org.medialiteracy.domain

import platform.Foundation.NSProcessInfo

actual object DeviceCapabilityChecker {
    actual fun recommendedVariant(): ModelVariant {
        return try {
            val totalRamGb = NSProcessInfo.processInfo.physicalMemory.toDouble() / (1024.0 * 1024.0 * 1024.0)
            
            // All modern iPhones have a GPU; disk space check is deferred to download time
            if (totalRamGb >= 6.0) ModelVariant.E4B else ModelVariant.E2B
        } catch (e: Exception) {
            ModelVariant.E2B
        }
    }

    actual fun isDebugBuild(): Boolean {
        return false
    }
}
