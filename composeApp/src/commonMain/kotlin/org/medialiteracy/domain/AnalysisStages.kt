package org.medialiteracy.domain

import kotlinx.serialization.json.*
import kotlinx.serialization.decodeFromString

/**
 * Pure logic for the Summary Analysis stage.
 */
/**
 * Stage 1: Executive Perception
 * Extracts a concise summary and key highlights from the content.
 * This is always the first prompt in a session.
 */
object ImageTranscriptionStage {
    fun buildPrompt(description: String): String = """
        MECHANICAL TRANSCRIPTION TASK:
        Transcribe the provided image line-by-line. 
        
        INSTRUCTIONS:
        1. Identify every individual line of text in the image.
        2. Transcribe each line verbatim, one by one.
        3. Do not skip any text. Do not summarize.
        
        CONTEXT: $description
        
        FORMATTING: Return ONLY a valid JSON object matching this schema:
        {
          "fullTranscript": "Line 1 content\nLine 2 content\nLine 3 content..."
        }
    """.trimIndent()
}

/**
 * Stage 1: Executive Perception
 * Extracts a concise summary and key highlights from the content.
 * This is always the first prompt in a session.
 */
object SummaryStage {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun buildPrompt(): String = """
        Perceive and summarize the previously provided content.
        
        TASK:
        Provide a 2-sentence executive summary.
        
        FORMATTING: Return ONLY a valid JSON object matching this schema:
        {
          "summary": "2-sentence summary"
        }
    """.trimIndent()

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
            AnalysisResult(summary = "Analysis parsing error. $raw")
        }
    }
}

/**
 * Stage 2: Metrication
 * Calculates structural scores (0-100).
 */
object MetricsStage {
    fun buildPrompt(): String = """
        Analyze the structural integrity and framing of the previously provided content.
        
        TASK:
        Provide scores from 0-100 for:
        1. Objectivity (100 = Neutral framing, no bias)
        2. Logic (100 = No fallacies, consistent)
        3. Evidence (100 = Verifiable support)
        4. Credibility (100 = Trustworthy)
        5. A qualitative label (e.g. "Highly Credible", "Mixed")
        
        FORMATTING: Return ONLY a valid JSON object matching this schema:
        {
          "objectivityScore": 0-100,
          "logicScore": 0-100,
          "evidenceQuality": 0-100,
          "credibilityScore": 0-100,
          "credibility": "Label"
        }
    """.trimIndent()
}

/**
 * Stage 3: Extraction
 * Identifies the specific claims made.
 */
object ClaimsStage {
    fun buildPrompt(): String = """
        Extract the primary claims made in the previously provided content.
        
        TASK:
        List the 3-5 most significant factual or argumentative claims.
        
        FORMATTING: Return ONLY a valid JSON object matching this schema:
        {
          "keyClaims": ["Claim 1", "Claim 2"]
        }
    """.trimIndent()
}
/**
 * Stage 4: Tone Synthesis (Audio only)
 * Consolidates prosody observations.
 */
object ToneStage {
    fun buildPrompt(): String = """
        Synthesize the vocal tone and emotional framing based on the previously provided segment observations.
        
        TASK:
        Provide a 1-sentence summary of the overall vocal tone.
        
        FORMATTING: Return ONLY a valid JSON object matching this schema:
        {
          "vocalTone": "Summary of prosody"
        }
    """.trimIndent()
}
object FallacyStage {
    private val deepAnalysisPromptTemplate = """
        As an expert in Logic and Critical Thinking, identify exactly 3 significant rhetorical patterns or logical fallacies. 
        Return ONLY a JSON array of objects with these keys:
        - "type": Name of the pattern
        - "evidence": The primary literal quote from the text
        - "description": 1 sentence explaining why this is relevant, followed by a newline and brief supporting quotes or phrases.
    """.trimIndent()

    fun buildPrompt(): String = "$deepAnalysisPromptTemplate\n"

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun parse(raw: String): List<Fallacy> {
        val fallacies = mutableListOf<Fallacy>()
        
        // 1. Primary: JSON Parsing
        try {
            val jsonStart = raw.indexOf("[")
            val jsonEnd = raw.lastIndexOf("]")
            if (jsonStart != -1 && jsonEnd != -1 && jsonEnd > jsonStart) {
                val jsonStr = raw.substring(jsonStart, jsonEnd + 1)
                val element = json.parseToJsonElement(jsonStr)
                if (element is JsonArray) {
                    element.forEach { item ->
                        if (item is JsonObject) {
                            val type = item["type"]?.jsonPrimitive?.content 
                                ?: item["name"]?.jsonPrimitive?.content 
                                ?: "Unknown Pattern"
                            val evidence = item["evidence"]?.jsonPrimitive?.content 
                                ?: item["instance"]?.jsonPrimitive?.content 
                                ?: item["quote"]?.jsonPrimitive?.content 
                                ?: ""
                            val description = item["description"]?.jsonPrimitive?.content 
                                ?: item["analysis"]?.jsonPrimitive?.content 
                                ?: "Logical Deconstruction"
                            
                            if (evidence.isNotBlank()) {
                                fallacies.add(Fallacy(type, description, evidence))
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            // Fallback to Markdown if JSON fails
        }

        // 2. Secondary: Markdown Regex Fallback
        if (fallacies.isEmpty()) {
            val fallacyRegex = Regex("(?i)#{3,4}\\s*(?:\\d+\\.)?\\s*([^\\n]+)[\\s\\S]*?[*\\-]\\s*(?:\\*\\*)?Instance(?:\\*\\*)?:?\\s*([^\\n]+)[\\s\\S]*?[*\\-]\\s*(?:\\*\\*)?Analysis(?:\\*\\*)?:?\\s*([\\s\\S]*?)(?=#{3,4}|###|Conclusion|$)")
            fallacyRegex.findAll(raw).forEach { match ->
                fallacies.add(Fallacy(
                    type = match.groupValues[1].trim(),
                    description = "Logical Fallacy Detected",
                    evidence = "${match.groupValues[2].trim()}\n\n${match.groupValues[3].trim()}"
                ))
            }
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
          "dominantTone": "Summary of tone",
          "transcript": "[Speaker 1]: text\n\n[Speaker 2]: text"
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
 * Stage 5: Socratic Bridge
 * Generates thought-provoking questions to encourage critical thinking.
 */
object SocraticStage {
    fun buildPrompt(): String = """
        Based on the previous analysis of claims and evidence, generate exactly 3 thought-provoking questions for the user.
        
        TASK:
        These questions should encourage the user to look deeper into the "hidden agenda" or "framing" of the article.
        - Do not give the answer.
        - Encourage self-reflection.
        
        FORMATTING: Return ONLY a valid JSON object matching this schema:
        {
          "socraticQuestions": ["Question 1?", "Question 2?", "Question 3?"]
        }
    """.trimIndent()
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
            "Segment ${obs.timestamp}:\nTone: ${obs.dominantTone}"
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
              "objectivityScore": 0-100,
              "logicScore": 0-100,
              "evidenceQuality": 0-100,
              "credibilityScore": 0-100,
              "credibility": "e.g. Highly Credible",
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
    val keyClaims: List<String> = emptyList(),
    val fallacies: List<ChunkFallacy> = emptyList()
)

@kotlinx.serialization.Serializable
data class ChunkFallacy(
    val type: String,
    val instance: String
)
