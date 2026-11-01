package dev.nacho.wibox.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.RoundedCornerShape

/**
 * Legacy palette facade kept for existing call sites.
 * Values are derived from the active Material 3 [ColorScheme], so every
 * screen follows Material You (including wallpaper-based dynamic color).
 */
data class WiboxPalette(
    val primary: Color,
    val onPrimary: Color,
    val background: Color,
    val surface: Color,
    val text: Color,
    val inactiveTab: Color,
    val selected: Color,
    val error: Color,
    val secondaryText: Color,
    val divider: Color,
    val disabled: Color,
    val pressed: Color,
    val banner: Color,
    val bannerText: Color,
)

// Fallback brand scheme (Fermax teal seed) for devices without dynamic color.
private val LightScheme = lightColorScheme(
    primary = Color(0xFF006F8E),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFBDEAFB),
    onPrimaryContainer = Color(0xFF001F2A),
    secondary = Color(0xFF4C626B),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFCFE6F1),
    onSecondaryContainer = Color(0xFF071E26),
    tertiary = Color(0xFF5A5B7E),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFE1DFF9),
    onTertiaryContainer = Color(0xFF171837),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFF5FAFD),
    onBackground = Color(0xFF171C1F),
    surface = Color(0xFFF5FAFD),
    onSurface = Color(0xFF171C1F),
    surfaceVariant = Color(0xFFDBE4E9),
    onSurfaceVariant = Color(0xFF40484D),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFEFF4F8),
    surfaceContainer = Color(0xFFE9EEF2),
    surfaceContainerHigh = Color(0xFFE3E8ED),
    surfaceContainerHighest = Color(0xFFDEE3E7),
    outline = Color(0xFF70787D),
    outlineVariant = Color(0xFFC0C8CD),
    inverseSurface = Color(0xFF2C3134),
    inverseOnSurface = Color(0xFFECF1F4),
    inversePrimary = Color(0xFF86D2E9),
)

private val DarkScheme = darkColorScheme(
    primary = Color(0xFF86D2E9),
    onPrimary = Color(0xFF00363F),
    primaryContainer = Color(0xFF004E5D),
    onPrimaryContainer = Color(0xFFBDEAFB),
    secondary = Color(0xFFB3CAD5),
    onSecondary = Color(0xFF1E333C),
    secondaryContainer = Color(0xFF344A53),
    onSecondaryContainer = Color(0xFFCFE6F1),
    tertiary = Color(0xFFC3C3EB),
    onTertiary = Color(0xFF2C2E4D),
    tertiaryContainer = Color(0xFF434465),
    onTertiaryContainer = Color(0xFFE1DFF9),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF0F1417),
    onBackground = Color(0xFFDEE3E7),
    surface = Color(0xFF0F1417),
    onSurface = Color(0xFFDEE3E7),
    surfaceVariant = Color(0xFF40484D),
    onSurfaceVariant = Color(0xFFC0C8CD),
    surfaceContainerLowest = Color(0xFF0A0F12),
    surfaceContainerLow = Color(0xFF171C1F),
    surfaceContainer = Color(0xFF1B2023),
    surfaceContainerHigh = Color(0xFF252B2E),
    surfaceContainerHighest = Color(0xFF303639),
    outline = Color(0xFF8A9297),
    outlineVariant = Color(0xFF40484D),
    inverseSurface = Color(0xFFDEE3E7),
    inverseOnSurface = Color(0xFF2C3134),
    inversePrimary = Color(0xFF006F8E),
)

val WiboxColors: WiboxPalette
    @Composable get() = LocalWiboxPalette.current

private val LocalWiboxPalette = staticCompositionLocalOf { paletteFrom(LightScheme) }

private fun paletteFrom(scheme: androidx.compose.material3.ColorScheme) = WiboxPalette(
    primary = scheme.primary,
    onPrimary = scheme.onPrimary,
    background = scheme.background,
    surface = scheme.surface,
    text = scheme.onSurface,
    inactiveTab = scheme.onSurfaceVariant,
    selected = scheme.primary,
    error = scheme.error,
    secondaryText = scheme.onSurfaceVariant,
    divider = scheme.outlineVariant,
    disabled = scheme.surfaceContainerHigh,
    pressed = scheme.secondaryContainer,
    banner = scheme.secondaryContainer,
    bannerText = scheme.onSecondaryContainer,
)

private val WiboxShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

private val typography = Typography(
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 22.sp,
        lineHeight = 28.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 24.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
    ),
)

@Composable
fun WiboxTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val scheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkScheme
        else -> LightScheme
    }
    CompositionLocalProvider(LocalWiboxPalette.provides(paletteFrom(scheme))) {
        MaterialTheme(
            colorScheme = scheme,
            typography = typography,
            shapes = WiboxShapes,
            content = content,
        )
    }
}
