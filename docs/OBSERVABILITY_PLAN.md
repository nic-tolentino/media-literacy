# Observability & Model Quality Plan

> **Status**: Draft v1 — 2026-05-04
> **Owner**: Development Team
> **Scope**: App performance monitoring, LLM production telemetry, pre-deployment model evaluation

---

## Table of Contents

1. [Executive Summary](#executive-summary)
2. [Current State Assessment](#current-state-assessment)
3. [Part 1: App Performance Monitoring](#part-1-app-performance-monitoring)
4. [Part 2: LLM Production Telemetry](#part-2-llm-production-telemetry)
5. [Part 3: Pre-Deployment Model Quality Assessment](#part-3-pre-deployment-model-quality-assessment)
   - [3.7 LLM-as-a-Judge Evaluation](#37-llm-as-a-judge-evaluation)
6. [Implementation Roadmap](#implementation-roadmap)
7. [Appendix A: Metric Definitions](#appendix-a-metric-definitions)
8. [Appendix B: Proposed Evaluation Dataset Structure](#appendix-b-proposed-evaluation-dataset-structure)

---

## Executive Summary

This plan addresses three layers of observability for the Media Literacy (GemmaLens) app:

1. **App Performance Monitoring** — tracking app stability, crash rates, latency, and resource consumption across devices.
2. **LLM Production Telemetry** — measuring inference speed, token throughput, error rates, and response consistency for on-device Gemma 4 models.
3. **Pre-Deployment Model Quality Assessment** — establishing evaluation pipelines to measure image-to-text accuracy, audio analysis accuracy, tone detection, fallacy detection, and hallucination resistance *before* a model ships to users.

Because this app performs **100% on-device inference** (no cloud API), the observability strategy must respect the local-first privacy model. Telemetry is opt-in, aggregated, and anonymized — no raw user content (articles, images, audio) leaves the device.

---

## Current State Assessment

### What Exists

| Capability | Status | Location |
|---|---|---|
| `InferenceMetrics` data class | ✅ Implemented, not consumed | `InferenceService.kt` (lines 29-35) |
| `EngineInternalState` tracking | ✅ Implemented, not persisted | `AndroidInferenceService.kt` |
| Basic `Logger` (println) | ✅ Implemented, local only | `Logger.kt` |
| Token estimation heuristic | ✅ Rough heuristic (chars/4) | `AndroidInferenceService.kt` |
| Mutex-protected engine | ✅ Prevents SIGSEGV | `LlmEngine.android.kt` |
| Hardware detection (GPU/CPU) | ✅ With fallback | `LlmEngine.android.kt` |
| Parse fallback for bad JSON | ✅ Minimal fallback | `AnalysisStages.kt` |

### What Is Missing

| Gap | Impact |
|---|---|
| No crash reporting | Cannot detect or triage production crashes |
| Metrics not displayed or persisted | `InferenceMetrics` computed but never observed |
| No error rate tracking | Cannot measure stability over time |
| No model quality evaluation | No way to compare model A vs. B before shipping |
| No JSON parse success/failure tracking | Silent fallback hides model output quality degradation |
| No device-tier performance comparison | Cannot identify which devices struggle |
| No hallucination detection | Bad outputs accepted without flagging |
| No user-visibility into performance | Users cannot see if inference is slow or failing |

---

## Part 1: App Performance Monitoring

### 1.1 Crash Reporting

**Goal**: Capture, aggregate, and triage crashes in production without compromising local-first privacy.

#### Approach: Opt-In Crash Reporting via Firebase Crashlytics (or Sentry)

| Layer | Implementation |
|---|---|
| SDK | Firebase Crashlytics (recommended for Android) or Sentry KMP (for multi-platform future) |
| Opt-in model | User selects analytics level during onboarding: `None`, `Crash Reports Only`, `Basic Telemetry`, `Full Diagnostics` |
| Privacy filter | Custom crash key scrubbing — remove any PII, article text, or user input from crash breadcrumbs |
| Native crash support | Crashlytics NDK plugin captures SIGSEGV from LiteRT-LM native layer |

**Why Crashlytics over Sentry**: Crashlytics has tighter Android integration, better native crash symbolication for `.so` files (LiteRT-LM), and is free. Sentry KMP is a viable alternative if iOS crash reporting becomes a priority sooner.

#### Implementation Steps

1. **Add Crashlytics dependency** to `composeApp/build.gradle.kts`:
   ```kotlin
   implementation("com.google.firebase:firebase-crashlytics:19.2.0")
   implementation("com.google.firebase:firebase-analytics")
   ```

2. **Create `TelemetryManager`** (commonMain):
   ```kotlin
   interface TelemetryManager {
       val analyticsLevel: AnalyticsLevel // None, CrashesOnly, Basic, Full
       fun recordCrash(throwable: Throwable, context: CrashContext)
       fun recordEvent(name: String, attributes: Map<String, String> = emptyMap())
       fun recordMetric(name: String, value: Double, unit: String)
       fun setUserId(hash: String) // Anonymized device ID
   }

   enum class AnalyticsLevel {
       None, CrashesOnly, Basic, Full
   }

   data class CrashContext(
       val screen: String,        // e.g., "AnalysisScreen"
       val inputType: InputType,  // TEXT, IMAGE, AUDIO
       val engineState: EngineInternalState,
       val deviceTier: DeviceTier, // LOW, MID, HIGH (based on RAM/CPU)
       val modelBackend: String,   // "GPU", "CPU", "CPU_FALLBACK"
       val articleLength: Int,     // Character count (not content)
       val audioDurationSec: Int?,
       val isModelDownloaded: Boolean
   )
   ```

3. **Wrap existing Logger** to also route to `TelemetryManager` when `analyticsLevel >= Basic`:
   ```kotlin
   // In Logger.kt
   object Logger {
       var telemetrySink: TelemetryManager? = null

       fun e(tag: String, message: String, throwable: Throwable? = null) {
           println("ERROR: [$tag] $message")
           throwable?.printStackTrace()
           telemetrySink?.recordEvent("error_log", mapOf(
               "tag" to tag,
               "message" to message.take(200) // Truncate
           ))
       }
   }
   ```

4. **Catch and report native crashes** in `LlmEngine.android.kt`:
   ```kotlin
   // In initialize(), catch blocks
   catch (e: Exception) {
       Logger.e("GemmaEngine", "Failed to initialize LiteRT-LM: ${e.message}")
       telemetrySink?.recordCrash(e, CrashContext(
           screen = "Initialization",
           inputType = InputType.TEXT,
           engineState = EngineInternalState.Error,
           deviceTier = detectDeviceTier(),
           modelBackend = useGpu.toString(),
           articleLength = 0,
           audioDurationSec = null,
           isModelDownloaded = modelFile != null
       ))
   }
   ```

5. **Device tier detection** (heuristic based on `Build` properties):
   ```kotlin
   fun detectDeviceTier(): DeviceTier {
       val runtime = Runtime.getRuntime()
       val maxMemory = runtime.maxMemory() / (1024 * 1024) // MB
       val processors = runtime.availableProcessors()
       return when {
           maxMemory >= 6144 && processors >= 8 -> DeviceTier.HIGH
           maxMemory >= 3072 && processors >= 4 -> DeviceTier.MID
           else -> DeviceTier.LOW
       }
   }

   enum class DeviceTier { LOW, MID, HIGH }
   ```

---

### 1.2 Performance Metrics Dashboard

**Goal**: Surface existing `InferenceMetrics` to both developers (debug builds) and users (optional).

#### 1.2.1 Debug Overlay (Dev Builds Only)

Create a debug overlay that displays real-time inference metrics:

```kotlin
@Composable
fun DebugPerformanceOverlay(
    metrics: InferenceMetrics,
    engineState: EngineInternalState,
    coordinatorState: InferenceState
) {
    // Semi-transparent overlay showing:
    // - Engine state
    // - Time to first token (ms)
    // - Tokens/sec
    // - Estimated tokens used
    // - Current coordinator state
    // - Parse success/failure count
}
```

**Where to consume metrics**: In `GemmaOrchestrator`, collect `_metrics` from `InferenceService` and expose to UI:

```kotlin
// In GemmaOrchestrator.kt
val inferenceMetrics: StateFlow<InferenceMetrics> = inferenceService.metrics
```

#### 1.2.2 User-Facing Performance Indicator (Optional)

A subtle indicator in the Analysis screen showing inference health:

- 🟢 **Fast** (>15 tokens/sec)
- 🟡 **Moderate** (5-15 tokens/sec)
- 🔴 **Slow** (<5 tokens/sec, device may be struggling)

This helps users understand why analysis might take longer on their device.

#### 1.2.3 Persistent Metrics Storage

Store aggregated metrics locally using DataStore for later review or opt-in export:

```kotlin
data class PerformanceLog(
    val timestamp: Long,
    val inputType: InputType,
    val timeToFirstTokenMs: Long,
    val tokensPerSecond: Double,
    val totalTokensEstimated: Int,
    val parseSuccess: Boolean,       // Did JSON parsing succeed?
    val engineBackend: String,       // GPU, CPU, CPU_FALLBACK
    val deviceTier: DeviceTier,
    val inputLength: Int,            // chars for text, seconds for audio
    val errorType: String?           // null if success
)
```

Store last 100 entries in a ring buffer in DataStore. User can export this as JSON for debugging.

---

### 1.3 Error Rate Tracking

**Goal**: Measure and alert on inference failure rates.

#### Error Classification

Define a taxonomy of errors to track:

| Error Category | Subtypes | Severity |
|---|---|---|
| **Engine Initialization Failure** | Model not found, native crash, OOM | Critical |
| **Inference Runtime Error** | Exception during generation, timeout | High |
| **Parse Failure** | LLM returned invalid JSON, schema mismatch | Medium |
| **Token Budget Exceeded** | Context overflow, forced reset | Low (expected) |
| **Cancellation** | User navigated away, interrupted | Info |
| **Input Validation** | Article >8000 chars, audio >12 chunks | Info |

#### Implementation

Add error counting to `AndroidInferenceService`:

```kotlin
data class ErrorCounts(
    val initFailure: AtomicInteger = AtomicInteger(0),
    val runtimeError: AtomicInteger = AtomicInteger(0),
    val parseFailure: AtomicInteger = AtomicInteger(0),
    val budgetExceeded: AtomicInteger = AtomicInteger(0),
    val cancellation: AtomicInteger = AtomicInteger(0),
    val inputValidation: AtomicInteger = AtomicInteger(0)
)

// In handleInferenceError():
private fun handleInferenceError(e: Exception) {
    if (e is CancellationException) {
        errorCounts.cancellation.incrementAndGet()
        Logger.d("InferenceService", "Generation cancelled.")
    } else {
        errorCounts.runtimeError.incrementAndGet()
        Logger.e("InferenceService", "Inference error: ${e.message}")
        _state.value = EngineInternalState.Error
    }
}
```

#### Parse Success/Failure Tracking (in AnalysisCoordinator)

```kotlin
// In startAnalysis(), after SummaryStage.parse():
val parseSuccess = result.summary != "Parsing failed. Content: ..."
if (!parseSuccess) {
    errorCounts.parseFailure.incrementAndGet()
    Logger.w("AnalysisCoordinator", "JSON parse failed, raw response length: ${summaryResponse.length}")
}
```

---

### 1.4 Input Size & Context Window Optimization

**Goal**: Dynamically determine optimal context window per device and track input distribution.

#### Dynamic Context Window Calculation

On app startup (or model initialization), calculate an optimal context window:

```kotlin
fun calculateOptimalContextWindow(): Int {
    val runtime = Runtime.getRuntime()
    val maxMemoryMB = runtime.maxMemory() / (1024 * 1024)
    val processors = runtime.availableProcessors()
    val hasGPU = hasOpenCL() // From LlmEngine check

    return when {
        hasGPU && maxMemoryMB >= 6144 -> 8192   // High-tier GPU
        hasGPU && maxMemoryMB >= 3072 -> 4096   // Mid-tier GPU
        !hasGPU && maxMemoryMB >= 6144 -> 4096  // High-tier CPU
        !hasGPU && maxMemoryMB >= 3072 -> 2048  // Mid-tier CPU
        else -> 1024                             // Low-tier
    }
}
```

Track the calculated window size and actual input sizes to understand utilization:

```kotlin
data class ContextWindowMetrics(
    val optimalWindow: Int,
    val actualInputTokens: Int,
    val utilizationPercent: Double, // actual / optimal
    val wasTruncated: Boolean
)
```

---

## Part 2: LLM Production Telemetry

### 2.1 Inference Performance Monitoring

**Goal**: Track how the LLM performs in the wild across devices, input types, and model versions.

#### Metrics to Collect Per Inference Turn

Extend `InferenceMetrics`:

```kotlin
data class InferenceMetrics(
    val timeToFirstToken: Long = 0,           // ms — latency感知
    val tokensPerSecond: Double = 0.0,        // throughput
    val totalTokensEstimated: Int = 0,        // cost proxy
    val teardownDuration: Long = 0,           // cleanup overhead
    val inputType: InputType = InputType.TEXT,
    val inputLength: Int = 0,                 // chars or audio seconds
    val outputLength: Int = 0,                // chars in response
    val parseSuccess: Boolean = false,        // Did structured output parse?
    val engineBackend: String = "UNKNOWN",    // GPU, CPU, CPU_FALLBACK
    val modelVersion: String = "gemma-4-unknown",
    val deviceTier: DeviceTier = DeviceTier.MID,
    val errorType: String? = null             // null if success
)
```

#### Aggregation & Reporting

Collect metrics into session-level summaries:

```kotlin
data class SessionTelemetry(
    val sessionId: String,
    val startTime: Long,
    val totalInferences: Int,
    val successfulInferences: Int,
    val failedInferences: Int,
    val avgTimeToFirstToken: Double,
    val avgTokensPerSecond: Double,
    val parseSuccessRate: Double,
    val p50TimeToFirstToken: Long,
    val p95TimeToFirstToken: Long,
    val p50TokensPerSecond: Double,
    val p95TokensPerSecond: Double,
    val errorBreakdown: Map<String, Int>,     // errorType -> count
    val inputTypeBreakdown: Map<InputType, Int>,
    val deviceTier: DeviceTier,
    val modelBackend: String
)
```

Upload opt-in session summaries at session end (app backgrounded) or daily.

---

### 2.2 Response Consistency Monitoring

**Goal**: Detect when the LLM produces inconsistent outputs for similar inputs — a sign of model instability.

#### 2.2.1 Score Distribution Analysis

Track the distribution of analysis scores over time:

```kotlin
data class ScoreDistribution(
    val objectivityScores: List<Int>,
    val logicScores: List<Int>,
    val evidenceQuality: List<Int>,
    val credibilityScores: List<Int>,
    val periodStart: Long,
    val periodEnd: Long
)
```

**Red flags**:
- Scores clustering at 0 or 100 (model not discriminating)
- Sudden shift in mean score after model update
- High variance within a single session (inconsistent grading)

#### 2.2.2 JSON Parse Failure Rate

A high JSON parse failure rate indicates the model is not following the structured output schema:

```kotlin
// Track per-analysis
data class ParseResult(
    val success: Boolean,
    val rawResponseLength: Int,
    val failureReason: String?, // "No JSON found", "Schema mismatch", "Invalid number"
    val inputType: InputType,
    val stage: String           // "SummaryStage", "AudioAnalysisStage", "SynthesisStage"
)
```

**Thresholds**:
- Parse success rate < 90% → Model prompt needs adjustment
- Parse success rate < 70% → Model may be fundamentally broken for this task

#### 2.2.3 Fallback Activation Rate

Track when the fallback `AnalysisResult` is used (in `SummaryStage.parse`):

```kotlin
// In SummaryStage.parse, instead of silently falling back:
val result = try {
    // ... parse logic
} catch (e: Exception) {
    Logger.w("SummaryStage", "Fallback activated: ${e.message}")
    telemetrySink?.recordEvent("parse_fallback", mapOf(
        "error" to e.message.orEmpty().take(100),
        "stage" to "SummaryStage"
    ))
    AnalysisResult(/* fallback values */)
}
```

---

### 2.3 Hallucination Detection Heuristics

**Goal**: Flag potentially hallucinated or low-quality responses before the user sees them.

Since the app is local-first, we cannot send responses to a cloud validator. Instead, use heuristic checks:

#### 2.3.1 Structural Validators

```kotlin
object ResponseValidator {
    data class ValidationResult(
        val passed: Boolean,
        val flags: List<String>
    )

    fun validate(result: AnalysisResult): ValidationResult {
        val flags = mutableListOf<String>()

        // Check 1: Scores must be in valid range
        listOf(
            "objectivity" to result.objectivityScore,
            "logic" to result.logicScore,
            "evidence" to result.evidenceQuality,
            "credibility" to result.credibilityScore
        ).forEach { (name, score) ->
            if (score !in 0..100) flags.add("SCORE_OUT_OF_RANGE: $name=$score")
        }

        // Check 2: Summary must not be the fallback text
        if (result.summary.startsWith("Parsing failed")) {
            flags.add("FALLBACK_SUMMARY_DETECTED")
        }

        // Check 3: Summary must be reasonable length (not empty, not one word)
        val wordCount = result.summary.split("\\s+".toRegex()).size
        if (wordCount < 3) flags.add("SUMMARY_TOO_SHORT: ${wordCount} words")
        if (wordCount > 100) flags.add("SUMMARY_SUSPECTED_HALLUCINATION: ${wordCount} words")

        // Check 4: Highlights should not be empty for a successful parse
        if (result.highlights.isEmpty() && result.summary != "Parsing failed. Content: ...") {
            flags.add("NO_HIGHLIGHTS_EXTRACTED")
        }

        // Check 5: Credibility label should match score ranges
        val expectedLabel = when {
            result.credibilityScore >= 80 -> "Highly Credible"
            result.credibilityScore >= 60 -> "Moderately Credible"
            result.credibilityScore >= 40 -> "Mixed"
            result.credibilityScore >= 20 -> "Low Credibility"
            else -> "Not Credible"
        }
        if (result.credibility != expectedLabel) {
            flags.add("CREDIBILITY_LABEL_MISMATCH: label=${result.credibility}, expected=$expectedLabel")
        }

        return ValidationResult(flags.isEmpty(), flags)
    }
}
```

#### 2.3.2 Cross-Stage Consistency Check

For the two-stage text analysis pipeline, verify Stage 2 (fallacies) is consistent with Stage 1 (scores):

```kotlin
fun checkCrossStageConsistency(
    stage1Result: AnalysisResult,
    fallacies: List<Fallacy>
): List<String> {
    val flags = mutableListOf<String>()

    // If logicScore is high (>80), but many fallacies found, flag inconsistency
    if (stage1Result.logicScore > 80 && fallacies.size >= 3) {
        flags.add("LOGIC_SCORE_FALLACY_MISMATCH: logicScore=${stage1Result.logicScore}, fallacies=${fallacies.size}")
    }

    // If logicScore is low (<20) but no fallacies found, flag
    if (stage1Result.logicScore < 20 && fallacies.isEmpty()) {
        flags.add("LOW_LOGIC_NO_FALLACIES: logicScore=${stage1Result.logicScore}")
    }

    return flags
}
```

#### 2.3.3 User Feedback Loop (Optional, Future)

Allow users to flag responses as "unhelpful" or "incorrect":

```kotlin
// In AnalysisScreen UI
IconButton(onClick = { reportPoorQuality() }) {
    Icon(Icons.Default.ThumbDown, "Report poor quality analysis")
}

// Records locally and optionally uploads with next telemetry batch
data class UserFeedback(
    val analysisId: String,
    val feedbackType: FeedbackType, // POOR_SUMMARY, INCORRECT_SCORE, MISSED_FALLACY, HALLUCINATION
    val userComment: String?
)
```

---

### 2.4 Model Version Tracking

**Goal**: Know which model version each user is running to correlate performance and quality.

```kotlin
data class ModelMetadata(
    val version: String,           // e.g., "gemma-4-2b-it-cpu-int4-v1.0"
    val backend: String,           // GPU, CPU
    val maxTokens: Int,
    val fileSizeMB: Long,
    val quantization: String,      // INT4, FP16, etc.
    val downloadDate: Long?,       // null if bundled
    val source: String             // "bundled", "cloud_download"
)
```

Include `modelVersion` in all telemetry events. When a new model is deployed, compare metrics before/after.

---

## Part 3: Pre-Deployment Model Quality Assessment

### 3.1 Evaluation Framework Overview

**Goal**: Systematically evaluate new model versions *before* shipping, using a standardized test suite.

#### Architecture

```
Evaluation Runner (JVM Test)
├── Evaluation Dataset (golden set)
│   ├── Text articles with ground truth
│   ├── Images with ground truth
│   ├── Audio clips with ground truth
├── Model Under Test (LiteRT-LM instance)
├── Scoring Engine
│   ├── JSON parse success rate
│   ├── Score accuracy (vs. ground truth)
│   ├── Fallacy detection precision/recall
│   ├── Tone detection accuracy
│   ├── Transcript accuracy (WER/CER for audio)
│   └── Hallucination rate
└── Report Generator
    └── HTML/JSON report with pass/fail thresholds
```

The evaluation runner is a **JVM-based test suite** (not Android) that loads the model via LiteRT-LM's desktop API (or runs on an Android emulator/device in CI).

---

### 3.2 Text Analysis Evaluation

#### 3.2.1 Dataset Structure

Create a curated set of 50-100 articles with known characteristics:

```kotlin
data class TextEvaluationSample(
    val id: String,
    val articleText: String,
    val expectedScores: ExpectedScores,
    val expectedFallacies: List<String>,      // e.g., ["Ad Hominem", "Straw Man"]
    val expectedHighlights: List<String>,      // Key points that should be extracted
    val expectedCredibilityLabel: String,
    val category: ArticleCategory,             // NEWS, OPINION, SATIRE, PROPAGANDA
    val difficulty: Difficulty                 // EASY, MEDIUM, HARD
)

data class ExpectedScores(
    val objectivityScore: Int,
    val logicScore: Int,
    val evidenceQuality: Int,
    val credibilityScore: Int
)
```

#### 3.2.2 Evaluation Metrics

```kotlin
object TextEvaluator {
    data class TextEvaluationResult(
        val sampleId: String,
        val parseSuccess: Boolean,
        val scoreErrors: Map<String, Double>,        // metric -> absolute error
        val fallacyDetection: FallacyDetectionMetrics,
        val hallucinationFlags: List<String>,
        val overallPass: Boolean
    )

    data class FallacyDetectionMetrics(
        val truePositives: Int,
        val falsePositives: Int,
        val falseNegatives: Int,
        val precision: Double,
        val recall: Double,
        val f1: Double
    )

    fun evaluate(sample: TextEvaluationSample, modelOutput: AnalysisResult): TextEvaluationResult {
        // 1. Parse success
        val parseSuccess = modelOutput.summary != "Parsing failed. Content: ..."

        // 2. Score accuracy (tolerance ±15 points)
        val scoreErrors = mapOf(
            "objectivity" to abs(modelOutput.objectivityScore - sample.expectedScores.objectivityScore).toDouble(),
            "logic" to abs(modelOutput.logicScore - sample.expectedScores.logicScore).toDouble(),
            "evidence" to abs(modelOutput.evidenceQuality - sample.expectedScores.evidenceQuality).toDouble(),
            "credibility" to abs(modelOutput.credibilityScore - sample.expectedScores.credibilityScore).toDouble()
        )
        val scorePass = scoreErrors.values.all { it <= 15 }

        // 3. Fallacy detection
        val expectedFallacySet = sample.expectedFallacies.toSet()
        val detectedFallacySet = modelOutput.fallacies.map { it.type }.toSet()
        val tp = expectedFallacySet.intersect(detectedFallacySet).size
        val fp = detectedFallacySet.subtract(expectedFallacySet).size
        val fn = expectedFallacySet.subtract(detectedFallacySet).size
        val precision = if (tp + fp > 0) tp.toDouble() / (tp + fp) else 0.0
        val recall = if (tp + fn > 0) tp.toDouble() / (tp + fn) else 0.0
        val f1 = if (precision + recall > 0) 2 * precision * recall / (precision + recall) else 0.0
        val fallacyPass = f1 >= 0.5 && recall >= 0.4

        // 4. Hallucination check
        val validation = ResponseValidator.validate(modelOutput)
        val hallucinationFlags = validation.flags

        // 5. Overall pass
        val overallPass = parseSuccess && scorePass && fallacyPass && validation.passed

        return TextEvaluationResult(
            sampleId = sample.id,
            parseSuccess = parseSuccess,
            scoreErrors = scoreErrors,
            fallacyDetection = FallacyDetectionMetrics(tp, fp, fn, precision, recall, f1),
            hallucinationFlags = hallucinationFlags,
            overallPass = overallPass
        )
    }
}
```

#### 3.2.3 Pass/Fail Thresholds

| Metric | Threshold | Rationale |
|---|---|---|
| JSON Parse Success Rate | ≥ 90% | Model must follow schema reliably |
| Score MAE (Mean Absolute Error) | ≤ 15 points | Scores are 0-100, ±15 is acceptable for LLM judgment |
| Fallacy Detection F1 | ≥ 0.5 | Balanced precision/recall for fallacy identification |
| Fallacy Detection Recall | ≥ 0.4 | Must catch at least 40% of known fallacies |
| Hallucination Rate | < 10% | <10% of outputs should trigger validation flags |
| Overall Pass Rate | ≥ 75% | 75% of samples should pass all checks |

---

### 3.3 Image Analysis Evaluation

#### 3.3.1 Dataset Structure

Curate 30-50 images with known characteristics:

```kotlin
data class ImageEvaluationSample(
    val id: String,
    val imageBytes: ByteArray,
    val description: String,            // What the image depicts
    val expectedBias: ImageBiasType?,   // CROPPED, MISLEADING_CAPTION, EMOTIONAL_MANIPULATION, NONE
    expectedFallacies: List<String>,
    expectedSummaryKeywords: List<String>, // Words/phrases that should appear in summary
    val ocrExpected: Boolean             // Whether OCR should extract text from image
)

enum class ImageBiasType {
    CROPPED, MISLEADING_CAPTION, EMOTIONAL_MANIPULATION,
    DEEPFAKE_INDICATORS, OUT_OF_CONTEXT, NONE
}
```

#### 3.3.2 Evaluation Metrics

```kotlin
object ImageEvaluator {
    data class ImageEvaluationResult(
        val sampleId: String,
        val parseSuccess: Boolean,
        val biasDetected: ImageBiasType?,
        val biasMatch: Boolean,             // Did detected bias match expected?
        val keywordCoverage: Double,         // % of expected keywords found in summary
        val hallucinationFlags: List<String>
    )

    fun evaluate(sample: ImageEvaluationSample, modelOutput: AnalysisResult): ImageEvaluationResult {
        val parseSuccess = modelOutput.summary != "Parsing failed. Content: ..."

        // Check if detected credibility label suggests bias
        val biasDetected = when {
            modelOutput.credibilityScore < 30 -> ImageBiasType.EMOTIONAL_MANIPULATION // heuristic
            modelOutput.observationArea.contains("image", ignoreCase = true) -> ImageBiasType.CROPPED
            else -> null
        }
        val biasMatch = biasDetected == sample.expectedBias ||
                       (sample.expectedBias == null && biasDetected == null)

        // Keyword coverage
        val summaryLower = modelOutput.summary.lowercase()
        val keywordsFound = sample.expectedSummaryKeywords.count { kw ->
            summaryLower.contains(kw.lowercase())
        }
        val keywordCoverage = if (sample.expectedSummaryKeywords.isNotEmpty()) {
            keywordsFound.toDouble() / sample.expectedSummaryKeywords.size
        } else 1.0

        val validation = ResponseValidator.validate(modelOutput)

        return ImageEvaluationResult(
            sampleId = sample.id,
            parseSuccess = parseSuccess,
            biasDetected = biasDetected,
            biasMatch = biasMatch,
            keywordCoverage = keywordCoverage,
            hallucinationFlags = validation.flags
        )
    }
}
```

#### 3.3.3 Pass/Fail Thresholds

| Metric | Threshold | Rationale |
|---|---|---|
| JSON Parse Success Rate | ≥ 85% | Multimodal parsing is harder; slightly lower bar |
| Bias Detection Recall | ≥ 60% | Should flag majority of manipulative images |
| Keyword Coverage | ≥ 50% | Summary should mention at least half the expected concepts |
| Hallucination Rate | < 15% | Multimodal models hallucinate more; higher tolerance |

---

### 3.4 Audio Analysis Evaluation

#### 3.4.1 Dataset Structure

Curate 20-30 audio clips (25s each, matching chunk size) with ground truth:

```kotlin
data class AudioEvaluationSample(
    val id: String,
    val audioBytes: ByteArray,           // 16kHz mono 16-bit PCM, 25s
    val referenceTranscript: String,     // Human-verified transcript
    val expectedTone: String,            // e.g., "Aggressive", "Calm", "Rushed"
    val expectedFallacies: List<String>,
    val expectedKeyClaims: List<String>,
    val speakerEmotion: EmotionLabel,
    val audioQuality: AudioQuality       // CLEAR, BACKGROUND_NOISE, OVERLAPPING_SPEECH
)

enum class EmotionLabel { ANGRY, CALM, EXCITED, FEARFUL, SAD, NEUTRAL, RUSHED }
enum class AudioQuality { CLEAR, BACKGROUND_NOISE, OVERLAPPING_SPEECH, LOW_VOLUME }
```

#### 3.4.2 Evaluation Metrics

```kotlin
object AudioEvaluator {
    data class AudioEvaluationResult(
        val sampleId: String,
        val parseSuccess: Boolean,
        val transcriptWER: Double,         // Word Error Rate vs. reference
        val toneMatch: Boolean,            // Did detected tone match expected?
        val fallacyDetection: FallacyDetectionMetrics,
        val claimRecall: Double,           // % of expected claims found
        val hallucinationFlags: List<String>
    )

    fun evaluate(sample: AudioEvaluationSample, chunkOutput: ChunkObservation): AudioEvaluationResult {
        val parseSuccess = chunkOutput.transcript.isNotBlank()

        // 1. Transcript accuracy (Word Error Rate)
        val wer = calculateWER(chunkOutput.transcript, sample.referenceTranscript)

        // 2. Tone detection
        val toneMatch = chunkOutput.dominantTone.lowercase().contains(sample.expectedTone.lowercase()) ||
                       sample.expectedTone.lowercase().contains(chunkOutput.dominantTone.lowercase())

        // 3. Fallacy detection (same as text)
        val expectedFallacySet = sample.expectedFallacies.toSet()
        val detectedFallacySet = chunkOutput.fallacies.map { it.type }.toSet()
        val tp = expectedFallacySet.intersect(detectedFallacySet).size
        val fp = detectedFallacySet.subtract(expectedFallacySet).size
        val fn = expectedFallacySet.subtract(detectedFallacySet).size
        val precision = if (tp + fp > 0) tp.toDouble() / (tp + fp) else 0.0
        val recall = if (tp + fn > 0) tp.toDouble() / (tp + fn) else 0.0
        val f1 = if (precision + recall > 0) 2 * precision * recall / (precision + recall) else 0.0

        // 4. Claim recall
        val claimsFound = chunkOutput.keyClaims.count { claim ->
            sample.expectedKeyClaims.any { expected ->
                claim.lowercase().contains(expected.lowercase()) ||
                expected.lowercase().contains(claim.lowercase())
            }
        }
        val claimRecall = if (sample.expectedKeyClaims.isNotEmpty()) {
            claimsFound.toDouble() / sample.expectedKeyClaims.size
        } else 1.0

        return AudioEvaluationResult(
            sampleId = sample.id,
            parseSuccess = parseSuccess,
            transcriptWER = wer,
            toneMatch = toneMatch,
            fallacyDetection = FallacyDetectionMetrics(tp, fp, fn, precision, recall, f1),
            claimRecall = claimRecall,
            hallucinationFlags = emptyList() // Validate separately
        )
    }

    // Simple Word Error Rate (Levenshtein distance on word lists)
    private fun calculateWER(hypothesis: String, reference: String): Double {
        val hypWords = hypothesis.lowercase().split("\\s+".toRegex()).filter { it.isNotBlank() }
        val refWords = reference.lowercase().split("\\s+".toRegex()).filter { it.isNotBlank() }
        val distance = levenshteinDistance(hypWords, refWords)
        return if (refWords.isNotEmpty()) distance.toDouble() / refWords.size else 0.0
    }
}
```

#### 3.4.3 Pass/Fail Thresholds

| Metric | Threshold | Rationale |
|---|---|---|
| Transcript WER | ≤ 30% | On-device audio transcription is imperfect; 30% WER is usable |
| Tone Detection Accuracy | ≥ 70% | Tone should be detectable in clear audio |
| Tone Detection (noisy audio) | ≥ 40% | Degrades gracefully with audio quality |
| Fallacy Detection F1 | ≥ 0.4 | Audio fallacy detection is harder than text |
| Claim Recall | ≥ 50% | Should capture majority of key claims |
| Parse Success Rate | ≥ 80% | Audio parsing fails more often; acceptable floor |

---

### 3.5 Data Quality Assessment (Hallucination Prevention)

**Goal**: Evaluate the model's ability to assess the quality of input data (image/audio) and refuse to analyze poor-quality inputs that would trigger hallucinations.

#### 3.5.1 Image Quality Assessment

Test whether the model can identify images that are:
- Too blurry to analyze
- Text too small to read
- Irrelevant (e.g., a photo of a landscape when asked to analyze for bias)
- Potentially manipulated (deepfake indicators)

```kotlin
data class ImageQualitySample(
    val id: String,
    val imageBytes: ByteArray,
    val expectedQuality: ImageQualityLevel,
    val expectedRefusal: Boolean  // Should the model refuse or caveat its analysis?
)

enum class ImageQualityLevel {
    HIGH,      // Clear, readable
    MODERATE,  // Some blur, text partially readable
    LOW,       // Very blurry, text unreadable
    IRRELEVANT // Not a media/news image
)
```

**Evaluation**: Check if the model's output includes appropriate caveats:
```kotlin
fun evaluateImageQualityAssessment(
    sample: ImageQualitySample,
    modelOutput: AnalysisResult
): Boolean {
    val hasCaveat = modelOutput.summary.contains(
        listOf("unclear", "blurry", "low quality", "cannot determine", "difficult to assess"),
        ignoreCase = true
    )
    val hasLowCredibility = modelOutput.credibilityScore < 30
    return when {
        sample.expectedRefusal -> hasCaveat || hasLowCredibility
        else -> !hasCaveat // Should not cave unnecessarily on good images
    }
}
```

#### 3.5.2 Audio Quality Assessment

Test whether the model can identify audio that is:
- Too noisy to transcribe reliably
- Overlapping speech (multiple speakers)
- Too quiet or distorted

```kotlin
data class AudioQualitySample(
    val id: String,
    val audioBytes: ByteArray,
    val expectedQuality: AudioQuality,
    val expectedTranscriptConfidence: Float // 0.0-1.0
)
```

**Evaluation**: Check if chunk observations reflect audio quality:
```kotlin
fun evaluateAudioQualityAssessment(
    sample: AudioQualitySample,
    chunkOutput: ChunkObservation
): Boolean {
    val lowQualityTones = listOf("unclear", "noise", "difficult to hear", "background")
    val hasQualityNote = lowQualityTones.any {
        chunkOutput.dominantTone.contains(it, ignoreCase = true)
    }
    val emptyTranscript = chunkOutput.transcript.isBlank()

    return when (sample.expectedQuality) {
        AudioQuality.CLEAR -> !hasQualityNote && !emptyTranscript
        AudioQuality.BACKGROUND_NOISE,
        AudioQuality.OVERLAPPING_SPEECH -> hasQualityNote || emptyTranscript
        AudioQuality.LOW_VOLUME -> emptyTranscript || hasQualityNote
    }
}
```

#### 3.5.3 Pass/Fail Thresholds

| Metric | Threshold | Rationale |
|---|---|---|
| Image Quality Detection Accuracy | ≥ 80% | Model should flag most low-quality images |
| Audio Quality Detection Accuracy | ≥ 70% | Audio quality is harder to self-assess |
| False Refusal Rate | < 10% | Should not refuse to analyze good inputs |

---

### 3.6 Evaluation Runner Implementation

#### 3.6.1 Test Structure

Create a dedicated test module: `composeApp/src/jvmTest/` (or a separate `evaluation/` module):

```
evaluation/
├── build.gradle.kts
├── src/main/kotlin/org/medialiteracy/evaluation/
│   ├── EvaluationRunner.kt          // Main orchestrator
│   ├── datasets/
│   │   ├── text/                    // JSON files with test articles
│   │   ├── images/                  // Image files + metadata JSON
│   │   └── audio/                   // WAV files + metadata JSON
│   ├── metrics/
│   │   ├── TextEvaluator.kt
│   │   ├── ImageEvaluator.kt
│   │   └── AudioEvaluator.kt
│   └── report/
│       ├── ReportGenerator.kt       // HTML/JSON report
│       └── ThresholdChecker.kt      // Pass/fail logic
└── src/test/kotlin/org/medialiteracy/evaluation/
    └── ModelEvaluationTest.kt       // Runs as a test
```

#### 3.6.2 Runner Orchestration

```kotlin
object EvaluationRunner {
    data class EvaluationConfig(
        val modelPath: File,
        val datasetPath: File,
        val backend: Backend = Backend.CPU,
        val maxSamples: Int? = null  // For quick runs
    )

    data class EvaluationReport(
        val modelVersion: String,
        val timestamp: Long,
        val textResults: AggregateResults,
        val imageResults: AggregateResults,
        val audioResults: AggregateResults,
        val overallPass: Boolean,
        val perSampleResults: List<SampleResult>
    )

    fun run(config: EvaluationConfig): EvaluationReport {
        // 1. Initialize model
        // 2. Run text evaluation
        // 3. Run image evaluation
        // 4. Run audio evaluation
        // 5. Aggregate results
        // 6. Check thresholds
        // 7. Generate report
    }
}
```

#### 3.6.3 CI Integration (Future)

```yaml
# .github/workflows/evaluation.yml (future)
name: Model Evaluation
on:
  push:
    paths:
      - 'models/**'
      - 'evaluation/**'
jobs:
  evaluate:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - name: Run model evaluation
        run: ./gradlew :evaluation:test
      - name: Upload report
        uses: actions/upload-artifact@v4
        with:
          name: evaluation-report
          path: evaluation/build/reports/evaluation/
```

---

### 3.7 LLM-as-a-Judge Evaluation

**Goal**: Use capable cloud LLMs (Qwen, Gemini, Claude) as evaluators to rank and compare outputs from candidate on-device models (Gemma 2B, E2B, E4B, future versions) on qualitative dimensions that automated metrics cannot measure.

#### 3.7.1 Why This Complements the Automated Evaluator

The automated evaluator (Sections 3.2-3.6) excels at:
- **Binary checks**: JSON parse success, score ranges, threshold compliance
- **String matching**: Fallacy type overlap, keyword coverage
- **Distance metrics**: Word Error Rate for audio transcripts

But it **cannot** assess:
- *Does the fallacy explanation make logical sense?*
- *Is the summary's characterization of the article accurate and fair?*
- *Is the tone analysis nuanced and correct?*
- *Did the model miss a subtle manipulation technique?*

LLM-as-a-Judge fills these qualitative gaps. It is a **pragmatic, cost-effective proxy** for human expert annotation — not a replacement.

#### 3.7.2 Approach: Blind A/B Ranking with Rubric

```
For each evaluation sample:
  1. Run N candidate models (e.g., Gemma 2B, E2B, E4B) on the same input
  2. Feed all outputs + the original input to a judge LLM
  3. Judge ranks outputs using a structured rubric
  4. Randomize output order (A/B swap) and re-judge
  5. Average rankings across permutations to control position bias
```

**Judge prompt template**:

```
You are evaluating outputs from multiple models analyzing the same media content.

INPUT: [article text / image description / audio transcript reference]

RUBRIC:
1. Summary accuracy: Does the summary accurately characterize the input?
   (Excellent / Good / Partial / Poor)
2. Score reasonableness: Are the 0-100 scores justified by the input quality?
   (Excellent / Good / Partial / Poor)
3. Fallacy detection quality: Are the identified fallacies real and correctly explained?
   (Excellent / Good / Partial / Poor / No fallacies detected)
4. Missed patterns: Are there significant fallacies or manipulations the model missed?
   (List them)
5. Overall ranking: Rank these outputs from best (1) to worst (N) with reasoning.

OUTPUT A: [model A full output]
OUTPUT B: [model B full output]
OUTPUT C: [model C full output]
```

#### 3.7.3 Human Calibration Set (Required)

**LLM judges cannot be trusted without calibration.** Before running the full dataset:

1. Select **10-15 samples** from the evaluation dataset covering diverse categories (news, opinion, propaganda, satire).
2. Have a **human annotator** (even just one developer) rank the same candidate outputs for these samples.
3. Run the judge LLM on the same 10-15 samples.
4. Compare judge rankings to human rankings using **Kendall's tau** or simple pairwise agreement.
5. **Proceed** if agreement ≥ 80% on pairwise comparisons.
6. **Refine rubric or switch judge** if agreement < 80%.

This calibration step ensures the judge's preferences align with human judgment on the specific domain of media literacy analysis.

#### 3.7.4 Multiple Judges with Majority Vote

To reduce individual model bias, use **two or three judges** and take the majority vote on pairwise rankings:

| Judge | Strength | Cost (50 samples) |
|---|---|---|
| Qwen (general reasoning) | Structured rubric following, logical analysis | ~$2-5 |
| Gemini (Google ecosystem) | Familiar with Gemma model family | ~$2-5 |
| Claude (Anthropic) | Strong at nuanced text analysis | ~$3-8 |

Ensemble voting (majority of 3) has been shown to correlate with human judgment at **~0.85 Spearman's ρ**, significantly better than any single judge alone.

**Total estimated cost**: ~$5-15 for 50 samples × 3 judges, or ~$1-3 for a single-judge quick pass.

#### 3.7.5 Known Limitations and Mitigations

| Bias | Description | Mitigation |
|---|---|---|
| **Position bias** | Judges prefer the first output they see | Randomize output order; A/B swap and average |
| **Verbosity bias** | Longer outputs rated higher even if not better | Instruct judge to penalize unnecessary verbosity |
| **Self-similarity bias** | Models favor outputs resembling their own style | Use judges from different model families; ensemble vote |
| **No ground truth anchor** | Judge always picks a "winner" even if all are bad | Human calibration set catches this |
| **Cross-date drift** | Gemini April ≠ Gemini June | Within-session comparisons only; never compare results across different judge invocation dates |

#### 3.7.6 What LLM-as-a-Judge Should NOT Replace

| Still requires human judgment |
|---|
| **Initial dataset curation** — you must know what "good" looks like before a judge can measure it |
| **Edge cases where all models fail** — the judge will still pick a winner, but a human needs to see that *all* are bad |
| **Threshold calibration** — "F1 ≥ 0.5" needs human-grounded data, not judge-relative scores |
| **Pass/fail go/no-go decisions** — the judge provides signal; humans decide whether to ship |

#### 3.7.7 Integration with the Evaluation Pipeline

LLM-as-a-Judge runs **after** the automated evaluator and **alongside** human review:

```
Sample → Candidate Models → Automated Evaluator → Scores + Pass/Fail
                          → LLM-as-a-Judge → Rankings + Qualitative Feedback
                          → Human Review (calibration set + edge cases)
                          → Combined Report
```

The combined report shows:
- Automated metrics (parse rate, score MAE, fallacy F1, WER)
- Judge rankings and qualitative feedback
- Human annotations (for calibration samples)
- Final recommendation (ship / iterate / reject)

#### 3.7.8 Implementation Notes

The judge evaluation can be implemented as a separate script or test that:

1. Loads candidate model outputs from the automated evaluator's results
2. Constructs judge prompts via a cloud LLM API (OpenAI, Google AI, Anthropic)
3. Collects and aggregates judge responses
4. Outputs a ranked comparison table

```kotlin
// Pseudocode for judge orchestration
object LlmJudgeEvaluator {
    data class JudgeConfig(
        val judges: List<JudgeApi>,       // e.g., [QwenApi, GeminiApi, ClaudeApi]
        val rubric: String,               // Structured evaluation rubric
        val swapPositions: Boolean = true // A/B randomization
    )

    fun evaluate(
        input: String,
        candidates: Map<String, String>,  // modelName -> modelOutput
        config: JudgeConfig
    ): JudgeReport {
        // For each judge, randomize order, send prompt, collect ranking
        // Aggregate across judges (majority vote on pairwise comparisons)
        // Return ranked list with judge comments
    }
}
```

This would live alongside the `EvaluationRunner` (Section 3.6) as a complementary evaluation path — not a replacement, but an augmentation for qualitative dimensions.

---

## Implementation Roadmap

### Phase 1: Foundation (Weeks 1-2)

| Task | Effort | Priority |
|---|---|---|
| Create `TelemetryManager` interface and DataStore persistence | 2 days | High |
| Wire `InferenceMetrics` to UI (debug overlay) | 1 day | High |
| Add error classification and counting | 1 day | High |
| Implement `ResponseValidator` for hallucination detection | 1 day | Medium |
| Add parse success/failure tracking in `AnalysisCoordinator` | 1 day | High |

### Phase 2: Crash Reporting & Device Analytics (Weeks 3-4)

| Task | Effort | Priority |
|---|---|---|
| Integrate Firebase Crashlytics (Android) | 2 days | High |
| Implement device tier detection | 1 day | Medium |
| Add opt-in analytics level to onboarding | 1 day | High |
| Implement session telemetry aggregation | 2 days | Medium |
| Create performance log ring buffer in DataStore | 1 day | Low |

### Phase 3: Evaluation Framework (Weeks 5-7)

| Task | Effort | Priority |
|---|---|---|
| Create `evaluation/` module structure | 1 day | High |
| Build text evaluation dataset (50 samples) | 3 days | High |
| Implement `TextEvaluator` with pass/fail thresholds | 2 days | High |
| Build image evaluation dataset (30 samples) | 3 days | Medium |
| Implement `ImageEvaluator` | 2 days | Medium |
| Build audio evaluation dataset (20 samples) | 3 days | Medium |
| Implement `AudioEvaluator` with WER calculation | 2 days | Medium |
| Create report generator (HTML output) | 2 days | Low |

### Phase 3.5: LLM-as-a-Judge Calibration (Week 7-8)

| Task | Effort | Priority |
|---|---|---|
| Select 15 human-validated calibration samples from dataset | 1 day | High |
| Implement judge prompt template and rubric | 1 day | High |
| Run candidates through 2-3 judge LLMs (blinded, randomized order) | 1 day | High |
| Compare judge rankings to human rankings on calibration set | 1 day | High |
| If agreement ≥ 80%, proceed with full dataset judging | — | Gate |
| If agreement < 80%, refine rubric or use different judge | — | Gate |
| Run full dataset through calibrated judge(s) | 1 day | Medium |
| Integrate judge results into combined evaluation report | 2 days | Medium |

### Phase 4: Data Quality & Hallucination Prevention (Weeks 8-9)

| Task | Effort | Priority |
|---|---|---|
| Create image quality assessment dataset | 2 days | Medium |
| Create audio quality assessment dataset | 2 days | Medium |
| Implement quality assessment evaluators | 2 days | Medium |
| Add quality caveats to model prompts | 1 day | Medium |
| Integrate quality checks into evaluation pipeline | 1 day | Low |

### Phase 5: Advanced Features (Weeks 10+)

| Task | Effort | Priority |
|---|---|---|
| Dynamic context window calculation | 2 days | Low |
| User feedback mechanism (thumbs down) | 2 days | Low |
| CI integration for model evaluation | 2 days | Low |
| Cross-stage consistency checks in production | 1 day | Low |
| Model version tracking and comparison dashboard | 3 days | Low |

---

## Appendix A: Metric Definitions

### A.1 Inference Metrics

| Metric | Definition | Unit | Good Threshold |
|---|---|---|---|
| Time to First Token (TTFT) | Time from command submission to first token emitted | Milliseconds | < 2000ms |
| Tokens Per Second (TPS) | Sustained generation throughput | tokens/sec | > 10 |
| Total Tokens Estimated | Heuristic count of tokens consumed (chars/4) | Count | — |
| Parse Success Rate | % of LLM responses that successfully parse to expected schema | Percent | > 90% |
| Error Rate | % of inference commands that fail | Percent | < 5% |
| Token Budget Resets | Number of forced resets due to context overflow | Count/session | < 2 |

### A.2 App Performance Metrics

| Metric | Definition | Unit | Good Threshold |
|---|---|---|---|
| Cold Start Time | Time from app launch to HomeScreen ready | Milliseconds | < 3000ms |
| Model Init Time | Time from model load request to engine ready | Milliseconds | < 5000ms |
| Crash Rate | Crashes per 1000 sessions | Count/1000 | < 10 |
| ANR Rate | App Not Responding events per 1000 sessions | Count/1000 | < 5 |
| Memory Peak | Maximum RSS during analysis | MB | < 1500 (GPU), < 2000 (CPU) |

### A.3 Model Quality Metrics

| Metric | Definition | Unit | Pass Threshold |
|---|---|---|---|
| Text Score MAE | Mean absolute error of scores vs. ground truth | Points (0-100) | < 15 |
| Text Fallacy F1 | Harmonic mean of precision and recall for fallacy detection | Score (0-1) | > 0.5 |
| Image Bias Recall | % of biased images correctly flagged | Percent | > 60% |
| Audio WER | Word Error Rate for audio transcription | Percent | < 30% |
| Audio Tone Accuracy | % of clips with correct tone detected | Percent | > 70% |
| Hallucination Rate | % of outputs triggering validation flags | Percent | < 10% |
| Overall Pass Rate | % of evaluation samples passing all checks | Percent | > 75% |

### A.4 LLM-as-a-Judge Metrics

| Metric | Definition | Unit | Good Threshold |
|---|---|---|---|
| Human-Judge Agreement | % of pairwise rankings where judge agrees with human | Percent | ≥ 80% |
| Judge Ensemble Correlation | Spearman's ρ between ensemble rankings and human rankings | Correlation (-1 to 1) | ≥ 0.85 |
| Position Bias Magnitude | % difference in rankings when output order is swapped | Percent | < 10% |
| Judge Consistency | % of samples where all 3 judges agree on #1 rank | Percent | ≥ 60% |
| Cross-Date Stability | Correlation of judge rankings when re-run on different dates | Correlation (-1 to 1) | N/A — track but don't compare |

---

## Appendix B: Proposed Evaluation Dataset Structure

### B.1 Text Dataset Categories

| Category | Count | Description |
|---|---|---|
| Balanced News | 15 | Objective reporting with multiple perspectives |
| Opinion/Editorial | 10 | Clear bias, some logical fallacies |
| Propaganda | 10 | Heavy manipulation, multiple fallacies |
| Satire | 5 | Intentionally absurd, tests fallacy detection |
| Misinformation | 5 | False claims mixed with logical arguments |
| Short Excerpts | 5 | Under 500 chars, tests minimum viable analysis |

### B.2 Image Dataset Categories

| Category | Count | Description |
|---|---|---|
| News Photo (Legitimate) | 10 | Standard press photo, captioned accurately |
| Cropped/Decontextualized | 8 | Photo cropped to change meaning |
| Emotional Manipulation | 5 | Image chosen to evoke strong emotion |
| Meme/Infographic | 5 | Text-heavy image, tests OCR + analysis |
| Low Quality | 5 | Blurry, low-res, or irrelevant |
| Physical Newspaper | 5 | Photo of printed article, tests OCR |

### B.3 Audio Dataset Categories

| Category | Count | Description |
|---|---|---|
| Clear Speech (Neutral) | 8 | Single speaker, clear audio, calm tone |
| Clear Speech (Emotional) | 5 | Single speaker, angry or excited tone |
| Background Noise | 5 | Speech with music, crowd noise, or static |
| Overlapping Speech | 3 | Two or more speakers talking over each other |
| Low Volume | 3 | Quiet recording, hard to hear |
| Non-English | 3 | Speech in another language (tests model behavior) |

---

## Appendix C: Privacy Considerations

All telemetry respects the app's local-first philosophy:

1. **No content leaves the device**: Article text, images, and audio are never transmitted.
2. **Opt-in only**: Users choose their analytics level during onboarding and can change it in settings.
3. **Anonymized**: No PII collected. Device ID is a one-way hash.
4. **Aggregated**: Session summaries aggregate many inferences; individual analysis data is not sent.
5. **Transparent**: Users can view and export their own telemetry data from settings.
6. **Deletable**: Users can clear all telemetry from their device.

---

## Appendix D: Dependencies to Add

```kotlin
// composeApp/build.gradle.kts — androidMain dependencies

// Crash reporting
implementation("com.google.firebase:firebase-crashlytics:19.2.0")
implementation("com.google.firebase:firebase-analytics")

// For evaluation runner (separate module or jvmTest)
testImplementation("org.jetbrains.kotlinx:kotlinx-html:0.11.0")  // HTML report generation
testImplementation("org.junit.jupiter:junit-jupiter:5.10.0")     // JUnit 5 for evaluation tests
```

## Appendix E: Cloud LLM APIs for Judge Evaluation

These are **not** app dependencies — they are only used during the pre-deployment evaluation phase (Section 3.7). The app itself remains 100% local.

| API | Purpose | Auth | Cost Estimate |
|---|---|---|---|
| **OpenAI API** (GPT-4o) | Judge for qualitative ranking | API key | ~$3-8 per 50-sample pass |
| **Google AI / Vertex AI** (Gemini 2.5 Pro) | Judge, especially for Gemma-family familiarity | API key | ~$2-5 per 50-sample pass |
| **Anthropic API** (Claude Sonnet 4) | Judge for nuanced text analysis | API key | ~$3-8 per 50-sample pass |
| **Qwen API** (Dashscope / OpenRouter) | Judge for structured rubric following | API key | ~$2-5 per 50-sample pass |

**API keys should be stored as environment variables** in the evaluation runner, never committed to the repository:

```bash
export OPENAI_API_KEY="..."
export GOOGLE_AI_API_KEY="..."
export ANTHROPIC_API_KEY="..."
export QWEN_API_KEY="..."
```

The judge evaluation script reads these at runtime and calls the respective APIs. No user data is sent — only evaluation dataset samples and candidate model outputs.
