package org.medialiteracy

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.*
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.transitions.SlideTransition
import org.medialiteracy.domain.SettingsRepository
import org.medialiteracy.domain.ThemeMode
import org.medialiteracy.ui.AppTheme
import org.medialiteracy.ui.screens.OnboardingScreen
import org.medialiteracy.ui.tabs.TabHost
import org.medialiteracy.ui.LocalRootNavigator

@Composable
fun App() {
    val settingsRepository = remember { SettingsRepository.getInstance() }
    val themeMode by settingsRepository.getThemeMode().collectAsState(ThemeMode.SYSTEM)
    val hasCompletedOnboarding by settingsRepository.hasCompletedOnboarding().collectAsState(null)
    
    val useDarkTheme = when (themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }

    AppTheme(useDarkTheme = useDarkTheme) {
        if (hasCompletedOnboarding != null) {
            val initialScreen = if (hasCompletedOnboarding == true) TabHost() else OnboardingScreen()
            Navigator(initialScreen) { navigator ->
                CompositionLocalProvider(LocalRootNavigator provides navigator) {
                    SlideTransition(navigator)
                }
            }
        }
    }
}