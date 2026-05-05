package org.medialiteracy.domain

import kotlinx.coroutines.flow.Flow

/**
 * The core analytical engine interface for the Media Literacy Dashboard.
 * 
 * Provides an abstraction for local LLM inference, supporting both 
 * stateless single-turn generation and "Persistent/Sticky" conversational sessions.
 */
data class MultimodalContent(
    val text: String? = null,
    val image: ByteArray? = null,
    val audio: ByteArray? = null
)

interface LlmEngine {
    /** 
     * Initializes the native model weights and hardware backend.
     * @param context Platform-specific context (e.g. Android Context).
     */
    suspend fun initialize(context: Any)

    /** Generates a one-off streaming response; creates a fresh session for every call. */
    fun generateStreaming(prompt: String): Flow<String>

    /** 
     * Generates a streaming response within a persistent conversational context.
     * @param isFirstTurn If true, resets any existing conversation history.
     */
    fun generatePersistentStreaming(prompt: String, isFirstTurn: Boolean): Flow<String>
    
    /** 
     * Generates a streaming response for multimodal input (Image/Audio/Text).
     * This is typically used for stateless, deep-dive analysis of media assets.
     */
    fun generateMultimodalStreaming(content: MultimodalContent, isFirstTurn: Boolean = true): Flow<String>

    /** Returns true if there is an ongoing conversation context in memory. */
    fun hasActiveConversation(): Boolean
    
    /** Synchronously/Suspendingly closes the active session. */
    suspend fun closeSession()

    /** Releases all native resources and model handles. */
    suspend fun close()

    companion object {
        /** Provides a singleton instance of the platform-specific engine. */
        fun getInstance(): LlmEngine = getLlmEngine()
    }
}

/** Accessor for the platform-specific actual implementation of the [LlmEngine]. */
expect fun getLlmEngine(): LlmEngine
