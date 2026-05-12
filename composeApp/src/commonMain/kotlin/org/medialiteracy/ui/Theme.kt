package org.medialiteracy.ui

import androidx.compose.material3.*
import org.medialiteracy.setSystemAppearance
import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.navigator.Navigator
import androidx.compose.runtime.compositionLocalOf

val LocalThemeIsDark = compositionLocalOf { false }
val LocalRootNavigator = compositionLocalOf<cafe.adriel.voyager.navigator.Navigator?> { null }

// Clarity & Trust Palette
private val ClarityIndigo = Color(0xFF3F51B5)
private val ClarityTeal = Color(0xFF4DB6AC)
private val TrustOffWhite = Color(0xFFF8F9FA)
private val NeutralBlack = Color(0xFF212121)
private val RecordRed = Color(0xFFD32F2F)

@Immutable
data class AnalyticalColors(
    val blue: Color,
    val teal: Color,
    val red: Color,
    val purple: Color,
    val brightBlue: Color,
    val brightTeal: Color,
    val brightRed: Color,
    val brightPurple: Color,
    val appBarElevation: androidx.compose.ui.unit.Dp
)

val LocalAnalyticalColors = staticCompositionLocalOf {
    AnalyticalColors(
        blue = Color.Unspecified,
        teal = Color.Unspecified,
        red = Color.Unspecified,
        purple = Color.Unspecified,
        brightBlue = Color.Unspecified,
        brightTeal = Color.Unspecified,
        brightRed = Color.Unspecified,
        brightPurple = Color.Unspecified,
        appBarElevation = androidx.compose.ui.unit.Dp.Unspecified
    )
}

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

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFFD0D0D0), // Deeper Indigo //FF5C6BC0
    onPrimary = Color.White,
    secondary = Color(0xFF00695C), // Deeper Teal
    onSecondary = Color.White,
    tertiary = Color(0xFFFB263B), // Ultra Dark Navy //0xFF0F172A
    onTertiary = Color.White,
    background = Color(0xFF081121), // Deep Navy Blue //0xFF0D1B2A
    onBackground = Color(0xFFF1F5F9),
    surface = Color(0xFF1B263B),//0xFF1B263B
    onSurface = Color(0xFFF1F5F9),
    surfaceVariant = Color(0xFF02F6F7), // Deepest Slate for cards //0xFF020617
    onSurfaceVariant = Color(0xFF94A3B8),
    error = Color(0xFFB91C1C),
    onError = Color.White
)

@Composable
fun AppTheme(
    useDarkTheme: Boolean = androidx.compose.foundation.isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (useDarkTheme) DarkColorScheme else LightColorScheme
    
    // Set status bar appearance
    setSystemAppearance(useDarkTheme)
    
    val typography = Typography(
        headlineLarge = TextStyle(
            fontSize = 32.sp,
            color = colorScheme.onSurface
        ),
        headlineMedium = TextStyle(
            fontSize = 24.sp,
            color = colorScheme.onSurface
        ),
        bodyLarge = TextStyle(
            fontSize = 16.sp,
            color = colorScheme.onSurface
        )
    )

    val analyticalColors = if (useDarkTheme) {
        AnalyticalColors(
            blue = Color(0xFF101955),
            teal = Color(0xFF003D33),
            red = Color(0xFF5F0000),
            purple = Color(0xFF281057),
            brightBlue = Color(0xFF7986CB),
            brightTeal = Color(0xFF4DB6AC),
            brightRed = Color(0xFFE57373),
            brightPurple = Color(0xFF9575CD),
            appBarElevation = 0.dp
        )
    } else {
        AnalyticalColors(
            blue = Color(0xFF303F9F),
            teal = Color(0xFF00796B),
            red = Color(0xFFC62828),
            purple = Color(0xFF512DA8),
            brightBlue = Color(0xFF303F9F),
            brightTeal = Color(0xFF00796B),
            brightRed = Color(0xFFC62828),
            brightPurple = Color(0xFF512DA8),
            appBarElevation = 16.dp
        )
    }

    CompositionLocalProvider(
        LocalThemeIsDark provides useDarkTheme,
        LocalAnalyticalColors provides analyticalColors
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = typography
        ) {
            // Fallback to avoid IndicationNodeFactory crash in newer Compose versions
        // when the ripple system is mismatched.
            CompositionLocalProvider(LocalIndication provides DefaultDebugIndication) {
                content()
            }
        }
    }
}

val MaterialTheme.analyticalColors: AnalyticalColors
    @Composable
    get() = LocalAnalyticalColors.current

private object DefaultDebugIndication : IndicationNodeFactory {
    override fun create(interactionSource: InteractionSource): Modifier.Node {
        return object : Modifier.Node() {}
    }

    override fun equals(other: Any?) = other === this
    override fun hashCode() = 0
}
