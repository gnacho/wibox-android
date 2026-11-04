package dev.nacho.wibox.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InvitationLinksTest {
    @Test
    fun cloudInvitationUsesTheRealInvitePageAndOwner() {
        val record = InvitationRecord(
            id = "g1",
            deviceId = "device 1",
            displayName = "Ana",
            code = "A&B",
            createdAtMillis = 1,
            expiresAtMillis = 2,
            invitePage = "https://cloud.example/share",
            ownerId = "42",
        )
        val link = record.toLocalInvitationUrl()
        assertTrue(link.startsWith("https://cloud.example/share?"))
        assertTrue(link.contains("InviteCode=A%26B"))
        assertTrue(link.contains("OwnerId=42"))
        assertTrue(link.contains("DeviceId=device%201"))
    }

    @Test
    fun expirationAcceptsEpochAndRelativeMinutes() {
        assertEquals(1_700_000_000_000L, expirationMillis("1700000000", null))
        assertEquals(1_700_000_000_123L, expirationMillis("1700000000123", null))
        assertEquals(360_000L, expirationMillis("", 5, nowMillis = 60_000L))
        assertEquals(0L, expirationMillis("", null))
    }
}
