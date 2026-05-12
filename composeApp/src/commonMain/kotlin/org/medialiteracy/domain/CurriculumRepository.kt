package org.medialiteracy.domain

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.Json
import medialiteracy.composeapp.generated.resources.Res
import org.jetbrains.compose.resources.ExperimentalResourceApi

@OptIn(ExperimentalResourceApi::class)
class CurriculumRepository {
    private val _curriculum = MutableStateFlow<Curriculum?>(null)
    val curriculum: StateFlow<Curriculum?> = _curriculum

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun loadCurriculum() {
        if (_curriculum.value != null) return
        
        try {
            val curriculumBytes = Res.readBytes("files/curriculum.json")
            val curriculumString = curriculumBytes.decodeToString()
            _curriculum.value = json.decodeFromString<Curriculum>(curriculumString)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun isTacticSupported(tacticName: String): Boolean {
        val curr = _curriculum.value ?: return false
        val normalizedSearch = normalizeName(tacticName)
        
        return curr.categories.flatMap { it.tactics }.any { 
            val normalizedTitle = normalizeName(it.title)
            normalizedSearch.contains(normalizedTitle) || normalizedTitle.contains(normalizedSearch)
        }
    }

    fun getTactic(tacticName: String): Tactic? {
        val curr = _curriculum.value ?: return null
        val normalizedSearch = normalizeName(tacticName)
        
        return curr.categories.flatMap { it.tactics }.find { 
            val normalizedTitle = normalizeName(it.title)
            normalizedSearch.contains(normalizedTitle) || normalizedTitle.contains(normalizedSearch)
        }
    }

    private fun normalizeName(name: String): String {
        return name.lowercase()
            .replace(Regex("[^a-z0-9]"), "")
            .trim()
    }
}
