package org.medialiteracy.domain

import java.io.File

object GpuUtils {
    fun checkOpenClAvailability(): Boolean {
        return File("/system/vendor/lib64/libOpenCL.so").exists() || 
               File("/vendor/lib64/libOpenCL.so").exists() ||
               File("/vendor/lib64/egl/libGLES_mali.so").exists() ||
               File("/vendor/lib64/libOpenCL_adreno.so").exists()
    }
}
