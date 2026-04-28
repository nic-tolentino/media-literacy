package org.medialiteracy.domain

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

interface AnalysisRepository {
    fun getSavedAnalyses(): Flow<List<SavedAnalysis>>
    suspend fun saveAnalysis(analysis: SavedAnalysis)
    suspend fun deleteAnalysis(id: String)
    
    companion object {
        fun getInstance(): AnalysisRepository = DataStoreAnalysisRepository()
    }
}

class DataStoreAnalysisRepository(
    private val dataStore: DataStore<Preferences> = createDataStore()
) : AnalysisRepository {

    private val historyKey = stringPreferencesKey("analysis_history")
    private val json = Json { ignoreUnknownKeys = true }

    override fun getSavedAnalyses(): Flow<List<SavedAnalysis>> {
        return dataStore.data.map { preferences ->
            val jsonString = preferences[historyKey] ?: "[]"
            try {
                json.decodeFromString<List<SavedAnalysis>>(jsonString)
            } catch (e: Exception) {
                emptyList()
            }
        }
    }

    override suspend fun saveAnalysis(analysis: SavedAnalysis) {
        dataStore.edit { preferences ->
            val currentJson = preferences[historyKey] ?: "[]"
            val currentList = try {
                json.decodeFromString<List<SavedAnalysis>>(currentJson).toMutableList()
            } catch (e: Exception) {
                mutableListOf()
            }
            
            // Update if exists, else add
            val index = currentList.indexOfFirst { it.id == analysis.id }
            if (index != -1) {
                currentList[index] = analysis
            } else {
                currentList.add(0, analysis) // Newest first
            }
            
            preferences[historyKey] = json.encodeToString(currentList)
        }
    }

    override suspend fun deleteAnalysis(id: String) {
        dataStore.edit { preferences ->
            val currentJson = preferences[historyKey] ?: "[]"
            val currentList = try {
                json.decodeFromString<List<SavedAnalysis>>(currentJson).toMutableList()
            } catch (e: Exception) {
                mutableListOf()
            }
            
            currentList.removeAll { it.id == id }
            preferences[historyKey] = json.encodeToString(currentList)
        }
    }
}
