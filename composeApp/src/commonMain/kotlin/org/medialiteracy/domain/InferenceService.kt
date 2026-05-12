package org.medialiteracy.domain

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Commands sent to the InferenceService for serialized execution.
 */
sealed class InferenceCommand {
    data class Analyze(val id: String, val prompt: String) : InferenceCommand()
    data class Chat(val message: String) : InferenceCommand()
    data class AnalyzeMultimodal(
        val type: MultimodalType, 
        val data: ByteArray, 
        val prompt: String,
        val isFirstTurn: Boolean = true
    ) : InferenceCommand() {
        override fun toString(): String = "AnalyzeMultimodal(type=$type, dataSize=${data.size}, promptLength=${prompt.length}, isFirstTurn=$isFirstTurn)"
    }
    object CancelCurrent : InferenceCommand() {
        override fun toString(): String = "CancelCurrent"
    }
    object ReleaseResources : InferenceCommand() {
        override fun toString(): String = "ReleaseResources"
    }
    object Reset : InferenceCommand() {
        override fun toString(): String = "Reset"
    }
    data class Prime(val context: String) : InferenceCommand()
}

enum class MultimodalType {
    IMAGE, AUDIO
}

/**
 * Performance metrics for a single inference turn.
 */
data class InferenceMetrics(
    val timeToFirstToken: Long = 0,
    val tokensPerSecond: Double = 0.0,
    val totalTokensEstimated: Int = 0,
    val teardownDuration: Long = 0
)

/**
 * Internal states of the Inference Actor.
 */
enum class EngineInternalState {
    Idle,
    Initializing,
    PreFilling,
    Generating,
    Teardown,
    Error
}

/**
 * The Centralized Inference Actor interface.
 * Serializes all access to the native LLM engine via a command queue.
 */
interface InferenceService {
    /** The current state of the engine. */
    val state: StateFlow<EngineInternalState>

    /** Reference to the model management repository. */
    val modelRepository: ModelRepository

    /** Re-initializes the engine, usually after a model download. */
    suspend fun resetEngine()

    /** Telemetry for the latest turn. */
    val metrics: Flow<InferenceMetrics>

    /** 
     * Submits a command for execution and returns a stream of tokens.
     * The flow completes when the command is finished.
     */
    fun execute(command: InferenceCommand): Flow<String>
}

/**
 * Global registry for core domain services.
 * In a larger app, this would be replaced by Koin or Dagger/Hilt.
 */
object ServiceRegistry {
    private var _inferenceService: InferenceService? = null
    val inferenceService: InferenceService get() = _inferenceService ?: throw Exception("InferenceService not initialized")

    private var _analysisCoordinator: AnalysisCoordinator? = null
    val analysisCoordinator: AnalysisCoordinator get() = _analysisCoordinator ?: throw Exception("AnalysisCoordinator not initialized")

    private var _curriculumRepository: CurriculumRepository? = null
    val curriculumRepository: CurriculumRepository get() = _curriculumRepository ?: throw Exception("CurriculumRepository not initialized")

    fun init(service: InferenceService, coordinator: AnalysisCoordinator, curriculum: CurriculumRepository) {
        _inferenceService = service
        _analysisCoordinator = coordinator
        _curriculumRepository = curriculum
    }
}
