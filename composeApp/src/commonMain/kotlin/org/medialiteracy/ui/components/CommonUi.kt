package org.medialiteracy.ui.components

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Standardized title for all TopAppBars in the application.
 * Uses the Serif, Deep Navy, ExtraBold style preferred by the user.
 */
@Composable
fun AppBarTitle(text: String) {
    Text(
        text = text,
        color = Color(0xFF1A237E),
        fontWeight = FontWeight.ExtraBold,
        fontSize = 20.sp,
        fontFamily = FontFamily.Serif
    )
}
