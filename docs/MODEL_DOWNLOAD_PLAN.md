# Model Download & Management Plan

## Overview

This document details the implementation plan for downloading LLM model files from a web server to the user's device, automatic model selection based on hardware capability, model management in the Settings screen, and where in the UX the download prompt appears.

---

## 1. Decision: When to Prompt for Download

### Strategy: Deferred Download — Prompt at First Analysis Attempt

The Learning Hub and all educational content are fully usable without a model. Forcing a 2.4–3.4 GB download before the user has seen any value is a significant retention risk — many users will abandon at that point. Instead:

- **Onboarding:** Lightweight welcome screen. No download gate. Navigate straight to `TabHost` after a brief introduction.
- **Learning tab:** Fully accessible immediately. Users can explore all media literacy content with zero commitment.
- **First analysis attempt:** When the user first taps any analysis entry point (Paste Text, Scan Newspaper, Record Audio), intercept with a `ModelDownloadSheet` bottom sheet that explains what's needed and why, then drives the download.

This respects the user's time, lets them evaluate the app before the large commitment, and frames the download as unlocking a powerful feature rather than a toll gate.

**Exception — model already downloaded:** If `ModelRepository.isAnyModelDownloaded()` returns `true` on launch (returning user or side-loaded model), proceed directly to engine initialisation during app startup (background, silent). The download sheet is never shown.

---

## 2. Model Variants

| Variant | File name | Size | Target hardware |
|---|---|---|---|
| Gemma 4 E2B-it | `gemma-4-E2B-it.litertlm` | ~2.4 GB | ≤ 6 GB RAM, no discrete GPU |
| Gemma 4 E4B-it | `gemma-4-E4B-it.litertlm` | ~3.4 GB | ≥ 6 GB RAM, GPU/OpenCL available |

Both files are served from the same base URL. The selection logic runs before the download begins.

---

## 3. Auto Model Selection Logic

A new `expect/actual` class `DeviceCapabilityChecker` will determine which variant to recommend. The decision uses three signals evaluated in order of importance: available RAM (primary), GPU/hardware-acceleration availability (secondary), and available disk space (hard constraint).

### Signals and Thresholds

| Signal | E4B threshold | Rationale |
|---|---|---|
| Total RAM | ≥ 6 GB | E4B weights are 3.4 GB; a 6 GB device has ~2.6 GB headroom for OS + app overhead. Below 6 GB, loading E4B risks OOM kills during inference. |
| GPU / OpenCL | Must be available | E4B benefits most from GPU acceleration. On CPU-only paths its inference speed is uncomfortably slow. Without GPU, E4B is not recommended even if RAM is sufficient. |
| Free disk space | ≥ 4.0 GB | Even if hardware can run E4B, there must be enough room to download and verify it. 4.0 GB provides a buffer above the 3.4 GB file size. If under 4 GB free, cap recommendation at E2B (requires checking `availableDiskBytes()`). |

All three conditions must pass for E4B to be recommended. Any single failure falls back to E2B.

### Android (`DeviceCapabilityChecker.android.kt`)

```kotlin
fun recommendedVariant(context: Context): ModelVariant {
    val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager

    // Signal 1: total RAM
    val memInfo = ActivityManager.MemoryInfo()
    activityManager.getMemoryInfo(memInfo)
    val totalRamGb = memInfo.totalMem / (1024.0 * 1024.0 * 1024.0)

    // Signal 2: GPU / OpenCL availability (method already in LlmEngine.android.kt)
    val hasGpu = checkOpenClAvailability()

    // Signal 3: free disk space
    val stat = StatFs(context.filesDir.path)
    val freeDiskGb = stat.availableBytes / (1024.0 * 1024.0 * 1024.0)

    return if (totalRamGb >= 6.0 && hasGpu && freeDiskGb >= 4.0) ModelVariant.E4B else ModelVariant.E2B
}
```

**Representative Android device mapping:**

| Device class | RAM | GPU | Expected recommendation |
|---|---|---|---|
| Pixel 9 Pro, Samsung S24+ | 12 GB | Yes | E4B |
| Pixel 8, Samsung S23 | 8 GB | Yes | E4B |
| Pixel 7a, Samsung A54 | 6 GB | Yes | E4B (borderline — RAM is at threshold) |
| Pixel 6a, Samsung A34 | 6 GB | Limited | E2B (OpenCL may not pass) |
| Pixel 5, budget devices | 4–6 GB | Limited | E2B |

### iOS (`DeviceCapabilityChecker.ios.kt`)

```kotlin
fun recommendedVariant(): ModelVariant {
    val totalRamGb = NSProcessInfo.processInfo.physicalMemory.toDouble() / (1024.0 * 1024.0 * 1024.0)

    // All modern iPhones have a GPU; disk space is checked separately via ModelRepository.availableDiskBytes()
    // RAM is the primary signal on iOS
    return if (totalRamGb >= 6.0) ModelVariant.E4B else ModelVariant.E2B
}
```

**Representative iOS device mapping:**

| Device | RAM | Expected recommendation |
|---|---|---|
| iPhone 15 Pro / Pro Max, iPad Pro M4 | 8 GB | E4B |
| iPhone 15, iPhone 14 Pro | 6 GB | E4B |
| iPhone 14, iPhone 13 Pro | 6 GB | E4B |
| iPhone 13, iPhone 12 | 4 GB | E2B |
| iPhone 11 and older | 4 GB or less | E2B |

### Fallback

If any capability check throws (e.g. `SecurityException`, unavailable API), default to **E2B** (conservative choice). Log the failure so it can be investigated in crash reports.

---

## 4. New Domain Classes

### 4.1 `ModelVariant` (commonMain)

```kotlin
enum class ModelVariant(val fileName: String, val displayName: String, val approximateSizeGb: Double) {
    E2B("gemma-4-E2B-it.litertlm", "Gemma 4 E2B", 2.4),
    E4B("gemma-4-E4B-it.litertlm", "Gemma 4 E4B", 3.4)
}
```

### 4.2 `DownloadState` (commonMain)

```kotlin
sealed class DownloadState {
    object Idle : DownloadState()
    object Offline : DownloadState()   // no network — checked before enqueuing
    data class Downloading(val progressFraction: Float, val bytesDownloaded: Long, val totalBytes: Long) : DownloadState()
    data class Paused(val progressFraction: Float, val bytesDownloaded: Long, val totalBytes: Long) : DownloadState()
    object Verifying : DownloadState()   // SHA-256 check after download
    object Complete : DownloadState()
    data class Failed(val reason: String, val isRetryable: Boolean) : DownloadState()
    object Cancelled : DownloadState()
}
```

### 4.3 `ModelRepository` (commonMain interface, expect/actual implementation)

```kotlin
interface ModelRepository {
    /** Emits the current download state. Persists across recompositions. */
    val downloadState: StateFlow<DownloadState>

    /** The variant currently stored on disk, or null if none. */
    suspend fun installedVariant(): ModelVariant?

    /** Absolute path to the installed model file, or null. */
    suspend fun installedModelPath(): String?

    /** Available disk space in bytes. */
    suspend fun availableDiskBytes(): Long

    /** Start downloading the given variant. */
    fun startDownload(variant: ModelVariant)

    /** Cancel an in-progress download. */
    fun cancelDownload()

    /** Pause an in-progress download (DownloadManager handles automatically on network loss; this is for explicit user pause). */
    fun pauseDownload()

    /** Resume a previously paused download. */
    fun resumeDownload()

    /** Returns true if a network connection is available. Checked before enqueuing any download. */
    fun isOnline(): Boolean

    /** Delete a specific installed variant. Returns true on success. */
    suspend fun deleteModel(variant: ModelVariant): Boolean

    companion object {
        fun getInstance(): ModelRepository = PlatformModelRepository()
    }
}
```

The Android implementation (`ModelRepository.android.kt`) uses `DownloadManager` for background-safe, resumable downloads with system notification support. The iOS implementation uses `URLSession` with a background configuration.

### 4.4 `DeviceCapabilityChecker` (expect/actual)

```kotlin
expect object DeviceCapabilityChecker {
    fun recommendedVariant(): ModelVariant
}
```

---

## 5. Updated `SettingsRepository`

Add model selection preference alongside the existing `ThemeMode`:

```kotlin
// New keys in DataStoreSettingsRepository
val selectedVariantKey = stringPreferencesKey("selected_model_variant")

suspend fun setSelectedVariant(variant: ModelVariant)
fun getSelectedVariant(): Flow<ModelVariant?>   // null = not yet chosen
```

This persists the user's manual override if they switch models from the Settings screen.

---

## 6. Download URL Configuration

Add a `BuildConfig`-style constant (or `local.properties` entry) for the base model server URL:

```kotlin
// In commonMain/domain/ModelConfig.kt
object ModelConfig {
    const val BASE_URL = "https://models.newsdecoder.app/"   // placeholder — set real URL before ship
    fun urlForVariant(variant: ModelVariant) = "$BASE_URL${variant.fileName}"
}
```

**Server requirements:**
- Must support `Content-Length` header (needed for progress calculation).
- Should support `Range` requests (enables resumable downloads via `DownloadManager`).
- SHA-256 checksum files at `<fileName>.sha256` for post-download verification.

---

## 7. Android Download Implementation (`ModelRepository.android.kt`)

```kotlin
class AndroidModelRepository(private val context: Context) : ModelRepository {

    private val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    private val _downloadState = MutableStateFlow<DownloadState>(DownloadState.Idle)
    override val downloadState: StateFlow<DownloadState> = _downloadState.asStateFlow()

    private var activeDownloadId: Long = -1

    override fun startDownload(variant: ModelVariant) {
        val destFile = File(context.filesDir, variant.fileName)   // e.g. "gemma-4-E4B-it.litertlm"
        val request = DownloadManager.Request(Uri.parse(ModelConfig.urlForVariant(variant)))
            .setTitle("Downloading ${variant.displayName}")
            .setDescription("${variant.approximateSizeGb} GB — required for offline AI analysis")
            .setDestinationUri(Uri.fromFile(destFile))
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
            .setAllowedOverMetered(false)    // Wi-Fi only by default (user-configurable)
            .setAllowedOverRoaming(false)

        activeDownloadId = downloadManager.enqueue(request)
        startPollingProgress()
    }

    private fun startPollingProgress() {
        // Poll DownloadManager every 500ms, emit DownloadState.Downloading updates
        // On SUCCESSFUL status: verify SHA-256, then emit Complete or Failed
        // On FAILED status: emit Failed with reason from DownloadManager error column
    }

    override suspend fun installedVariant(): ModelVariant? {
        // Check variant-named files first (production), then generic dev fallbacks
        return ModelVariant.entries.firstOrNull { variant ->
            File(context.filesDir, variant.fileName).let { it.exists() && it.length() > 0 }
        }
    }

    override suspend fun installedModelPath(): String? {
        val variant = installedVariant()
        if (variant != null) return File(context.filesDir, variant.fileName).absolutePath
        // Dev fallbacks (push_model.sh generic names)
        val devFallbacks = listOf("gemma.litertlm", "gemma.task")
        return devFallbacks.map { File(context.filesDir, it) }.firstOrNull { it.exists() && it.length() > 0 }?.absolutePath
    }

    override suspend fun deleteModel(variant: ModelVariant): Boolean {
        val file = File(context.filesDir, variant.fileName)
        return file.exists() && file.delete()
    }
}
```

**Important:** After download completes and is verified, rename the file to `gemma.litertlm` (the canonical search path already used by `LlmEngine.android.kt`) OR update `LlmEngine.android.kt`'s `potentialLocations` list to include the variant-specific filenames.

Preferred approach: **store with the variant filename and extend `potentialLocations`** so the installed variant is self-documenting on disk.

---

## 8. Canonical Model Storage Path

Models are stored using their **variant filenames** in `context.filesDir`:

```
/data/data/org.medialiteracy/files/gemma-4-E2B-it.litertlm
/data/data/org.medialiteracy/files/gemma-4-E4B-it.litertlm
```

Keeping distinct filenames is essential for the no-downtime upgrade strategy in section 12: both variants must coexist on disk during the transition. Using a single `gemma.litertlm` would make that impossible.

**`LlmEngine.android.kt` — trimmed `potentialLocations`:**

```kotlin
val potentialLocations = listOf(
    // Production downloads land here (variant-named)
    File(appContext.filesDir, ModelVariant.E4B.fileName),
    File(appContext.filesDir, ModelVariant.E2B.fileName),
    // Dev fallback: push_model.sh uses the generic name — keep for local dev workflow only
    File(appContext.filesDir, "gemma.litertlm"),
    File(appContext.filesDir, "gemma.task"),
)
```

All external storage and `/data/local/tmp/` paths are removed. If the file isn't in `filesDir` under one of these four names, it isn't there.

**`ModelRepository.android.kt` — download destination:**

```kotlin
val destFile = File(context.filesDir, variant.fileName)   // e.g. "gemma-4-E4B-it.litertlm"
```

The installed variant identity is also persisted in `SettingsRepository` (`selectedVariantKey`), so the Settings screen and `LlmEngine` can both reference it without re-scanning the filesystem.

**Dev workflow note:** `push_model.sh` currently lands files at `gemma.litertlm` / `gemma.task`. The fallback entries above keep this working. Optionally update the script to use the variant filename directly so dev and prod are fully aligned, but it is not required.

**Previous section 7 note (resolved):** An earlier draft of this document suggested both "variant filename (preferred)" in one paragraph and then hardcoded `gemma.litertlm` in the code. The variant filename approach is correct and is what this section specifies. Section 7's code snippet should use `variant.fileName` as shown here, not the hardcoded generic name.

---

## 9. Updated `GemmaOrchestrator`

Add download-related methods alongside the existing analysis methods:

```kotlin
// New properties
val downloadState: StateFlow<DownloadState> = modelRepository.downloadState

// New methods
fun startModelDownload(variant: ModelVariant) {
    val recommended = DeviceCapabilityChecker.recommendedVariant()
    modelRepository.startDownload(variant)
}

fun cancelDownload() = modelRepository.cancelDownload()

fun deleteModel(onComplete: (Boolean) -> Unit) {
    screenModelScope.launch {
        val success = modelRepository.deleteModel()
        onComplete(success)
    }
}
```

Replace the existing stub `fun downloadModel()` with the above.

---

## 10. Updated Onboarding Screen

### Flow

```
App launch
    │
    ▼
isAnyModelDownloaded()?
    ├─ YES → engine.initialize() (background, silent) → TabHost
    └─ NO  → Show lightweight welcome screen
                │
                ▼
            Logo + tagline + 2-3 bullet "what this app does"
            "Get Started" button → TabHost (no download yet)
```

The onboarding screen no longer blocks on a download. It becomes a pure first-run introduction. The `downloadModel()` stub call is removed entirely; engine initialisation is only triggered when a model file is actually present.

### Onboarding Screen State Machine (simplified)

| State | UI |
|---|---|
| Model present, engine initialising | Spinner + "Loading AI engine…" (brief, background) |
| Model present, engine ready | Auto-navigate to `TabHost` |
| Model present, engine error | Error card + Retry button |
| No model | Welcome intro + "Get Started" → `TabHost` |

---

## 10b. `ModelDownloadSheet` — The Download Entry Point

A `ModalBottomSheet` (Material 3) shown whenever the user attempts an analysis action and no model is installed. It is also reachable from the Settings screen "Download" button.

### Trigger Points

Any of these actions check `ModelRepository.isAnyModelDownloaded()` before proceeding:
- Tapping "Paste Text" on `HomeScreen`
- Tapping "Scan Newspaper" on `HomeScreen`
- Tapping "Record Audio" on `HomeScreen`

If no model is present, push `ModelDownloadSheet` instead of the normal destination screen.

### Sheet States

**State 1 — Model Selection (initial)**
```
╔══════════════════════════════════════════╗
║  Unlock AI Analysis                      ║
║  ─────────────────────────────────────── ║
║  News Decoder analyses articles using    ║
║  a private AI model stored on your       ║
║  device. Nothing ever leaves your phone. ║
║                                          ║
║  Recommended for your device:            ║
║  ┌──────────────────────────────────┐    ║
║  │ ● Gemma 4 E4B  · 3.4 GB         │    ║
║  │   Higher accuracy                │    ║
║  └──────────────────────────────────┘    ║
║  ┌──────────────────────────────────┐    ║
║  │ ○ Gemma 4 E2B  · 2.4 GB         │    ║
║  │   Faster, uses less storage      │    ║
║  └──────────────────────────────────┘    ║
║                                          ║
║  Free space on device: 18.3 GB           ║
║                                          ║
║  [ Download 3.4 GB · Wi-Fi recommended ] ║
║  [ Not now ]                             ║
╚══════════════════════════════════════════╝
```

- `DeviceCapabilityChecker.recommendedVariant()` pre-selects the appropriate option.
- Both options are always shown; the user can override.
- "Free space on device" is shown so the user can make an informed choice.
- If free space is insufficient for the selected variant, the Download button is disabled with a warning: "Not enough storage — free up X GB first."
- If `ModelRepository.isOnline()` returns `false`, the Download button is replaced with the Offline state (below) immediately — no attempt to fetch file sizes or enqueue a download.

**State 1b — Offline (no network)**
```
  [Cloud-off icon]  No internet connection
  An internet connection is required to download the AI engine.
  Connect to Wi-Fi and try again.
  [ OK ]
```
The sheet is dismissible in this state. If the user opens it while offline, they can close it and come back when connected. The sheet does not auto-retry; the user must re-trigger from a HomeScreen entry point or Settings.

**State 2 — Downloading**
```
  Downloading Gemma 4 E4B…
  [██████████░░░░░░░░░░]  1.8 GB / 3.4 GB · ~4 min left
  [ Pause ]   [ Cancel ]
```
- Progress fraction, bytes downloaded / total, estimated time remaining (rolling average of download speed).
- The sheet cannot be dismissed by dragging while a download is active (set `sheetState` accordingly).
- **Pause** calls `modelRepository.pauseDownload()`; Android `DownloadManager` will also pause automatically on network loss. On OEM devices with aggressive background process killing (Xiaomi MIUI, Huawei EMUI, some Samsung One UI), a gentle in-sheet reminder may appear after a failed attempt: "Keep the app open for a reliable download."

**State 2b — Paused**
```
  Download paused
  [██████████░░░░░░░░░░]  1.8 GB / 3.4 GB
  [ Resume ]   [ Cancel ]
```
- `DownloadState.Paused` carries current progress so the bar doesn't reset.
- `DownloadManager` reports `STATUS_PAUSED` which maps to this state. Resume calls `resumeDownload()` which re-enqueues from where it left off (Range request).

**State 3 — Verifying**
```
  Verifying file integrity…
  [████████████████████]
```

**State 4 — Complete**
```
  ✓  AI engine ready
```
- Brief success state (1–2 seconds), then auto-dismiss and proceed to the original destination screen the user was trying to reach.

**State 5 — Failed**
```
  Download failed
  [Specific reason — e.g. "Server unreachable" / "Not enough storage" / "File integrity check failed"]
  [ Retry ]   [ Cancel ]
```
- Error message maps from `DownloadState.Failed.reason` to a human-readable string.
- If the failure reason suggests a transient issue (network, server), show Retry.
- If the failure is "integrity check failed", show: "The file may be corrupted. Retrying will start a fresh download." and clear the partial file before re-enqueuing.

---

## 11. Settings Screen — Model Management Section

Replace the static "Gemma 4-E2B-it / Version 1.2 (Active)" row with a live, functional section:

### Sub-section: "AI Model"

**When a model is installed — no upgrade available (device too limited, or already on E4B):**
```
┌─────────────────────────────────────────────────┐
│  [Storage icon]  Gemma 4 E2B-it                 │
│                  2.4 GB · Downloaded 12 May 2026 │
└─────────────────────────────────────────────────┘

  [Delete Model]   ← destructive, requires confirmation dialog
```

**When a model is installed — upgrade available (E2B installed, device can handle E4B):**
```
┌─────────────────────────────────────────────────┐
│  [Storage icon]  Gemma 4 E2B-it  (Active)       │
│                  2.4 GB · Downloaded 12 May 2026 │
└─────────────────────────────────────────────────┘

  ┌─────────────────────────────────────────────────┐
  │  ✨ Upgrade available                           │
  │  Gemma 4 E4B · 3.4 GB                          │
  │  More accurate analysis — your device supports  │
  │  it. Needs 3.4 GB free temporarily (+1 GB net). │
  │                                    [Upgrade ▶]  │
  └─────────────────────────────────────────────────┘

  [Delete Model]
```

**When no model is installed:**
```
┌─────────────────────────────────────────────────┐
│  [Warning icon]  No model installed             │
│                  Analysis features are disabled  │
└─────────────────────────────────────────────────┘

  [ Download AI Model ]  ← opens ModelDownloadSheet
```

**During a download or upgrade (inline progress in Settings):**
```
  Downloading Gemma 4 E4B…
  [████████░░░░░░░░░░░░]  1.2 GB / 3.4 GB
  [ Cancel ]
```

### Delete Confirmation Dialog

Before deleting, show a Material 3 `AlertDialog`:

```
Title:   "Delete model file?"
Body:    "This removes the 2.4 GB Gemma 4 E2B model from your device. 
          Analysis features will be unavailable until you re-download a model."
Actions: [Cancel]   [Delete]  ← Delete styled in error colour
```

After deletion: `engine.close()` must be called to release the native handle before the file is deleted. The engine state transitions to `Error` and `ServiceRegistry` references become unusable until a model is re-downloaded and `engine.initialize()` is called. No forced navigation change — the user stays in Settings, but all analysis entry points will now intercept with `ModelDownloadSheet`.

---

## 12. Model Upgrade Flow

The upgrade path is the mechanism for moving from E2B → E4B on a capable device. It is surfaced in two places:

### 12a. Settings Screen Upgrade Card (section 11 above)

Shown when:
- E2B is installed, and
- `DeviceCapabilityChecker.recommendedVariant() == ModelVariant.E4B`

Tapping `[Upgrade ▶]` opens the same `ModelDownloadSheet` pre-selected on E4B, but with upgrade-specific copy. The sheet must first check available disk space against the full temporary requirement (see storage check below).

**Storage check before showing upgrade UI:**

The no-downtime overlap means both files exist on disk simultaneously during the transition. The device therefore needs **≥ 3.4 GB + 500 MB buffer = ~4 GB free** to upgrade safely, not just the 1 GB net difference between models.

```kotlin
val freeGb = availableDiskBytes() / (1024.0 * 1024.0 * 1024.0)
val canOverlap = freeGb >= 4.0
```

**If `canOverlap == true` (≥ 4 GB free) — no-downtime path:**

```
╔══════════════════════════════════════════╗
║  Upgrade to Gemma 4 E4B                  ║
║  ─────────────────────────────────────── ║
║  Your device can run the larger model,   ║
║  which produces more accurate analysis,  ║
║  better fallacy detection, and richer    ║
║  summaries.                              ║
║                                          ║
║  Your current model stays active while   ║
║  the upgrade downloads. No downtime.     ║
║                                          ║
║  Temporary space needed: 3.4 GB          ║
║  Net change after upgrade: +1.0 GB       ║
║  Free space on device: 18.3 GB           ║
║                                          ║
║  [ Download & Upgrade · 3.4 GB ]         ║
║  [ Keep current model ]                  ║
╚══════════════════════════════════════════╝
```

**If `canOverlap == false` but `freeGb >= 3.9` — tight space path:**

```
╔══════════════════════════════════════════╗
║  Upgrade to Gemma 4 E4B                  ║
║  ─────────────────────────────────────── ║
║  Not enough space to download in the     ║
║  background while keeping your current   ║
║  model active.                           ║
║                                          ║
║  Option: delete E2B first, then          ║
║  download E4B. Analysis will be          ║
║  unavailable during the download.        ║
║                                          ║
║  Free space on device: 2.5 GB            ║
║                                          ║
║  [ Delete current & upgrade ]            ║
║  [ Keep current model ]                  ║
╚══════════════════════════════════════════╝
```

**If `freeGb < 3.4 GB + 200 MB buffer` — not enough even for sequential download:**

The upgrade card in Settings is hidden entirely. No upgrade option is presented until the user frees storage.

**No-downtime overlap strategy:** Download E4B alongside E2B. Only delete E2B after E4B is verified and `engine.initialize()` succeeds with the new file. The engine continues serving E2B throughout the download. On the "delete current & upgrade" path: `engine.close()` → `deleteModel(E2B)` → `startDownload(E4B)` — engine is unavailable until E4B completes.

### 12b. Post-Analysis Upgrade Nudge (optional, future)

After a completed analysis, if E2B is active and the device supports E4B, a dismissible banner can appear at the bottom of `AnalysisScreen`:

```
  ✨ Your device supports a smarter model — upgrade in Settings for deeper analysis.  [→]
```

This is a soft nudge, not a blocker. It should be shown at most once per session and respect a "don't show again" preference stored in `SettingsRepository`.

### 12c. Downgrade (E4B → E2B)

A "Switch to smaller model" option in Settings allows users to free up ~1 GB of storage. Uses the same confirmation + download + overlap flow, but in reverse. Only shown if E4B is currently installed.

---

## 13. OEM Background Download Reliability

Android `DownloadManager` is the right choice for V1, but some heavily customised OEM firmware (Xiaomi MIUI, Huawei EMUI, older Samsung One UI) aggressively pauses or kills background downloads for apps not on a whitelist, especially for large files.

**Mitigations for V1:**
- Map `DownloadManager.STATUS_PAUSED` with reason `PAUSED_WAITING_FOR_NETWORK` to `DownloadState.Paused` with an in-sheet message: "Your device paused this download. Keep the app open or check battery saver settings."
- Map `STATUS_FAILED` with reason `ERROR_CANNOT_RESUME` to `DownloadState.Failed(isRetryable = true)` so the user can start fresh.
- The system notification (from `VISIBILITY_VISIBLE`) keeps the download visible even if the app is backgrounded, which helps on most OEMs.

**Future V2 path (if production data shows high failure rates on specific OEMs):**
Replace `DownloadManager` with a `Foreground Service` backed by `OkHttp` / `Ktor`. A foreground service with a persistent notification is much harder for OEM battery managers to kill. The `DownloadState` sealed class and `ModelRepository` interface are designed to be implementation-agnostic, so the switch only requires a new `ModelRepository.android.kt` implementation — no UI changes.

---

## 14. Model Versioning and Update Detection

The initial plan covers hardware-tier upgrades (E2B → E4B), but not model version updates (e.g. a future E2B v1.3 with improved fine-tuning). Plan for this now so the infrastructure isn't a surprise later.

### Approach: Server Metadata File

Add a lightweight `metadata.json` endpoint at `ModelConfig.BASE_URL + "metadata.json"`:

```json
{
  "models": [
    { "variant": "E2B", "version": "1.2", "fileName": "gemma-4-E2B-it-v1.2.litertlm", "sizeBytes": 2576980377, "sha256": "abc123..." },
    { "variant": "E4B", "version": "1.0", "fileName": "gemma-4-E4B-it-v1.0.litertlm", "sizeBytes": 3650722201, "sha256": "def456..." }
  ]
}
```

`ModelVariant.fileName` then becomes the value fetched from this manifest at runtime rather than a hardcoded enum constant.

**Update detection:**

1. On app launch (after model is installed), fetch `metadata.json` in the background on `Dispatchers.IO`.
2. Compare the fetched `version` against the version stored in `SettingsRepository` at download time.
3. If a newer version exists, show an "Update available" card in Settings (same visual pattern as the upgrade card), not a push notification or intrusive banner.
4. The update flow is identical to the upgrade flow: download new version, verify, swap engine reference, delete old file.

**V1 simplification:** For the initial release, `ModelVariant.fileName` can remain hardcoded. The metadata endpoint can be added in V1.1 alongside the first model update. The key thing is that `SettingsRepository` already stores `selectedVariantKey` — add a `installedVariantVersion` key alongside it so version comparison is possible when the endpoint arrives.

---

## 15. Wi-Fi Gate

Downloading 2.4–3.4 GB over mobile data without user consent is a poor experience and can incur carrier charges. Default behaviour:

- `DownloadManager.Request.setAllowedOverMetered(false)` — blocks cellular by default.
- If the device is on cellular when the user taps Download, show a snackbar/dialog: "Wi-Fi recommended. Download anyway?" with a "Use Mobile Data" option that re-enqueues with `setAllowedOverMetered(true)`.
- Persist the user's "allow mobile data for model downloads" choice in `SettingsRepository`.

---

## 16. Post-Download Integrity Verification

After `DownloadManager` reports `STATUS_SUCCESSFUL`:

1. Compute `SHA-256` of the downloaded file using `MessageDigest` on `Dispatchers.IO`.
2. Fetch the checksum from `<MODEL_URL>.sha256` (a small text file, no large download).
3. Compare. On mismatch: delete the partial/corrupt file, emit `DownloadState.Failed("File integrity check failed. Retrying will start a fresh download.", isRetryable = true)`.

This protects against partial downloads, CDN corruption, and storage errors.

---

## 17. File Structure Summary

### New files to create

| Path | Purpose |
|---|---|
| `commonMain/domain/ModelVariant.kt` | Enum for E2B/E4B with filename and size |
| `commonMain/domain/ModelConfig.kt` | Server base URL + metadata endpoint constant |
| `commonMain/domain/DownloadState.kt` | Sealed class for download state (incl. Offline, Paused) |
| `commonMain/domain/ModelRepository.kt` | Common interface (incl. pause/resume/isOnline) |
| `androidMain/domain/ModelRepository.android.kt` | Android `DownloadManager` implementation |
| `iosMain/domain/ModelRepository.ios.kt` | iOS `URLSession` implementation |
| `commonMain/domain/DeviceCapabilityChecker.kt` | `expect` declaration |
| `androidMain/domain/DeviceCapabilityChecker.android.kt` | RAM + OpenCL + disk check |
| `iosMain/domain/DeviceCapabilityChecker.ios.kt` | `NSProcessInfo` RAM check |
| `commonMain/ui/screens/ModelDownloadSheet.kt` | Bottom sheet (first-download, upgrade, offline, pause/resume) |

### Files to modify

| Path | Change |
|---|---|
| `commonMain/domain/SettingsRepository.kt` | Add `selectedVariant`, `installedVariantVersion`, `upgradeNudgeDismissed` preferences |
| `commonMain/domain/GemmaOrchestrator.kt` | Replace stub `downloadModel()`, expose download/pause/resume API |
| `androidMain/domain/LlmEngine.android.kt` | Trim `potentialLocations` to variant files + dev fallbacks |
| `commonMain/ui/screens/OnboardingScreen.kt` | Simplify to lightweight welcome; remove download gate |
| `commonMain/ui/screens/SecondaryScreens.kt` (SettingsScreen) | Live model management + upgrade card + version info |
| `commonMain/ui/screens/HomeScreen.kt` | Intercept analysis entry points when no model is present |
| `commonMain/ui/screens/AnalysisScreen.kt` | Optional: post-analysis upgrade nudge banner |

---

## 18. Implementation Order

1. **`ModelVariant`, `DownloadState`, `ModelConfig`** — pure data, no dependencies.
2. **`DeviceCapabilityChecker`** (expect/actual) — RAM + GPU + disk check, device mapping tables.
3. **`ModelRepository`** interface + Android implementation — download, pause/resume, isOnline, deleteModel(variant).
4. **`SettingsRepository` update** — add variant, version, and nudge-dismissed preferences.
5. **`LlmEngine.android.kt` update** — trim path search to variant files + dev fallbacks.
6. **`GemmaOrchestrator` update** — expose full download/upgrade API to UI.
7. **`OnboardingScreen` update** — strip to lightweight welcome, remove download gate.
8. **`ModelDownloadSheet`** — all states: offline, selection, downloading, paused, verifying, complete, failed.
9. **`HomeScreen` update** — intercept analysis taps, show sheet when no model present.
10. **`SettingsScreen` update** — live model card, upgrade card (with correct storage calculation), version display.
11. **`AnalysisScreen` nudge** — optional soft upgrade banner (defer to a later iteration).
12. **iOS `ModelRepository`** — can be stubbed initially if Android ships first.
13. **`metadata.json` endpoint + version checking** — V1.1 work; `SettingsRepository` key reserved now.
14. **End-to-end testing** on emulator using `scripts/serve_model.sh` as the local server.
