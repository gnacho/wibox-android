package dev.nacho.wibox.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

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

private val lightPalette = WiboxPalette(
    primary = Color(0xFF006F8E),
    onPrimary = Color.White,
    background = Color(0xFFF4F4F4),
    surface = Color.White,
    text = Color(0xFF4D4D4D),
    inactiveTab = Color(0xFFD6E6ED),
    selected = Color(0xFF007A9E),
    error = Color(0xFFA92F04),
    secondaryText = Color(0xFF666666),
    divider = Color(0xFFCECECE),
    disabled = Color(0xFFDFDDDE),
    pressed = Color(0xFFE8E8E8),
    banner = Color(0xFFDFF3FA),
    bannerText = Color(0xFF4D4D4D),
)

private val darkPalette = WiboxPalette(
    primary = Color(0xFF78C9E7),
    onPrimary = Color(0xFF102E38),
    background = Color(0xFF111417),
    surface = Color(0xFF1A1F22),
    text = Color(0xFFE7E9EA),
    inactiveTab = Color(0xFF24414D),
    selected = Color(0xFF78C9E7),
    error = Color(0xFFFFB4AB),
    secondaryText = Color(0xFFB5BABD),
    divider = Color(0xFF4B5357),
    disabled = Color(0xFF343A3E),
    pressed = Color(0xFF293338),
    banner = Color(0xFF173640),
    bannerText = Color(0xFFE7E9EA),
)

val WiboxColors: WiboxPalette
    @Composable get() = LocalWiboxPalette.current

private val LocalWiboxPalette = staticCompositionLocalOf { lightPalette }

private val typography = Typography(
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 20.sp,
        lineHeight = 25.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 16.sp,
        lineHeight = 22.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 18.sp,
        lineHeight = 25.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 22.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 19.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 18.sp,
        lineHeight = 23.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
    ),
)

@Composable
fun WiboxTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val palette = if (darkTheme) darkPalette else lightPalette
    val scheme = if (darkTheme) {
        darkColorScheme(
            primary = palette.primary,
            onPrimary = palette.onPrimary,
            background = palette.background,
            onBackground = palette.text,
            surface = palette.surface,
            onSurface = palette.text,
            surfaceVariant = palette.banner,
            onSurfaceVariant = palette.bannerText,
            secondary = palette.selected,
            onSecondary = palette.onPrimary,
            error = palette.error,
            onError = palette.onPrimary,
            outline = palette.divider,
        )
    } else {
        lightColorScheme(
            primary = palette.primary,
            onPrimary = palette.onPrimary,
            background = palette.background,
            onBackground = palette.text,
            surface = palette.surface,
            onSurface = palette.text,
            surfaceVariant = palette.banner,
            onSurfaceVariant = palette.bannerText,
            secondary = palette.selected,
            onSecondary = palette.onPrimary,
            error = palette.error,
            onError = palette.onPrimary,
            outline = palette.divider,
        )
    }
    CompositionLocalProvider(LocalWiboxPalette.provides(palette)) {
        MaterialTheme(
            colorScheme = scheme,
            typography = typography,
            content = content,
        )
    }
}
