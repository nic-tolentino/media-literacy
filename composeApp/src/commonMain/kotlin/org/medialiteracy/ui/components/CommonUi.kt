package org.medialiteracy.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Standardized title for all TopAppBars in the application.
 * Uses the Serif style preferred by the user, now theme-aware.
 */
@Composable
fun AppBarTitle(text: String) {
    Text(
        text = text,
        color = MaterialTheme.colorScheme.primary,
    fontWeight = FontWeight.Medium,
        fontSize = 22.sp,
        fontFamily = FontFamily.Serif
    )
}
