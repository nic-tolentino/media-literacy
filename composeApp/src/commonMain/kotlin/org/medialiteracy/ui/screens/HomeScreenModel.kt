package org.medialiteracy.ui.screens

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.medialiteracy.domain.AnalysisRepository
import org.medialiteracy.domain.SavedAnalysis

class HomeScreenModel(
    private val repository: AnalysisRepository = AnalysisRepository.getInstance()
) : ScreenModel {

    val savedAnalyses: StateFlow<List<SavedAnalysis>> = repository.getSavedAnalyses()
        .stateIn(
            scope = screenModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun deleteAnalysis(id: String) {
        screenModelScope.launch {
            repository.deleteAnalysis(id)
        }
    }
}
