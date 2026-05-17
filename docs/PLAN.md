# News Decoder: Project Implementation Status 🛰️🧠

## 1. Strategic Alignment (Kaggle Hackathon Tracks)
* **Safety & Trust**: Pioneered explainable AI that unmasks logical fallacies and manipulation.
* **Digital Equity**: Provides high-end news analysis for users in low-connectivity environments.
* **Cactus/LiteRT Prizes**: Targeted for local-first mobile implementation using Google AI Edge.

## 2. Technical Architecture
* **Model**: Gemma 4 E2B-IT (LiteRT-LM). Optimized for mobile (INT4 quantization).
* **Modality**: Native Multimodal (Image OCR-First, Audio, Text).
* **Reasoning**: Gemma 4 Thinking Mode (Native Chain-of-Thought) to expose the "logic" behind fallacy detection.
* **Engine**: **LiteRT-LM Android SDK** for optimized on-device inference.
* **Privacy**: 100% Offline; zero data leakage.

## 3. Feature Roadmap (Implementation Status)
* **[✅] Multimodal Intake:** Full-resolution Photo capture (FileProvider), Audio chunking (WAV), and Text pasting.
* **[✅] Cactus Intelligent Routing:** Intelligent path selection between native OCR (ML Kit) and Multimodal Fallback.
* **[✅] Truth Radar:** 4-axis visualization (Logic, Evidence, Credibility, Objectivity) via structured JSON extraction.
* **[✅] Socratic Bridge:** Agentic loop that prompts users with critical thinking questions before revealing final results.
* **[✅] Learning Hub:** Integration with a local `CurriculumRepository` for fallacy education.
* **[✅] Logic Transparency:** Real-time streaming of Gemma 4's "Chain of Thought" (Thinking block).

## 4. Risk & Effort Assessment

### Identified Risks
1. **The "Fact-Checking" Gap (High Risk)**: As an offline tool, it cannot verify new breaking news facts.
   * *Mitigation*: Re-brand as an "Analysis & Logic Tool" rather than a "Fact Checker." Focus on internal consistency.
2. **Hardware Fragmentation (Medium Risk)**: Gemma 4 E4B requires high RAM (6GB+).
   * *Mitigation*: Use Gemma 4 E2B as the base model, which fits in ~1.5GB of RAM with 4-bit quantization.
3. **Translation & Context Nuances (Medium Risk)**: Nuance in international journalistic phrasing may be lost or misinterpreted by smaller models.
   * *Mitigation*: Use targeted prompt engineering focused on common international media colloquialisms in the ESL module, mapping them to English logic equivalents.
4. **Development Effort (High Risk)**: Integrating the multimodal input pipeline (Audio/Image -> Tokens -> Model) is the primary engineering bottleneck.
   * *Mitigation*: Use robust, off-the-shelf APIs (like MediaPipe) where possible before building custom bridges.

## 5. UX & Design Considerations (Local-First LLM Focus)
Before UI prototyping, consider these specific UX elements tailored to a local on-device architecture:
1. **Model Download / Onboarding Screen**: Since Gemma weights are large (~1.5GB-3GB) they won't be in the initial app download. Design an engaging onboarding experience (mini-tutorials, tips) so users don't abandon the app during the prolonged first download.
2. **"Thinking Mode" Transparency**: Design a non-overwhelming way to expose Gemma's "Chain of Thought." This could be an extending accordion, a "Show Thought Process" toggle, or soft typing animations conveying active reasoning.
3. **Latency and Processing States**: Local inference can take 10-20 seconds for deep analysis on long text. Include elegant skeleton loaders or "AI is analyzing..." animations so the app feels alive during heavy computation.
4. **Multi-Language Text Expansion**: Ensure UI container responsiveness since Spanish translations often require 20-30% more horizontal space than English text.
5. **Visualizing Complex Scores**: Argument Analysis and Bias tools should be immediately legible. Consider spider charts, gauges, or traffic-light visual systems rather than pure walls of text.

## 6. Strategy for Kaggle & User Adoption

### Maximizing Kaggle Competition Success
* **Explicit Gemma 4 Features**: Thoroughly document how the app uniquely leverages Gemma 4's native modalities (e.g., direct Image-to-Token support) and Chain-of-Thought (Thinking Mode).
* **Open Source Reproducibility**: The repository must contain an impeccably clear setup and build guide to ensure judges can easily run the Kotlin Multiplatform app locally without environment errors.
* **Empirical Benchmarking**: Present concrete performance data to the judges, including inference speeds (tokens/sec) and memory footprints across varying device hardware tiers to prove "local-first" viability.

### Maximizing User Value & Open-Mindedness
* **Neutral, Non-Judgmental AI Persona**: The system prompt should strictly forbid a lecturing tone. Instead of "This article is false," the AI should say "This argument relies heavily on emotional language," allowing the user to make their own final judgment.
* **Progressive Disclosure & Gamification**: Shift the paradigm from "the AI does the thinking for you" to "the AI teaches you to think." Incorporate interactions that encourage users to spot fallacies themselves before the AI reveals them.
* **Epistemic Humility**: Design the UI to elegantly remind users that the AI is evaluating the *structural quality* and *arguments*, not acting as an infallible arbiter of absolute truth.

## 7. Success Criteria for Kaggle Submission
* **3-Minute Demo Video**: Showing real-time analysis of a physical newspaper headline in "Airplane Mode" along with a live Translation module demo analyzing an international text.
* **Logic Transparency**: The app must show the "Thought Process" (Gemma’s internal reasoning) to build user trust.
* **Local-First Benchmark**: Report inference speed (tokens/sec) on standard hardware (e.g., Pixel 9 or Samsung S26).

## 8. Immediate Next Steps
- [ ] **Set up Dev Environment**: Pull the MediaPipe `.task` weights for `google/gemma-4-E2B` from Kaggle (Do NOT use GGUF).
- [ ] **Prompt Engineering**: Define the "Logic Master" system prompt using the `<|think|>` token, including translation instructions for the ESL module.
- [x] **Mobile Skeleton**: Create a Kotlin/Compose Multiplatform app skeleton with basic camera/mic permissions.

## 9. Model Sources & Weights
For development and production, use the **LiteRT/MediaPipe-compatible** Gemma 4 weights:
*   **Official Collection**: [litert-community/gemma-family](https://huggingface.co/collections/litert-community/gemma-family)
*   **Primary Model (E2B-IT)**: [gemma-4-E2B-it-litert-lm](https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm)
*   **Expert Model (E4B-IT)**: [gemma-4-E4B-it-litert-lm](https://huggingface.co/litert-community/gemma-4-E4B-it-litert-lm)

## 10. Benchmarking Plan
* **Metrics**: 
    * Time to First Token (TTFT).
    * tokens/sec (Inference throughput).
    * Peak Memory Usage (RAM footprint).
* **Instrumentation**: 
    * Integrate timing logic within `GemmaOrchestrator` (start-of-prompt to end-of-stream).
    * Log results to a hidden "Dev/Benchmark" log in the app's Settings to capture real-world data from beta testers.
* **Target Devices**:
    * Flagship Tier (e.g., Pixel 9 Pro).
    * Base Tier (Standard hardware with ~6GB RAM).

## 10. Test Coverage Strategy
* **Domain Logic**: Validating the mapping of fallacy types to definitions.
* **Report Parsing**: Unit testing the regex/JSON parser that extracts structured metrics from Gemma's raw text stream.
* **Prompt Safety**: Ensuring the `systemPrompt` construction remains consistent across analysis, steel-man, and chat modes.
