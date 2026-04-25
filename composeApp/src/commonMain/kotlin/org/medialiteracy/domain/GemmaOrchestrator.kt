package org.medialiteracy.domain

import kotlinx.serialization.json.Json
import kotlinx.serialization.decodeFromString
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.delay
import cafe.adriel.voyager.core.model.ScreenModel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import cafe.adriel.voyager.core.model.screenModelScope
import kotlinx.coroutines.yield
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow

/**
 * The central orchestration engine for the Media Literacy Dashboard.
 *
 * This class manages the [InferenceState] lifecycle and bridges the gap between the raw 
 * [LlmEngine] and the UI. It handles:
 * 1. **Two-Stage Analysis**: Requesting an initial JSON summary followed by a deep fallacy scan.
 * 2. **JSON Marshaling**: Extracting structured [AnalysisResult] data from model responses.
 * 3. **Chat State**: Managing persistent multi-turn conversations through "Sticky Sessions".
 */
class GemmaOrchestrator(
    private val engine: LlmEngine = LlmEngine.getInstance()
) : ScreenModel {
    
    /** Configured for lenient parsing to handle model-generated formatting variations. */
    private val json = Json { 
        ignoreUnknownKeys = true 
        coerceInputValues = true
        isLenient = true
    }

    private val _state = MutableStateFlow<InferenceState>(InferenceState.Idle)
    
    /** Public observable state for UI reaction. */
    val state: StateFlow<InferenceState> = _state.asStateFlow()

    private var lastAnalyzedInput: String? = null
    private var lastAnalysisResult: AnalysisResult = createDefaultResult()
    private var deepAnalysisJob: Job? = null

    /** Requests a structured JSON report covering summary, highlights, and 4-axis metrics. */
    private val summaryPrompt = """
        <|turn|>user
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

    /** Requests a specific scan for exactly 3 logical fallacies. */
    private val deepAnalysisPrompt = """
        <|turn|>user
        As a Logic Master, dive deeper into the text. 
        Identify exactly 3 significant rhetorical patterns or logical fallacies. 
        Format EACH as: 
        #### [N]. [Name]
        * **Instance:** [Quote]
        * **Analysis:** [Logic Deconstruction]
    """.trimIndent()

    private fun createDefaultResult() = AnalysisResult(
        summary = "Analytical engine initializing...",
        highlights = emptyList(),
        fallacies = emptyList(),
        objectivityScore = 0,
        logicScore = 0,
        evidenceQuality = 0,
        credibility = "Analyzing...",
        credibilityScore = 0,
        primaryStrength = "Scanning...",
        observationArea = "Scanning...",
        isAnalyzingFallacies = false
    )

    private fun persistentFlow(prompt: String, isFirstTurn: Boolean): Flow<String> {
        return engine.generatePersistentStreaming(prompt, isFirstTurn)
    }

    /**
     * Triggers the full structural analysis pipeline.
     * Executes in two stages:
     * 1. **Summary Stage**: Generates the executive summary and Truth Radar scores.
     * 2. **Fallacy Stage**: Enriches the result with deep logical pattern analysis.
     */
    fun startAnalysis(input: String) {
        if (_state.value is InferenceState.Complete && lastAnalyzedInput == input) return
        
        deepAnalysisJob?.cancel()
        lastAnalysisResult = createDefaultResult()

        deepAnalysisJob = screenModelScope.launch {
            try {
                lastAnalyzedInput = input
                _state.value = InferenceState.Thinking("Analyzing structure...")

                val quickPrompt = "$summaryPrompt\n$input\n<|turn|>model\n"
                var summaryResponse = ""
                persistentFlow(quickPrompt, isFirstTurn = true).collect { token ->
                    summaryResponse += token
                    _state.value = InferenceState.Thinking(summaryResponse)
                }

                lastAnalysisResult = parseInterimSummary(summaryResponse).copy(isAnalyzingFallacies = true)
                _state.value = InferenceState.Complete(lastAnalysisResult)

                yield()
                delay(500) 

                val deepPrompt = "$deepAnalysisPrompt\n<|turn|>model\n"
                var deepResponse = ""
                persistentFlow(deepPrompt, isFirstTurn = false).collect { token ->
                    deepResponse += token
                }

                val finalFallacies = parseFallacies(deepResponse)
                lastAnalysisResult = lastAnalysisResult.copy(
                    fallacies = if (finalFallacies.isEmpty()) lastAnalysisResult.fallacies else finalFallacies,
                    isAnalyzingFallacies = false
                )
                
                _state.value = InferenceState.Complete(lastAnalysisResult)

            } catch (e: Exception) {
                if (e !is kotlinx.coroutines.CancellationException) {
                    _state.value = InferenceState.Error(e.message ?: "Analysis failed")
                }
            }
        }
    }

    /**
     * Generates a chat response using the model's active knowledge session.
     * Includes logic to sanitize and filter internal Chain-of-Thought tags for the final UI display.
     */
    fun generateChatResponse(userMessage: String, onUpdate: (String) -> Unit, onComplete: (String) -> Unit) {
        deepAnalysisJob?.cancel()

        screenModelScope.launch {
            try {
                val chatPrompt = "<|turn|>user\n$userMessage\nExpert Guidance:\n<|turn|>model\n"
                
                var fullResponse = ""
                persistentFlow(chatPrompt, isFirstTurn = false).collect { token ->
                    fullResponse += token
                    val cleaned = fullResponse.replace(Regex("<\\|think\\|>[\\s\\S]*?\\*?\\/\\|think\\|>"), "").trim()
                    onUpdate(cleaned)
                }
                
                val finalDisplay = fullResponse.replace(Regex("<\\|think\\|>[\\s\\S]*?\\*?\\/\\|think\\|>"), "").trim()
                onComplete(finalDisplay)
            } catch (e: Exception) {
                fallbackChatResponse(userMessage, onUpdate, onComplete)
            }
        }
    }

    private suspend fun fallbackChatResponse(message: String, onUpdate: (String) -> Unit, onComplete: (String) -> Unit) {
        val result = lastAnalysisResult
        val fallbackPrompt = """
            <|turn|>user
            You are a Logic Master. Use these previous findings to answer the question.
            Note: Objectivity Score (0-100) measures neutrality.
            
            Context: ${lastAnalyzedInput?.take(500)}
            Summary: ${result.summary}
            Metrics: Logic ${result.logicScore}, Evidence ${result.evidenceQuality}, Objectivity ${result.objectivityScore}
            
            Question: $message
            Expert Guidance:
            <|turn|>model
        """.trimIndent()
        
        var fullText = ""
        engine.generateStreaming(fallbackPrompt).collect { token ->
            fullText += token
            onUpdate(fullText.trim())
        }
        onComplete(fullText.trim())
    }

    /**
     * Extracts a structured [AnalysisResult] from the model's raw string response.
     * Uses a boundary-finding algorithm to isolate the JSON block from potential markdown decorations.
     */
    private fun parseInterimSummary(raw: String): AnalysisResult {
        println("RAW_OUTPUT_FULL ->\n$raw")

        return try {
            val jsonStart = raw.indexOf("{")
            val jsonEnd = raw.lastIndexOf("}") + 1
            
            if (jsonStart != -1 && jsonEnd > jsonStart) {
                val jsonString = raw.substring(jsonStart, jsonEnd)
                println("JSON_DEBUG_DUMP_START\n$jsonString\nJSON_DEBUG_DUMP_END")
                
                val parsed = json.decodeFromString<AnalysisResult>(jsonString)
                
                // Ensure values are derived correctly
                parsed.copy(
                    isAnalyzingFallacies = true
                )
            } else {
                throw Exception("No valid JSON found")
            }
        } catch (e: Exception) {
            android.util.Log.e("GemmaEngine", "JSON Parse Failed: ${e.message}")
            
            // EMERGENCY FALLBACK: Regex for absolute minimal stability
            val logic = Regex("(?i)LOGIC[:\\s-*]+(\\d+)").find(raw)?.groupValues?.get(1)?.toIntOrNull() ?: 0
            val evidence = Regex("(?i)EVIDENCE[:\\s-*]+(\\d+)").find(raw)?.groupValues?.get(1)?.toIntOrNull() ?: 0
            val credibility = Regex("(?i)CREDIBILITY[:\\s-*]+(\\d+)").find(raw)?.groupValues?.get(1)?.toIntOrNull() ?: 0
            val objectivity = Regex("(?i)OBJECTIVITY[:\\s-*]+(\\d+)").find(raw)?.groupValues?.get(1)?.toIntOrNull() ?: 0

            AnalysisResult(
                summary = "DEBUG: JSON FAIL. RAW:\n${raw.take(500)}",
                highlights = listOf("Structural Integrity"),
                fallacies = emptyList(),
                objectivityScore = objectivity,
                logicScore = logic,
                evidenceQuality = evidence,
                credibility = "Evaluated",
                credibilityScore = credibility,
                primaryStrength = "System",
                observationArea = "Parsing",
                isAnalyzingFallacies = true
            )
        }
    }

    private fun parseFallacies(raw: String): List<Fallacy> {
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

    /** Mock Model weight download progress simulation. */
    fun downloadModel() {
        screenModelScope.launch {
            _state.value = InferenceState.DownloadingModel(0f)
            for (i in 0..100 step 10) {
                _state.value = InferenceState.DownloadingModel(i / 100f)
                delay(400)
            }
            _state.value = InferenceState.Idle
        }
    }
}
