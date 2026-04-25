# Gemma4ML: High-Level Project Plan

## 1. Strategic Alignment (Kaggle Hackathon Tracks)
* **Safety & Trust**: Pioneer explainable AI that unmasks logical fallacies and manipulation.
* **Digital Equity**: Provide high-end news analysis for users in low-connectivity environments.
* **Cactus/LiteRT Prizes**: Targeted for local-first mobile implementation using Google AI Edge.

## 2. Technical Architecture
* **Model**: Gemma 4 E2B (2.3B) or E4B (4.5B). Optimized for mobile (INT4 quantization depending on device quality?).
* **Modality**: Native Multimodal (Image OCR for newspapers, Audio for radio/speech, Text for digital news).
* **Reasoning**: Gemma 4 Thinking Mode (Native Chain-of-Thought) to expose the "logic" behind fallacy detection.
* **Engine**: MediaPipe LLM Inference API (Primary baseline to guarantee cross-device compatibility). AICore is treated strictly as an optional, flagship-only enhancement path to prevent hardware lock-in.
* **Privacy**: 100% Offline; zero data leakage. Uses a statically embedded lightweight JSON/SQLite file containing concrete fallacy definitions and examples (eschewing complex Local RAG).

## 3. Feature Roadmap
* **[P0] Multimodal Intake:** Capture photos of headlines, record 30s audio clips, or paste text.
* **[P0] Logical Fallacy Detector:** Identify 10-12 well-executed fallacies (Ad Hominem, Strawman, etc.) using Thinking Mode (Quality over Quantity).
* **[P0] "Steel-man" Generator:** Local AI generates the strongest arguments for and against an article to build a balanced view. (Elevated as a unique standout feature).
* **[P0] Summary:** Provide a concise summary of the news article, highlighting the main points and key arguments.
* **[P0] Argument Analysis:** Provides insights into the article's structure, outputting a concrete, predefined schema (e.g., Tone: 1-5 scale, Evidence Quality: 0-100%).
* **[P0] Multi-Language UI (i18n):** Native user interface support for multiple languages, starting with English and Spanish to ensure full accessibility.
* **[P1] Interactive Chat Mode:** Have a two-way discussion with the local AI about the article to ask questions, request further explanations, clarify terminology, or dig deeper into historical context.
* **[P1] Learning Hub:** A simple "spot the fallacy" interactive quiz to build media literacy (standout educational demo moment).
* **[P2] Translation/ESL Context:** Handled strictly as an LLM prompt variation rather than an independent software module to prevent scope creep.

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
