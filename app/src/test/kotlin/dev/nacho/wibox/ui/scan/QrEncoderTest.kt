package dev.nacho.wibox.ui.scan

import dev.nacho.wibox.data.DeviceCodeParser
import dev.nacho.wibox.data.ParsedCode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QrEncoderTest {

    private val invitationLink = "https://example.invalid/invite?InviteCode=INV7K2QD" +
        "&OwnerId=owner-1&OemId=G0021&AppVersion=2.2&DeviceId=DEMO7410001&From=app&AppId=2021"

    @Test
    fun encodesContentThatTheScannerReadsBack() {
        val modules = WiboxQrEncoder.encode(invitationLink)
        assertNotNull(modules)
        val decoded = WiboxQrDecoder.decode(
            luminance = luminance(modules!!),
            width = modules.first().size * SCALE,
            height = modules.size * SCALE,
        )
        assertEquals(invitationLink, decoded)
    }

    @Test
    fun encodedInvitationIsAcceptedByTheParser() {
        val modules = WiboxQrEncoder.encode(invitationLink) ?: error("no symbol")
        val decoded = WiboxQrDecoder.decode(
            luminance(modules),
            modules.first().size * SCALE,
            modules.size * SCALE,
        )
        val parsed = DeviceCodeParser.parse(decoded ?: error("no payload"))
        assertTrue(parsed is ParsedCode.Invitation)
        assertEquals("INV7K2QD", (parsed as ParsedCode.Invitation).value.inviteCode)
    }

    @Test
    fun keepsAQuietZoneAroundTheSymbol() {
        val modules = WiboxQrEncoder.encode(invitationLink) ?: error("no symbol")
        val zone = WiboxQrEncoder.QUIET_ZONE_MODULES
        assertTrue("first row must be blank", modules.first().none { it })
        assertTrue("last row must be blank", modules.last().none { it })
        assertTrue(modules.all { row -> !row.first() && !row.last() })
        assertTrue("the zone is at least $zone modules", modules.size > 2 * zone)
    }

    @Test
    fun refusesEmptyContent() {
        assertNull(WiboxQrEncoder.encode(""))
        assertNull(WiboxQrEncoder.encode("   "))
    }

    private fun scaled(modules: List<BooleanArray>): List<BooleanArray> {
        val rows = modules.size * SCALE
        val columns = modules.first().size * SCALE
        return List(rows) { y ->
            BooleanArray(columns) { x -> modules[y / SCALE][x / SCALE] }
        }
    }

    private fun luminance(modules: List<BooleanArray>): ByteArray {
        val scaled = scaled(modules)
        val bytes = ByteArray(scaled.size * scaled.first().size)
        var index = 0
        for (row in scaled) {
            for (dark in row) {
                bytes[index++] = if (dark) 0 else 255.toByte()
            }
        }
        return bytes
    }

    private companion object {
        const val SCALE = 4
    }
}
