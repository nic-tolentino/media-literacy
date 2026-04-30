package org.medialiteracy

import android.app.Application
import org.medialiteracy.domain.LlmEngine
import org.medialiteracy.domain.initializeModelManager
import org.medialiteracy.domain.initDataStore
import java.io.File
import kotlinx.coroutines.*
import org.medialiteracy.domain.*

class GemmaApp : Application() {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreate() {
        super.onCreate()
        
        initDataStore { 
            File(filesDir, "saved_analyses.preferences_pb").absolutePath 
        }

        val engine = LlmEngine.getInstance()
        val repository = AnalysisRepository.getInstance()
        
        // Load the 3.6GB model asynchronously so we don't crash with an ANR
        appScope.launch(Dispatchers.IO) {
            engine.initialize(this@GemmaApp)
        }
        
        // Initialize the new Inference Service and Coordinator
        val inferenceService = AndroidInferenceService(engine, appScope, this)
        val analysisCoordinator = AnalysisCoordinator(inferenceService, repository, appScope)
        ServiceRegistry.init(inferenceService, analysisCoordinator)
        
        initializeModelManager(this)
    }
}
