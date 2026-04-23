# Gemma4ML

**Gemma4ML** (Gemma 4 Media Literacy) is a local-first mobile application designed to empower digital equity and safety. Built with Kotlin Multiplatform and powered by on-device multimodal AI (Gemma 4 E2B/E4B), it analyzes news sources (Text, Audio, Photo) for logical fallacies, biases, and structural integrity—all without an internet connection.

This project is aimed as a submission for the [Gemma 4 Kaggle competition: "Gemma 4 Good"](https://www.kaggle.com/competitions/gemma-4-good-hackathon).

## 🎯 Objective
To provide a privacy-first, secure environment where users can evaluate the news critically. By leveraging *Thinking Mode* and local models, Gemma4ML exposes the internal "logic" behind media narratives.

## 🎯 Why

We face a critical global erosion of trust in media, exacerbated by filter bubbles and the rapid spread of misinformation. Traditional solutions—like centralized "fact-checkers"—are fundamentally flawed for three reasons:
1. **Connectivity Barrier**: They require constant internet access, alienating users in low-connectivity regions where digital equity is most fragile.
2. **Binary Trust Issues**: They deliver absolute judgments ("True" or "False") which users often reject, naturally perceiving centralized fact-checkers as biased entities.
3. **Data Sovereignty Risks**: Cloud-based tools force users to upload their reading material to third-party servers. By executing 100% offline, Gemma4ML guarantees a secure, surveillance-free environment where citizens and journalists in restrictive media landscapes can safely analyze counter-narratives without fear of monitoring.

**Gemma4ML takes a radically different approach: Epistemic Humility.** 
Instead of acting as an infallible arbiter of truth, the app acts as a local logic tutor. It does not judge the *facts*; it evaluates the *structural logic* and *evidence quality* of the arguments presented. By operating locally, privately, and transparently, it teaches users *how* to think critically, rather than telling them *what* to think.

### Why Gemma 4? (The Technical Imperative)
Gemma4ML is structurally reliant on the unique innovations of the Gemma 4 architecture to function; it is not a generic LLM wrapper.
* **Native Multimodal Understanding:** Bypassing clunky OCR or Speech-to-Text pipelines entirely, Gemma 4 natively digests image and audio tensors. A user can snap a photo of a physical newspaper in airplane mode, and the model intrinsically understands the context.
* **Explainable Logic via "Thinking Mode":** We heavily utilize Gemma 4's native Chain-of-Thought protocol (`<|think|>`). By exposing the model's internal step-by-step reasoning process directly to the UI, we achieve absolute transparency in how logical fallacies are detected.
* **Edge-Optimized (E2B):** The parameter-to-performance ratio of the Gemma 4 E2B weights allows deep, structured logical reasoning to happen 100% offline within the strict RAM constraints of a typical mobile device.

## 🚀 Key Features

* **[P0] "Steel-man" Generator:** Generates the strongest possible counter-arguments to the provided news. By actively steel-manning opposing views entirely locally, users break out of their filter bubbles.
* **[P0] Multimodal Intake**: Capture photos of physical newspaper headlines, record short audio clips, or paste digital text directly into the processing engine.
* **[P0] Logical Fallacy Detector**: Identifies 10-12 well-executed, common logic fallacies (e.g., Ad Hominem, Strawman) using Gemma 4's Chain-of-Thought reasoning. Focuses heavily on quality and accuracy over quantity.
* **[P0] Argument Analysis Schema**: Outputs a concrete structural analysis of the article based on predefined schemas (e.g., Tone on a 1-5 scale, Evidence Quality 0-100%).
* **[P0] Multi-Language UI (i18n)**: Native UI localization ensuring accessibility across different regions, starting with full support for English and Spanish.
* **[P1] Interactive Chat Mode**: Have a two-way conversation with the local AI about the article—ask questions, request deeper explanations of complex terms, or explore historical context.
* **[P1] Learning Hub**: Features interactive "Spot the Fallacy" mini-quizzes to gamify and rapidly build media literacy skills.
* **[P2] ESL Prompt Context**: A dedicated interaction prompt to help expats understand international news by translating nuanced idioms and exposing culturally specific rhetorical devices.

## 🛠️ Technology Stack

* **Platform**: Kotlin Multiplatform (Android & iOS capable) utilizing Compose Multiplatform.
* **Model Engine**: MediaPipe LLM Inference API (Baseline implementation using LiteRT/Cactus Compute principles for cross-device compatibility).
* **Models**: Gemma 4 E2B (2.3B) `.task` bundles running locally via INT4 quantization.
* **Architecture**: 100% Offline. Uses static, embedded SQLite databases for definitions (eschewing complex Local RAG to maintain mobile performance).

## 📂 Documentation

* [Project Architecture & Design Decisions](./docs/ARCHITECTURE.md)
* [Project Setup & Requirements](./docs/SETUP_PLAN.md)
* [High-Level Hackathon Roadmap](./docs/PLAN.md)
