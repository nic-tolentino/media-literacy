package org.medialiteracy

import android.app.Application
import org.medialiteracy.domain.LlmEngine
import org.medialiteracy.domain.initializeModelManager
import org.medialiteracy.domain.initDataStore
import java.io.File
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers

class GemmaApp : Application() {
    override fun onCreate() {
        super.onCreate()
        
        // Load the 3.6GB model asynchronously so we don't crash with an ANR
        GlobalScope.launch(Dispatchers.IO) {
            LlmEngine.getInstance().initialize(this@GemmaApp)
        }
        
        initializeModelManager(this)
        initDataStore { 
            File(filesDir, "saved_analyses.preferences_pb").absolutePath 
        }
    }
}
