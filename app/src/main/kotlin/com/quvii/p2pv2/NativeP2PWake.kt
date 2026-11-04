package com.quvii.p2pv2

import android.content.Context
import android.util.Log
import dev.nacho.wibox.core.cloud.quii.UstDiscovery
import java.io.File

/**
 * Wake the doorbell through the vendor SDK instead of the MQTT UST packet.
 * The server accepts our device-awaken but the hardware stays asleep, so we
 * reuse the same native call the stock app performs.
 */
object NativeP2PWake {

    private const val TAG = "WIBOX-NATIVE"
    private const val SERVICE_URL = "https://r1-8.qvcloud.net"
    private const val CLIENT_ID = "003-2021-7d8e4f2a91c5b306"
    private const val OEM = "G0021"
    private const val APP_ID = "2021"
    private const val APP_TYPE = "3"
    private const val KEEPALIVE_SEC = 30

    /** Defaults used by the stock SDK when the device is not registered locally. */
    private const val DEFAULT_P2P_TYPE = 9
    private const val DEFAULT_MTU = 1200
    private const val DEFAULT_TIMEOUT_SEC = 20

    @Volatile
    private var clientReady = false

    @Volatile
    private var lastError: String? = null

    @Volatile
    private var lastPort: Int = -1

    fun lastError(): String? = lastError

    fun lastPort(): Int = lastPort

    fun ensureClient(context: Context, caPem: ByteArray): Boolean {
        if (clientReady) return true
        return runCatching {
            val dir = File(context.filesDir, "qvsdk").apply { mkdirs() }
            val ca = copyAsset(context, "qv_ca.pem", dir)
            val cert = copyAsset(context, "client.pem", dir)
            val key = copyAsset(context, "client.txt", dir)
            val services = UstDiscovery.queryServices(SERVICE_URL, CLIENT_ID, OEM, APP_ID.toInt(), caPem)
            val ust = services.ustMqttUrl ?: error("sin ustMqttUrl")
            val ustTest = services.ustTestAddress ?: ""
            // QvP2PClientV2.initSDK sends this mask before creating the client:
            // loginMode 0 plus bits 5,8,9,11,13,15,18,19. Without it the SDK
            // subscribes to a smaller service set and never sees the device wake.
            val mask = QvP2PV2Api.generateMask(0, 5, 8, 9, 11, 13, 15, 18, 19)
            QvP2PV2Api.setDefaultServiceMask(mask)
            Log.d(TAG, "setDefaultServiceMask=$mask")
            val rc = QvP2PV2Api.createP2PClient(
                SERVICE_URL,
                ust,
                ustTest,
                ca.absolutePath,
                key.absolutePath,
                cert.absolutePath,
                OEM,
                CLIENT_ID,
                APP_TYPE,
                APP_ID,
                0,
                KEEPALIVE_SEC,
            )
            Log.d(TAG, "createP2PClient rc=$rc ust=$ust test=$ustTest")
            clientReady = rc == 0
            if (rc != 0) lastError = "createP2PClient rc=$rc"
            clientReady
        }.getOrElse {
            lastError = it.message ?: it::class.java.simpleName
            Log.e(TAG, "createP2PClient fail", it)
            false
        }
    }

    fun awaken(context: Context, caPem: ByteArray, deviceId: String): Int {
        if (!ensureClient(context, caPem)) return -1
        // The SDK opens its own UST; deviceAwaken answers -1 until it is up.
        var waited = 0
        while (QvP2PV2Api.lastUstStatus != 3 && waited < 8_000) {
            Thread.sleep(200)
            waited += 200
        }
        Log.d(TAG, "waitUst status=${QvP2PV2Api.lastUstStatus} waited=${waited}ms")
        return runCatching {
            var rc = QvP2PV2Api.deviceAwaken(deviceId)
            if (rc != 0) {
                Thread.sleep(1_000)
                rc = QvP2PV2Api.deviceAwaken(deviceId)
            }
            Log.d(TAG, "deviceAwaken($deviceId) rc=$rc")
            if (rc == 0) {
                val info = runCatching {
                    QvP2PV2Api.addPortByP2P(deviceId, 1, -1, 0, 0, 0, 0)
                }.getOrNull()
                lastPort = info?.port ?: -1
                Log.d(TAG, "addPort port=${info?.port} type=${info?.type}")
            }
            rc
        }.getOrElse {
            lastError = it.message ?: it::class.java.simpleName
            Log.e(TAG, "deviceAwaken fail", it)
            -2
        }
    }

    /**
     * Maps the device media port (34567) to a local TCP listener, the same call
     * the stock player performs before startPlay("127.0.0.1", port).
     *
     * Returns the local port on success. On failure the error keeps the vendor
     * code: -2 device busy, -3 device low power, -4 p2p busy.
     */
    fun openStreamPort(context: Context, caPem: ByteArray, deviceId: String): StreamPort =
        openPort(context, caPem, deviceId, protocolNo = 0, label = "stream")

    /** Puerto CGI del timbre (protocolNo 1) mapeado en 127.0.0.1. */
    fun openCgiPort(context: Context, caPem: ByteArray, deviceId: String): StreamPort =
        openPort(context, caPem, deviceId, protocolNo = 1, label = "cgi")

    private fun openPort(
        context: Context,
        caPem: ByteArray,
        deviceId: String,
        protocolNo: Int,
        label: String,
    ): StreamPort {
        if (!ensureClient(context, caPem)) return StreamPort(-1, -1)
        var waited = 0
        // 3 s basta: si el UST no ha subido, el bucle de addPortByP2P de abajo
        // sigue intentandolo. Esperar 8 s en serie era parte de la lentitud.
        while (QvP2PV2Api.lastUstStatus != 3 && waited < 3_000) {
            Thread.sleep(200)
            waited += 200
        }
        var lastError = -1
        var busyTries = 0
        // 0 = P2P online, 1 = sleep (ONLINE_MASK_SLEEP del SDK).
        val status = runCatching { QvP2PV2Api.getDeviceStatus(deviceId) }.getOrDefault(-1)
        val direct = runCatching { QvP2PV2Api.getDirectDeviceStatus(deviceId) }.getOrDefault(-1)
        val connType = runCatching { QvP2PV2Api.getDeviceConnectType(deviceId) }.getOrDefault(-1)
        runCatching { QvP2PV2Api.addDeviceStatusQuery(deviceId) }
        Log.d(TAG, "status=$status direct=$direct connType=$connType waited=${waited}ms")
        // The stock DeviceHelper.CleanPort drops the port mappings and the P2P
        // leg before asking for a new one. Without this every attempt leaves a
        // mapping behind and the device ends up answering BUSY to the play.
        runCatching {
            if (lastPort > 0) QvP2PV2Api.deletePortByP2P(lastPort)
            QvP2PV2Api.delCurrentDevP2pLink(deviceId)
        }
        Log.d(TAG, "cleaned old link lastPort=$lastPort")
        // Mismo orden que funcionaba: despertar primero y pedir el puerto despues.
        val awakenRc = runCatching { QvP2PV2Api.deviceAwaken(deviceId) }.getOrNull()
        Log.d(TAG, "deviceAwaken($deviceId) rc=$awakenRc waited=${waited}ms")
        Log.d(TAG, "status after awaken=" + runCatching { QvP2PV2Api.getDeviceStatus(deviceId) }.getOrDefault(-1))
        val deadline = System.currentTimeMillis() + 25_000
        while (System.currentTimeMillis() < deadline) {
            val info = runCatching {
                QvP2PV2Api.addPortByP2P(deviceId, protocolNo, -1, 0, DEFAULT_P2P_TYPE, DEFAULT_MTU, DEFAULT_TIMEOUT_SEC)
            }.getOrNull()
            val port = info?.port ?: -1
            if (port > 0) {
                lastPort = port
                Log.d(TAG, "$label port=$port type=${info?.type} waited=${waited}ms")
                return StreamPort(port, 0)
            }
            lastError = port
            Log.d(TAG, "addPortByP2P $label rc=$port")
            if (port == -3) return StreamPort(-1, -3)
            if (port == -2 && ++busyTries >= 3) return StreamPort(-1, -2)
            // Reintento corto: el enlace suele tardar menos de un segundo en estar
            // listo y esperar 1 s por vuelta sumaba varios segundos al arranque.
            Thread.sleep(400)
        }
        lastError = if (lastError == -1) -1 else lastError
        return StreamPort(-1, lastError)
    }

    /** Releases the port mapping and the P2P leg, mirroring DeviceHelper.CleanPort. */
    fun closePort(deviceId: String) {
        val port = lastPort
        lastPort = -1
        runCatching {
            if (port > 0) QvP2PV2Api.deletePortByP2P(port)
            QvP2PV2Api.delCurrentDevP2pLink(deviceId)
            Log.d(TAG, "closePort port=$port device=$deviceId")
        }
    }

    data class StreamPort(val port: Int, val error: Int) {
        val ok: Boolean get() = port > 0

        fun describe(): String = when (error) {
            -2 -> "device busy (-2)"
            -3 -> "device low power (-3)"
            -4 -> "p2p busy (-4)"
            else -> "port=$port error=$error"
        }
    }

    private fun copyAsset(context: Context, name: String, dir: File): File {
        val target = File(dir, name)
        context.assets.open(name).use { input ->
            target.outputStream().use { output -> input.copyTo(output) }
        }
        return target
    }

    /** Un timbre visto por el descubrimiento LAN del SDK. */
    data class LanDevice(
        val ip: String,
        val streamPort: Int,
        val cgiPort: Int,
        val uid: String,
        val type: String,
        val version: String,
    )

    /**
     * Pregunta a la red local si hay timbres. El SDK escucha el anuncio del
     * aparato y devuelve ip, puerto de video y puerto CGI. Solo tiene sentido
     * estando en la misma red que el timbre.
     */
    fun lanScan(context: Context, caPem: ByteArray, timeoutMs: Long = 5_000): List<LanDevice> {
        if (!ensureClient(context, caPem)) return emptyList()
        return runCatching {
            QvP2PV2Api.lanSearchDeviceClear()
            val rc = QvP2PV2Api.lanSearchDeviceStart()
            Log.d(TAG, "lanSearch start rc=$rc")
            val found = LinkedHashMap<String, LanDevice>()
            val deadline = System.currentTimeMillis() + timeoutMs
            while (System.currentTimeMillis() < deadline) {
                Thread.sleep(500)
                val list = runCatching { QvP2PV2Api.lanSearchDeviceGet() }.getOrNull() ?: continue
                for (info in list) {
                    val ip = intToIp(info.ip)
                    found[ip] = LanDevice(
                        ip = ip,
                        streamPort = info.streamPort,
                        cgiPort = info.cgiPort,
                        uid = info.uid ?: "",
                        type = info.type ?: "",
                        version = info.version ?: "",
                    )
                }
            }
            runCatching { QvP2PV2Api.lanSearchDeviceStop() }
            Log.d(TAG, "lanScan found=${found.size} " + found.values.joinToString { "${it.ip}:${it.streamPort}" })
            found.values.toList()
        }.getOrElse {
            Log.d(TAG, "lanScan fail ${it.javaClass.simpleName}: ${it.message}")
            emptyList()
        }
    }

    private fun intToIp(value: Int): String = listOf(
        value and 0xFF,
        (value shr 8) and 0xFF,
        (value shr 16) and 0xFF,
        (value shr 24) and 0xFF,
    ).joinToString(".")

}
