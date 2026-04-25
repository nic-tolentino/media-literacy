package org.medialiteracy

import android.app.Application
import org.medialiteracy.domain.LlmEngine
import org.medialiteracy.domain.initializeModelManager

class GemmaApp : Application() {
    override fun onCreate() {
        super.onCreate()
        LlmEngine.getInstance().initialize(this)
        initializeModelManager(this)
    }
}
