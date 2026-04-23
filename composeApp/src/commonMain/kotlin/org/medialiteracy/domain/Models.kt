package org.medialiteracy.domain

import kotlinx.serialization.Serializable

@Serializable
data class AnalysisResult(
    val summary: String,
    val highlights: List<String>,
    val fallacies: List<Fallacy>,
    val toneScore: Int, // 1-5
    val evidenceQuality: Int, // 0-100
    val credibility: String
)

@Serializable
data class Fallacy(
    val type: String,
    val description: String,
    val evidence: String
)

sealed interface InferenceState {
    data object Idle : InferenceState
    data object Loading : InferenceState
    data class DownloadingModel(val progress: Float) : InferenceState
    data class Thinking(val tokens: String) : InferenceState
    data class Complete(val result: AnalysisResult) : InferenceState
    data class Error(val message: String) : InferenceState
}
