package org.medialiteracy.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val ClarityBlue = Color(0xFF3DB8F5)
private val TrustGray = Color(0xFFF5F7F9)
private val DeepText = Color(0xFF1A1C1E)

private val LightColorScheme = lightColorScheme(
    primary = ClarityBlue,
    onPrimary = Color.White,
    background = TrustGray,
    onBackground = DeepText,
    surface = Color.White,
    onSurface = DeepText,
    primaryContainer = Color(0xFFE1F5FE)
)

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColorScheme,
        content = content
    )
}
