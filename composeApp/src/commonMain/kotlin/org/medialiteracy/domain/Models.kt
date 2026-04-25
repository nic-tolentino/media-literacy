package org.medialiteracy.domain

import kotlinx.serialization.Serializable

/**
 * Supported intake methods for media literacy analysis.
 */
enum class InputType {
    PHOTO, AUDIO, TEXT
}

/**
 * Represents the structured output of a Media Literacy analysis.
 * This class is the primary data contract between the Gemma 4 engine and the UI layer.
 * All numeric scores follow a "High = Good/Resilient" standard.
 *
 * @property summary An executive 2-sentence summary of the model's findings.
 * @property highlights Key bullet points extracted during the structural analysis.
 * @property fallacies A list of detected logical fallacies or rhetorical manipulations.
 * @property objectivityScore 0-100 scale measuring neutral framing (100 = Highly Objective).
 * @property logicScore 0-100 scale measuring structural consistency (100 = No Fallacies).
 * @property evidenceQuality 0-100 scale measuring verifiable support (100 = Strong Evidence).
 * @property credibilityScore 0-100 scale measuring overall trust (100 = Highly Credible).
 * @property credibility Qualitative assessment label (e.g., "Balanced", "Biased").
 * @property primaryStrength The most resilient part of the article's structure.
 * @property observationArea The area most vulnerable to manipulation or bias.
 * @property isAnalyzingFallacies State flag indicating if a deep fallacy scan is active.
 */
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

/**
 * Represents a specific logical flaw or rhetorical device detected in the text.
 *
 * @property type The name of the fallacy (e.g., "Slippery Slope", "Ad Hominem").
 * @property description A brief educational explanation of why this device is used.
 * @property evidence The specific quote or behavior in the text that matches this pattern.
 */
@Serializable
data class Fallacy(
    val type: String,
    val description: String,
    val evidence: String
)

/**
 * Represents the hierarchical state of the local AI inference lifecycle.
 */
sealed class InferenceState {
    /** Engine is initialized and awaiting user input. */
    object Idle : InferenceState()
    
    /** Model is currently streaming tokens; provides partial output for real-time UI. */
    data class Thinking(val partialResponse: String) : InferenceState()
    
    /** Analysis is complete and structured result is ready for display. */
    data class Complete(val result: AnalysisResult) : InferenceState()
    
    /** An unrecoverable error occurred during inference or initialization. */
    data class Error(val message: String) : InferenceState()
    
    /** Initial model weight download in progress; progress is 0.0 to 1.0. */
    data class DownloadingModel(val progress: Float) : InferenceState()
}
