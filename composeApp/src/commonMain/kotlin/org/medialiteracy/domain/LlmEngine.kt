package org.medialiteracy.domain

interface LlmEngine {
    suspend fun generateResponse(prompt: String): String
    suspend fun analyzeMultimodal(input: ByteArray, type: InputType): AnalysisResult
}

enum class InputType {
    TEXT, IMAGE, AUDIO
}

expect fun getLlmEngine(): LlmEngine
