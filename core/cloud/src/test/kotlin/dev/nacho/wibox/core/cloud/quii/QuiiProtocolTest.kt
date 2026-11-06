package dev.nacho.wibox.core.cloud.quii

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class QuiiProtocolTest {
    private val keyA = ByteArray(32) { 'A'.code.toByte() }
    private val keyB = ByteArray(32) { 'B'.code.toByte() }

    @Test
    fun `aes cbc matches python quii-helper vector`() {
        val plain = hex("68656c6c6f20717569692070726f746f636f6c21212121212121212121000000")
        val expected = hex("076d89359e997235d35a0788c165ae5251da7d6640464b492591839884dab9c1")
        val enc = QuiiCrypto.aesCbcEncrypt(keyA, plain)
        assertEquals(hexToList(expected), hexToList(enc))
        assertEquals(hexToList(plain), hexToList(QuiiCrypto.aesCbcDecrypt(keyA, enc)))
    }

    @Test
    fun `setup packet is a9 zeroed`() {
        val setup = QuiiEnvelope.buildSetup()
        assertEquals(32, setup.size)
        assertEquals(0xA9, setup[0].toInt() and 0xFF)
        assertTrue(setup.copyOfRange(1, 32).all { it == 0.toByte() })
    }

    @Test
    fun `play header matches python vector`() {
        val body = QuiiEnvelope.playBody(
            "adminapp2",
            "d".repeat(64),
            "/mode=real&idc=0&ids=1&ap=2",
        )
        assertEquals(104, body.size)
        val clearHeader = QuiiEnvelope.buildHeader(
            packetType = QuiiEnvelope.TYPE_PLAY,
            timestampSecs = 1_700_000_000_000L,
            commandPayloadSize = QuiiCrypto.zeroPad(body + ByteArray(32)).size,
            rawSize = body.size,
            b13 = 0,
            b14 = 0,
            flag15 = 1,
            flag16 = 0,
            flag17 = 0,
        )
        val expected = "010068e5cf8b0100009000680000000100000000000000000000000000000000"
        assertEquals(expected, clearHeader.joinToString("") { "%02x".format(it) })
    }

    @Test
    fun `encrypt then decode roundtrip keeps command and sha`() {
        val body = "hello-command".toByteArray(Charsets.UTF_8)
        val packet = QuiiEnvelope.encryptPacket(
            packetType = 0x10,
            body = body,
            key = keyB,
            timestamp = 1_700_000_000_000L,
        )
        val decoded = QuiiEnvelope.decodeBlob(packet, keyB)
        assertEquals(0x10, decoded.header.packetType)
        assertEquals(hexToList(body), hexToList(decoded.commandBytes!!))
        assertEquals(32 + QuiiCrypto.zeroPad(body + ByteArray(32)).size, decoded.totalLength)
    }

    @Test
    fun `keepalive is 64 bytes and self decodes`() {
        val packet = QuiiEnvelope.buildKeepalive(keyB, timestampSecs = 1_700_000_000L)
        assertEquals(64, packet.size)
        val decoded = QuiiEnvelope.decodeBlob(packet, keyB)
        assertEquals(QuiiEnvelope.TYPE_KEEPALIVE, decoded.header.packetType)
        assertEquals(0, decoded.header.rawSize)
    }

    @Test
    fun `play packet decrypts to body and valid sha`() {
        val body = QuiiEnvelope.playBody("adminapp2", "p".repeat(64), QuiiEnvelope.playTarget(0, 1))
        val packet = QuiiEnvelope.encryptPacket(
            packetType = QuiiEnvelope.TYPE_PLAY,
            body = body,
            key = keyB,
            b13 = 0,
            b14 = 0,
            flag15 = 1,
            flag16 = 0,
            timestamp = 1_700_000_000_000L,
        )
        val decoded = QuiiEnvelope.decodeBlob(packet, keyB)
        assertEquals(QuiiEnvelope.TYPE_PLAY, decoded.header.packetType)
        assertEquals(hexToList(body), hexToList(decoded.commandBytes!!))
    }

    @Test
    fun `video cpacket parses like python`() {
        val payload = hex("000001e010000000000000000000011980026801000102030405060708090a0b0c0d0e0f")
        val frames = CPacketDemuxer.demux(payload)
        assertEquals(1, frames.size)
        val video = frames[0] as MediaFrame.Video
        assertEquals(640, video.width)
        assertEquals(360, video.height)
        assertEquals(6.25f, video.fps)
        assertEquals(1, video.codec)
        assertEquals(16, video.bitstream.size)
    }

    @Test
    fun `audio cpacket parses like python`() {
        val payload = hex("000001e3080000000000000000000401401f0000d5d5d5d5d5d5d5d5")
        val frames = CPacketDemuxer.demux(payload)
        assertEquals(1, frames.size)
        val audio = frames[0] as MediaFrame.Audio
        assertEquals(CPacketDemuxer.AUDIO_CODEC_G711A, audio.codec)
        assertEquals(8000, audio.sampleRate)
        assertEquals(1, audio.channels)
        assertEquals(8, audio.samples.size)
    }

    @Test
    fun `encodeDevicePassword hashes short values only`() {
        assertEquals("64hexchars".repeat(0) + "a".repeat(64), QuiiCrypto.encodeDevicePassword("a".repeat(64)))
        val hashed = QuiiCrypto.encodeDevicePassword("secret")
        assertEquals(64, hashed.length)
    }

    @Test
    fun `truncated last cpacket keeps the bytes that are present`() {
        // The header advertises 20 bytes but only 4 arrive: the AES padding makes
        // the last cpacket of a payload short.
        val payload = hex("000001e014000000000000000000011980026801aabbccdd")
        val frames = CPacketDemuxer.demux(payload)
        assertEquals(1, frames.size)
        val video = frames[0] as MediaFrame.Video
        assertEquals(4, video.bitstream.size)
        assertEquals(hex("aabbccdd").toList(), video.bitstream.toList())
    }

    @Test
    fun `frames before a truncated cpacket are still returned`() {
        val payload = hex(
            "000001e010000000000000000000011980026801000102030405060708090a0b0c0d0e0f" +
                "000001e014000000000000000000011980026801aabbccdd",
        )
        val frames = CPacketDemuxer.demux(payload)
        assertEquals(2, frames.size)
        assertEquals(16, (frames[0] as MediaFrame.Video).bitstream.size)
        assertEquals(4, (frames[1] as MediaFrame.Video).bitstream.size)
    }

    @Test
    fun `zero length cpacket is skipped and later frames still demux`() {
        val payload = hex(
            "000001e000000000000000000000011980026801" +
                "000001e010000000000000000000011980026801000102030405060708090a0b0c0d0e0f",
        )
        val frames = CPacketDemuxer.demux(payload)
        assertEquals(1, frames.size)
        assertEquals(16, (frames[0] as MediaFrame.Video).bitstream.size)
    }

    private fun hex(s: String): ByteArray =
        s.chunked(2).map { it.toInt(16).toByte() }.toByteArray()

    private fun hexToList(b: ByteArray): List<Byte> = b.toList()
}
