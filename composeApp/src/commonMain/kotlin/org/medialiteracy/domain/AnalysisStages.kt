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
                val jsonString = raw.substring(jsonStart, jsonEnd)
                    .removePrefix("```json")
                    .removePrefix("```")
                    .removeSuffix("```")
                    .trim()
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
        You are a specialized Audio Analysis module. Analyze this 25s audio clip (Segment $timestamp) for vocal tone, emotion, and key claims.
        Identify any logical fallacies heard in the speech.
        
        Strictly return ONLY a valid JSON object matching this schema:
        {
          "timestamp": "$timestamp",
          "dominantTone": "e.g., Aggressive, rushed",
          "transcript": "Verbatim transcription of this 25s segment.",
          "keyClaims": ["Claim 1", "Claim 2"],
          "fallacies": [{"type": "Name", "instance": "Quote"}],
          "objectivityScore": 0-100,
          "logicScore": 0-100
        }
    """.trimIndent()

    fun parse(raw: String): ChunkObservation? {
        val cleaned = raw.trim()
            .removePrefix("```json")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()
        return try {
            val jsonStart = cleaned.indexOf("{")
            val jsonEnd = cleaned.lastIndexOf("}") + 1
            if (jsonStart != -1 && jsonEnd > jsonStart) {
                val jsonString = cleaned.substring(jsonStart, jsonEnd)
                json.decodeFromString<ChunkObservation>(jsonString)
            } else null
        } catch (e: Exception) { null }
    }
}

/**
 * Pure logic for the Final Synthesis stage.
 */
object SynthesisStage {
    fun buildPrompt(observations: List<ChunkObservation>): String {
        // Bloat Guard: If we have > 8 chunks (approx 3.5 mins), sample the middle to stay within context
        val safeObservations = if (observations.size > 8) {
            listOf(observations.first()) + 
            observations.drop(1).dropLast(1).chunked(2).map { it.first() } + 
            listOf(observations.last())
        } else observations

        val obsList = safeObservations.joinToString("\n\n") { obs ->
            "Segment ${obs.timestamp}:\nTone: ${obs.dominantTone}\nClaims: ${obs.keyClaims.joinToString(", ")}\nFallacies: ${obs.fallacies.joinToString { "${it.type}: ${it.instance}" }}"
        }
        
        return """
            You are a Media Literacy Guide. You have analyzed an audio recording in segments. 
            Synthesize these segment observations into a final, unified report.
            
            Observations:
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
    val dominantTone: String,
    val transcript: String = "",
    val keyClaims: List<String>,
    val fallacies: List<ChunkFallacy>,
    val objectivityScore: Int,
    val logicScore: Int
)

@kotlinx.serialization.Serializable
data class ChunkFallacy(
    val type: String,
    val instance: String
)
