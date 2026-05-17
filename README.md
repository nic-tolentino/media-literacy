# News Decoder 🛰️🧠
### Local-First Media Literacy & Bias Analysis

> [!IMPORTANT]
> **Documentation Hygiene**: This project maintains high-fidelity alignment between documentation and implementation. Architectural decisions regarding edge-inference and agentic routing are documented in [ARCHITECTURE.md](./docs/ARCHITECTURE.md).

**News Decoder** is a production-grade, local-first mobile application designed to empower digital equity and safety. Built with **Kotlin Multiplatform** and powered by **Gemma 4**, it enables users to critically deconstruct media—identifying logical fallacies, biases, and structural integrity—all 100% offline.

This project is a submission for the [Gemma 4 Kaggle competition: "Gemma 4 Good"](https://www.kaggle.com/competitions/gemma-4-good-hackathon).

---

## 🏆 Hackathon Track Alignment

| Track | Implementation Detail |
| :--- | :--- |
| **Cactus Prize** | **Intelligent Model Routing**: Uses a custom `AnalysisCoordinator` to route tasks between high-fidelity native OCR (ML Kit) and multimodal vision models based on text density. |
| **LiteRT Prize** | Built using the **Google AI Edge LiteRT-LM** SDK for optimized Gemma 4 inference on mobile hardware. |
| **Future of Education** | Implements a **Socratic Bridge**—an agentic loop that prompts users with critical questions *before* revealing final scores, encouraging active learning. |

---

## 🏗️ Technical Moats

* **Cactus-Style Intelligent Routing**: To maximize battery life and minimize latency, the engine performs a "Perception Turn" first. If the source is text-heavy, it routes through a high-speed native pipeline. If it's visual/symbolic, it falls back to a deep multimodal path.
* **Agentic Synthesis Pipeline**: Unlike simple Q&A, the engine uses a 4-stage progressive synthesis:
    1. **Perception**: Summary & Highlights.
    2. **Extraction**: Claim & Evidence verification.
    3. **Metrication**: Formal scoring of objectivity and logic.
    4. **Socratic Bridge**: Interactive educational dialogue.
* **Native Memory Safety**: Optimized for Android with a **Mutex-locked Actor pattern** that prevents resource contention during intensive on-device inference.

## 🚀 Technology Stack

* **Platform**: Kotlin Multiplatform (Android/Compose)
* **Model Engine**: LiteRT-LM (Gemma 4 2B/4B)
* **Logic**: MVI Architecture with a centralized `InferenceService` actor loop.
* **Data Layer**: Schema-based JSON extraction using `kotlinx-serialization`.

---

## 📂 Documentation

* [Project Architecture & Design Decisions](./docs/ARCHITECTURE.md)
* [Hackathon Strategy & Roadmap](./docs/hackathon_strategy.md)
* [Sample Articles & Test Cases](./docs/SAMPLE_ARTICLES.md)
* [Kaggle Submission Writeup](./docs/KAGGLE_SUBMISSION.md)

---

## ⚖️ Quick Start for Judges

To verify the technical depth and track alignment of **News Decoder**, please follow these steps:

1. **Test the Engine**: Use the pre-baked test cases in [SAMPLE_ARTICLES.md](./docs/SAMPLE_ARTICLES.md). These cover three distinct scenarios (Health, Civic, and Science) designed to trigger the full range of the logic engine.
2. **Verify the "Cactus" Routing**: Inspect `AnalysisCoordinator.kt`. You will see the logic that performs a "Perception Turn" via ML Kit before deciding whether to route to a high-speed native OCR path or a deep multimodal vision fallback.
3. **Explore the Socratic Bridge**: Run an analysis and observe the interactive questions generated after the "Metrics Stage." This implementation fulfills the **Future of Education** requirement for active user engagement.
4. **Audit the Weights**: The app uses the **Gemma 4 E2B-IT** model via LiteRT-LM, ensuring 100% offline, privacy-first inference as detailed in our [Technical Writeup](./docs/KAGGLE_SUBMISSION.md).
