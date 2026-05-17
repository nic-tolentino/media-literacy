# Prompt Engineering Strategy

This document outlines the system prompting strategy for the Gemma 4 logic engine. Reliable structured output and clear chain-of-thought (CoT) reasoning are critical for the "Logic Master" persona.

## 1. Core Personas

### Logic Master (Default)
- **Role**: A non-judgmental, objective media literacy expert.
- **Goal**: Deconstruct arguments and identify rhetorical patterns without taking sides.
- **Constraint**: Must use the `<|think|>` tag for internal reasoning before providing the final report.

## 3. Agentic Prompt Orchestration

News Decoder utilizes a **Multi-Stage Prompt Pipeline** to ensure deterministic results and high-fidelity reasoning.

### Stage 1: Executive Perception (Summary)
- **Prompt Style**: "Summarize this content in 2 sentences."
- **Goal**: Establish the base context for the model.

### Stage 2: Structural Extraction (Metrication)
- **Prompt Style**: "Analyze structural integrity... Provide scores 0-100."
- **Goal**: Extract numeric metrics for the 'Truth Radar'.

### Stage 3: Logical Deconstruction (Fallacies)
- **Prompt Style**: "Identify exactly 3 rhetorical patterns... Provide quote and evidence."
- **Goal**: High-precision JSON extraction of logical flaws.

### Stage 4: The Socratic Bridge (Interactive Learning)
- **Prompt Style**: "Based on the previous analysis, generate 3 thought-provoking questions. Do not give the answer."
- **Goal**: Transform the app from a passive scanner into an active educational agent.

---

## 4. "Cactus" Intelligent Routing
For multimodal inputs (Images/Audio), the prompting engine performs a **Perception Turn** to detect text density. 
- **OCR-First Path**: Uses ML Kit for raw extraction, followed by Stage 1-4.
- **Vision Fallback Path**: Uses multimodal vision prompts to describe the image's non-textual framing (e.g., "Analyze the emotional impact of the imagery") before proceeding to logical analysis.
{
  "summary": "String",
  "credibility": "Low/Moderate/High",
  "evidenceQuality": 0-100,
  "toneScore": 1-5,
  "fallacies": [
    {
      "type": "String",
      "definition": "String",
      "evidence": "String (Quote or context from text)"
    }
  ]
}
```

### Separating Thinking from Output
The parser in `GemmaOrchestrator` will look for content outside the `<|think|>` tags and attempt to parse the trailing JSON block.

## 3. System Prompts

### Analysis Mode
```text
You are a Media Literacy Expert. Analyze the following text for logical consistency and rhetorical strategies. 
Rules:
1. Start with <|think|> and deconstruct the logic.
2. After </|think|>, provide a structured report in JSON format.
3. Be objective and avoid political bias.
```

## 4. Multi-turn Conversational Context
MediaPipe's `generateResponse` is stateless. Conversation history must be manually injected:
- User: ...
- Model: ...
- User: New question...
