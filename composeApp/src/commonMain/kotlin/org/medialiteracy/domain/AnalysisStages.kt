package org.medialiteracy.domain

import kotlinx.serialization.json.Json
import kotlinx.serialization.decodeFromString

/**
 * Pure logic for the Summary Analysis stage.
 */
object SummaryStage {
    private val json = Json { 
        ignoreUnknownKeys = true 
        coerceInputValues = true
        isLenient = true
    }

    private val summaryPromptTemplate = """
        You are a Media Literacy Guide. Analyze the following text and provide a structured JSON report.
        Strictly return ONLY a valid JSON object matching this schema:
        {
          "summary": "Short 2-sentence executive summary.",
          "highlights": ["Key Insight 1", "Key Insight 2"],
          "fallacies": [{"type": "Name", "description": "Why it is a fallacy", "evidence": "Quote from text"}],
          "objectivityScore": 0-100,
          "logicScore": 0-100,
          "evidenceQuality": 0-100,
          "credibilityScore": 0-100,
          "credibility": "e.g. Balanced",
          "primaryStrength": "e.g. Logic",
          "observationArea": "e.g. Tone"
        }
        
        Text:
    """.trimIndent()

    fun buildPrompt(article: String): String = "$summaryPromptTemplate\n$article\n"

    fun parse(raw: String): AnalysisResult {
        return try {
            val jsonStart = raw.indexOf("{")
            val jsonEnd = raw.lastIndexOf("}") + 1
            
            if (jsonStart != -1 && jsonEnd > jsonStart) {
                val jsonString = raw.substring(jsonStart, jsonEnd).trim()
                json.decodeFromString<AnalysisResult>(jsonString)
            } else {
                throw Exception("No valid JSON found")
            }
        } catch (e: Exception) {
            // Minimal fallback for stability
            AnalysisResult(
                summary = "Parsing failed. Content: ${raw.take(200)}...",
                highlights = emptyList(),
                fallacies = emptyList(),
                objectivityScore = 0,
                logicScore = 0,
                evidenceQuality = 0,
                credibility = "Error",
                credibilityScore = 0,
                primaryStrength = "System",
                observationArea = "Parsing",
                isAnalyzingFallacies = false,
                vocalTone = null,
                keyClaims = emptyList()
            )
        }
    }
}

/**
 * Pure logic for the Deep Fallacy Scan stage.
 */
object FallacyStage {
    private val deepAnalysisPromptTemplate = """
        As a Logic Master, dive deeper into the text. 
        Identify exactly 3 significant rhetorical patterns or logical fallacies. 
        Format EACH as: 
        #### [N]. [Name]
        * **Instance:** [Quote]
        * **Analysis:** [Logic Deconstruction]
    """.trimIndent()

    fun buildPrompt(): String = "$deepAnalysisPromptTemplate\n"

    fun parse(raw: String): List<Fallacy> {
        val fallacies = mutableListOf<Fallacy>()
        val fallacyRegex = Regex("#### \\d+\\. ([^\\n]+)[\\s\\S]*?\\*\\s+\\*\\*Instance:\\*\\*\\s+([^\\n]+)[\\s\\S]*?\\*\\s+\\*\\*Analysis:\\*\\*\\s+([\\s\\S]*?)(?=####|###|Conclusion|$)")
        fallacyRegex.findAll(raw).forEach { match ->
            fallacies.add(Fallacy(
                type = match.groupValues[1].trim(),
                description = "Logical Fallacy Detected",
                evidence = "${match.groupValues[2].trim()}\n\n${match.groupValues[3].trim()}"
            ))
        }
        return fallacies
    }
}

/**
 * Pure logic for the Audio Analysis stage (independent chunk analysis).
 */
object AudioAnalysisStage {
    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true; isLenient = true }

    fun buildPrompt(timestamp: String): String = """
        Analyze this 25s audio clip (Segment $timestamp).
        
        TASK:
        1. Transcribe the audio content as accurately as possible.
        2. Identify different speakers if present (use [Speaker 1], [Speaker 2] labels).
        3. Analyze for vocal tone and emotion.
        
        FORMATTING: Return ONLY the JSON object. No intro, no outro, no commentary. Use \n for turn-based newlines in the transcript field.
        
        Strictly return ONLY a valid JSON object matching this schema:
        {
          "timestamp": "$timestamp",
          "hasMultipleSpeakers": true/false,
          "dominantTone": "Summary of tone",
          "transcript": "[Speaker 1]: text\n\n[Speaker 2]: text",
          "objectivityScore": 0-100,
          "logicScore": 0-100
        }
    """.trimIndent()

    fun parse(raw: String): ChunkObservation? {
        val cleaned = raw.trim()
        return try {
            // Find the first { and last } to isolate the JSON block
            val jsonStart = cleaned.indexOf("{")
            val jsonEnd = cleaned.lastIndexOf("}") + 1
            if (jsonStart != -1 && jsonEnd > jsonStart) {
                var jsonString = cleaned.substring(jsonStart, jsonEnd)
                
                // Best-effort fix: Replace raw newlines inside the JSON string values
                // This is a common failure mode for smaller models
                // We look for newlines that are NOT followed by a JSON key pattern
                // This is very rough but helps in some cases.
                
                json.decodeFromString<ChunkObservation>(jsonString)
            } else {
                null
            }
        } catch (e: Exception) {
            Logger.e("AnalysisStages", "Failed to parse JSON: ${e.message}")
            null
        }
    }
}

/**
 * Pure logic for the Final Synthesis stage.
 */
object SynthesisStage {
    fun buildPrompt(observations: List<ChunkObservation>, fullTranscript: String): String {
        // Bloat Guard: If we have > 15 chunks (approx 6 mins), sample to stay within context
        // but keep the first and last chunks for context.
        val safeObservations = if (observations.size > 15) {
            listOf(observations.first()) + 
            observations.drop(1).dropLast(1).chunked(2).map { it.first() } + 
            listOf(observations.last())
        } else observations

        val obsList = safeObservations.joinToString("\n\n") { obs ->
            val speakerFlag = if (obs.hasMultipleSpeakers) " [Multi-speaker]" else ""
            "Segment ${obs.timestamp}$speakerFlag:\nTone: ${obs.dominantTone}\nClaims: ${obs.keyClaims.joinToString(", ")}\nFallacies: ${obs.fallacies.joinToString { "${it.type}: ${it.instance}" }}"
        }
        
        return """
            You are a Media Literacy Guide. You have analyzed an audio recording. 
            Below is the FULL TRANSCRIPT and the SEGMENT-BY-SEGMENT observations (including vocal tone and emotional cues).
            
            Synthesize these into a final, unified report. 
            Look for "Global" logical fallacies and extract the primary "Key Claims" made throughout the entire transcript.
            If multiple speakers are present, describe their interaction dynamics.
            
            FULL TRANSCRIPT:
            $fullTranscript
            
            SEGMENT OBSERVATIONS (Vocal Tone & Local Logic):
            $obsList
            
            Strictly return ONLY a valid JSON object matching this schema:
            {
              "summary": "Short 2-sentence executive summary.",
              "highlights": ["Key Insight 1", "Key Insight 2"],
              "objectivityScore": 0-100,
              "logicScore": 0-100,
              "evidenceQuality": 0-100,
              "credibilityScore": 0-100,
              "credibility": "e.g. Balanced",
              "primaryStrength": "e.g. Logic",
              "observationArea": "e.g. Tone",
              "vocalTone": "Summary of prosody across segments",
              "keyClaims": ["Claim 1", "Claim 2"]
            }
            
            Ensure you include "vocalTone" and "keyClaims" in the JSON.
        """.trimIndent()
    }
}

@kotlinx.serialization.Serializable
data class ChunkObservation(
    val timestamp: String,
    val hasMultipleSpeakers: Boolean = false,
    val dominantTone: String,
    val transcript: String = "",
    val keyClaims: List<String> = emptyList(),
    val fallacies: List<ChunkFallacy> = emptyList(),
    val objectivityScore: Int,
    val logicScore: Int
)

@kotlinx.serialization.Serializable
data class ChunkFallacy(
    val type: String,
    val instance: String
)
