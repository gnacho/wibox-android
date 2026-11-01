package dev.nacho.wibox.ui.scan

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import kotlin.math.floor
import kotlin.math.min

/**
 * Draws [payload] as a QR code. Modules are snapped to whole pixels so the symbol stays crisp at
 * any size, and the caller owns the background so the quiet zone is never painted over.
 */
@Composable
fun QrCodeImage(
    payload: String,
    modifier: Modifier = Modifier,
    darkColor: Color = Color.Black,
    lightColor: Color = Color.White,
) {
    val modules = remember(payload) { WiboxQrEncoder.encode(payload) } ?: return
    val rows = modules.size
    val columns = modules.firstOrNull()?.size ?: return
    Canvas(modifier = modifier) {
        drawRect(color = lightColor, size = size)
        val cell = floor(min(size.width / columns, size.height / rows))
        if (cell < 1f) return@Canvas
        val left = floor((size.width - cell * columns) / 2f)
        val top = floor((size.height - cell * rows) / 2f)
        for (row in 0 until rows) {
            val y = top + row * cell
            for (column in 0 until columns) {
                if (!modules[row][column]) continue
                drawRect(
                    color = darkColor,
                    topLeft = Offset(left + column * cell, y),
                    size = Size(cell, cell),
                )
            }
        }
    }
}
