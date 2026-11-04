package dev.nacho.wibox.core.cloud

object DevicePermissionCodec {
    private val powerByPermission = linkedMapOf(
        DevicePermission.PREVIEW to 1,
        DevicePermission.PLAYBACK to 3,
        DevicePermission.ALARM to 4,
        DevicePermission.UNLOCK to 1001,
        DevicePermission.F1 to 1002,
        DevicePermission.CALL_GUARD to 1003,
    )

    fun encode(permissions: Set<DevicePermission>): String =
        powerByPermission.filterKeys { it in permissions }.values.joinToString(",")

    fun decode(powers: String): Set<DevicePermission> {
        if (powers.equals("all", ignoreCase = true)) return DevicePermission.entries.toSet()
        val accepted = powers.split(',').mapNotNull { it.trim().toIntOrNull() }.toSet()
        return powerByPermission.filterValues { it in accepted }.keys
    }
}
