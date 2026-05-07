# Media Literacy App: PLAN_STAGE_2.md

## Objective: Transition from Core Engine to MVP Readiness
This plan outlines the next phase of development for the Media Literacy (GemmaLens) application, incorporating the "Socratic Bridge" strategy and the centralized Inference Actor architecture.

---

## Phase 1: The Socratic Bridge (High-Value Learning) - [PENDING]
*Transition from static analysis to active cognitive engagement.*

### 1.1 Socratic Review Mode
- **Feature**: Instead of immediately listing fallacies, the UI presents a "Reflective Challenge" first.
- **Implementation**:
    - Add a `SocraticStage` to `AnalysisCoordinator`.
    - Prompt: *"I've identified a potential reasoning error. Can you spot what might be problematic about the author's logic in the [specific section]?"*
    - User interacts via Chat to "discover" the fallacy before it is revealed in the list.
- **Benefit**: Shifts user from "System 1" (passive consumption) to "System 2" (active reasoning).

### 1.2 "Spot the Tactic" Mini-Game
- **Feature**: A daily interactive challenge to build cognitive "antibodies."
- **Implementation**:
    - Create a `GamificationStage` that generates 3 fake headlines/tweets.
    - User must identify the manipulation tactic (e.g., Cherry-Picking, Ad Hominem).
    - Implement a "Literacy Rank" (Novice Detective -> Master Analyst) stored in `DataStore`.

---

## Phase 2: Multimodal Maturity (Physical & Audio Media) - [COMPLETED]
*Enabling analysis of newspapers, speeches, and podcasts.*

### 2.1 Full Image/OCR Pipeline
- **Status**: **DONE**.
- **Integration**: `startImageAnalysis` is fully integrated with the PhotoPicker and Camera.
- **Context**: OCR transcripts are correctly persisted to history for chat restoration.

### 2.2 Robust Audio Chunking
- **Status**: **DONE**.
- **Integration**: Implemented 25s window/5s overlap strategy with word-window deduplication for seamless transcripts.

---

## Phase 3: Persistence & User Journey - [COMPLETED / ONGOING]

### 3.1 Analysis Library
- **Status**: **DONE**.
- **Feature**: Full local persistence using `SQLDelight`.
- **Implementation**: History screen allows deleting, viewing, and **Restoring and Re-running** chat sessions via the priming mechanism.

### 3.2 Personalized Onboarding
- **Feature**: Capture user's initial media literacy level and goals.
- **Questions**:
    - "Why did you download GemmaLens?" (Discover, Analyze, Learn).
    - "How comfortable are you with identifying logical fallacies?"
- **UI**: A smooth, multi-step onboarding flow using `Voyager` transitions.

---

## Phase 4: UI/UX Excellence (Premium Feel) - [IN PROGRESS]

### 4.1 Advanced Visualizations
- **Circular Indicators**: Implement dynamic "Credibility Rings" as mentioned in the strategy doc.
- **Collapsible Reasoning Traces**: Allow users to opt-in to seeing the LLM's step-by-step logic (CoT).
- **Radar Chart Polish**: Ensure high-contrast, bold rendering for the 4-axis metrics.

### 4.2 Failure Gracefully
- **Status**: **DONE**.
- **Logic**: Implemented robust fallback parsing for LLM failures (Markdown fallback).

---

## MVP Success Criteria
- [x] **Text Analysis**: 100% stable with progressive loading.
- [x] **Multimodal**: Functional Photo/Audio analysis (5 min audio, 8k char text limits).
- [ ] **Retention**: "Spot the Tactic" game functional.
- [x] **Persistence**: Analyses are saved and viewable in the Home Screen.

---

> [!IMPORTANT]
> **Priority for Next Sprint**: Socratic Bridge reveal and ML Kit OCR integration to eliminate transcription hallucinations.
