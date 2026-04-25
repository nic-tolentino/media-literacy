# Gemma4ML Architecture & Design Decisions

This document outlines the high-level architecture of the Gemma4ML project, preserving the "why" behind the technical implementation for future developers and AI models.

## 1. Core Architectural Patterns

### 7. Development Fallback & Hybrid Engine

To enable rapid UI development and testing across environments without requiring 1.2GB of model weights, the `AndroidLlmEngine` implements a **Hybrid Logic**:

*   **Real Mode**: Triggered automatically if `gemma.task` is present in the app's internal storage (`filesDir`). Uses MediaPipe for on-device inference.
*   **Stub Mode (Developer Fallback)**: Triggered if the model file is missing. It mimics the behavior of the real model by streaming a high-fidelity mock response, including `<|think|>` blocks and structured JSON.

This allows for:
*   Full UI/UX validation without large downloads.
*   Testing of the `InferenceState` lifecycle (Idle -> Thinking -> Complete).
*   Deterministic verification of the "Thinking Mode" layout.

### Clean Architecture & MVI
We follow a Clean Architecture approach to isolate the AI domain logic from the UI.
*   **Domain Layer (`commonMain/kotlin/org/medialiteracy/domain`)**: Contains the business logic, state machines (`GemmaOrchestrator`), and interfaces (`LlmEngine`).
*   **UI Layer (`commonMain/kotlin/org/medialiteracy/ui`)**: Built with Compose Multiplatform, observing states from the domain layer.

### State-Driven UI (`InferenceState`)
The app's behavior is modeled as a **Sealed Interface** representing the lifecycle of local AI inference:
*   `Idle`: The engine is ready.
*   `DownloadingModel(progress)`: Represents the one-time weight retrieval. **Note**: This was converted from a singleton `data object` to a `data class` to support immutable progress updates.
*   `Thinking(tokens)`: Represents the "Chain-of-Thought" generation (`<|think|>` tokens).
*   `Complete(AnalysisResult)`: The final structured output.

## 2. Multiplatform AI Bridge (`LlmEngine`)
To support the "Logic Master" cross-platform while acknowledging the native hardware complexity:
*   **Expect/Actual Pattern**: We define an `LlmEngine` interface in `commonMain`.
*   **Android Target**: Wires to Google's **MediaPipe LLM Inference API** (LiteRT).
*   **iOS Target**: Currently a **No-Op stub** that returns dummy data synchronously. This allows us to demonstrate a beautiful UI on iOS without fighting early Swift/C++ CInterop linking errors during the hackathon phase.

## 3. Navigation & User Journey
We use the **Voyager** library for navigation.

### The Journey Path
1.  **Onboarding**: Checks for local model weights. If missing, triggers a download simulation from public cloud storage (GCS) to avoid Kaggle API key friction.
2.  **TabHost**: The main entry point following onboarding. Contains **Home**, **Learn**, and **Settings** tabs.
3.  **HomeScreen**: The intake center. Provides stubs for OCR (Camera), Audio recording, and Text pasting.
4.  **AnalysisScreen**: Pushed on top of the `TabHost` for an immersive experience. It takes the input text, calls `GemmaOrchestrator`, and observes the `InferenceState`.
5.  **ChatScreen**: A full-screen dialog used to debate the analysis or generate "Steel-man" arguments.

## 4. Key Infrastructure Decisions

### Privacy & Data Sovereignty
The app is **100% Offline-First**. All inference happens on the device CPU/GPU. No user data, news headlines, or captured text ever leaves the device. This provides a surveillance-free environment for users in restrictive media landscapes.

### Dependency Management
*   **Material3**: Pinned to stable `1.3.1` to prevent breaking changes during the hackathon.
*   **KMP Wizard**: Used for the initial Gradle project structure to ensure a stable Kotlin 2.0+ build environment.
*   **ScreenModel**: We use Voyager's `ScreenModel` for state management, providing a lifecycle-aware bridge between Compose and our `GemmaOrchestrator`.

## 5. Security & Permissions
The following platform-specific permissions are strictly required for the multimodal engine:
*   `CAMERA`: For news OCR.
*   `RECORD_AUDIO`: For radio/speech capture.
*   `INTERNET`: Exclusively for the initial ~1.5GB model weight download.
