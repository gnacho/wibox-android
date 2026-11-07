package dev.nacho.wibox.data

import dev.nacho.wibox.core.cloud.ProviderError

/**
 * Result codes of accept-nologin-share-invitation, taken from the vendor SDKStatus:
 * 100152007 is EmErrServerDExceedDevviceShareLimits (already used), while 100151007 is
 * EmErrServerUExpiredOrIllegalInvitation and covers both an expired and an unknown code.
 * Reporting them apart matters: an expired invitation asks for a fresh QR, a network
 * error asks for a retry.
 */
internal fun invitationAcceptError(result: Int): ProviderError = when (result) {
    100152007 -> ProviderError.InvitationUsed("acceptShareInvitation")
    100151007 -> ProviderError.InvitationExpired("acceptShareInvitation")
    100100003 -> ProviderError.Conflict("acceptShareInvitation")
    else -> ProviderError.Network("acceptShareInvitation")
}
