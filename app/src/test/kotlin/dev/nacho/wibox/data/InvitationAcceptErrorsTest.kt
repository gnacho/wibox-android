package dev.nacho.wibox.data

import dev.nacho.wibox.core.cloud.ProviderError
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class InvitationAcceptErrorsTest {

    @Test
    fun `an already used invitation is not a network failure`() {
        val error = invitationAcceptError(100152007)
        assertIs<ProviderError.InvitationUsed>(error)
        assertEquals(100152007, error.providerCode)
    }

    @Test
    fun `an expired or unknown invitation asks for a fresh code instead of a retry`() {
        val error = invitationAcceptError(100151007)
        assertIs<ProviderError.InvitationExpired>(error)
        assertEquals(100151007, error.providerCode)
    }

    @Test
    fun `a duplicate device stays a conflict`() {
        assertIs<ProviderError.Conflict>(invitationAcceptError(100100003))
    }

    @Test
    fun `an unknown result is still reported as a network failure`() {
        assertIs<ProviderError.Network>(invitationAcceptError(100004))
    }

    @Test
    fun `the two invitation failures do not share a provider code`() {
        val used = ProviderError.InvitationUsed("acceptShareInvitation")
        val expired = ProviderError.InvitationExpired("acceptShareInvitation")
        assertTrue(used.providerCode != expired.providerCode)
        assertEquals(used.retryPolicy, expired.retryPolicy)
    }
}
