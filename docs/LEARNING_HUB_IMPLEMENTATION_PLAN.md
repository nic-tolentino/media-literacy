# Learning Hub: Technical Implementation Plan

This document provides the high-level engineering specifications and execution order for the Learning Hub features defined in `MEDIA_LITERACY_RESOURCES.MD`.

## 1. Phase 0: The Discovery Layer (v1.0 - Hackathon MVP)
The goal of Phase 0 is to provide the "Vocabulary & Toolkit" that supports the primary analysis tool. This layer is purely static and discovery-based.

### Step 1: The Resource Portal
*   **Asset**: `assets/media_literacy_resources.json`.
*   **Logic**: A categorized list of external books, podcasts, fact-checking sites, and investigative tools.
*   **Goal**: Awareness of the broader ecosystem outside the app.

### Step 2: The Tactic Glossary
*   **Asset**: `assets/curriculum.json`.
*   **UI**: A searchable "Reference" sub-tab in the Learning Hub.
*   **Content**: A simple "Flashcard" for each tactic containing:
    *   **Name & Category**.
    *   **One-Sentence Definition**.
    *   **One Canonical Example**.
*   **Integration**: Every analysis result that identifies a fallacy/bias provides a direct link to its entry in this Glossary.

---

## 2. Core Data Architecture (Preparation for v1.1)
While not required for the Phase 0 MVP, the following architecture is required for the persistence and retention features in Phase 1.

### A. Spaced Repetition Schema (`LearningProgress.proto`)
We will use **Jetpack DataStore (Proto)** for small, frequently updated mastery metadata.
```protobuf
message LearningProgress {
  map<string, TacticMastery> mastery_data = 1; // Key: tactic_id (e.g., "STRAWMAN")
  int32 total_srs_reviews = 2;
  int32 total_mastery_events = 3;
  LiteracyRank current_rank = 4;
  int32 onboarding_score = 5; // Placeholder for v1.1
}

message TacticMastery {
  int64 last_seen_ms = 1;
  int64 next_review_ms = 2;
  int32 interval_index = 3; // index into [1, 3, 7, 21]
  int32 confidence_score = 4;
}
```

### B. Persistence Strategy
*   **Mastery Data**: Stored in `LearningProgress.proto` (DataStore).
*   **Analysis History**: Queried from the existing **SQLDelight** `AnalysisResult` table. Do NOT duplicate this data in DataStore. Field Work Mode will perform a SQL query: `SELECT * FROM AnalysisResult WHERE timestamp > :min AND timestamp < :max`.

---

## 3. Phase 1: Cognitive Engineering & Integration (v1.1)

### Step 3: KMP Notification Service
*   **Architecture**: `expect class NotificationScheduler` in `commonMain`.
*   **Android Actual**: Implemented using `WorkManager` and `AlarmManager`.
*   **iOS Actual**: Implemented using `BGTaskScheduler` and `UNUserNotificationCenter`.
*   **Trigger**: Fires when `next_review_ms` in DataStore is reached.

### Step 4: Mastery Event Trigger (The "Bridge")
*   **Hook**: Inject a listener into `AnalysisCoordinator` (Stage 4: Deep Scan).
*   **Execution**:
    1.  Parse fallacies from Stage 4 JSON output.
    2.  For each fallacy, check `mastery_data` in DataStore.
    3.  If it's the first encounter or a low-confidence encounter, trigger a **Mastery Event** UI notification.
    4.  Update `total_mastery_events` and `last_seen_ms`.

### Step 5: Field Work Mode implementation
*   **Query**: Attempt to find an `AnalysisResult` from 7-30 days ago.
*   **Fallback**: If no results are found in the 7-30 day window, select the article with the **lowest `confidence_score`** from the entire history.
*   **Recall Logic**: "You analyzed [Title] recently. Do you remember which tactic was present?"

---

## 4. Phase 2: Dynamic AI Generation (v1.2)

### Step 6: Prompt Engineering (Gemma 4 Control)
*   **Inference Pattern**: Use the `<start_of_turn>model` followed by an explicit `<thought>` block to elicit reasoning.
*   **Template**:
    ```text
    <start_of_turn>user
    Identify the fallacy in this text: [TEXT]
    <start_of_turn>model
    <thought>
    [AI Reasoning]
    </thought>
    { "fallacy": "...", "explanation": "..." }
    ```

### Step 7: The Inoculation & Steel-man Generators
*   **Steel-man Logic**: AI generates a controversial but neutral proposition (e.g., "Universal Basic Income").
*   **Dual-Inference**: Gemma 4 generates the strongest versions of both Pro and Con arguments.

---

## 5. Phase 3: Advanced Verification & Visualization (v1.3+)

### Step 8: Socratic Bridge (Stage 5)
*   **Refactor**: Add `SOCRATIC_BRIDGE` as a final stage in the `AnalysisCoordinator` pipeline.
*   **State Management**: The stage holds the current `AnalysisResult` and a `ConversationHandle`.
*   **Logic**: AI generates an observation-based question. User response transitions the state to `Thinking` then back to `SocraticQuestion`.

### Step 9: Chart Crime Detective
*   **UI**: Use `androidx.compose.ui.graphics.Canvas` for interactive rendering of misleading charts.

### Step 10: Resource Graduation
*   **Logic**: Pre-authored static JSON quizzes per resource.
*   **Graduation**: Success updates `LearningProgress` and triggers Rank check.

---

## 6. Curriculum & Exercise Engine

### A. The "SRS Internal Modes" Ladder
The app does not have user-selectable "levels." Instead, the SRS engine automatically selects the **Interaction Mode** based on the user's `confidence_score` for a specific tactic.

| Mode | Cognitive Task | Trigger |
| :--- | :--- | :--- |
| **Recognition (L1)** | **True/False** check | `confidenceScore == 0` |
| **Discrimination (L2)** | **Multiple Choice** (4 options) | `confidenceScore == 1-2` |
| **Cued Recall (L3)** | **Autocomplete Text** | `confidenceScore == 3` |
| **Free Recall (L4)** | **Open Text Box** (No hints) | `confidenceScore == 4+` |

*Note: The engine favors Mode L2 for daily maintenance and L4 only for "Mastery Graduation."*

### B. Curriculum Schema (`curriculum.json`)
```json
{
  "categories": [
    {
      "id": "FALLACIES",
      "tactics": [
        {
          "id": "AD_HOMINEM",
          "title": "Ad Hominem",
          "definition": "Attacking the person instead of the argument.",
          "canonical_example": "You can't trust his economic plan; he never finished college."
        }
      ]
    }
  ]
}
```

### C. Advanced Exercise Types
In addition to identification, the engine supports:
1.  **Inverse Identification**: "Here is the definition of **Strawman**. Which of these 3 headlines uses it?"
2.  **Structural Audit**: "This text is an **Ad Hominem**. Is it attacking character (A) or credentials (B)?"

---

## 7. Final Implementation Roadmap

### Phase 0: The Vocabulary MVP (v1.0)
*   **Static Resource Portal**: Curated list of 20+ external high-quality resources.
*   **Tactic Glossary**: A simple, searchable A-Z reference of manipulation techniques.
*   **Deep-Link Integration**: Analysis results link directly to the Glossary for education-on-demand.

### Phase 1: The Retention Layer (v1.1)
*   **Mastery Tracking**: SRS DataStore implementation + `confidenceScore` logic.
*   **L1/L2 Drills**: Recognition and Discrimination exercises using static seed examples.
*   **Mastery Events**: Notifications when an analysis finds a new tactic in the wild.
*   **Field Work Recall**: Passive history-based recall drills.

### Phase 2: The Mastery Layer (v1.2)
*   **Dynamic Generation**: Gemma 4 creates infinite non-partisan scenarios for drills.
*   **L3/L4 Drills**: Autocomplete and Open Text recall.
*   **Inverse Identification**: "Which headline uses this tactic?" exercises.

### Phase 3: The Workshop Layer (v1.3+)
*   **Socratic Review (Advanced)**: Multi-turn logic refinement with the AI.
*   **Steel-man Workshop**: Cognitive empathy exercises for controversial propositions.
*   **Chart Crime Detective**: Interactive visual literacy tool.
