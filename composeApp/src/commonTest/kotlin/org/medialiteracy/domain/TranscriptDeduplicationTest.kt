package org.medialiteracy.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow

class TranscriptDeduplicationTest {

    // Dummy dependencies for AnalysisCoordinator instance
    private val coordinator = AnalysisCoordinator(
        inferenceService = object : InferenceService {
            override val state: StateFlow<EngineInternalState> = MutableStateFlow(EngineInternalState.Idle)
            override val metrics: Flow<InferenceMetrics> = emptyFlow()
            override fun execute(command: InferenceCommand) = error("Not needed")
        },
        repository = object : AnalysisRepository {
            override suspend fun saveAnalysis(analysis: SavedAnalysis) {}
            override fun getSavedAnalyses() = emptyFlow<List<SavedAnalysis>>()
            override suspend fun deleteAnalysis(id: String) {}
        },
        scope = MainScope()
    )

    @Test
    fun testExactOverlap() {
        val prevLong = "We are now starting the official test of the emergency broadcast system."
        val currLong = "the official test of the emergency broadcast system. Please stay calm."
        
        val result = coordinator.removeTranscriptOverlap(prevLong, currLong)
        assertEquals("Please stay calm.", result)
    }

    @Test
    fun testPunctuationAgnosticMatch() {
        val prev = "The quick brown fox, jumps over the lazy dog."
        val curr = "jumps over the lazy dog! And then he ran away."
        
        val result = coordinator.removeTranscriptOverlap(prev, curr)
        assertEquals("And then he ran away.", result)
    }

    @Test
    fun testMatchWithExtraWordsAtStartOfCurr() {
        val prev = "one two three four five six seven eight"
        val curr = "and four five six seven eight nine ten"
        
        val result = coordinator.removeTranscriptOverlap(prev, curr)
        assertEquals("nine ten", result)
    }

    @Test
    fun testDuplicatePhrases() {
        val prev = "This is a test test test test test and again a test test test test test"
        val curr = "test test test test test end of recording"
        
        val result = coordinator.removeTranscriptOverlap(prev, curr)
        assertEquals("end of recording", result)
    }

    @Test
    fun testNoOverlap() {
        val prev = "The first segment ends here."
        val curr = "Completely different text in the second segment."
        
        val result = coordinator.removeTranscriptOverlap(prev, curr)
        assertEquals(curr, result)
    }

    @Test
    fun testFullOverlap() {
        val prev = "This entire second segment is already in the first one."
        val curr = "already in the first one."
        
        val result = coordinator.removeTranscriptOverlap(prev, curr)
        assertEquals("", result)
    }

    @Test
    fun testTheUserReportedCase() {
        val prev = "Artificially producing a fully functional god who possesses all of the divine traits of original. We now have the ability to conduct hands-on research on Lord."
        val curr = "the ability to conduct hands-on research on the lord we cannot wait to get our hands dirty"
        
        val result = coordinator.removeTranscriptOverlap(prev, curr)
        assertEquals("lord we cannot wait to get our hands dirty", result)
    }

    @Test
    fun testFuzzyOverlapWithSingleWordDifference() {
        // One word "fox" vs "cat" in the middle of an otherwise identical overlap.
        // As long as there is a 5-word window that matches, it will anchor and skip.
        val prev = "We saw The quick brown fox jumps over the lazy dog."
        val curr = "The quick brown cat jumps over the lazy dog. And it was cute."
        
        val result = coordinator.removeTranscriptOverlap(prev, curr)
        // Match found at "jumps over the lazy dog".
        // i in prev: index of "jumps" is 5.
        // prevWords.size is 11.
        // prevWords.size - i = 6 words to skip after match start.
        // j in curr: index of "jumps" is 4.
        // skipCount = 4 + 6 = 10.
        // Result starts after word 10 in curr.
        assertEquals("And it was cute.", result)
    }

    @Test
    fun testIncompleteWindowMatch() {
        // If there are NO 5-word sequences that match, it should fallback to returning curr.
        val prev = "word1 word2 word3 word4 word5"
        val curr = "word1 word2 wordX word4 word5 word6"
        
        val result = coordinator.removeTranscriptOverlap(prev, curr)
        assertEquals(curr, result)
    }
}
