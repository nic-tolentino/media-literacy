package org.medialiteracy.domain

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AnalysisCoordinatorTest {

    private class MockRepository : AnalysisRepository {
        var savedCount = 0
        var lastSaved: SavedAnalysis? = null
        override suspend fun saveAnalysis(analysis: SavedAnalysis) { 
            savedCount++ 
            lastSaved = analysis
        }
        override fun getSavedAnalyses(): Flow<List<SavedAnalysis>> = flowOf(emptyList())
        override suspend fun deleteAnalysis(id: String) {}
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun testArticleLengthEnforcement() = runTest {
        val engine = MockLlmEngine()
        val service = AndroidInferenceService(engine, FakeModelRepository(), backgroundScope, "mockContext", StandardTestDispatcher(testScheduler))
        val coordinator = AnalysisCoordinator(service, MockRepository(), backgroundScope)
        
        val longArticle = "A".repeat(8001)
        coordinator.startAnalysis(longArticle)
        
        advanceUntilIdle()
        assertTrue(coordinator.state.value is InferenceState.SourceTooLarge)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun testTwoStagePipeline() = runTest {
        val engine = MockLlmEngine()
        // Stage 1 Summary
        engine.enqueueTokens(listOf("""{"summary": "Stage 1 Summary", "objectivityScore": 100, "logicScore": 100, "evidenceQuality": 100, "credibilityScore": 100, "credibility": "G", "primaryStrength": "S", "observationArea": "A"}"""))
        // Stage 2 Claims
        engine.enqueueTokens(listOf("""{"keyClaims": ["Claim 1"]}"""))
        // Stage 3 Metrics
        engine.enqueueTokens(listOf("""{"objectivityScore": 100, "logicScore": 100, "evidenceQuality": 100, "credibilityScore": 100, "credibility": "G"}"""))
        // Stage 4 Fallacies
        engine.enqueueTokens(listOf("#### 1. FallacyName\n* **Instance:** Quote\n* **Analysis:** Deconstruction"))
        // Stage 5 Socratic
        engine.enqueueTokens(listOf("""{"socraticQuestions": ["Question 1"]}"""))

        // Using a dedicated scope for the test to avoid interference
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val testScope = CoroutineScope(testDispatcher + Job())
        
        val service = AndroidInferenceService(engine, FakeModelRepository(), testScope, "mockContext", testDispatcher)
        val repository = MockRepository()
        val coordinator = AnalysisCoordinator(service, repository, testScope)
        
        coordinator.startAnalysis("Short article")
        
        advanceUntilIdle()
        
        val state = coordinator.state.value
        assertTrue(state is InferenceState.Complete, "Pipeline should finish. Final state: $state")
        
        val result = (state as InferenceState.Complete).result
        assertEquals("Stage 1 Summary", result.summary)
        assertEquals(1, result.fallacies.size)
        assertEquals(1, repository.savedCount)
        
        testScope.cancel()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun testLoadResult() = runTest {
        val engine = MockLlmEngine()
        val service = AndroidInferenceService(engine, FakeModelRepository(), backgroundScope, "mockContext", StandardTestDispatcher(testScheduler))
        val coordinator = AnalysisCoordinator(service, MockRepository(), backgroundScope)
        
        val mockResult = AnalysisResult(
            summary = "Cached Summary",
            objectivityScore = 80,
            logicScore = 80,
            evidenceQuality = 80,
            credibilityScore = 80,
            credibility = "High",
            primaryStrength = "Strength",
            observationArea = "Area"
        )
        val saved = SavedAnalysis("id1", 0L, "Article text", mockResult)
        
        coordinator.loadResult(saved.originalArticleText, saved.analysisResult)
        
        val state = coordinator.state.value
        assertTrue(state is InferenceState.Complete)
        assertEquals("Cached Summary", (state as InferenceState.Complete).result.summary)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun testStateTransitions() = runTest {
        val engine = MockLlmEngine().apply {
            mockTokens = listOf("""{"summary": "Summary", "objectivityScore": 100, "logicScore": 100, "evidenceQuality": 100, "credibilityScore": 100, "credibility": "G", "primaryStrength": "S", "observationArea": "A"}""")
            streamDelay = 100
        }
        
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val testScope = CoroutineScope(testDispatcher + Job())
        
        val service = AndroidInferenceService(engine, FakeModelRepository(), testScope, "mockContext", testDispatcher)
        val coordinator = AnalysisCoordinator(service, repository = MockRepository(), scope = testScope)
        
        assertEquals(InferenceState.Idle, coordinator.state.value)
        
        coordinator.startAnalysis("Transition test")
        
        // Use advanceTimeBy to ensure the launch has time to reach the first assignment
        advanceTimeBy(10)
        
        val currentState = coordinator.state.value
        assertTrue(currentState is InferenceState.Thinking, "Should be in Thinking state but was $currentState")
        
        advanceUntilIdle()
        assertTrue(coordinator.state.value is InferenceState.Complete)
        
        testScope.cancel()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun testAudioAnalysisPipeline() = runTest {
        val engine = MockLlmEngine()
        
        // Setup expected responses:
        // 1. Chunk 1 Observation
        engine.enqueueTokens(listOf("""{"timestamp": "00:00-00:25", "dominantTone": "Calm", "keyClaims": ["C1"], "fallacies": [], "objectivityScore": 90, "logicScore": 90}"""))
        // 2. Chunk 2 Observation
        engine.enqueueTokens(listOf("""{"timestamp": "00:20-00:40", "dominantTone": "Rushed", "keyClaims": ["C2"], "fallacies": [], "objectivityScore": 60, "logicScore": 70}"""))
        // 3. Final Synthesis
        engine.enqueueTokens(listOf("""{"summary": "Unified Audio Analysis", "objectivityScore": 75, "logicScore": 80, "evidenceQuality": 80, "credibilityScore": 80, "credibility": "Mixed", "primaryStrength": "None", "observationArea": "Tone"}"""))

        val testDispatcher = StandardTestDispatcher(testScheduler)
        val testScope = CoroutineScope(testDispatcher + Job())
        
        val service = AndroidInferenceService(engine, FakeModelRepository(), testScope, "mockContext", testDispatcher)
        val repository = MockRepository()
        val coordinator = AnalysisCoordinator(service, repository, testScope)
        
        // 40s of audio @ 16kHz 16bit mono = 40 * 16000 * 2 = 1,280,000 bytes
        val audioData = ByteArray(40 * 16000 * 2)
        coordinator.startAudioAnalysis(audioData)
        
        advanceUntilIdle()
        
        val state = coordinator.state.value
        assertTrue(state is InferenceState.Complete, "Pipeline should finish. Final state: $state")
        
        val result = (state as InferenceState.Complete).result
        assertEquals("Unified Audio Analysis", result.summary)
        
        // BUG 2 Fix: Verify persistence
        assertEquals(1, repository.savedCount, "Audio analysis should be saved to history")
        assertEquals("[Audio Analysis]", repository.lastSaved?.originalArticleText)
        
        testScope.cancel()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun testImageAnalysisPipeline() = runTest {
        val engine = MockLlmEngine()
        // Stage 0 Multimodal Transcription
        engine.enqueueTokens(listOf("""{"fullTranscript": "Image Analysis Result"}"""))
        // Stage 1 Summary
        engine.enqueueTokens(listOf("""{"summary": "Image Analysis Result", "objectivityScore": 85, "logicScore": 85, "evidenceQuality": 85, "credibilityScore": 85, "credibility": "Trustworthy", "primaryStrength": "Visual Evidence", "observationArea": "None"}"""))
        // Stage 2 Claims
        engine.enqueueTokens(listOf("""{"keyClaims": ["Claim 1"]}"""))
        // Stage 3 Metrics
        engine.enqueueTokens(listOf("""{"objectivityScore": 85, "logicScore": 85, "evidenceQuality": 85, "credibilityScore": 85, "credibility": "Trustworthy"}"""))
        // Stage 4 Fallacies
        engine.enqueueTokens(listOf("#### 1. FallacyName\n* **Instance:** Quote\n* **Analysis:** Deconstruction"))

        val testDispatcher = StandardTestDispatcher(testScheduler)
        val testScope = CoroutineScope(testDispatcher + Job())
        
        val service = AndroidInferenceService(engine, FakeModelRepository(), testScope, "mockContext", testDispatcher)
        val repository = MockRepository()
        val coordinator = AnalysisCoordinator(service, repository, testScope)
        
        coordinator.startImageAnalysis(ByteArray(100))
        
        advanceUntilIdle()
        
        val state = coordinator.state.value
        assertTrue(state is InferenceState.Complete)
        assertEquals("Image Analysis Result", (state as InferenceState.Complete).result.summary)
        
        // BUG 2 Fix: Verify persistence
        assertEquals(1, repository.savedCount, "Image analysis should be saved to history")
        assertTrue(repository.lastSaved?.originalArticleText?.contains("Image Analysis") == true)
        
        testScope.cancel()
    }
}
