package dev.nacho.wibox.ui.scan

import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.WriterException
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/**
 * Renders the QR codes this app shares with itself, so a link shown on one phone can be read by
 * the scanner of another one without leaving the app.
 */
object WiboxQrEncoder {

    /** Modules of quiet zone around the symbol. Four is the width the QR spec asks for. */
    const val QUIET_ZONE_MODULES = 4

    private const val DEFAULT_SIZE_MODULES = 33

    /** True means dark module. Returns null when the content is empty or cannot be encoded. */
    fun encode(content: String, sizeModules: Int = DEFAULT_SIZE_MODULES): List<BooleanArray>? {
        if (content.isBlank()) return null
        val hints = mapOf(
            EncodeHintType.CHARACTER_SET to "UTF-8",
            EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
            EncodeHintType.MARGIN to QUIET_ZONE_MODULES,
        )
        val matrix = try {
            QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, sizeModules, sizeModules, hints)
        } catch (error: WriterException) {
            null
        } catch (error: IllegalArgumentException) {
            null
        } ?: return null
        if (matrix.width <= 0 || matrix.height <= 0) return null
        return List(matrix.height) { y ->
            BooleanArray(matrix.width) { x -> matrix.get(x, y) }
        }
    }
}
