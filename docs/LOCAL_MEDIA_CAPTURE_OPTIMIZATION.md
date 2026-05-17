# Local Media Capture & Optimization Plan (Lean MVP)

**Status:** Partially implemented (Android thumbnails/AAC only)  
**Updated:** 2026-05-12

---

## Part 1: MVP / Hackathon Scope (Ultra-Lean)

The goal is to provide high-quality media analysis for a demo using the existing stable paths (Gallery & Photo Capture) while fixing silent quality blockers.

### Phase 1.1: High-Fidelity Photo Capture & OCR (Android)
**Goal:** Deliver readable text-heavy captures (newspapers, articles) for analysis.
*   **Full-Res Capture:** Replace `TakePicturePreview()` with `TakePicture()` using a `FileProvider` URI.
*   **ML Kit OCR (New):** Integrate `com.google.mlkit:text-recognition`. 
    *   **Routing Logic:** Image → ML Kit (~50ms) → Text Found? → **Text Analysis Path** (StartAnalysis)
    *   **Fallback:** If no text detected → **Multimodal Path** (StartImageAnalysis with letterbox resizing).
*   **Letterbox Resizing:** Scale images to fit 448×448 while preserving aspect ratio (add black padding) for the multimodal fallback path.

### Phase 1.2: Audio Quality & Pipeline Fixes (Android)
**Goal:** Fix "silent" distortion and potential pipeline blockers.
*   **Gallery-First:** Drop in-app audio recording from MVP; focus on user-supplied files via `launchGallery()`. 
*   **Stereo-to-mono (BUG):** Fix `AudioDecoder.processAudioData()` to average L/R channels.
*   **Linear Resampling (BUG):** Improve `resampleIfNecessary()` with basic linear interpolation to reduce high-frequency aliasing.
*   **WAV Pipeline Fix:** Verify and ensure `AudioDecoder.wrapInWav()` is called for each audio chunk. LiteRT-LM requires the WAV header.

### Phase 1.3: Demo Safety & Build Stability
*   **Error Visibility (BUG-4):** Add explicit logging for JSON parse failures in `AnalysisCoordinator`.
*   **iOS Compatibility:** Maintain `iosMain` stubs for all `expect` declarations so the project continues to compile.

---

## Part 2: Future Enhancements (Post-Hackathon)

*   **In-App Recording:** Implementation of `AudioRecord` for high-fidelity direct PCM capture.
*   **Architecture Refactor:** Unification of `runSharedStages()` and migration to `Flow<AudioChunk>` for streaming.
*   **iOS Implementation:** Full `AVFoundation` capture and decoding pipeline.

---

## Files to Modify (MVP)

| File | Context |
|------|---------|
| `composeApp/src/androidMain/.../MediaPicker.android.kt` | Full-res photo capture + FileProvider |
| `composeApp/src/androidMain/.../AudioDecoder.android.kt` | Fix stereo downmix and resampling aliasing |
| `composeApp/src/commonMain/.../AnalysisCoordinator.kt` | **OCR-First routing logic** + **wrapInWav()** + **BUG-4 logging** |
| `composeApp/src/commonMain/.../ImageTranscriber.kt` | **NEW** — Interface for OCR abstraction |
| `composeApp/src/androidMain/.../MlKitImageTranscriber.kt` | **NEW** — Android implementation using ML Kit |
| `AndroidManifest.xml` | Add FileProvider + ML Kit dependency |
| `composeApp/src/iosMain/...` | Maintain stubs for build compatibility |

## References
- Android Camera: [TakePicture](https://developer.android.com/reference/androidx/activity/result/contract/ActivityResultContracts.TakePicture)
- ML Kit: [Text Recognition](https://developers.google.com/ml-kit/vision/text-recognition/android)
- LiteRT-LM: Input requires 16kHz Mono 16-bit PCM (wrapped in WAV header).
