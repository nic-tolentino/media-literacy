# Architecture Review: Media Literacy Engine

_Initial review: 2026-05-12 · Cross-validated and extended with Qwen feedback: 2026-05-12_

Two bugs from the initial review were found to be incorrect after cross-validation: SHA-256 model integrity verification is already implemented, and runtime permission checks are already in place in `MediaPicker.android.kt`. These are struck through in the summary table and corrected inline. Five additional bugs and one critical testability issue were added from the cross-validation pass.

---

## 1. Executive Summary

The app is a well-structured Kotlin Multiplatform (KMP) project with a thoughtful domain layer, a clean multiplatform abstraction strategy, and solid feature coverage. The core actor/mutex pattern for native LLM access is exactly right, and the progressive analysis pipeline produces a good user experience.

The main concerns for long-term maintainability are:

- **Feature compartmentalization** is weak — a monolithic `AnalysisCoordinator` owns too much logic, making it hard to extend or test in isolation.
- **Dependency injection** relies on manual singletons (`ServiceRegistry`), which makes testing difficult and creates hidden coupling.
- **Testing coverage is minimal** (~5%), and completely absent for UI, audio processing, and model management.
- Several **medium-to-high severity bugs** exist around audio deduplication, token budgeting, error masking, and pipeline stage isolation.

---

## 2. Current Architecture Overview

```
UI Layer (Compose Multiplatform + Voyager)
  └── GemmaOrchestrator (ScreenModel)
        ├── AnalysisCoordinator          ← orchestrates analysis pipeline
        │     ├── InferenceService       ← actor queue wrapping LlmEngine
        │     ├── AnalysisStages         ← pure prompt construction + parsing
        │     └── AnalysisRepository     ← DataStore persistence
        ├── ModelRepository              ← download + install model weights
        └── SettingsRepository           ← user preferences

Platform Layer
  ├── AndroidLlmEngine                   ← LiteRT-LM SDK, Mutex-guarded
  ├── AudioDecoder                       ← MediaCodec transcoding
  └── DeviceCapabilityChecker            ← RAM/GPU detection
```

The layering is correct at a high level: domain logic in `commonMain`, platform implementations behind `expect/actual`. The key weakness is that several domain components have grown into large classes that mix too many responsibilities.

---

## 3. Compartmentalization Issues

These are the highest-leverage areas to address for reducing the cognitive and contextual load needed to add or maintain features.

### 3.1 `AnalysisCoordinator` is a God Class

`AnalysisCoordinator.kt` currently handles:
- Dispatching analysis to `InferenceService`
- Multi-stage pipeline orchestration (Summary → Claims → Metrics → Fallacies → Tone)
- Transcript overlap deduplication for audio stitching
- Token budget enforcement
- Auto-saving results to history
- Multimodal vs. text routing

**Recommendation:** Split into focused collaborators:

```
AnalysisPipeline             ← stage sequencing only; emits intermediate states
TranscriptMerger             ← audio chunk stitching and deduplication
AnalysisResultPersister      ← auto-save + history retrieval
MultimodalRouter             ← dispatches to text vs. audio vs. image paths
```

Each class becomes independently testable and can be understood without reading the others. `AnalysisCoordinator` becomes a thin orchestrator that wires them together.

### 3.2 `GemmaOrchestrator` Mixes UI State and Business Logic — and is Untestable by Design

`GemmaOrchestrator` (the Voyager `ScreenModel`) owns both UI state management and domain-facing coordination — e.g., it directly decides when to call `prime()`, manages chat history, and routes audio vs. text input.

More critically, its property initializers hard-code `ServiceRegistry.analysisCoordinator` and `ServiceRegistry.inferenceService`. There is no constructor injection, no factory, and no seam for substituting mocks. This makes it impossible to write any UI-level test without a fully initialized native engine — zero UI testing is achievable without addressing this first.

**Recommendation:** Keep `GemmaOrchestrator` as a pure UI state holder. Accept all dependencies via constructor parameters (even without Koin, this alone unblocks testing). Move routing logic into the domain layer so it can be tested without a ScreenModel. A separate `AnalysisSession` domain object could own the concept of "an active analysis and any follow-up chat" without any Compose/Voyager dependency.

### 3.3 `InferenceService` Couples Command Format to Feature Logic

The `InferenceCommand` sealed class lists feature-level commands (`Analyze`, `AnalyzeMultimodal`, `Chat`, `Prime`). This means every new analysis feature requires changes to `InferenceService` itself.

**Recommendation:** Reduce `InferenceService` to a lower-level interface — it should only know about prompts, not about whether this is a "claims stage" or a "chat turn". Move feature dispatch up to `AnalysisPipeline`:

```kotlin
// Current (feature-coupled):
InferenceCommand.Analyze(id, prompt)
InferenceCommand.AnalyzeMultimodal(type, data, prompt)

// Better (prompt-level):
InferenceCommand.SingleTurn(prompt)
InferenceCommand.MultimodalTurn(mediaType, data, prompt)
InferenceCommand.PersistentTurn(prompt)   // continues active session
```

### 3.4 No Feature-Level Module Boundaries

All domain code lives in a single Gradle module (`composeApp`). As the app grows, this will cause:
- Longer build times (everything rebuilds on any change)
- No enforceable API boundaries between features
- Harder to onboard contributors to a single feature

**Recommendation (medium-term):** Extract Gradle modules for each feature domain:

```
:core:llm-engine          ← LlmEngine interface + platform impls
:core:analysis            ← AnalysisStages, AnalysisPipeline
:core:persistence         ← AnalysisRepository, SettingsRepository
:feature:home             ← HomeScreen + HomeScreenModel
:feature:analysis         ← AnalysisScreen + GemmaOrchestrator
:feature:chat             ← ChatScreen
:feature:learning         ← LearningScreen, TacticsLibrary
:feature:model-download   ← ModelRepository + ModelDownloadSheet
```

This isn't urgent, but even splitting `:core:llm-engine` from the rest immediately reduces the "minimum context to understand X" for new contributors.

### 3.5 `ServiceRegistry` Global Singleton

`ServiceRegistry` is a manually managed global object that holds `InferenceService` and `AnalysisCoordinator`. It throws at runtime if accessed before initialization, provides no null-safety during the init window, and makes unit testing very difficult (you can't substitute implementations).

**Recommendation:** Replace with [Koin](https://insert-koin.io/) (the standard KMP-compatible DI framework). It integrates cleanly with Voyager ScreenModels:

```kotlin
// Definition (in shared commonMain)
val appModule = module {
    single<LlmEngine> { AndroidLlmEngine.getInstance() }
    single { InferenceService(get()) }
    single { AnalysisCoordinator(get(), get(), applicationScope) }
    factory { GemmaOrchestrator(get(), get(), get()) }
}

// In ScreenModel
class GemmaOrchestrator(
    private val coordinator: AnalysisCoordinator,
    private val modelRepo: ModelRepository,
    private val settings: SettingsRepository
) : ScreenModel
```

This makes every dependency explicit and swappable in tests.

---

## 4. Testing Strategy

Current coverage is approximately 5%, limited to pure domain logic unit tests. UI, audio processing, model management, and integration paths have no tests.

### 4.1 What Exists (and is Good)

- `AudioChunkerTest` — 4 tests for chunk splitting and overlap logic
- `AnalysisStagesTest` — 3 tests for prompt construction and JSON/Markdown parsing
- `AnalysisCoordinatorTest` — 3 tests for length limits and pipeline state
- `MockLlmEngine` — deterministic token emission for unit tests
- `MockRepository` — in-memory analysis history

These are the right things to test at the unit level. The pattern is good; it just needs to be applied more broadly.

### 4.2 Unit Test Gaps

| Missing Tests | Why it Matters |
|---|---|
| `TranscriptMerger` edge cases (zero overlap, full duplicate, transcription error) | Fragile audio stitching is a high-severity bug path |
| `ModelRepository` state transitions | Silent stuck states on download failure |
| `DeviceCapabilityChecker` variant selection | Wrong model selected for device → crashes or poor performance |
| JSON parsing failure paths | Currently masked; hard to debug in production |
| Token budget enforcement | Budget accumulates across pipeline stages, can exhaust mid-analysis |
| `handleMultimodal` token budget bypass | Multimodal runs with an exhausted budget, producing garbage silently |

**Note:** The existing `InferenceServiceTest` may not compile against the current `AndroidInferenceService` constructor. The test appears to pass `backgroundScope` as the `modelRepository` parameter and `"mockContext"` as `scope`, which does not match the current constructor signature `(engine, modelRepository, scope, androidContext, dispatcher)`. This should be verified and fixed — if the test is silently failing to compile, the coverage it provides is zero.

### 4.3 Integration Tests

Add a `commonTest` integration test that exercises the full analysis pipeline against `MockLlmEngine`:

```kotlin
@Test
fun `full text analysis pipeline produces complete result`() = runTest {
    val mockEngine = MockLlmEngine()
    mockEngine.enqueue(summaryJson)
    mockEngine.enqueue(claimsJson)
    mockEngine.enqueue(metricsJson)
    mockEngine.enqueue(fallaciesJson)

    val service = InferenceService(mockEngine)
    val coordinator = AnalysisCoordinator(service, MockRepository(), this)
    
    val states = mutableListOf<InferenceState>()
    coordinator.state.take(5).toList(states)
    
    coordinator.startAnalysis("some article text")
    
    assertIs<InferenceState.Complete>(states.last())
    val result = (states.last() as InferenceState.Complete).result
    assertEquals(4, result.keyClaims.size)
}
```

This kind of test is far more valuable than testing stages in isolation because it catches interaction bugs between pipeline stages.

### 4.4 Audio Processing Tests

The `AudioChunker` and `AudioDecoder` need parametrized tests with real audio fixtures:

```
src/commonTest/resources/audio/
  silence_5s.wav
  speech_30s.wav
  speech_5min.wav          ← should hit chunk limit
  low_sample_rate_8khz.wav ← resampling path
  stereo_input.wav         ← downmix path
```

Test matrix:
- Chunking counts and timestamps are correct for each fixture
- Overlap removal stitches correctly when chunks share words
- Silence/noise produces an appropriate error state
- Wrong sample rate triggers resampling (not silence or crash)

### 4.5 Compose UI Tests

Add Compose UI tests for the happy-path screens using `createComposeRule()`:

```kotlin
@Test
fun `analysis screen shows truth radar when analysis completes`() {
    val orchestrator = FakeGemmaOrchestrator(
        initialState = InferenceState.Complete(fakeResult)
    )
    composeTestRule.setContent {
        AnalysisScreen(orchestrator)
    }
    composeTestRule.onNodeWithTag("truth_radar").assertIsDisplayed()
    composeTestRule.onNodeWithText("Objectivity").assertIsDisplayed()
}
```

Key screens to cover: `HomeScreen`, `AnalysisScreen`, `OnboardingScreen`, `ChatScreen`.

### 4.6 Screenshot Tests (Paparazzi)

Consider adding [Paparazzi](https://github.com/cashapp/paparazzi) for screenshot regression testing of the Truth Radar visualization and other custom-drawn UI. This catches regressions in visual logic without a device.

---

## 5. Bugs and Potential Issues

### BUG-1 (High): Audio Transcript Deduplication is Fragile

`AnalysisCoordinator.removeTranscriptOverlap()` uses a 5-word window search with a 40-word lookback. If the transcription produces a minor error (different word form, filler word) in the overlap region, the stitch point is missed and the overlap text is duplicated or dropped.

**Fix:** Replace the word-window search with a fuzzy matching algorithm. A sliding-window normalized edit distance (Levenshtein) over the last 60 words of the previous chunk against the first 60 words of the current chunk will find the optimal stitch point even with transcription noise.

### ~~BUG-2~~: Model Download SHA-256 Verification — Already Implemented

_This bug was incorrectly reported in the initial review._ `verifyIntegrity()` does use `HashUtils.calculateSha256()` and fetches a `.sha256` manifest from the server for comparison. The TODO comment cited in the initial review was stale. No fix required here — **BUG-3 still applies** independently.

### BUG-3 (High): `pendingVariant` Race Condition in `AndroidModelRepository`

`pendingVariant` is an instance variable set in `startDownload()` and read in `verifyIntegrity()`. If the process is killed and restarted mid-download (Android will restart downloads), `pendingVariant` will be `null`, the `?: return` silently exits, and the user sees a permanently stuck "Verifying..." state.

**Fix:** Pass `ModelVariant` as a parameter to `verifyIntegrity()` instead of reading the instance variable. Persist the in-flight variant to DataStore so it survives process death.

### BUG-4 (Medium): JSON Parse Errors are Silently Swallowed

Throughout `AnalysisCoordinator`, caught parse exceptions fall back to `initialResult` with no logging:

```kotlin
} catch (e: Exception) {
    initialResult  // User gets stale data; developer has no signal
}
```

This means a broken prompt or model regression will surface as "no claims extracted" rather than an error the team can act on.

**Fix:** Log the exception with the raw model output (truncated to 500 chars):

```kotlin
} catch (e: Exception) {
    Logger.e("AnalysisCoordinator", "Claims parse failed: ${e.message} | raw: ${raw.takeLast(500)}")
    initialResult
}
```

### BUG-5 (Medium): Token Budget Heuristic Underestimates Non-ASCII Text

`updateTokenEstimate()` uses `token.length / 4` — four characters per token. This holds approximately for English ASCII but underestimates by 2–3× for Chinese, Japanese, Korean, or heavily-punctuated text, and also misses that many English words tokenize as 2 subword tokens rather than one.

**Fix:** Use a conservative heuristic of `ceil(token.length / 3)` until the LiteRT-LM SDK exposes a tokenizer count API. Also lower the soft budget limit from 3,500 to 3,200 to add a safety margin.

### BUG-6 (Medium): Cancellation Does Not Propagate to Pipeline Stages

`GemmaOrchestrator.cancelActiveInference()` sends `InferenceCommand.CancelCurrent` to the service but does not cancel the `analysisJob` coroutine in `AnalysisCoordinator`. The next pipeline stage can launch before the cancel signal is processed, causing stale state emissions after the user has already navigated away.

**Fix:**

```kotlin
// In AnalysisCoordinator
fun cancelAnalysis() {
    analysisJob?.cancel()
}

// In GemmaOrchestrator
fun cancelActiveInference() {
    screenModelScope.launch {
        coordinator.cancelAnalysis()
        inferenceService.execute(InferenceCommand.CancelCurrent).collect()
    }
}
```

### BUG-7 (Medium): GPU Initialization Has No Retry Logic for Transient Failures

A multi-step fallback chain exists (GPU → CPU → emergency CPU), which is correct. However, there are no retries with delay at any step. A transient failure during GPU init — caused by thermal throttling or momentary driver state — immediately drops the session to CPU-only mode with no recovery path and no user notification.

**Fix:** Add 2–3 retry attempts with a short exponential delay before each fallback step. Log each attempt and reason distinctly. If GPU init ultimately fails after retries, notify the user ("Running in reduced-performance mode").

### ~~BUG-8~~: Runtime Permission Checks — Already Implemented

_This bug was incorrectly reported in the initial review._ `MediaPicker.android.kt` uses `rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission())` and `ContextCompat.checkSelfPermission` before accessing camera and audio. No fix required.

### BUG-8 (Low): Chat Message Prompt Injection

User chat input is concatenated directly into the prompt with no system-level framing:

```kotlin
val chatPrompt = "$message\nExpert Guidance (be concise):"
```

A user could inject instructions to steer the model off-topic. This is low severity for a local offline app but is worth addressing to improve reliability.

**Fix:** Wrap the user turn with a system instruction that anchors the model's role before the user message is inserted.

### BUG-9 (Medium): `estimatedTokensUsed` Accumulates Across All Pipeline Stages

`estimatedTokensUsed` is tracked at the service level and only resets on `handleReset()` or a new `Analyze` command. Pipeline stages 2–4 (Claims → Metrics → Fallacies) are dispatched as `Chat` commands through the same service instance, so the budget accumulates across them. A moderately long article can exhaust the 3,500-token budget mid-pipeline, causing the fallacies stage to silently run on a spent context window.

**Fix:** Either reset `estimatedTokensUsed` at the start of each full analysis run, or track the budget at the `AnalysisCoordinator` level and pass a remaining-budget hint into each stage command.

### BUG-10 (Medium): `handleMultimodal` Bypasses the Token Budget Check

`handleChat` gates execution on `if (estimatedTokensUsed > tokenBudgetLimit)`, but `handleMultimodal` does not include this check. An image or audio analysis that fires after the budget is already exhausted will produce garbage output with no warning.

**Fix:** Apply the same budget guard at the start of `handleMultimodal` before dispatching to the engine.

### BUG-11 (Medium): Duplicated Pipeline Logic Across Text, Image, and Audio Paths

Stages 2–4 (Claims → Metrics → Fallacies) are implemented nearly identically in three separate methods in `AnalysisCoordinator` — one each for text, image, and audio. This is approximately 80 lines duplicated three times with minor parameter variations. Any fix to a stage prompt or parsing logic must be applied in three places.

**Fix:** Extract a shared `runSharedStages(initialResult: AnalysisResult): Flow<InferenceState>` method that takes the stage-1 result and runs stages 2–4 identically. Each input-type method calls into this shared pipeline after completing its first stage.

### BUG-12 (Low): `Logger.e()` Throwable Parameter is Dead Code

```kotlin
fun e(tag: String, message: String, throwable: Throwable? = null) {
    println("ERROR: [$tag] $message")
    throwable?.printStackTrace()
}
```

Every call site passes `e.message` (a `String`) rather than the exception object itself, so the `throwable` parameter is always `null`. Stack traces are never captured.

**Fix:** At all call sites, change `Logger.e(tag, e.message ?: "...")` to `Logger.e(tag, "...", e)` so the full stack trace is printed.

### BUG-13 (Medium): `BASE_URL` is a Placeholder in Production Code

`ModelConfig.BASE_URL = "https://models.newsdecoder.app/"` is marked with a comment noting it is a placeholder that must be updated before release. This is easy to forget and will silently fail all model downloads in production builds.

**Fix:** Replace with a compile-time assertion or a build-config value injected via `BuildConfig`. If no real URL exists yet, replace with an obvious `TODO` exception that will surface immediately at test time:

```kotlin
val BASE_URL: String get() = BuildConfig.MODEL_BASE_URL.also {
    require(it.isNotBlank()) { "MODEL_BASE_URL must be set before release" }
}
```

---

## 6. Additional Suggestions

### 6.1 Model Warm-Up is Not Surfaced in the UI

The first inference after engine initialization takes significantly longer than subsequent ones due to model loading and JIT warm-up. This delay is currently indistinguishable from slow analysis. Users may think the app has frozen.

**Suggestion:** Emit a distinct `InferenceState.WarmingUp` state during the first-inference window so the UI can display a more informative loading message ("Warming up the AI engine for the first time…").

### 6.2 No Observability for Production Debugging

The `Logger` object provides local logcat output but there is no structured telemetry for production issues. When a user reports "analysis failed," there is no signal to diagnose whether it was a JSON parse error, a token budget overflow, a GPU fallback, or a corrupted model.

**Suggestion:** Add a lightweight, privacy-preserving local telemetry layer that writes structured events to an append-only file:

```
{ "event": "analysis_complete", "stage": "claims", "parse_success": false, "duration_ms": 1230 }
{ "event": "engine_init", "mode": "gpu_fallback", "reason": "OpenCLNotFound" }
```

This stays on-device (no PII sent anywhere) but gives developers a log to request from users when debugging issues.

### 6.3 `AnalysisResult` Partial-Loading Flags are Fragile

`AnalysisResult` carries four boolean loading flags (`isSummaryLoading`, `isMetricsLoading`, etc.) that are toggled at different pipeline stages. These flags are spread across `copy()` calls throughout `AnalysisCoordinator`. Adding a new pipeline stage requires updating multiple sites.

**Suggestion:** Model pipeline progress as an enum or a set:

```kotlin
enum class AnalysisStage { Summary, Claims, Metrics, Fallacies, Tone }

data class AnalysisResult(
    val completedStages: Set<AnalysisStage> = emptySet(),
    ...
)
```

The UI then derives loading states from `AnalysisStage.Summary !in result.completedStages`, and adding a new stage only requires a new enum value.

### 6.4 Audio Chunk Limits are Hardcoded Magic Numbers

```kotlin
if (chunks.size > 12) { ... }  // ≈5 minutes
val maxAudioChunks = 12
val chunkDurationSeconds = 25
val overlapSeconds = 5
```

These constants are scattered across `AnalysisCoordinator` and `AudioChunker` with no single source of truth. If the token budget changes or the model's context window grows, all these numbers need to be updated manually.

**Suggestion:** Consolidate into a single `AudioProcessingConfig` data class in `commonMain`, initialized once from the device capability tier.

### 6.5 `SampleArticles` is Defined in UI Layer

`SampleArticles.kt` lives in `ui/screens/` but it's not a UI concern — it's test/demo data. This means it can't be used in unit tests without importing the UI module.

**Suggestion:** Move to `domain/` or a dedicated `testfixtures/` source set in `commonTest`.

### 6.6 No Changelog or Migration Strategy for DataStore Schema

`AnalysisRepository` serializes `SavedAnalysis` objects to DataStore JSON. There is no versioning or migration logic. If a field is added, renamed, or removed in a future release, all existing saved analyses will fail to deserialize, silently deleting user history.

**Suggestion:** Add a `schemaVersion: Int` field to the DataStore payload. On deserialization failure, log the error and migrate rather than silently dropping records.

### 6.7 `ModelDownloadSheet.kt` is Untracked

`ModelDownloadSheet.kt` appears in `git status` as an untracked file (`??`). If this is intended to be part of the app, it should be committed. If it's a work-in-progress that shouldn't be included yet, it should be added to `.gitignore`.

---

## 7. Summary Table

| Area | Severity | Item |
|---|---|---|
| Audio deduplication | High | Fragile word-window stitch; fails on transcription noise (BUG-1) |
| ~~Model integrity~~ | ~~High~~ | ~~SHA-256 check~~ — **already implemented, initial report incorrect** |
| `pendingVariant` race | High | Silent stuck state if process killed mid-download (BUG-3) |
| Token budget accumulation | Medium | Budget not reset between pipeline stages; fallacies stage can run spent (BUG-9) |
| Multimodal budget bypass | Medium | `handleMultimodal` skips budget check entirely (BUG-10) |
| Parse error masking | Medium | Exceptions swallowed with no logging (BUG-4) |
| Token heuristic | Medium | Underestimates non-ASCII; use `/3` and lower limit to 3,200 (BUG-5) |
| Cancellation propagation | Medium | Pipeline stages run after user cancels (BUG-6) |
| GPU init fallback | Medium | No retry; transient failures → permanent CPU-only session (BUG-7) |
| Duplicated pipeline logic | Medium | Stages 2–4 copy-pasted across text/image/audio paths (BUG-11) |
| `BASE_URL` placeholder | Medium | Hardcoded placeholder URL will silently fail downloads in production (BUG-13) |
| `GemmaOrchestrator` testability | Architecture | Hard-codes `ServiceRegistry`; zero UI tests possible without full engine (§3.2) |
| `AnalysisCoordinator` size | Architecture | God class; too many responsibilities to test cleanly (§3.1) |
| `ServiceRegistry` | Architecture | Manual singleton DI blocks testability (§3.5) |
| Test coverage | Architecture | ~5%; no UI, audio, or integration tests; `InferenceServiceTest` may not compile (§4.2) |
| DataStore versioning | Architecture | Schema changes will silently corrupt history (§6.6) |
| `Logger.e()` dead parameter | Low | Throwable parameter never used; stack traces never captured (BUG-12) |
| ~~Runtime permissions~~ | ~~Low~~ | ~~No checks before camera/audio~~ — **already implemented, initial report incorrect** |
| Prompt injection | Low | User chat input not sandboxed (BUG-8) |
| `SampleArticles` location | Low | Test data in UI layer (§6.5) |
| `ModelDownloadSheet.kt` | Low | Untracked file in working tree (§6.7) |
