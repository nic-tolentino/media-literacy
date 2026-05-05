- [x] Implement centralized Inference Actor (Architecture V1).
- [x] Add temporary buttons for sample articles for dev/test.
- [ ] Implement "Socratic Review" Mode (Analyzer Enhancement).
- [ ] Implement "Spot the Tactic" Mini-Game (Daily Inoculation).
- [ ] Finalize Image/Photo analysis (PhotoPicker integration).
- [ ] Finalize Audio analysis (Chunked Native pipeline).
- [ ] Build the "Literacy Rank" progress tracking system.
- [ ] Implement LLM model download from GCP cloud storage.
- [ ] Complete the "Learning" tab with SIFT method tutorials.
- [ ] Refine "Radar Chart" with circular indicators and premium styling.
- [ ] Implement onboarding with user literacy assessment.
- [ ] Add iOS support.
- [ ] Add dependency injection framework.

- Implementing a real-time resampler in the AudioPickerScreen to normalize non-PCM inputs.
- Adding a loading state in AnalysisScreen that explicitly waits for the coordinator's first "Thinking" emission.
- Finalizing the "Literacy Rank" progress tracking system to gamify media deconstruction.


- Run the analysis on the same article multiple times to assess the consistency of the scores and overall analysis. 
- Test the performance of the app on different devices and form factors. 
- Add extensive test coverage!
- Save analysis and chat for later reference.
- Ability to view the text/image/audio that generated the article.
- Implement image / photo capsure / selection for input analysis.
- Implement audio input for analysis.
- Figure out how to support audio clips longer than 30 seconds?
- Implement LLM model download, and host the LLM on GCP cloud storage.
- When the chat for a section loads, offer two to 3 suggestions such as "What is this section" and "Explain the score"
- Add iOS support.
- Add dependency injection framework.
- Implement the whole learning tab!
- Implement the settings tab!
- Dynamically (or on startup?) calculate an optimal context window for the device hardware, so we can support larger inputs (text, audio, images).
- How hard would it be to extract the text of an article that's being viewed from a WebView? That we can then feed into the LLM to analyze? That could be a cool feature. 

Further improvements:
    Onboarding screen
    Asking about The article, have they read it. How do they feel about the topic, how do they feel about the article/author/publication
    Title, comments, skim, read the whole article 
    Ask this while the analysis is being generated - take this into doing during the chat

    Improving media literacy is at much about learning about communication and the people and world around us. As it is about learning about ourselves.
    Ask them why they downloaded the app, discover, analyse, learn
    Ask them about what they'd like to learn more about? Why is media literacy important to them? 
    Ask them what level of media literacy they feel they have (and then break it down into categories?)

    Ask what level of analytics they're comfortable with: none, crash reports, basic, full

    Resources:
    - crash course intrp to Science 
    - Wikipedia 
    - Games
    - other websites
    - courses? 
    - learning about communication
    Do we break this down into topics, or resource types, or both? Probably both, but with a focus on topics.

Oh and we need to consider whether using ML Kit/AICode for certain tasks is the right way to go. Eg, perhaps we should use it for image to text, and offer it as an options for certain summarisation and analysis tasks before committing to downloading a much larger model? When is it better to use one approach vs the other?

We need to come up with a plan on:
- How to monitor the performance of the app as a whole
- How to monitor the performance and effectiveness of the LLM in production
- How to assess the quality of the LLM responses before deployment of models (image to text accuracy, audio analysis accuracy, tone detection accuracy, fallacy detection accuracy, as well as the ability of the model to assess the quality of the data (image/audio) it's been given to process, to reduce the changes of major hallucinations).
    - Also test the ability to merge audio chunks correctly.
    - Also the ability to merge transcriptions across multiple images from a single artile correctly.

Why ML Kit is the right move for V2:
- Zero "Political" Laziness: ML Kit doesn't care about the meaning of the text; it only cares about the geometry of the characters. It won't skip "Donald Trump" because it doesn't know who that is—it just sees a series of glyphs.
- No "Hallucinated" Summaries: As you saw in the Snoopy example, the LLM is trying to be "helpful" by shortening "commander-in-chief's antagonism" to "chief's antagonist." ML Kit will give you the literal characters every time.
- Speed: It runs in milliseconds locally on the CPU/GPU, whereas the multimodal LLM turn takes several seconds.
- Layout Awareness: ML Kit can detect "blocks" of text. We can actually use that to solve your concern about other articles in the photo—we could let the user tap the specific "block" they want to analyze.

Nice to have:
- Support for multiple languages (Spanish+)
- The ability to translate the article into other languages + simplified versions
