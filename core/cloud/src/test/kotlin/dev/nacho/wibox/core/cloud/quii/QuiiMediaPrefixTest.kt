package dev.nacho.wibox.core.cloud.quii

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * El prefijo cifrado de un cpacket de media lo anuncia la cabecera en crudo y no
 * tiene por que ser multiplo de 16. Antes, un valor desalineado lanzaba dentro de
 * `aesCbcDecrypt` y `decodeChained` perdia el resto del blob (audio incluido).
 */
class QuiiMediaPrefixTest {
    private val key = ByteArray(32) { 'K'.code.toByte() }
    private val prefixPlain = ByteArray(32) { (it + 1).toByte() }
    private val tail = ByteArray(32) { (0x80 + it).toByte() }

    private fun cipher() = QuiiCrypto.aesCbcEncrypt(key, prefixPlain)

    @Test
    fun `an aligned prefix decrypts and leaves the tail untouched`() {
        val body = cipher() + tail
        val out = QuiiCrypto.aesCbcDecryptAlignedPrefix(key, body, 32)
        assertEquals(prefixPlain.toList(), out.copyOfRange(0, 32).toList())
        assertEquals(tail.toList(), out.copyOfRange(32, out.size).toList())
    }

    @Test
    fun `an unaligned prefix rounds down to whole blocks`() {
        val body = cipher() + tail
        val out = QuiiCrypto.aesCbcDecryptAlignedPrefix(key, body, 40)
        // Los 32 primeros bytes (dos bloques) se descifran; de ahi en adelante viaja
        // tal cual, sin perder ni un byte del cuerpo.
        assertEquals(prefixPlain.toList(), out.copyOfRange(0, 32).toList())
        assertEquals(body.copyOfRange(32, body.size).toList(), out.copyOfRange(32, out.size).toList())
        assertEquals(body.size, out.size)
    }

    @Test
    fun `a prefix shorter than one block decrypts nothing`() {
        val body = cipher() + tail
        assertEquals(body.toList(), QuiiCrypto.aesCbcDecryptAlignedPrefix(key, body, 8).toList())
        assertEquals(body.toList(), QuiiCrypto.aesCbcDecryptAlignedPrefix(key, body, 0).toList())
    }

    @Test
    fun `a prefix longer than the body is clamped`() {
        val body = cipher() + tail
        // Se recorta al tamano del cuerpo y no lanza: como mucho descifra de mas.
        assertEquals(body.size, QuiiCrypto.aesCbcDecryptAlignedPrefix(key, body, body.size + 50).size)
        assertEquals(body.size, QuiiCrypto.aesCbcDecryptAlignedPrefix(key, body, Int.MAX_VALUE).size)
    }

    /** Media packet with an unaligned command payload size, as the device can send. */
    private fun mediaPacket(declaredPrefix: Int): ByteArray {
        val mediaBody = cipher() + tail
        val clearHeader = QuiiEnvelope.buildHeader(
            packetType = 0xA0,
            timestampSecs = 1_700_000_000L,
            commandPayloadSize = declaredPrefix,
            rawSize = mediaBody.size and 0xFFFF,
            b13 = (mediaBody.size shr 16) and 0xFF,
            b14 = (mediaBody.size shr 24) and 0xFF,
            flag16 = 0,
            flag17 = 0,
        )
        return QuiiCrypto.aesCbcEncrypt(key, clearHeader) + mediaBody
    }

    @Test
    fun `a media packet with an unaligned prefix decodes without throwing`() {
        val decoded = QuiiEnvelope.decodeBlob(mediaPacket(declaredPrefix = 40), key)
        assertTrue(decoded.header.isMedia)
        val media = decoded.mediaPayload!!
        assertEquals(prefixPlain.toList(), media.copyOfRange(0, 32).toList())
        assertEquals(32 + 64, decoded.totalLength)
    }

    @Test
    fun `the packets behind an unaligned media packet are still decoded`() {
        val blob = mediaPacket(declaredPrefix = 40) + QuiiEnvelope.buildKeepalive(key, timestampSecs = 1_700_000_000L)
        val packets = QuiiEnvelope.decodeChained(blob, key)
        assertEquals(2, packets.size)
        assertTrue(packets[0].header.isMedia)
        assertEquals(QuiiEnvelope.TYPE_KEEPALIVE, packets[1].header.packetType)
    }
}
