# Progressive Synthesis Implementation Plan (v2: Session-Based) - [STATUS: COMPLETED]

## Overview
As of 2026-05-05, we have successfully transitioned to a **Session-Based Progressive Synthesis**. This approach leverages the LLM's KV cache by maintaining a single continuous session, avoiding the high cost of re-injecting the full transcript context for every analytical dimension. 

This applies to **Text**, **Image**, and **Audio** analysis pipelines.

## 1. Implemented Architecture: The "Chain of Thought" Pipeline

Instead of atomic, disconnected prompts, we use a stateful interaction with the model:

1.  **Initial Prompt (Stage 1: Perception)**
    - Send: Full Transcript / Text.
    - Ask: Executive Summary and Highlights.
    - Command: `InferenceCommand.Analyze` (Starts a new session).
    - Output: UI displays Summary immediately.
2.  **Follow-up 1 (Stage 2: Extraction)**
    - Ask: "Identify the key claims made in this content."
    - Command: `InferenceCommand.Chat` (Continues session).
    - Output: UI populates the Claims list.
3.  **Follow-up 2 (Stage 3: Metrication)**
    - Ask: "Provide the logic, objectivity, evidence, and credibility scores (0-100) in JSON."
    - Command: `InferenceCommand.Chat`.
    - Output: UI populates the Radar Chart and Credibility indicator.
4.  **Follow-up 3 (Stage 4: Deep Scan)**
    - Ask: "Perform a deep rhetorical scan for logical fallacies."
    - Command: `InferenceCommand.Chat`.
    - Output: UI populates the Fallacies section.

### 1.1 Robust Parsing Strategy
We moved from fragile Markdown parsing to a **JSON-First** strategy.
- **Primary**: Attempt to extract a JSON block using regex and parse via `kotlinx.serialization`.
- **Fallback**: Use a custom line-based parser to handle cases where the model produces Markdown lists instead of JSON.
- **Fallacy Format**: Now supports a two-line description (Relevance + Evidence quotes) for better clarity.

## 2. Technical Context Restoration (Priming)

To solve the "Chat Context" problem when loading analyses from history:
- **Background Priming**: When an analysis is loaded from history, the coordinator sends a silent `InferenceCommand.Prime(transcript)` command.
- **Effect**: This re-populates the LLM's active session history, ensuring the user can chat with the article even if the analysis happened days ago.

## 3. Workflow Comparison (Actuals)

| Feature | Monolithic (Old) | Progressive Session (New) |
| :--- | :--- | :--- |
| **First Paint** | ~20-30s | **5-8s** (Summary) |
| **Total Completion** | 40-50s | ~25s |
| **UX Feel** | "App is stuck" | **"App is thinking / writing"** |
| **Chat Readiness** | Low (Re-transcription) | **High** (Context is already active) |

## 4. Current Limitations & Next Steps
- **Model Size**: Gemma 4B-E2B is powerful but can be slow on mid-range devices.
- **Image Hallucination**: The multimodal vision model occasionally summarizes rather than transcribing literally.
- **Next Step**: Integrate **Google ML Kit OCR** for the "Transcription" stage to provide zero-hallucination literal extraction, using the LLM only for the subsequent analytical stages.
