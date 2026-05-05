# Progressive Synthesis Implementation Plan (v2: Session-Based)

## Overview
Based on feedback, we will transition to a **Session-Based Progressive Synthesis**. This approach leverages the LLM's KV cache by maintaining a single continuous session, avoiding the high cost of re-injecting the full transcript context for every analytical dimension. 

This will apply to both **Text** and **Audio** analysis pipelines.

## 1. Architectural Changes: The "Chain of Thought" Pipeline

Instead of atomic, disconnected prompts, we will use a stateful interaction with the model:

1.  **Initial Prompt (Stage 1: Perception)**
    - Send: Full Transcript / Text.
    - Ask: Executive Summary and Highlights.
    - Output: UI displays Summary immediately.
2.  **Follow-up 1 (Stage 2: Metrication)**
    - Ask: "Based on the above, provide the logic, objectivity, evidence, and credibility scores (0-100)."
    - Output: UI populates the Radar Chart and Narrative Tone indicator.
3.  **Follow-up 2 (Stage 3: Extraction)**
    - Ask: "Identify the key claims made in this content."
    - Output: UI populates the Claims list.
4.  **Follow-up 3 (Stage 4: Deep Scan)**
    - Ask: "Now perform a deep rhetorical scan for logical fallacies."
    - Output: UI populates the Fallacies section.

### 1.1 State Model Updates
We will extend `AnalysisResult` with boolean loading flags to drive the UI states without breaking existing non-null logic.

```kotlin
data class AnalysisResult(
    val summary: String = "",
    val highlights: List<String> = emptyList(),
    val keyClaims: List<String> = emptyList(),
    val logicScore: Int = 0,
    // ...
    val isSummaryLoaded: Boolean = false,
    val isScoresLoading: Boolean = false,
    val isClaimsLoading: Boolean = false,
    val isFallaciesLoading: Boolean = false,
    val vocalTone: String? = null
)
```

## 2. Technical Implementation

### 2.1 LlmEngine & Session Management
- We will ensure the `LlmEngine` maintains the context across these sequential calls.
- In `AnalysisCoordinator`, we will use a single `ChatSession` for the entire analysis flow.
- This prevents the "20s wait" because only the first prompt (Stage 1) needs to process the full transcript. Subsequent prompts will be near-instantaneous as they only process the short delta.

### 2.2 UI: The Progressive Report
- **Shimmer Placeholder**: A new composable `SectionShimmer()` will be used in `AnalysisSectionCard`.
- **Transitions**: `AnimatedContent` will handle the swap from Shimmer -> Content.
- **Section Locking**: The "Logic Hat" (Chat) icon for a section will be disabled or hidden until that section's loading flag is false.

## 3. Workflow Comparison

| Feature | Current (Monolithic) | New (Progressive Session) |
| :--- | :--- | :--- |
| **First Paint** | 20-40s (Full JSON) | 5-10s (Summary only) |
| **Total Completion** | 40-60s | 25-45s |
| **Context Cost** | High (Once) | Low (Incremental Cache) |
| **UX Feel** | "App is stuck" | "App is thinking / writing" |

## 4. Risks & Mitigations
- **Context Window**: Long chains of thought might hit the context limit on mobile.
  - *Mitigation*: We will keep the follow-up prompts extremely terse ("Just the JSON metrics").
- **Session Corruption**: If one stage fails, does the whole chat break?
  - *Mitigation*: We will implement a "Reset & Retry" logic that can re-init the session if a parsing error occurs mid-chain.

---
**Next Steps:**
1. Update `AnalysisResult` data class.
2. Refactor `AnalysisCoordinator` to use a `ChatSession` for the analysis pipeline.
3. Implement `SectionShimmer` in the UI.
