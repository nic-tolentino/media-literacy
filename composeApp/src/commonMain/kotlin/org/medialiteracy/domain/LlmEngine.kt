package org.medialiteracy.domain

import kotlinx.coroutines.flow.Flow

/**
 * The core analytical engine interface for the Media Literacy Dashboard.
 * 
 * Provides an abstraction for local LLM inference, supporting both 
 * stateless single-turn generation and "Persistent/Sticky" conversational sessions.
 */
interface LlmEngine {
    /** 
     * Initializes the native model weights and hardware backend.
     * @param context Platform-specific context (e.g. Android Context).
     */
    fun initialize(context: Any)

    /** Generates a one-off streaming response; creates a fresh session for every call. */
    fun generateStreaming(prompt: String): Flow<String>

    /** 
     * Generates a streaming response within a persistent conversational context.
     * @param isFirstTurn If true, resets any existing conversation history.
     */
    fun generatePersistentStreaming(prompt: String, isFirstTurn: Boolean): Flow<String>

    /** Synchronous/Blocking response generation. */
    suspend fun generateResponse(prompt: String): String

    /** Analyzes image or audio data for structural integrity. (Coming Soon) */
    suspend fun analyzeMultimodal(input: ByteArray, type: InputType): AnalysisResult

    /** Releases all native resources and model handles. */
    fun close()

    companion object {
        /** Provides a singleton instance of the platform-specific engine. */
        fun getInstance(): LlmEngine = getLlmEngine()
    }
}

/** Accessor for the platform-specific actual implementation of the [LlmEngine]. */
expect fun getLlmEngine(): LlmEngine
