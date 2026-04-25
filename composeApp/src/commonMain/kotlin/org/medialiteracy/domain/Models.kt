package org.medialiteracy.domain

import kotlinx.serialization.Serializable

enum class InputType {
    PHOTO, AUDIO, TEXT
}

@Serializable
data class AnalysisResult(
    val summary: String,
    val highlights: List<String> = emptyList(),
    val fallacies: List<Fallacy> = emptyList(),
    val objectivityScore: Int, // 0 to 100 (High = Objective/Neutral)
    val logicScore: Int,       // 0 to 100 (High = Structurally Sound)
    val evidenceQuality: Int,  // 0 to 100 (High = Strong/Verified)
    val credibilityScore: Int, // 0 to 100 (High = Trustworthy)
    val credibility: String,
    val primaryStrength: String,
    val observationArea: String,
    val isAnalyzingFallacies: Boolean = false
)

@Serializable
data class Fallacy(
    val type: String,
    val description: String,
    val evidence: String
)

sealed class InferenceState {
    object Idle : InferenceState()
    data class Thinking(val partialResponse: String) : InferenceState()
    data class Complete(val result: AnalysisResult) : InferenceState()
    data class Error(val message: String) : InferenceState()
    data class DownloadingModel(val progress: Float) : InferenceState()
}
