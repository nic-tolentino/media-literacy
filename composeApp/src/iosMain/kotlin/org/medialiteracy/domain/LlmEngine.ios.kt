package org.medialiteracy.domain

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class IosLlmEngine : LlmEngine {
    override fun initialize(context: Any) {
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

    override suspend fun generateResponse(prompt: String): String {
        delay(500)
        return "This is a dummy response from the iOS No-Op engine."
    }

    override suspend fun analyzeMultimodal(input: ByteArray, type: InputType): AnalysisResult {
        delay(1000)
        return AnalysisResult(
            summary = "iOS Dummy Analysis",
            highlights = emptyList(),
            fallacies = emptyList(),
            objectivityScore = 0,
            objectivityValue = 0.0f,
            logicScore = 0,
            evidenceQuality = 0,
            credibility = "N/A",
            credibilityScore = 0,
            primaryStrength = "N/A",
            observationArea = "N/A",
            isAnalyzingFallacies = false
        )
    }

    override fun close() {
        // No-op for iOS stub
    }
}

actual fun getLlmEngine(): LlmEngine = IosLlmEngine()
