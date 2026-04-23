# Model Integration Plan: Gemma 4 & MediaPipe

This plan outlines the transition from a "Stubbed" skeleton to a "Live" AI-powered application. We will focus on the Android target first, ensuring the Gemma 4-E2B model can be downloaded, loaded into memory, and chatted with via a text interface.

## 1. Dependencies & Permissions
*   **MediaPipe GenAI**: Add `com.google.mediapipe:tasks-genai:0.10.27` to the Android target.
*   **Permissions**: We already have `INTERNET` and `CAMERA/AUDIO`, but we may need to ensure we have sufficient internal storage access for the ~1.5GB weight file.

## 2. Model Download Utility
Since the weights are large, we need a robust download mechanism:
*   **URL**: A public GCS/Firebase link for the `gemma-4-E2B-it-int4.task` file.
*   **Implementation**: Create a `FileDownloader` in `commonMain` or `androidMain` that provides a `Flow<DownloadState>` to update the `OnboardingScreen` progress bar accurately.
*   **Storage**: Save the file to `context.filesDir` (internal storage) to ensure it stays private and persistent.

## 3. Real `LlmEngine` (Android)
The `LlmEngine.android.kt` will be updated from a No-Op stub to a real MediaPipe implementation:
*   **Initialization**: Use `LlmInference.createFromModelPath()` pointing to the downloaded `.task` file.
*   **State Handling**: Ensure the engine is only initialized once and handled carefully to avoid memory leaks.
*   **Multimodal Analysis**: Implement the logic to feed captured text/images into the `LlmInference` engine.

## 4. Basic Text Chat & Prompting
We will implement the "Logic Master" persona:
*   **System Prompt**: Define the persona ("You are an expert in media literacy, spotting logical fallacies, and promoting epistemic humility...").
*   **Chat History**: Implement a simple message list in the `ChatScreen` that maintains context during a session.
*   **Streaming Output**: (Optional P1) Update the UI to show tokens as they arrive for a better UX.

## 5. UI Polishing (Visual Design)
To improve the "terrible" look:
*   **Glassmorphism**: Apply subtle transparency and blurs to cards.
*   **Typography**: Ensure we are using the "Inter" or "Outfit" font family as planned.
*   **Dynamic Colors**: Ensure the "Clarity Blue" is consistent across the intake buttons.

## 6. Success Metrics
*   The `OnboardingScreen` progress bar reflects a real network download.
*   The `AnalysisScreen` "Thinking" state represents a real local model inference.
*   The `ChatScreen` returns a sensible response from Gemma 4 based on the input news text.
