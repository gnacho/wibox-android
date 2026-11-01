package dev.nacho.wibox.ui.scan

import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.QRCodeWriter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class QrDecoderTest {

    private val payload = "IDS1111test u1d2test3 MZNTIVVE IDS1111 A1B2C3D4E5F6"

    @Test
    fun decodesQrFromLuminanceFrame() {
        val matrix = render(payload)
        val luminance = luminance(matrix, padding = 0)

        assertEquals(payload, WiboxQrDecoder.decode(luminance, matrix.width, matrix.height))
    }

    @Test
    fun decodesWithRowStridePadding() {
        val matrix = render(payload)
        val stride = matrix.width + 32
        val luminance = luminance(matrix, padding = 32)

        assertEquals(payload, WiboxQrDecoder.decode(luminance, matrix.width, matrix.height, stride))
    }

    @Test
    fun returnsNullForFrameWithoutCode() {
        val noise = ByteArray(320 * 320) { index -> ((index * 37) % 251).toByte() }

        assertNull(WiboxQrDecoder.decode(noise, 320, 320))
    }

    @Test
    fun returnsNullForDegenerateFrame() {
        assertNull(WiboxQrDecoder.decode(ByteArray(0), 0, 0))
    }

    private fun render(text: String): BitMatrix = QRCodeWriter().encode(
        text,
        BarcodeFormat.QR_CODE,
        360,
        360,
        mapOf(EncodeHintType.MARGIN to 2),
    )

    /** Y plane of an NV21 frame: white modules are 255 and dark modules are 0. */
    private fun luminance(matrix: BitMatrix, padding: Int): ByteArray {
        val stride = matrix.width + padding
        val frame = ByteArray(stride * matrix.height)
        for (row in 0 until matrix.height) {
            for (column in 0 until matrix.width) {
                frame[row * stride + column] = if (matrix.get(column, row)) 0 else 255.toByte()
            }
        }
        return frame
    }
}
