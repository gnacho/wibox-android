package dev.nacho.wibox.data

fun InvitationRecord.toLocalInvitationUrl(): String = ParsedInvitation(
    inviteCode = code,
    ownerId = ownerId ?: "2041769",
    oemId = "G0021",
    appVersion = "0.1.0",
    deviceId = deviceId,
    from = "QrInvite",
    appId = 2021,
).toLocalUrl(invitePage?.takeIf { it.isNotBlank() } ?: "https://example.invalid/invite")
