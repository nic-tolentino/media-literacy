package org.medialiteracy

import android.app.Application
import org.medialiteracy.domain.LlmEngine
import org.medialiteracy.domain.initializeModelManager

class GemmaApp : Application() {
    override fun onCreate() {
        super.onCreate()
        LlmEngine.initialize(this)
        initializeModelManager(this)
    }
}
