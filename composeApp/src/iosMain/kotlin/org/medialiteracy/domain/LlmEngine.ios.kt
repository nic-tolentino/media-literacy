package org.medialiteracy.domain

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class IosLlmEngine : LlmEngine {
    override suspend fun initialize(context: Any) {
        // No-op for iOS stub
    }

    override fun generateStreaming(prompt: String): Flow<String> = flow {
        delay(500)
        emit("This is a streaming response from the iOS No-Op engine.")
    }

    override fun generatePersistentStreaming(prompt: String, isFirstTurn: Boolean): Flow<String> = flow {
        delay(500)
        emit("This is a persistent streaming response from the iOS No-Op engine.")
    }

    override fun hasActiveConversation(): Boolean = false
    
    override suspend fun closeSession() {
        // No-op for iOS stub
    }

    override suspend fun close() {
        // No-op for iOS stub
    }
}

actual fun getLlmEngine(): LlmEngine = IosLlmEngine()
