package org.medialiteracy.domain

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class InferenceServiceTest {

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun testCommandSerialization() = runTest {
        val engine = MockLlmEngine()
        val service = AndroidInferenceService(engine, FakeModelRepository(), backgroundScope, "mockContext", StandardTestDispatcher(testScheduler))
        
        val results1 = service.execute(InferenceCommand.Chat("Hello 1")).toList()
        val results2 = service.execute(InferenceCommand.Chat("Hello 2")).toList()
        
        assertEquals(listOf("Token1", "Token2"), results1)
        assertEquals(listOf("Token1", "Token2"), results2)
        assertEquals("Hello 2", engine.lastPrompt)
        assertTrue(engine.initializeCount > 0)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun testCancellation() = runTest {
        val engine = MockLlmEngine().apply { streamDelay = 100 }
        val service = AndroidInferenceService(engine, FakeModelRepository(), backgroundScope, "mockContext", StandardTestDispatcher(testScheduler))
        
        val results = mutableListOf<String>()
        val job = launch {
            service.execute(InferenceCommand.Chat("Slow Prompt")).collect { results.add(it) }
        }
        
        // Wait for it to start
        advanceTimeBy(50)
        service.execute(InferenceCommand.CancelCurrent).firstOrNull()
        
        job.join()
        assertTrue(results.isEmpty())
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun testReset() = runTest {
        val engine = MockLlmEngine()
        val service = AndroidInferenceService(engine, FakeModelRepository(), backgroundScope, "mockContext", StandardTestDispatcher(testScheduler))
        
        service.execute(InferenceCommand.Reset).firstOrNull()
        assertEquals(1, engine.closeCount)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun testTokenBudgetEnforcement() = runTest {
        val engine = MockLlmEngine()
        val service = AndroidInferenceService(engine, FakeModelRepository(), backgroundScope, "mockContext", StandardTestDispatcher(testScheduler))
        
        // Exceed budget (3500 tokens). Heuristic is length / 3.5.
        engine.mockTokens = listOf("A".repeat(4000 * 4)) 
        service.execute(InferenceCommand.Analyze("large_id", "prompt")).toList()
        
        // Next command should trigger handleReset() in handleChat
        service.execute(InferenceCommand.Chat("Next turn")).toList()
        
        assertTrue(engine.closeCount >= 1, "Engine should have been closed/reset due to budget. Close count: ${engine.closeCount}")
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun testErrorRecovery() = runTest {
        val engine = object : MockLlmEngine() {
            var shouldCrash = true
            override fun generatePersistentStreaming(prompt: String, isFirstTurn: Boolean): Flow<String> = flow {
                if (shouldCrash) {
                    shouldCrash = false
                    throw RuntimeException("Native Crash")
                }
                emit("Recovered")
            }
        }
        val service = AndroidInferenceService(engine, FakeModelRepository(), backgroundScope, "mockContext", StandardTestDispatcher(testScheduler))
        
        // Execute a command that crashes
        val results = mutableListOf<String>()
        try {
            service.execute(InferenceCommand.Analyze("id", "crash me")).collect { results.add(it) }
        } catch (e: Exception) {
            // Expected
        }
        
        assertTrue(results.isEmpty(), "Results should be empty after crash")
        
        // Actor must still be alive for subsequent commands
        val healthyResults = service.execute(InferenceCommand.Chat("Healthy turn")).toList()
        assertEquals(listOf("Recovered"), healthyResults, "Actor should recover and process next command")
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun testMultimodalCommand() = runTest {
        val engine = MockLlmEngine()
        val service = AndroidInferenceService(engine, FakeModelRepository(), backgroundScope, "mockContext", StandardTestDispatcher(testScheduler))
        
        // Test Audio command
        val audioData = ByteArray(1024)
        val results = service.execute(
            InferenceCommand.AnalyzeMultimodal(
                type = MultimodalType.AUDIO,
                data = audioData,
                prompt = "Analyze tone"
            )
        ).toList()
        
        assertEquals(listOf("Token1", "Token2"), results)
        // Check budget increment: Audio adds 625 tokens
        // updateTokenEstimate is also called for Token1/Token2. 
        // MockTokens total length is 12 (6+6). chars / 3.5 = ~3 tokens.
        // Total should be ~628
        val metrics = service.metrics.first()
        assertTrue(metrics.totalTokensEstimated >= 625, "Metrics should reflect audio budget overhead")
    }
}
