package org.medialiteracy.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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

/**
 * Standardized primary action button.
 * Enforces white text on primary background to fix visibility issues.
 */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = MaterialTheme.shapes.large,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = Color.White
        )
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
    }
}
