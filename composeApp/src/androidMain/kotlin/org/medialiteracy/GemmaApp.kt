package org.medialiteracy

import android.app.Application
import org.medialiteracy.domain.LlmEngine
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
        
        initModelRepository(this)
        val modelRepository = PlatformModelRepository()
        val inferenceService = AndroidInferenceService(engine, modelRepository, appScope, this)
        val analysisCoordinator = AnalysisCoordinator(inferenceService, repository, appScope)
        ServiceRegistry.init(inferenceService, analysisCoordinator)
        
        DeviceCapabilityChecker.init(this)
        
        // Only initialize engine if a model is already downloaded
        // This avoids noisy error logs and unnecessary resource allocation for new users
        appScope.launch {
            if (ServiceRegistry.inferenceService.modelRepository.installedModelPath() != null) {
                inferenceService.resetEngine()
            }
        }
    }
}
