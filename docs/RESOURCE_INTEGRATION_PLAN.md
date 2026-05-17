# RESOURCE INTEGRATION & LLM LINKING PLAN

## Overview
The goal is to bridge the gap between the AI's analytical output and our curated educational resources (Curriculum & Resource Portal). This will allow the LLM to provide "expert guidance" that directly links to deep-dive materials available within the app or externally.

## Constraints
- **Context Window:** Gemma-2b has a limited context window (approx. 2048-8192 tokens depending on the variant). We must be judicious about what we inject.
- **Inference Speed:** Large prompts can slow down the "time to first token."
- **Formatting:** The LLM needs a consistent way to reference resources that the UI can reliably parse.

## Proposed Strategies

### 1. Dynamic Context Injection (Short-term / Phase 1)
Instead of feeding the entire curriculum, we inject only relevant categories based on the current analysis.
- **Mechanism:** After the "Fallacy Scan" stage, we identify the detected fallacies. We then inject a "Resource Manifest" into the Chat context that lists the titles and IDs of matching local resources.
- **Prompt Fragment:**
  ```text
  You have access to the following local educational resources. If you discuss these, use the format [[ResourceID]]:
  - Ad Hominem (ID: ad_hominem)
  - Strawman (ID: strawman)
  ```

### 2. The "Resource Index" System Prompt (Phase 2)
Add a condensed index of all "Tactic" titles to the base system prompt for chat.
- **Pros:** LLM is always aware of the scope of the curriculum.
- **Cons:** Consumes permanent context space.
- **Optimization:** Use a comma-separated list of just titles. "Supported Tactics: Ad Hominem, Strawman, False Dilemma..."

### 3. Tool-Based Retrieval / RAG (Long-term / Phase 3)
If the context window becomes a critical bottleneck, implement a simple local search.
- **Mechanism:** Before sending a user query to the LLM, run a local keyword search against `curriculum.json` and `media_literacy_resources.json`. 
- **Injection:** "Contextual Knowledge: [Relevant Snippet from Resource]"

## Linking Syntax & UI Handling

### Chat Linking
To ensure the UI can handle links, the LLM should use a specific markdown-like syntax:
- **Local Tactics:** `[[tactic:ad_hominem]]`
- **External Resources:** `[[resource:book_id]]`

**UI implementation:**
- Update `ChatScreen` to parse these tags using a custom `AnnotatedString` or a `Regex` match in the message bubble.
- Clicking a `[[tactic:...]]` link would trigger `navigator.push(TacticsLibraryScreen(id))`.

### Fallacy Card "Auto-Linking"
We already implemented conditional icon display in the `PatternCard`. We can extend this to:
- **Rich Tooltips:** Show a snippet of the local definition when hovering/clicking the "Graduate Hat" before navigating.

## Next Steps & Experiments
1. **Condensed Manifest:** Create a utility to generate a minimal string representation of the `Curriculum` for prompt injection.
2. **Chat Parsing:** Prototype a `LinkifiedText` component in Compose that handles the `[[...]]` syntax.
3. **Prompt Engineering:** Test if Gemma-2b follows the instruction to "Only link to resources provided in the manifest."

## Implementation Roadmap
- [ ] **Sprint 1:** Implement `ResourceManifestGenerator` and inject into `AnalysisCoordinator` chat prompts.
- [ ] **Sprint 2:** Update `ChatScreen` with Regex-based link parsing.
- [ ] **Sprint 3:** Add "Suggested Resources" section to the bottom of the chat based on LLM output.
