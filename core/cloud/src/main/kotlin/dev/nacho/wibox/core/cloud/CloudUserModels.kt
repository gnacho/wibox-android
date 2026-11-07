package dev.nacho.wibox.core.cloud

/**
 * A person a device is shared with, as the stock cloud models it: the share group
 * of the account channel, with its name, the granted permissions and the code that
 * the invitation QR carries.
 *
 * The stock app has no account administration and no roles beyond owner and guest.
 * Its "Users" screen lists these groups, so this is the whole user model of the
 * provider.
 */
data class CloudUserInvitation(
    val id: String,
    val deviceId: String,
    val displayName: String,
    val shareCode: String,
    val expiresAtMillis: Long,
    val permissions: Set<DevicePermission>,
    val invitePage: String? = null,
    val ownerId: String? = null,
) {
    /** The share code and the invite page are secrets: keep them out of the logs. */
    override fun toString(): String =
        "CloudUserInvitation(id=$id, deviceId=$deviceId, displayName=<redacted>, " +
            "shareCode=<redacted>, expiresAtMillis=$expiresAtMillis, permissions=$permissions, " +
            "invitePage=<redacted>, ownerId=$ownerId)"
}

/**
 * Expiry of a share group, as answered by the gateway: `expire-time` is an epoch in
 * seconds (the stock also accepts milliseconds) and `expire-minutes` is the relative
 * form used when the device was just invited.
 */
internal fun invitationExpiryMillis(
    raw: String,
    expireMinutes: Int?,
    nowMillis: Long = System.currentTimeMillis(),
): Long {
    raw.toLongOrNull()?.let { value ->
        if (value > 0) return if (value < 10_000_000_000L) value * 1000 else value
    }
    return expireMinutes?.let { nowMillis + it * 60_000L } ?: 0L
}
