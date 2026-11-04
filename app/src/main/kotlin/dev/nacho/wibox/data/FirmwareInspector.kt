package dev.nacho.wibox.data

import dev.nacho.wibox.core.cloud.devicecgi.DeviceFirmwareInfo

/** Resultado de consultar el firmware del timbre. */
sealed interface FirmwareResult {
    data class Available(val info: DeviceFirmwareInfo) : FirmwareResult
    data class Unavailable(val reason: String) : FirmwareResult
}

/**
 * Lee el firmware del timbre. Solo es posible por el CGI local (`/tdkcgi`),
 * asi que devuelve [FirmwareResult.Unavailable] con motivo cuando el timbre
 * no esta en la misma red local.
 */
interface FirmwareInspector {
    suspend fun inspect(deviceId: String): FirmwareResult
}
