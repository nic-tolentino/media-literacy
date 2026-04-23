package org.medialiteracy.domain

import kotlinx.coroutines.delay

class AndroidLlmEngine : LlmEngine {
    override suspend fun generateResponse(prompt: String): String {
        delay(1000)
        return "This is a simulated response from Android MediaPipe stub."
    }

    override suspend fun analyzeMultimodal(input: ByteArray, type: InputType): AnalysisResult {
        delay(3000)
        return AnalysisResult(
            summary = "Simulated Analysis of $type",
            highlights = listOf("Highlight 1", "Highlight 2"),
            fallacies = listOf(Fallacy("Ad Hominem", "Attacking the person", "Example text")),
            toneScore = 3,
            evidenceQuality = 75,
            credibility = "Medium"
        )
    }
}

actual fun getLlmEngine(): LlmEngine = AndroidLlmEngine()
