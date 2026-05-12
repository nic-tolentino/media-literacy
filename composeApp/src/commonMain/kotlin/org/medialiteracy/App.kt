package org.medialiteracy

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.*
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.transitions.SlideTransition
import org.medialiteracy.domain.SettingsRepository
import org.medialiteracy.domain.ThemeMode
import org.medialiteracy.ui.AppTheme
import org.medialiteracy.ui.screens.OnboardingScreen
import org.medialiteracy.ui.LocalRootNavigator

@Composable
fun App() {
    val settingsRepository = remember { SettingsRepository.getInstance() }
    val themeMode by settingsRepository.getThemeMode().collectAsState(ThemeMode.SYSTEM)
    
    val useDarkTheme = when (themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }

    AppTheme(useDarkTheme = useDarkTheme) {
        Navigator(OnboardingScreen()) { navigator ->
            CompositionLocalProvider(LocalRootNavigator provides navigator) {
                SlideTransition(navigator)
            }
        }
    }
}