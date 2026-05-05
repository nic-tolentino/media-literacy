package org.medialiteracy.domain

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

/**
 * Mock engine to test the Inference Actor's coordination logic.
 */
open class MockLlmEngine : LlmEngine {
    var initializeCount = 0
    var closeCount = 0
    var lastPrompt: String? = null
    var streamDelay: Long = 0
    var mockTokens = listOf("Token1", "Token2")
    
    // Optional queue of token lists for sequential commands
    private val tokenQueue = mutableListOf<List<String>>()

    fun enqueueTokens(tokens: List<String>) {
        tokenQueue.add(tokens)
    }

    override suspend fun initialize(context: Any) { initializeCount++ }
    
    override fun generateStreaming(prompt: String): Flow<String> = flow {
        lastPrompt = prompt
        val tokens = if (tokenQueue.isNotEmpty()) tokenQueue.removeAt(0) else mockTokens
        tokens.forEach { 
            if (streamDelay > 0) delay(streamDelay)
            emit(it) 
        }
    }
    
    override fun generatePersistentStreaming(prompt: String, isFirstTurn: Boolean): Flow<String> = flow {
        lastPrompt = prompt
        val tokens = if (tokenQueue.isNotEmpty()) tokenQueue.removeAt(0) else mockTokens
        tokens.forEach { 
            if (streamDelay > 0) delay(streamDelay)
            emit(it) 
        }
    }

    override fun generateMultimodalStreaming(content: MultimodalContent, isFirstTurn: Boolean): Flow<String> = flow {
        lastPrompt = content.text
        val tokens = if (tokenQueue.isNotEmpty()) tokenQueue.removeAt(0) else mockTokens
        tokens.forEach { 
            if (streamDelay > 0) delay(streamDelay)
            emit(it) 
        }
    }
    
    override fun hasActiveConversation(): Boolean = true
    override suspend fun closeSession() {}
    override suspend fun close() { closeCount++ }
}
