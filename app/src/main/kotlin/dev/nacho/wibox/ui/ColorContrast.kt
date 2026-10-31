package dev.nacho.wibox.ui

import androidx.compose.ui.graphics.Color
import kotlin.math.pow

fun contrastRatio(foreground: Color, background: Color): Double {
    val first = relativeLuminance(foreground)
    val second = relativeLuminance(background)
    val lighter = maxOf(first, second)
    val darker = minOf(first, second)
    return (lighter + 0.05) / (darker + 0.05)
}

fun relativeLuminance(color: Color): Double {
    fun channel(value: Float): Double {
        val linear = if (value <= 0.04045f) value / 12.92f else ((value + 0.055f) / 1.055f).pow(2.4f)
        return linear.toDouble()
    }
    return 0.2126 * channel(color.red) + 0.7152 * channel(color.green) + 0.0722 * channel(color.blue)
}
