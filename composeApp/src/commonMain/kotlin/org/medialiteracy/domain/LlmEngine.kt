package org.medialiteracy.domain

import kotlinx.coroutines.flow.Flow

interface LlmEngine {
    fun initialize(context: Any)
    
    // Standard stateless generation
    fun generateStreaming(prompt: String): Flow<String>
    
    // Persistent generation for shared conversations
    fun generatePersistentStreaming(prompt: String, isFirstTurn: Boolean): Flow<String>
    
    suspend fun generateResponse(prompt: String): String
    suspend fun analyzeMultimodal(input: ByteArray, type: InputType): AnalysisResult
    
    fun close()
    
    companion object {
        fun getInstance(): LlmEngine = getLlmEngine()
    }
}

expect fun getLlmEngine(): LlmEngine
