package org.medialiteracy.domain

import kotlinx.coroutines.delay

class IosLlmEngine : LlmEngine {
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
            toneScore = 0,
            evidenceQuality = 0,
            credibility = "N/A"
        )
    }
}

actual fun getLlmEngine(): LlmEngine = IosLlmEngine()
