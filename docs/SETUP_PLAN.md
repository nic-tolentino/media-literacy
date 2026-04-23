# Gemma4ML Project Setup Plan

This document defines the step-by-step setup required to initialize the Gemma4ML Kotlin Multiplatform (KMP) project, shifting the design system to the "Clarity and Trust" Light theme and establishing "Home Intake" as the primary landing experience.

## 1. Environment Prerequisites
- **Android Studio** (Koala or newer) installed with the Kotlin Multiplatform plugin.
- **Xcode** installed for iOS compilation.
- **JDK 17** configured as the default Java environment.
- **KDoctor** installed and run successfully to verify the multiplatform environment.

## 2. Project Scaffolding
Generate the base project (e.g., via the Kotlin Multiplatform web wizard) with the following parameters:
- **Project Name:** Gemma4ML
- **Target Platforms:** Android, iOS
- **UI Framework:** Compose Multiplatform (sharing UI across both platforms)
- **Shared Module Name:** `shared`

## 3. Theming & Design System ("Clarity and Trust")
We will construct a bespoke Compose `MaterialTheme` inside the shared module to reflect the "Clarity and Trust" aesthetics from your Stitch design files.

- **Color Palette (Light Mode):** Shift away from the nocturnal look to a clean, authoritative light palette. 
  - Primary Backgrounds: Crisp whites and subtle cool grays (`#F9FAFB`).
  - Accents: "Clarity Blue" (`#47b4eb` / `#3db8f5` mapped from the Stitch metadata) for primary actions and trusted verifications.
- **Typography:** 
  - `Inter` for highly readable body text and humanist narrative.
  - `Space Grotesk` for data points, AI outputs, and monospaced technical elements to maintain the "engineered" persona without the dark backdrop.
- **Extraction:** We will parse the `Home Intake (Light)` screen from Stitch to extract exact padding, border radius (`rounded-xl`), and color hexes into Compose definitions (`Color.kt`, `Type.kt`).

## 4. Platform-Specific Configurations
To support multimodal intakes (Photos and Audio), we must configure OS-level permissions before implementation.

### Android (`composeApp/src/androidMain`)
- Add the following to `AndroidManifest.xml`:
  - `<uses-permission android:name="android.permission.CAMERA" />`
  - `<uses-permission android:name="android.permission.RECORD_AUDIO" />`
  - `<uses-permission android:name="android.permission.INTERNET" />` (For initial model weight download)

### iOS (`iosApp/iosApp`)
- Add privacy usage descriptions to `Info.plist`:
  - `NSCameraUsageDescription` (e.g., "Required to scan newspaper headlines.")
  - `NSMicrophoneUsageDescription` (e.g., "Required to capture audio snippets for analysis.")

## 5. MediaPipe LLM Engine Integration
Because MediaPipe's LLM Inference API is heavily Android-focused out of the box, we will use an `expect/actual` pattern in Kotlin.
- **Common:** Create an `LlmEngine` interface handling prompts and token streams.
- **Android:** Add `implementation("com.google.mediapipe:tasks-genai:...")` to `androidMain` and implement the engine using native MediaPipe.
- **iOS:** Temporarily stub the implementation or integrate a Swift-based local LLM equivalent, ensuring the UI remains platform-agnostic while the heavy lifting happens locally.

## 6. UI Routing & Screen Architecture
Based on the Stitch screens, we will establish a Compose navigation graph (e.g., using Voyager or native Compose Navigation) with the following structure:

1. `HomeIntakeScreen.kt`: **(Default Landing Route)** The main dashboard featuring camera, audio, and text input fields. 
2. `OnboardingScreen.kt`: The flow for downloading the ~1.5GB Gemma weights on the patient's first launch.
3. `AnalysisDashboardScreen.kt`: The result screen visualizing logic fallacies, structure, and bias scores.
4. `LogicMasterChatScreen.kt`: The chat interface for direct Q&A with the LLM about the analyzed news.
5. `LearningHubScreen.kt`: The interactive zone for media literacy exercises.

## 7. Model Asset Strategy
- Create an architecture to download the MediaPipe-compatible `gemma-4-E2B-it-int4.task` weights and persist them cleanly into the platform's local app storage directory to ensure 100% offline capability thereafter. (Avoid GGUF formats, as they are incompatible with the MediaPipe SDK).

## 8. Data Architecture & Layers
To maintain a clean separation of concerns within the KMP project, we will adhere to a robust layered architecture focusing on Unidirectional Data Flow (MVI/MVVM).

### Data Layers
- **UI Layer (`presentation`):** Compose Multiplatform screens and ViewModels. It strictly observes states and emits intents/events.
- **Domain Layer (`domain`):** Platform-agnostic business logic. Contains Use Cases (e.g., `AnalyzeArticleUseCase`, `ExtractFallaciesUseCase`) and core models like `AnalysisResult` and `FallacyType`.
- **Data Layer (`data`):** Manages local persistence, media caching, and AI model orchestration. This includes repositories like `NewsAnalysisRepository` and the interface `LlmEngine`.

### Main Core Classes
- **`GemmaOrchestrator`:** The central manager that tracks model load states, manages memory allocation warnings, and delegates inference execution to the `LlmEngine` (LiteRT/MediaPipe).
- **`MediaCaptureManager`:** A platform-specific `expect/actual` component responsible for interfacing with native camera/mic components. It optimizes and formats the captured image frames or raw audio byte arrays to be fed directly into Gemma 4's multimodal context window, bypassing legacy OCR/STT pipelines entirely.
- **`NewsAnalysisRepository`:** The Single Source of Truth caching historical offline analyses locally (e.g., via Room KMP or SQLDelight).
- **`InferenceState`:** A sealed interface defining UI states during the 10-20 second local inference window:
  - `Loading`: Preparing model context and warming up hardware accelerators (Cactus/LiteRT).
  - `Thinking(val tokens: String)`: Emitting generated tokens line-by-line (Gemma's Chain-of-Thought) to the UI for absolute transparency.
  - `Complete(val result: AnalysisResult)`: Finished analysis parsed into the domain model.

### Complete Data Flow (End-to-End Analysis)
1. **Intake:** The user captures a headline image or records an audio excerpt via `HomeIntakeScreen`. The UI delegates this intent to the `MediaCaptureManager`.
2. **Multimodal Injection:** The raw optimized media (Image or Audio byte array) is forwarded directly to the `AnalyzeArticleUseCase`. Because Gemma 4 natively handles these modalities, we skip traditional pre-processing.
3. **Prompting:** The logic layer constructs the strict Gemma 4 prompt template. It feeds in the media block and instructs the model to *first* generate a raw text transcript of the media (for display/caching), and *then* utilize `<|think|>` instructions for structured logic deduction directly from that media.
4. **Local Inference:** The request is sent to `NewsAnalysisRepository`, invoking the `GemmaOrchestrator` to stream inference via `LlmEngine` in a background coroutine safely off the main thread.
5. **State Emission:** As the quantized Gemma model infers natively on the device, it streams tokens back through `InferenceState.Thinking` directly into the Compose UI, providing the transparent "Thinking Mode" user experience.
6. **Persistence & Updating:** Once sequence generation yields the stop token, the output is parsed for specific fallacies and biases, mapped to `AnalysisResult`, persisted to the local SQL database by the repository, and pushed to the UI automatically catching `InferenceState.Complete`.
