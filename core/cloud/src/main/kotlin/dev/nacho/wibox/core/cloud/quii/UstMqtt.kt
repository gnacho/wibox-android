package dev.nacho.wibox.core.cloud.quii

import java.net.InetAddress
import kotlinx.serialization.json.JsonObject
import java.net.URI
import java.net.URLEncoder
import java.security.SecureRandom
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import org.eclipse.paho.client.mqttv3.MqttClient
import org.eclipse.paho.client.mqttv3.MqttConnectOptions
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence

/**
 * Canal MQTT UST del cloud (servicio p2papp de query-hlrv2): registro del cliente,
 * sub-device-state y p2pconnect por el topic app/ust/json/{client_id}. Mensajes JSON
 * en claro; credenciales del broker cifradas en el param de la URL (UstCrypto).
 *
 * Flujo portado de quii-helper (direct/p2p_mqtt.py + protocols/mqtt):
 * unregister -> register -> wait register -> wait device online -> p2pconnect
 * -> wait response (session-flag) -> update-netinfo.
 */
class UstMqtt(
    private val ustAddress: String,
    private val tables: UstCrypto.Tables,
    private val clientId: String,
    private val deviceId: String,
    private val caPem: ByteArray,
    private val oem: String = "G0021",
    private val app: Int = 2021,
    private val version: String = "v3.2.0",
    /** Token de sesion del login userauth; el SDK nativo lo envia en el header del UST. */
    private val sessionToken: String = "",
    /** Override de force-trans en p2pconnect (experimento; el stock usa 0). */
    private val forceTrans: Int = 0,
    /** Campo awaken-id opcional en device-awaken (experimento). */
    private val awakenId: String? = null,
    /** Campo usrkey opcional en p2pconnect (experimento). */
    private val usrkey: String? = null,
    /** Numero de intentos de p2pconnect. */
    private val p2pAttempts: Int = 20,
    /** Tipo de cliente del header (experimento; el id real usa type 3). */
    private val clientType: String = "0",
) {
    data class Connected(val response: P2PConnectInfo, val sessionFlag: String)

    private val rng = SecureRandom()
    private val userdata: String = (10_000_000_000L + (rng.nextLong() % 89_999_999_999L).let { if (it < 0) -it else it }).toString()
    private var client: MqttClient? = null
    private val inbox = LinkedBlockingQueue<Pair<String, String>>()
    private var sessionFlagServerIp: Int = 0
    private var sessionFlagServerPort: Int = 0
    private var sessionFlagCounter: Int = 0

    /** Contenido de device-awaken; anade awaken-id cuando el experimento lo pide. */
    private fun awakenContent(): Map<String, Any?> {
        val content = linkedMapOf<String, Any?>("devid" to deviceId)
        if (!awakenId.isNullOrBlank()) content["awaken-id"] = awakenId
        return content
    }

    /** Conecta y ejecuta el flujo completo hasta obtener la respuesta p2pconnect. */
    fun connectAndP2PConnect(localIps: List<String>, localUdpPort: Int, publicIp: String = ""): Connected {
        val decoded = UstDiscovery.decodeMqttCredentials(ustAddress, tables, clientId)
        val uri = URI(ustAddress.substringBefore("/?").substringBefore("?"))
        val host = uri.host ?: error("ust_address sin host: $ustAddress")
        val port = if (uri.port > 0) uri.port else 8883
        val user = decoded.first
        val pass = decoded.second
        sessionFlagServerIp = InetAddress.getByName(host).address.let { b ->
            ((b[0].toInt() and 0xFF) shl 24) or ((b[1].toInt() and 0xFF) shl 16) or
                ((b[2].toInt() and 0xFF) shl 8) or (b[3].toInt() and 0xFF)
        }
        sessionFlagServerPort = port

        val mqttClientId = "app_${clientId}_${userdata}_"
        val mqtt = MqttClient("ssl://$host:$port", mqttClientId, MemoryPersistence())
        // TLS con la CA Qualvision (cert del broker sin SAN y no en el truststore del sistema).
        val cf = java.security.cert.CertificateFactory.getInstance("X.509")
        val ks = java.security.KeyStore.getInstance(java.security.KeyStore.getDefaultType())
        ks.load(null, null)
        ks.setCertificateEntry("qualvision", cf.generateCertificate(caPem.inputStream()))
        val tmf = javax.net.ssl.TrustManagerFactory.getInstance("X509")
        tmf.init(ks)
        // TrustManager que valida la cadena contra la CA Qualvision PERO sin
        // verificacion de hostname (el cert del broker no tiene subjectAltNames).
        val delegate = tmf.trustManagers.first() as javax.net.ssl.X509TrustManager
        val tm = object : javax.net.ssl.X509TrustManager {
            override fun checkClientTrusted(chain: Array<java.security.cert.X509Certificate>, authType: String) =
                delegate.checkClientTrusted(chain, authType)
            override fun checkServerTrusted(chain: Array<java.security.cert.X509Certificate>, authType: String) =
                delegate.checkServerTrusted(chain, authType)
            override fun getAcceptedIssuers(): Array<java.security.cert.X509Certificate> = delegate.acceptedIssuers
        }
        val sslCtx = javax.net.ssl.SSLContext.getInstance("TLS")
        sslCtx.init(null, arrayOf(tm), null)
        val opts = MqttConnectOptions().apply {
            userName = user
            password = pass.toCharArray()
            isCleanSession = true
            isAutomaticReconnect = true
            connectionTimeout = 10
            keepAliveInterval = 30
            socketFactory = sslCtx.socketFactory
            // El cert del broker no tiene SAN: el hostname check lo hace PAHO (no JSSE).
            isHttpsHostnameVerificationEnabled = false
        }
        val willHeader = linkedMapOf(
            "flag" to "tdkcloud",
            "version" to version,
            "command" to "unregister",
            "client" to linkedMapOf("id" to clientId, "type" to clientType, "oem" to oem, "app" to app.toString()),
            "userdata" to userdata,
        )
        opts.setWill(
            "app/ust/json/$clientId",
            UstJson.dump(willHeader).toByteArray(),
            0,
            false,
        )
        try {
            mqtt.connect(opts)
        } catch (e: org.eclipse.paho.client.mqttv3.MqttException) {
            android.util.Log.d("WIBOX-LIVE", "MQTT EXC reason=" + e.reasonCode + " msg=" + e.message + " cause=" + e.cause)
            throw e
        }
        client = mqtt
        val topic = "app/ust/json/$clientId"
        println("MQTT STEP subscribe=$topic")
        mqtt.subscribe(topic, 0)
        // El server responde por el topic con el client_id primero (bootstrap.py L63 quii-helper).
        mqtt.subscribe("$clientId/ust/json", 0)
        println("MQTT subscribed OK")
        // mqtt.subscribe("#", 0) // DEBUG (desactivado: el broker puede expulsar por wildcard)
        mqtt.setCallback(object : org.eclipse.paho.client.mqttv3.MqttCallback {
            override fun connectionLost(cause: Throwable?) {
                println("MQTT CONNECTION LOST cause=$cause")
            }
            override fun deliveryComplete(token: org.eclipse.paho.client.mqttv3.IMqttDeliveryToken?) {}
            override fun messageArrived(t: String, message: org.eclipse.paho.client.mqttv3.MqttMessage) {
                println("MQTT RX topic=$t payload=" + String(message.payload).take(900))
                println("WIBOX-LIVE MQTT RX t=$t payload=" + String(message.payload))
                inbox.offer(t to String(message.payload, Charsets.UTF_8))
            }
        })

        publishHeaderOnly("unregister")
        Thread.sleep(100)
        publishHeaderOnly("register")
        waitCommand("register", timeoutMs = 10_000)
        publish("sub-device-state", linkedMapOf("devid" to listOf(deviceId)))
        // Wake del timbre low-power: mensaje UST "device-awaken" (RequMsgUstAwaken del
        // libqv-p2p-v2.so oficial) con content{devid}. El server pagina al device por NTS;
        // la respuesta llega como device-state con estado awaken online/offline + awaken-id.
        publish("device-awaken", awakenContent())
        waitDeviceOnline(timeoutMs = 10_000)

        val sessionFlag = buildSessionFlag()
        val requestSessionId = (((rng.nextInt().toLong() and 0xFFFF) shl 16) or (rng.nextInt().toLong() and 0xFFFF)) and 0xFFFFFFFFL
        val content = linkedMapOf<String, Any?>(
            "devid" to deviceId,
            "session-flag" to sessionFlag,
            "requ-session-id" to requestSessionId,
            "force-trans" to forceTrans,
            "kcpParam" to linkedMapOf<String, Any?>("mode" to "normal"),
            "devTrans" to linkedMapOf<String, Any?>("monChn" to -1),
            "devType" to "normal",
            "devSubState" to "awakened",
        )
        if (!usrkey.isNullOrBlank()) content["usrkey"] = usrkey
        // El despertar del device (NTS push) lleva segundos: bucle de intentos p2pconnect
        // (quii-helper run_mqtt_p2pconnect_attempt(attempt_index)).
        var response: P2PConnectInfo? = null
        for (attempt in 1..p2pAttempts) {
            publish("p2pconnect", content)
            println("MQTT STEP p2pconnect attempt=$attempt")
            val r = waitP2PConnectResponse(sessionFlag, timeoutMs = 12_000)
            if (r != null && r.hasProbeTargets()) { response = r; break }
            publish("sub-device-state", linkedMapOf("devid" to listOf(deviceId)))
        // Wake del timbre low-power: mensaje UST "device-awaken" (RequMsgUstAwaken del
        // libqv-p2p-v2.so oficial) con content{devid}. El server pagina al device por NTS;
        // la respuesta llega como device-state con estado awaken online/offline + awaken-id.
        publish("device-awaken", awakenContent())
        }
        val finalResponse = response ?: error("Timeout esperando p2pconnect response (session-flag=$sessionFlag)")
        publishUpdateNetinfo(localIps, localUdpPort, publicIp, publicUdpPort = localUdpPort)
        return Connected(response, sessionFlag)
    }

    private fun publishHeaderOnly(command: String) = publish(command, null)

    private fun publish(command: String, content: Map<String, Any?>?) {
        val header = linkedMapOf<String, Any?>(
            "flag" to "tdkcloud",
            "version" to version,
            "command" to command,
            "client" to linkedMapOf<String, Any?>(
                "id" to clientId,
                "type" to clientType,
                "oem" to oem,
                "app" to app.toString(),
            ),
        )
        // El SDK nativo (RequMsgHeader::BuildJson) escribe siempre userdata
        header["userdata"] = userdata
        // El SDK nativo (RequMsgHeader::BuildJson) envia version v1.1 + seq + session
        if (sessionToken.isNotBlank()) {
            header["seq"] = 0
            header["session"] = sessionToken
        }
        val payload = linkedMapOf<String, Any?>("header" to header)
        if (content != null) payload["content"] = content
        val json = dumpJson(payload)
        val topic = "app/ust/json/$clientId"
        val bytes = json.toByteArray(Charsets.UTF_8)
        val c = client ?: error("MQTT no conectado")
        if (!c.isConnected) {
            runCatching { c.reconnect() }
                .onFailure { println("WIBOX-LIVE MQTT reconnect fail ${it.message}") }
        }
        try {
            c.publish(topic, bytes, 0, false)
        } catch (e: org.eclipse.paho.client.mqttv3.MqttException) {
            println("WIBOX-LIVE MQTT publish retry cmd=$command cause=${e.message}")
            if (!c.isConnected) runCatching { c.reconnect() }
            c.publish(topic, bytes, 0, false)
        }
    }

    /** drain + rebuild: los mensajes MQTT UST suelen traer wrapper {header, content}. */
    private fun pollMessage(timeoutMs: Long): Pair<String, JsonObject>? {
        val raw = inbox.poll(timeoutMs, TimeUnit.MILLISECONDS) ?: return null
        val parsed = parseJson(raw.second) ?: return null
        return raw.first to extractInnerJson(parsed)
    }

    private fun waitCommand(command: String, timeoutMs: Long): JsonObject? {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            val msg = pollMessage(500) ?: continue
            val json = msg.second
            if (json["header"]?.jsonObjectOrNull()?.get("command")?.jsonStringOrNull() == command ||
                json["command"]?.jsonStringOrNull() == command
            ) return json
        }
        error("Timeout esperando $command")
    }

    private fun waitDeviceOnline(timeoutMs: Long) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            val msg = pollMessage(500) ?: continue
            val json = msg.second
            val text = json.toString()
            if (text.contains(deviceId) && text.contains("\"online\"")) return
        }
        // No bloquea el flujo si el cloud no emite el estado: el p2pconnect puede responder igual.
    }

    private fun waitP2PConnectResponse(sessionFlag: String, timeoutMs: Long): P2PConnectInfo? {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            val msg = pollMessage(500) ?: continue
            val json = msg.second
            val content = json["content"]?.jsonObjectOrNull() ?: continue
            val rxFlag = content["session-flag"]?.jsonStringOrNull()
            // El server devuelve el flag truncado a 63 chars (63 vs 64): match por prefijo
            val flagMatch = rxFlag != null && (sessionFlag.startsWith(rxFlag) || rxFlag.startsWith(sessionFlag))
            if (!flagMatch) {
                println("P2P SKIP: rxFlag=" + rxFlag + " expected=" + sessionFlag +
                    " pub-ip=" + content["pub-ip"]?.jsonStringOrNull() +
                    " contentKeys=" + content.keys + " full=" + json.toString().take(700))
                continue
            }
            val response = try {
                p2pConnectInfoFrom(content)
            } catch (e: Exception) {
                println("P2P PARSE EXC " + e::class.java.simpleName + ": " + e.message)
                continue
            }
            println("P2P FLAG OK targets-check: pub-ip=" + response.publicIp + " port=" + response.publicUdpPort)
            if (!response.hasProbeTargets()) {
                android.util.Log.d("WIBOX-LIVE", "p2p response sin candidates (esperando otra): $content")
                continue
            }
            return response
        }
        return null
    }

    fun publishNetinfo(localIps: List<String>, localUdpPort: Int, publicIp: String, publicUdpPort: Int = localUdpPort) {
        publishUpdateNetinfo(localIps, localUdpPort, publicIp, publicUdpPort)
    }

    private fun publishUpdateNetinfo(localIps: List<String>, localUdpPort: Int, publicIp: String, publicUdpPort: Int) {
        val content = linkedMapOf<String, Any?>(
            "nettype" to 4,
            "netsubtype" to 0,
            "pub-ip" to publicIp,
            "pub-udpport" to publicUdpPort,
            "loc-ip" to localIps,
            "loc-udp-port" to localUdpPort,
        )
        publish("update-netinfo", content)
    }

    /**
     * session_flag de 64 chars: prefijo letras random + device_id[4:] + base
     * %08x%05d%05d (ip_u32/puerto/contador) alineado a la derecha.
     */
    private fun buildSessionFlag(): String {
        val base = String.format(
            "%08x%05d%05d",
            sessionFlagServerIp,
            sessionFlagServerPort,
            (++sessionFlagCounter) and 0xFFFF,
        )
        val devSuffix = if (deviceId.length > 4) deviceId.substring(4) else deviceId
        val prefixLen = (64 - base.length - devSuffix.length).coerceAtLeast(0)
        val letters = StringBuilder()
        while (letters.length < prefixLen) letters.append('a' + rng.nextInt(26))
        return (letters.toString() + devSuffix + base).take(64)
    }

    fun close() {
        runCatching { client?.disconnect() }
        runCatching { client?.close() }
        client = null
    }

    companion object {
        fun encodeParam(v: String) = URLEncoder.encode(v, "UTF-8")
    }
}
