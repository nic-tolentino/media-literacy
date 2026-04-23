# MVP Skeleton Implementation Plan

This document outlines the exact technical implementation plan for scaffolding the initial Gemma4ML Kotlin Multiplatform app skeleton. The goal is to establish a working cross-platform foundation with the necessary navigation and architectural boundaries before injecting the complex LiteRT/Gemma weights.

## 1. Project Initialization & Structure
**Approach:** Base configuration utilizes the JetBrains Kotlin Multiplatform Wizard output. I will scaffold the specific architectural patterns (Clean Architecture / MVI) inside the `composeApp/src/commonMain` directory.

## 2. Shared Module Architecture Stubs
To ensure the skeleton reflects the final software boundaries defined in our setup plan, I will aggressively stub out the core domain and orchestrator class structures before building the UI. This prevents retrofitting headaches later.
- `LlmEngine`: `expect/actual` interface for bridging abstract tensor data. 
  - **Android Target**: Will eventually wire directly to the native MediaPipe SDK.
  - **iOS Target**: Explicitly defined as a pure No-Op stub (simulating processing delays but returning dummy data) to ensure Xcode compilation remains unbroken and the UI can still be demonstrated on iOS without fighting early experimental C++ bindings.
- `InferenceState`: Sealed interface (`Idle`, `Loading`, `Thinking(val tokens: String)`, `Complete(val result: AnalysisResult)`) to drive UI reactivity.
- `AnalysisResult`: Core domain model reflecting our predefined output schema (e.g., Tone, Fallacies, Evidence Quality).
- `GemmaOrchestrator`: A no-op implementation ViewModel/StateManager.

## 3. Navigation Architecture (Voyager)
We will use **Voyager** for multiplatform navigation, implementing a strict hierarchy matching the intended user journey.

### Root Stack
The top-level router checks for the model weight file:
- `OnboardingScreen`: Active on first launch. Handles the UI flow (and dummy progress bar) for downloading the `gemma-4-E2B`. The UX explicitly models downloading from a public Cloud Storage bucket (e.g., GCS) to avoid UX-breaking Kaggle API key requirements.
- `TabHost`: Loaded if onboarding is complete.

### The Tab Navigator (`TabHost`)
1. **Home Tab** (The Intake landing page)
2. **Learn Tab** (Interactive educational quizzes)
3. **Settings Tab** (Model management, language preferences)

## 4. Primary User Journey: Intake to Analysis
The skeleton will physically model the core user flow:

*   **`HomeScreen`**: The default screen inside the Home Tab. It will feature disabled UI stubs for Camera, Audio Record, and Text Paste, defining the app's visual identity. Submitting an intake pushes the next screen.
    *   ⬇ *Navigates to...*
*   **`AnalysisScreen`**: Simulates the `InferenceState.Thinking` dummy flow (a 5-second mock sequence generation) before presenting UI placeholder cards populated with the `AnalysisResult` stub.
    *   ⬇ *Navigates to...*
*   **`ChatScreen`**: Launched from the Analysis Screen. Provides the immersive, full-screen chat interface to debate the output or execute the "Steel-man" features.

## 5. Secondary Screens
*   **`LearningScreen`**: Resides in the Learn Tab natively.
*   **`SettingsScreen`**: Resides in the Settings Tab to allow the user to delete/re-download the inference bundle safely.
