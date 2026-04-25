package org.medialiteracy.domain

import kotlinx.coroutines.flow.Flow

interface LlmEngine {
    fun init(context: Any)
    fun generateStreaming(prompt: String): Flow<String>
    suspend fun generateResponse(prompt: String): String
    suspend fun analyzeMultimodal(input: ByteArray, type: InputType): AnalysisResult

    companion object {
        private var instance: LlmEngine? = null
        
        fun initialize(context: Any) {
            getInstance().init(context)
        }

        fun getInstance(): LlmEngine {
            return getLlmEngine()
        }
    }
}

enum class InputType {
    TEXT, IMAGE, AUDIO
}

expect fun getLlmEngine(): LlmEngine
