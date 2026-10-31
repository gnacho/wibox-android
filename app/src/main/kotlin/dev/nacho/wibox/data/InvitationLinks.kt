package dev.nacho.wibox.data

fun InvitationRecord.toLocalInvitationUrl(): String = ParsedInvitation(
    inviteCode = code,
    ownerId = "local-demo-owner",
    oemId = "G0021",
    appVersion = "0.1.0",
    deviceId = deviceId,
    from = "local-demo",
    appId = 2021,
).toLocalUrl()
