package org.medialiteracy.ui.screens

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import org.medialiteracy.domain.Curriculum
import org.medialiteracy.domain.ResourcePortal
import org.medialiteracy.domain.ServiceRegistry
import org.jetbrains.compose.resources.ExperimentalResourceApi
import medialiteracy.composeapp.generated.resources.Res

class LearningScreenModel : ScreenModel {
    private val repository = ServiceRegistry.curriculumRepository
    val curriculum: StateFlow<Curriculum?> = repository.curriculum

    private val _resourcePortal = MutableStateFlow<ResourcePortal?>(null)
    val resourcePortal: StateFlow<ResourcePortal?> = _resourcePortal

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val json = Json { ignoreUnknownKeys = true }

    init {
        loadData()
    }

    @OptIn(ExperimentalResourceApi::class)
    private fun loadData() {
        screenModelScope.launch {
            try {
                _isLoading.value = true
                
                // Ensure curriculum is loaded in repo
                repository.loadCurriculum()

                // Load Resource Portal
                val resourceBytes = Res.readBytes("files/media_literacy_resources.json")
                val resourceString = resourceBytes.decodeToString()
                _resourcePortal.value = json.decodeFromString<ResourcePortal>(resourceString)

            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isLoading.value = false
            }
        }
    }
}
