package org.medialiteracy.domain

/**
 * Simple multiplatform logger to avoid Android-specific Log dependencies in domain logic.
 */
object Logger {
    fun d(tag: String, message: String) {
        println("DEBUG: [$tag] $message")
    }
    
    fun i(tag: String, message: String) {
        println("INFO: [$tag] $message")
    }

    fun w(tag: String, message: String) {
        println("WARN: [$tag] $message")
    }
    
    fun e(tag: String, message: String, throwable: Throwable? = null) {
        println("ERROR: [$tag] $message")
        throwable?.printStackTrace()
    }
}
