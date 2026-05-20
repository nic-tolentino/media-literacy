package org.medialiteracy.domain

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*
import kotlinx.datetime.Clock

/**
 * Internal command wrapper to bridge the execute() flow with the consumer loop.
 */
private data class InternalCommand(
    val command: InferenceCommand,
    val tokenChannel: kotlinx.coroutines.channels.SendChannel<String>? = null
)

/**
 * Android implementation of the Inference Actor.
 * serializes all LLM interactions using a single-consumer Coroutine loop.
 */
class AndroidInferenceService(
    private val engine: LlmEngine,
    override val modelRepository: ModelRepository,
    private val scope: CoroutineScope,
    private val androidContext: Any, // Required for engine re-initialization on Reset
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default
) : InferenceService {

    // Bounded channel to prevent memory overflow during rapid navigation (spammed commands)
    private val commandQueue = Channel<InternalCommand>(10, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    
    private val _state = MutableStateFlow(EngineInternalState.Initializing)
    override val state: StateFlow<EngineInternalState> = _state.asStateFlow()

    private val _metrics = MutableStateFlow(InferenceMetrics())
    override val metrics: StateFlow<InferenceMetrics> = _metrics.asStateFlow()

    override suspend fun resetEngine() {
        try {
            // Queue a reset command to ensure the engine re-initializes on the next turn
            execute(InferenceCommand.Reset).collect { /* wait for completion */ }
        } catch (e: Exception) {
            Logger.e("InferenceService", "Failed to reset engine: ${e.message}")
        }
    }

    private var activeGenerationJob: Job? = null
    private var estimatedTokensUsed: Int = 0
    private val tokenBudgetLimit = 3500 

    init {
        startConsumerLoop()
    }

    override fun execute(command: InferenceCommand): Flow<String> = channelFlow {
        // High-priority interrupts bypass the queue logic for immediate responsiveness
        if (command is InferenceCommand.CancelCurrent || command is InferenceCommand.Reset) {
            activeGenerationJob?.cancel()
        }
        
        commandQueue.send(InternalCommand(command, this))
        awaitClose { /* Scope closure handles cleanup */ }
    }

    private fun startConsumerLoop() {
        // Close the channel when the scope is cancelled to terminate the loop
        scope.coroutineContext[Job]?.invokeOnCompletion {
            commandQueue.close()
        }

        // Run on the specified dispatcher
        scope.launch(dispatcher) {
            // Engine is lazily initialized on the first analysis command
            // or explicitly via resetEngine() call.
            _state.value = EngineInternalState.Idle

            for (internal in commandQueue) {
                ensureActive()
                processCommand(internal)
            }
        }
    }

    private suspend fun processCommand(internal: InternalCommand) {
        val command = internal.command
        val tokens = internal.tokenChannel
        
        try {
            // Ensure engine is initialized before processing inference commands
            if (command is InferenceCommand.Analyze || 
                command is InferenceCommand.Chat || 
                command is InferenceCommand.Prime || 
                command is InferenceCommand.AnalyzeMultimodal) {
                engine.initialize(androidContext)
            }
            
            Logger.d("InferenceService", "Processing command: $command")
            
            when (command) {
                is InferenceCommand.Analyze -> handleAnalyze(command, tokens)
                is InferenceCommand.Chat -> handleChat(command, tokens)
                is InferenceCommand.AnalyzeMultimodal -> handleMultimodal(command, tokens)
                is InferenceCommand.CancelCurrent -> {
                    handleCancel()
                    tokens?.close()
                }
                is InferenceCommand.ReleaseResources -> {
                    handleRelease()
                    tokens?.close()
                }
                is InferenceCommand.Reset -> {
                    handleReset()
                    tokens?.close()
                }
                is InferenceCommand.Prime -> {
                    handlePrime(command)
                    tokens?.close()
                }
            }
        } catch (e: Exception) {
            Logger.e("InferenceService", "Critical error in actor loop: ${e.message}")
            handleInferenceError(e, tokens)
        }
    }

    private suspend fun handleAnalyze(command: InferenceCommand.Analyze, tokens: kotlinx.coroutines.channels.SendChannel<String>?) {
        val startTime = Clock.System.now().toEpochMilliseconds()
        var firstTokenTime: Long? = null
        var totalChunks = 0

        var turnTokens = 0
        supervisorScope {
            activeGenerationJob = launch {
                try {
                    _state.value = EngineInternalState.Initializing
                    estimatedTokensUsed = 0
                    _state.value = EngineInternalState.Generating
                    
                    engine.generatePersistentStreaming(command.prompt, isFirstTurn = true)
                        .collect { token ->
                            if (firstTokenTime == null) {
                                firstTokenTime = Clock.System.now().toEpochMilliseconds()
                            }
                            // check for cancellation at each token
                            ensureActive()
                            val count = updateTokenEstimate(token)
                            turnTokens += count
                            tokens?.send(token)
                            totalChunks++
                        }
                    _state.value = EngineInternalState.Idle
                    tokens?.close() // Normal completion
                } catch (e: Exception) {
                    handleInferenceError(e, tokens)
                }
            }
            // Wait for the generation to finish (or be cancelled) before processing the next command
            try {
                activeGenerationJob?.join()
            } catch (e: CancellationException) {
                Logger.d("InferenceService", "Generation job cancelled (join interrupted).")
            }
        }
        
        val endTime = Clock.System.now().toEpochMilliseconds()
        val durationSec = (endTime - (firstTokenTime ?: startTime)) / 1000.0
        
        // Finalize metrics for this turn
        _metrics.value = InferenceMetrics(
            timeToFirstToken = firstTokenTime?.let { it - startTime } ?: 0,
            tokensPerSecond = if (durationSec > 0) turnTokens / durationSec else 0.0,
            totalTokensEstimated = estimatedTokensUsed
        )
    }

    private suspend fun handleMultimodal(command: InferenceCommand.AnalyzeMultimodal, tokens: kotlinx.coroutines.channels.SendChannel<String>?) {
        val startTime = Clock.System.now().toEpochMilliseconds()
        var firstTokenTime: Long? = null
        var turnTokens = 0

        // Track budget for multimodal embeddings
        if (command.type == MultimodalType.AUDIO) {
            // Audio uses ~25 tokens/sec. 25s chunk = 625 tokens
            estimatedTokensUsed += 625 
        }

        supervisorScope {
            activeGenerationJob = launch {
                try {
                    _state.value = EngineInternalState.Generating
                    val multimodalContent = MultimodalContent(
                        text = command.prompt,
                        image = if (command.type == MultimodalType.IMAGE) command.data else null,
                        audio = if (command.type == MultimodalType.AUDIO) command.data else null
                    )

                    engine.generateMultimodalStreaming(multimodalContent, command.isFirstTurn)
                        .collect { token ->
                            if (firstTokenTime == null) {
                                firstTokenTime = Clock.System.now().toEpochMilliseconds()
                            }
                            ensureActive()
                            val count = updateTokenEstimate(token)
                            turnTokens += count
                            tokens?.send(token)
                        }
                    _state.value = EngineInternalState.Idle
                    tokens?.close()
                } catch (e: Exception) {
                    handleInferenceError(e, tokens)
                }
            }
            try {
                activeGenerationJob?.join()
            } catch (e: CancellationException) {}
        }

        val endTime = Clock.System.now().toEpochMilliseconds()
        val durationSec = (endTime - (firstTokenTime ?: startTime)) / 1000.0
        
        _metrics.value = InferenceMetrics(
            timeToFirstToken = firstTokenTime?.let { it - startTime } ?: 0,
            tokensPerSecond = if (durationSec > 0) turnTokens / durationSec else 0.0,
            totalTokensEstimated = estimatedTokensUsed
        )
    }

    private suspend fun handleChat(command: InferenceCommand.Chat, tokens: kotlinx.coroutines.channels.SendChannel<String>?) {
        if (estimatedTokensUsed > tokenBudgetLimit) {
            Logger.w("InferenceService", "Token budget exceeded ($estimatedTokensUsed). Forcing reset.")
            handleReset()
            tokens?.close()
            return // Stop processing this command
        }

        val startTime = Clock.System.now().toEpochMilliseconds()
        var firstTokenTime: Long? = null

        var turnTokens = 0
        supervisorScope {
            activeGenerationJob = launch {
                try {
                    _state.value = EngineInternalState.Generating
                    engine.generatePersistentStreaming(command.message, isFirstTurn = false)
                        .collect { token ->
                            if (firstTokenTime == null) {
                                firstTokenTime = Clock.System.now().toEpochMilliseconds()
                            }
                            ensureActive()
                            val count = updateTokenEstimate(token)
                            turnTokens += count
                            tokens?.send(token)
                        }
                    _state.value = EngineInternalState.Idle
                    tokens?.close()
                } catch (e: Exception) {
                    handleInferenceError(e, tokens)
                }
            }
            try {
                activeGenerationJob?.join()
            } catch (e: CancellationException) {
                Logger.d("InferenceService", "Chat job cancelled (join interrupted).")
            }
        }

        val endTime = Clock.System.now().toEpochMilliseconds()
        val durationSec = (endTime - (firstTokenTime ?: startTime)) / 1000.0

        // Finalize metrics for this turn
        _metrics.value = InferenceMetrics(
            timeToFirstToken = firstTokenTime?.let { it - startTime } ?: 0,
            tokensPerSecond = if (durationSec > 0) turnTokens / durationSec else 0.0,
            totalTokensEstimated = estimatedTokensUsed
        )
    }

    private suspend fun handleCancel() {
        _state.value = EngineInternalState.Teardown
        // activeGenerationJob is already cancelled by execute()
        withTimeoutOrNull(5000) {
            engine.closeSession()
        }
        _state.value = EngineInternalState.Idle
    }

    private suspend fun handleRelease() {
        _state.value = EngineInternalState.Teardown
        engine.close() 
        _state.value = EngineInternalState.Idle
    }

    private suspend fun handlePrime(command: InferenceCommand.Prime) {
        // Priming creates a new conversation with the article text
        // but we don't stream the response back to a channel
        supervisorScope {
            activeGenerationJob = launch {
                _state.value = EngineInternalState.Initializing
                // Silent priming - we collect tokens but don't send them anywhere
                engine.generatePersistentStreaming(
                    prompt = "Context: ${command.context}\n\nPlease acknowledge with 'Ready'.", 
                    isFirstTurn = true
                ).collect { /* silent */ }
                _state.value = EngineInternalState.Idle
            }
            try {
                activeGenerationJob?.join()
            } catch (e: CancellationException) {}
        }
    }

    private suspend fun handleReset() {
        _state.value = EngineInternalState.Teardown
        Logger.i("InferenceService", "Resetting engine...")
        engine.close()
        // Properly re-initialize after close
        engine.initialize(androidContext)
        estimatedTokensUsed = 0
        _state.value = EngineInternalState.Idle
    }

    private fun handleInferenceError(e: Exception, tokens: kotlinx.coroutines.channels.SendChannel<String>?) {
        if (e is CancellationException) {
            Logger.d("InferenceService", "Generation cancelled.")
            tokens?.close(e)
        } else {
            Logger.e("InferenceService", "Inference error: ${e.message}")
            _state.value = EngineInternalState.Error
            tokens?.close(e)
        }
    }

    private fun updateTokenEstimate(token: String): Int {
        val count = token.length / 4 // Simple heuristic: 4 chars per token
        estimatedTokensUsed += count
        return count
    }
}
