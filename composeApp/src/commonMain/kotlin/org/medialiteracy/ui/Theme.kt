package org.medialiteracy.ui

import androidx.compose.material3.*
import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp

// Clarity & Trust Palette
private val ClarityIndigo = Color(0xFF3F51B5)
private val ClarityTeal = Color(0xFF4DB6AC)
private val TrustOffWhite = Color(0xFFF8F9FA)
private val NeutralBlack = Color(0xFF212121)
private val RecordRed = Color(0xFFD32F2F)

private val LightColorScheme = lightColorScheme(
    primary = ClarityIndigo,
    onPrimary = Color.White,
    secondary = ClarityTeal,
    onSecondary = Color.White,
    tertiary = TrustOffWhite,
    onTertiary = NeutralBlack,
    background = Color.White,
    onBackground = NeutralBlack,
    surface = TrustOffWhite,
    onSurface = NeutralBlack,
    error = RecordRed,
    onError = Color.White
)

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    val typography = Typography(
        headlineLarge = TextStyle(
            fontSize = 32.sp,
            color = NeutralBlack
        ),
        headlineMedium = TextStyle(
            fontSize = 24.sp,
            color = NeutralBlack
        ),
        bodyLarge = TextStyle(
            fontSize = 16.sp,
            color = NeutralBlack
        )
    )

    MaterialTheme(
        colorScheme = LightColorScheme,
        typography = typography
    ) {
        // Fallback to avoid IndicationNodeFactory crash in newer Compose versions
        // when the ripple system is mismatched.
        CompositionLocalProvider(LocalIndication provides DefaultDebugIndication) {
            content()
        }
    }
}

private object DefaultDebugIndication : IndicationNodeFactory {
    override fun create(interactionSource: InteractionSource): Modifier.Node {
        return object : Modifier.Node() {}
    }

    override fun equals(other: Any?) = other === this
    override fun hashCode() = 0
}
