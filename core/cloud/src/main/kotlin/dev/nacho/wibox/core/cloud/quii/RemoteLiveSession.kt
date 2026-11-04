package dev.nacho.wibox.core.cloud.quii

import dev.nacho.wibox.core.cloud.diag.LiveDiag
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow

/**
 * Sesion live REMOTA completa (timbre a kilometros, camino cloud):
 *
 * 1. UstDiscovery.queryServices: query-hlrv2 -> url MQTT UST (p2papp) + natcheck.
 * 2. UstMqtt.connectAndP2PConnect: credenciales MQTT descifradas con las tablas UST,
 *    register + sub-device-state + p2pconnect -> P2PConnectInfo + session_flag.
 * 3. P2PProbe.runProbe: sondeo UDP 0xA4 de los peers (lan/p2p/trans) y eleccion.
 * 4. RbUdpTunnel: tunel RB-UDP sobre el peer elegido (maquina de estados completa).
 * 5. QuiiLiveSession: protocolo QUII (setup/play/keepalive AES-256) dentro del tunel.
 *
 * Credenciales: authCode y dataEncodeKey salen de UserAuthCloud.login + getDeviceToken
 * (misma sesion userauth del app). La password QUII del play es sha256hex(authCode).
 */
class RemoteLiveSession(
    private val serviceUrl: String,
    private val clientId: String,
    private val deviceId: String,
    private val channel: Int,
    private val stream: Int,
    private val authCode: String,
    private val dataEncodeKey: String,
    private val caPem: ByteArray,
    private val tables: UstCrypto.Tables,
    private val dynamicPassword: String? = null,
    private val unlockPassword: String? = null,
    private val oem: String = "G0021",
    private val app: Int = 2021,
    private val sessionToken: String = "",
    private val p2pAttempts: Int = 20,
) {
    class ConnectException(message: String) : Exception(message)

    private var probeSocket: DatagramSocket? = null
    private var mqtt: UstMqtt? = null
    private var tunnel: RbUdpTunnel? = null
    private var session: QuiiLiveSession? = null

    val live: QuiiLiveSession
        get() = session ?: throw ConnectException("sesion live no abierta")

    /** Abre la cadena completa y devuelve la sesion QUII viva (frames H264/G711A). */
    suspend fun open(scope: CoroutineScope): QuiiLiveSession {
        // 1) Servicios cloud (p2papp -> MQTT UST, natcheck).
        val services = try {
            UstDiscovery.queryServices(serviceUrl, clientId, oem, app, caPem)
        } catch (e: Exception) {
            android.util.Log.d("WIBOX-LIVE", "DISCOVERY FAIL ${e.message}")
            LiveDiag.add("discovery fallo: ${e.message}")
            throw ConnectException("query-hlrv2 fallo: ${e.message}")
        }
        android.util.Log.d("WIBOX-LIVE", "DISCOVERY OK mqtt=${services.ustMqttUrl}")
        LiveDiag.add("discovery ok")

        // 2) Socket UDP local (el puerto via en el p2pconnect/update-netinfo).
        val socket = DatagramSocket()
        probeSocket = socket

        val localIps = enumerateLocalIpv4s()
        val ust = try {
            val mqttUrl = services.ustMqttUrl
                ?: throw ConnectException("query-hlrv2 sin url p2papp")
            val version = if (sessionToken.isNotBlank()) "v1.1" else "v3.2.0"
            UstMqtt(
                ustAddress = mqttUrl,
                tables = tables,
                clientId = clientId,
                deviceId = deviceId,
                caPem = caPem,
                oem = oem,
                app = app,
                version = version,
                sessionToken = sessionToken,
                p2pAttempts = p2pAttempts,
            )
                .also { println("MQTT STEP ctor ok version=$version session=${sessionToken.isNotBlank()}") }
        } catch (e: ConnectException) {
            socket.close()
            throw e
        } catch (e: Exception) {
            socket.close()
            throw ConnectException("ust init: ${e.message}")
        }
        mqtt = ust

        val connected = try {
            ust.connectAndP2PConnect(localIps, socket.localPort, discoverPublicIp())
        } catch (e: Exception) {
            android.util.Log.d("WIBOX-LIVE", "MQTT/P2P FAIL ${e.message}")
            LiveDiag.add("p2pconnect fallo: ${e.message}")
            close()
            val reason = (e as? org.eclipse.paho.client.mqttv3.MqttException)?.reasonCode
            throw ConnectException("p2pconnect fallo: ${e.message} reason=$reason cause=${e.cause?.cause ?: e.cause}")
        }
        android.util.Log.d("WIBOX-LIVE", "MQTT OK sessionFlag=${connected.sessionFlag} pub=${connected.response.publicIp}:${connected.response.publicUdpPort} utd=${connected.response.utdPublicIp}:${connected.response.utdPublicUdpPort}")
        LiveDiag.add("mqtt ok flag=${connected.sessionFlag}")

        // 2b) Natcheck: descubrir NUESTRO mapping UDP externo y republicar netinfo.
        runCatching {
            val natUrl = services.ustTestAddress
            if (natUrl != null) {
                val nh = natUrl.removePrefix("udp://").substringBefore(":")
                val np = natUrl.substringAfterLast(":").trimEnd('/').toIntOrNull() ?: 8300
                var mapped: java.net.InetSocketAddress? = null
                repeat(4) {
                    if (mapped != null) return@repeat
                    val pkt = P2PProbe.buildP2pTestPacket(0, connected.sessionFlag, connected.response.responseSessionId, socket.localPort, nh, np, 0)
                    socket.soTimeout = 1200
                    val addr = java.net.InetSocketAddress(java.net.InetAddress.getByName(nh), np)
                    socket.send(java.net.DatagramPacket(pkt, pkt.size, addr))
                    val buf = ByteArray(4096)
                    val dp = java.net.DatagramPacket(buf, buf.size)
                    try {
                        socket.receive(dp)
                        val parsed = P2PProbe.parseP2pTestResponse(dp.data.copyOf(dp.length))
                        val src = java.net.InetSocketAddress(dp.address, dp.port)
                        if (parsed != null && parsed.address.isNotBlank()) {
                            mapped = java.net.InetSocketAddress(java.net.InetAddress.getByName(parsed.address), parsed.port)
                            println("WIBOX NATCHECK mapped=" + mapped + " src=" + src)
                        }
                    } catch (e: java.net.SocketTimeoutException) { }
                }
                val m = mapped
                if (m != null) {
                    ust.publishNetinfo(localIps, socket.localPort, m.address.hostAddress ?: "", m.port)
                }
            }
        }.onFailure { println("WIBOX NATCHECK fail " + it.message) }

        // 3) Sondeo UDP 0xA4 de los peers y eleccion del mejor camino.
        val probe = try {
            P2PProbe.runProbeWithHandshake(
                socket = socket,
                info = connected.response,
                targetSessionFlag = connected.sessionFlag,
                responseSessionId = connected.response.responseSessionId,
                localUdpPort = socket.localPort,
            )
        } catch (e: Exception) {
            android.util.Log.d("WIBOX-LIVE", "PROBE FAIL ${e.message}")
            LiveDiag.add("probe fallo: ${e.message}")
            close()
            throw ConnectException("probe fallo: ${e.message}")
        }
        println("WIBOX PROBE OK peer=" + probe.peerAddr + " rank=" + probe.rank + " acked=" + probe.acked)
        android.util.Log.d("WIBOX-LIVE", "PROBE OK peer=${probe.peerAddr} rank=${probe.rank} acked=${probe.acked}")
        LiveDiag.add("probe ok rank=${probe.rank}")

        // 3b) Handshake activo para peer publico (el device sondea primero hacia nosotros).
        val peer: InetSocketAddress = if (probe.rank >= 3 || probe.acked) {
            probe.peerAddr
        } else {
            try {
                val transportPeer = P2PProbe.runActiveHandshake(
                    socket = socket,
                    sessionFlag = connected.sessionFlag,
                    timeoutMs = 8_000,
                    peerHint = probe.peerAddr,
                )
                android.util.Log.d("WIBOX-LIVE", "HANDSHAKE OK peer=$transportPeer")
                transportPeer
            } catch (e: Exception) {
                android.util.Log.d("WIBOX-LIVE", "HANDSHAKE fallback hint ${e.message}")
                probe.peerAddr
            }
        }
        val rbTunnel = RbUdpTunnel(
            socket,
            peer,
            connected.sessionFlag,
            dataEncodeKey = dataEncodeKey.toByteArray(Charsets.UTF_8),
            dynamicPassword = dynamicPassword,
            quiiChannel = channel,
            quiiStream = stream,
        )
        tunnel = rbTunnel
        rbTunnel.start(scope)
        try {
            rbTunnel.awaitConnected()
        } catch (e: Exception) {
            android.util.Log.d("WIBOX-LIVE", "TUNNEL FAIL ${e.message}")
            LiveDiag.add("tunel fallo: ${e.message}")
            close()
            throw ConnectException("tunel no conecto: ${e.message}")
        }
        android.util.Log.d("WIBOX-LIVE", "TUNNEL OK")
        LiveDiag.add("tunel ok")

        // 5) QUII live dentro del tunel (setup/play/keepalive enmarcados por el tunel).
        val transport = object : QuiiTransport {
            override suspend fun send(bytes: ByteArray) = rbTunnel.sendQuii(bytes)
            override suspend fun sendSetup(): Boolean = rbTunnel.awaitQuiiDestId()
            override suspend fun sendPlay(): Boolean = rbTunnel.sendQuiiPlayFramed()
            override suspend fun sendKeepalive(): Boolean = rbTunnel.sendQuiiKeepaliveFramed()
            override fun incoming(): Flow<ByteArray> = rbTunnel.quiiIncoming
            override fun close() = rbTunnel.close()
        }
        val live = QuiiLiveSession(
            transport = transport,
            config = QuiiLiveConfig(
                username = "adminapp2",
                password = QuiiCrypto.encodeDevicePassword(authCode),
                channel = channel,
                stream = stream,
                cryptoKey = dataEncodeKey.toByteArray(Charsets.UTF_8),
            ),
            scope = scope,
        )
        session = live
        val result = live.open()
        android.util.Log.d("WIBOX-LIVE", "QUII open=${result::class.java.simpleName}")
        LiveDiag.add("quii ${result::class.java.simpleName}")
        if (result is QuiiLiveSession.OpenResult.Rejected) {
            close()
            throw ConnectException(
                "play rechazado status=${result.status} crypto=${result.cryptoMode} sha=${result.shaProfile}",
            )
        }
        rbTunnel.markPlaySent()
        return live
    }

    /** Apertura remota por DeviceOrder 4 sobre el comando transparente 0xFE. */
    suspend fun unlockDoor(
        lockNumber: Int = 1,
        unlockChannel: Int = 1,
        passwordOverride: String? = null,
        passwordSha256: Boolean = true,
    ): Int {
        val rawPassword = passwordOverride ?: unlockPassword
            ?: throw ConnectException("contraseña de apertura no configurada")
        val effectivePassword = if (passwordSha256) {
            dev.nacho.wibox.core.cloud.quii.QuiiCrypto.encodeDevicePassword(rawPassword)
        } else rawPassword
        val password = effectivePassword.toByteArray(Charsets.UTF_8)
        val body = ByteArray(16 + password.size)
        body[0] = lockNumber.toByte()
        body[1] = 0
        body[2] = unlockChannel.toByte()
        body[3] = if (passwordSha256) 1 else 0
        password.copyInto(body, 16)
        val current = session ?: throw ConnectException("sesion live no abierta")
        val resp = current.sendTransparent(body, command = 4)
        println("WIBOX-UNLOCK RESP ${resp.joinToString("") { "%02x".format(it) }}")
        if (resp.size < 2) return -1
        if (resp[0].toInt() == 0) return 0
        return if (resp[1].toInt() == 2) -10029 else -1
    }

    suspend fun close() {
        try { session?.close() } catch (_: Exception) {}
        try { tunnel?.close() } catch (_: Exception) {}
        try { mqtt?.close() } catch (_: Exception) {}
        try { probeSocket?.close() } catch (_: Exception) {}
        session = null
        tunnel = null
        mqtt = null
        probeSocket = null
    }

    private fun enumerateLocalIpv4s(): List<String> {
        val out = mutableListOf<String>()
        for (nif in java.net.NetworkInterface.getNetworkInterfaces()) {
            for (addr in nif.inetAddresses) {
                if (!addr.isLoopbackAddress && addr is java.net.Inet4Address) out.add(addr.hostAddress ?: "")
            }
        }
        return out.filter { it.isNotEmpty() }
    }

    private fun discoverPublicIp(): String = try {
        val conn = java.net.URL("https://api.ipify.org").openConnection() as java.net.HttpURLConnection
        conn.connectTimeout = 5000
        conn.readTimeout = 5000
        conn.inputStream.bufferedReader().readText().trim().also {
            android.util.Log.d("WIBOX-LIVE", "PUBLIC IP $it")
        }
    } catch (e: Exception) {
        android.util.Log.d("WIBOX-LIVE", "PUBLIC IP FAIL ${e.message}")
        ""
    }
}