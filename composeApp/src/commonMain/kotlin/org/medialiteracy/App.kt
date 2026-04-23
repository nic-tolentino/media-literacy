package org.medialiteracy

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.transitions.SlideTransition
import org.medialiteracy.ui.AppTheme
import org.medialiteracy.ui.screens.OnboardingScreen

@Composable
fun App() {
    AppTheme {
        Navigator(OnboardingScreen()) { navigator ->
            SlideTransition(navigator)
        }
    }
}