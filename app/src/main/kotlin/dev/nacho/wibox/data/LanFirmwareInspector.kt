package dev.nacho.wibox.data

import dev.nacho.wibox.core.cloud.UserAuthCloud
import dev.nacho.wibox.core.cloud.devicecgi.DeviceCgiClient
import dev.nacho.wibox.core.cloud.quii.QuiiCrypto
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.net.Inet4Address
import java.net.NetworkInterface

/**
 * Lee el firmware del timbre por el CGI local. Busca el timbre en la red local
 * (misma /24 que el telefono) y consulta `get.device.status`.
 *
 * La version de firmware no esta expuesta por la nube: fuera de la red local el
 * resultado es [FirmwareResult.Unavailable] con motivo explicito.
 */
class LanFirmwareInspector(
    private val userAuthCloudFactory: () -> UserAuthCloud,
    private val credentials: () -> Pair<String, String>?,
    private val cgi: DeviceCgiClient = DeviceCgiClient.default(),
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : FirmwareInspector {

    override suspend fun inspect(deviceId: String): FirmwareResult = withContext(dispatcher) {
        val creds = credentials()
            ?: return@withContext FirmwareResult.Unavailable("sin credenciales guardadas del timbre")
        val password = try {
            val auth = userAuthCloudFactory()
            auth.login(creds.first, creds.second)
            QuiiCrypto.encodeDevicePassword(auth.getDeviceToken(deviceId).authCode)
        } catch (error: Exception) {
            return@withContext FirmwareResult.Unavailable(
                "no se pudo obtener el acceso del timbre: " + (error.message ?: error::class.java.simpleName),
            )
        }
        val prefixes = localPrefixes()
        if (prefixes.isEmpty()) {
            return@withContext FirmwareResult.Unavailable("el telefono no tiene red local")
        }
        scan(prefixes, password) ?: FirmwareResult.Unavailable(
            "no se encontro el timbre en la red local. La version de firmware solo " +
                "se puede leer estando en la misma red WiFi que el timbre.",
        )
    }

    private suspend fun scan(prefixes: List<String>, password: String): FirmwareResult.Available? =
        coroutineScope {
            val gate = Semaphore(32)
            val jobs = prefixes.flatMap { prefix -> (2..254).map { "$prefix.$it" } }.map { host ->
                async {
                    gate.withPermit {
                        withTimeoutOrNull(HOST_TIMEOUT_MS) {
                            runCatching { cgi.deviceStatus(host, password) }
                                .getOrNull()
                                ?.takeIf { !it.version.isNullOrBlank() }
                        }
                    }
                }
            }
            jobs.awaitAll().filterNotNull().firstOrNull()?.let { FirmwareResult.Available(it) }
        }

    private fun localPrefixes(): List<String> = runCatching {
        NetworkInterface.getNetworkInterfaces().toList()
            .filter { it.isUp && !it.isLoopback }
            .flatMap { it.inetAddresses.toList() }
            .filterIsInstance<Inet4Address>()
            .filterNot { it.isLoopbackAddress }
            .mapNotNull { address ->
                val parts = address.hostAddress?.split(".") ?: return@mapNotNull null
                if (parts.size != 4) null else parts.take(3).joinToString(".")
            }
            .distinct()
    }.getOrDefault(emptyList())

    private companion object {
        const val HOST_TIMEOUT_MS = 700L
    }
}
