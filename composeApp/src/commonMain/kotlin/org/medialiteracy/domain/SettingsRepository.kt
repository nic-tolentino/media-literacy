package org.medialiteracy.domain

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class ThemeMode {
    LIGHT, DARK, SYSTEM
}

interface SettingsRepository {
    fun getThemeMode(): Flow<ThemeMode>
    suspend fun setThemeMode(mode: ThemeMode)

    fun getSelectedVariant(): Flow<ModelVariant?>
    suspend fun setSelectedVariant(variant: ModelVariant)

    fun getInstalledModelHash(): Flow<String?>
    suspend fun setInstalledModelHash(hash: String)

    fun isUpgradeNudgeDismissed(): Flow<Boolean>
    suspend fun setUpgradeNudgeDismissed(dismissed: Boolean)

    fun hasCompletedOnboarding(): Flow<Boolean>
    suspend fun setHasCompletedOnboarding(completed: Boolean)
    
    companion object {
        private var instance: SettingsRepository? = null
        fun getInstance(): SettingsRepository = instance ?: synchronized(this) {
            instance ?: DataStoreSettingsRepository().also { instance = it }
        }
    }
}

class DataStoreSettingsRepository(
    private val dataStore: DataStore<Preferences> = createDataStore()
) : SettingsRepository {

    private val themeModeKey = stringPreferencesKey("theme_mode")
    private val selectedVariantKey = stringPreferencesKey("selected_model_variant")
    private val installedModelHashKey = stringPreferencesKey("installed_model_hash")
    private val upgradeNudgeDismissedKey = androidx.datastore.preferences.core.booleanPreferencesKey("upgrade_nudge_dismissed")
    private val onboardingCompletedKey = androidx.datastore.preferences.core.booleanPreferencesKey("onboarding_completed")

    override fun getThemeMode(): Flow<ThemeMode> {
        return dataStore.data.map { preferences ->
            val modeString = preferences[themeModeKey] ?: ThemeMode.SYSTEM.name
            try {
                ThemeMode.valueOf(modeString)
            } catch (e: Exception) {
                ThemeMode.SYSTEM
            }
        }
    }

    override suspend fun setThemeMode(mode: ThemeMode) {
        dataStore.edit { preferences ->
            preferences[themeModeKey] = mode.name
        }
    }

    override fun getSelectedVariant(): Flow<ModelVariant?> {
        return dataStore.data.map { preferences ->
            val variantString = preferences[selectedVariantKey] ?: return@map null
            try {
                ModelVariant.valueOf(variantString)
            } catch (e: Exception) {
                null
            }
        }
    }

    override suspend fun setSelectedVariant(variant: ModelVariant) {
        dataStore.edit { preferences ->
            preferences[selectedVariantKey] = variant.name
        }
    }

    override fun getInstalledModelHash(): Flow<String?> {
        return dataStore.data.map { preferences ->
            preferences[installedModelHashKey]
        }
    }

    override suspend fun setInstalledModelHash(hash: String) {
        dataStore.edit { preferences ->
            preferences[installedModelHashKey] = hash
        }
    }

    override fun isUpgradeNudgeDismissed(): Flow<Boolean> {
        return dataStore.data.map { preferences ->
            preferences[upgradeNudgeDismissedKey] ?: false
        }
    }

    override suspend fun setUpgradeNudgeDismissed(dismissed: Boolean) {
        dataStore.edit { preferences ->
            preferences[upgradeNudgeDismissedKey] = dismissed
        }
    }

    override fun hasCompletedOnboarding(): Flow<Boolean> {
        return dataStore.data.map { preferences ->
            preferences[onboardingCompletedKey] ?: false
        }
    }

    override suspend fun setHasCompletedOnboarding(completed: Boolean) {
        dataStore.edit { preferences ->
            preferences[onboardingCompletedKey] = completed
        }
    }
}
