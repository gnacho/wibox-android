package dev.nacho.wibox.ui.scan

import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.ReaderException
import com.google.zxing.common.HybridBinarizer

/**
 * Decodes QR codes from a camera frame without Google Play Services.
 *
 * The frame arrives as the Y plane of an NV21 image, so the input is plain
 * luminance bytes and this object stays testable on the JVM.
 */
object WiboxQrDecoder {

    private val hints = mapOf(
        DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE),
        DecodeHintType.TRY_HARDER to true,
    )

    fun decode(luminance: ByteArray, width: Int, height: Int, rowStride: Int = width): String? {
        if (width <= 0 || height <= 0) return null
        val pixels = if (rowStride == width) luminance else repack(luminance, width, height, rowStride)
        if (pixels.size < width * height) return null
        val source = PlanarYUVLuminanceSource(pixels, width, height, 0, 0, width, height, false)
        val reader = MultiFormatReader()
        return try {
            reader.setHints(hints)
            reader.decodeWithState(BinaryBitmap(HybridBinarizer(source))).text
        } catch (noCode: ReaderException) {
            null
        } finally {
            reader.reset()
        }
    }

    private fun repack(luminance: ByteArray, width: Int, height: Int, rowStride: Int): ByteArray {
        if (rowStride <= 0) return luminance
        val packed = ByteArray(width * height)
        for (row in 0 until height) {
            val from = row * rowStride
            if (from + width > luminance.size) break
            System.arraycopy(luminance, from, packed, row * width, width)
        }
        return packed
    }
}
