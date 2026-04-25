# Gemma 4: Media Literacy Engine Architecture

This document outlines the high-level architecture of the Media Literacy Engine, preserving the "why" behind the technical implementation for future developers and AI models.

## 1. Core Architectural Patterns

### Sticky-Session Actor Pattern
To support complex multi-turn conversations (Analysis ➡️ Chat), the engine utilizes a **Persistent Actor Pattern**:
*   **Context Continuity**: The `LlmEngine` maintains a single, active `Conversation` handle. This ensures that the model "remembers" the initial article analysis during subsequent chat interactions, preventing context fragmentation.
*   **Mutex Gating**: Native handles in LiteRT-LM are inherently single-threaded. We implement a **Mutex-locked lifecycle** in `AndroidLlmEngine` to prevent simultaneous inference commands and safeguard against SIGSEGV (Segmentation Fault) crashes during rapid UI navigation.

### The JSON Data Paradigm
The engine has transitioned from "Informal Text Parsing" to a **Formal JSON extraction model**:
*   **Structured Precision**: We use `kotlinx-serialization` to define a strict schema for analytical results (Logic, Evidence, Credibility, Objectivity).
*   **Prompt Alignment**: The analytical prompts explicitly request JSON responses, which are then extracted using a "Boundary-Finder" logic (isolating the `{...}` block). This eliminates the fragility of regex-based parsing prone to markdown formatting errors.

### State-Driven UI (`InferenceState`)
The app's behavior is modeled as a **Sealed Interface** representing the lifecycle of local AI inference:
*   `Idle`: The engine is ready for input.
*   `Thinking(tokens)`: Represents the real-time "Chain-of-Thought" generation.
*   `Complete(AnalysisResult)`: The final structured output, including Truth Radar scores and logic highlights.

## 2. Multiplatform AI Bridge (`LlmEngine`)
*   **Expect/Actual Pattern**: We define a common `LlmEngine` interface.
*   **Android Implementation**: Utilizes the **LiteRT-LM Android SDK (0.10.0)**. It supports multi-path model discovery (checking internal cache, external storage, and legacy file paths).
*   **iOS Implementation**: Currently a high-fidelity stub for UI demonstration, planned for native Swift c-interop integration.

## 3. Orchestration (`GemmaOrchestrator`)
The `GemmaOrchestrator` (Voyager `ScreenModel`) acts as the state manager and bridge:
*   **Token Telemetry**: Monitors token-per-second flow and character counts to detect model halting.
*   **Raw Output Surface**: In the event of a JSON parsing failure, the Orchestrator surfaces the raw model output to the UI for immediate developer diagnostics.

## 4. Key Infrastructure Decisions

### Model Lifecycle
*   **Context Window**: Locked to **4096 tokens** to support deep-dive analysis of long-form journalism without truncation.
*   **Native Optimization**: Inference is offloaded to the device CPU/GPU through the LiteRT-LM backend, ensuring 100% offline privacy and zero latency from network round-trips.

## 5. Security & Permissions
The following platform-specific permissions are required for the multimodal engine:
*   `CAMERA`: For upcoming Vision-based news OCR.
*   `RECORD_AUDIO`: For real-time speech literacy analysis.
*   `INTERNET`: Exclusively for the initial model weight download.
