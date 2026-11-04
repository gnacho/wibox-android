package dev.nacho.wibox.core.cloud.quii

import java.io.InputStream
import java.security.KeyStore
import java.security.cert.CertificateFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Descubrimiento de servicios cloud (query-hlrv2) - port de quii-helper
 * cloud/services/{discovery,query_xml,query_parser,config_apply}.py.
 *
 * POST XML a {service_url}/mst/query con envelope command=query-hlrv2 y content
 * server-type "p2papp,natcheck,appinfo". Respuesta: servers[] con url/uri/param.
 * - p2papp: url+uri(+/?param) = URL del broker MQTT UST (con srcid y credenciales cifradas).
 * - natcheck: url = servidor de test UDP (ust_test_address).
 */
data class UstServiceEntry(
    val serverType: String,
    val queryResult: Int,
    val regionId: Int,
    val url: String,
    val uri: String,
    val param: String,
)

data class UstServices(
    val p2papp: UstServiceEntry?,
    val natcheck: UstServiceEntry?,
) {
    val ustMqttUrl: String?
        get() = p2papp?.let { s ->
            if (s.param.isNotBlank()) "${s.url}${s.uri}/?${s.param}" else "${s.url}${s.uri}"
        }
    val ustTestAddress: String? get() = natcheck?.url
}

object UstDiscovery {
    private const val QUERY_PATH = "/mst/query"

    fun buildQueryXml(
        clientId: String,
        oem: String,
        app: Int,
        serverTypes: List<String> = listOf("p2papp", "natcheck", "appinfo"),
        regionId: String = "0",
        seq: Int = 0,
    ): String {
        val types = serverTypes.joinToString(",")
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
            "<envelope>" +
            "<header><flag>tdkcloud</flag><command>query-hlrv2</command><seq>$seq</seq></header>" +
            "<content>" +
            "<server-type>$types</server-type>" +
            "<oem>$oem</oem>" +
            "<devid></devid>" +
            "<public-ip></public-ip>" +
            "<client-id>$clientId</client-id>" +
            "<regionid>$regionId</regionid>" +
            "<version>4456</version>" +
            "</content></envelope>"
    }

    fun parseServices(xml: String): List<UstServiceEntry> {
        val factory = DocumentBuilderFactory.newInstance()
        val doc = factory.newDocumentBuilder().parse(xml.byteInputStream(Charsets.UTF_8))
        val servers = doc.getElementsByTagName("server")
        val out = mutableListOf<UstServiceEntry>()
        for (i in 0 until servers.length) {
            val el = servers.item(i) as org.w3c.dom.Element
            fun text(tag: String): String = el.getElementsByTagName(tag).item(0)?.textContent?.trim().orEmpty()
            out.add(
                UstServiceEntry(
                    serverType = text("server-type"),
                    queryResult = text("query-result").toIntOrNull() ?: 0,
                    regionId = text("regionid").toIntOrNull() ?: 0,
                    url = text("url"),
                    uri = text("uri"),
                    param = text("param"),
                ),
            )
        }
        return out
    }

    class ServiceQueryException(message: String) : Exception(message)

    /**
     * Consulta los servicios al cloud. caPem = CA del cloud (la misma ca.pem del canal
     * userauth). mTLS con cert cliente NO se usa (quii-helper lo exige por su
     * implementacion; el app stock va con la CA del sistema).
     */
    fun queryServices(
        serviceUrl: String,
        clientId: String,
        oem: String,
        app: Int,
        caPem: ByteArray,
        connectTimeoutMs: Int = 10_000,
    ): UstServices {
        val body = buildQueryXml(clientId = clientId, oem = oem, app = app)
        val url = serviceUrl.trimEnd('/') + QUERY_PATH
        val host = java.net.URI(serviceUrl).host

        val cf = CertificateFactory.getInstance("X.509")
        val ks = KeyStore.getInstance(KeyStore.getDefaultType())
        ks.load(null, null)
        ks.setCertificateEntry("cloud", cf.generateCertificate(caPem.inputStream()))
        val tmf = TrustManagerFactory.getInstance("X509")
        tmf.init(ks)
        val tm = tmf.trustManagers.first() as X509TrustManager
        val ssl = SSLContext.getInstance("TLS")
        ssl.init(null, arrayOf(tm), null)

        val conn = (java.net.URI(url).toURL().openConnection() as javax.net.ssl.HttpsURLConnection)
        conn.sslSocketFactory = ssl.socketFactory
        // El cert del server Qualvision va sin subjectAltNames: el pin de CA ya valida
        // la identidad, la verificacion de hostname estandar hay que saltarla.
        conn.setHostnameVerifier { _, _ -> true }
        conn.connectTimeout = connectTimeoutMs
        conn.readTimeout = connectTimeoutMs
        conn.requestMethod = "POST"
        conn.doOutput = true
        conn.setRequestProperty("Content-Type", "application/xml;charset=utf-8")
        conn.setRequestProperty("Host", host)
        conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
        val response: String = (conn.content as InputStream).readBytes().toString(Charsets.UTF_8)
        if (conn.responseCode !in 200..299) throw ServiceQueryException("query-hlrv2 http ${conn.responseCode}")

        val entries = parseServices(response)
        val services = UstServices(
            p2papp = entries.firstOrNull { it.serverType == "p2papp" },
            natcheck = entries.firstOrNull { it.serverType == "natcheck" },
        )
        if (services.p2papp == null || services.natcheck == null) {
            throw ServiceQueryException("query-hlrv2 did not return p2papp/natcheck")
        }
        return services
    }

    /**
     * Decodifica credenciales del broker MQTT UST desde su URL (params username/password,
     * XML-escaped con &amp;). La clave se deriva de [srcid] = nuestro client_id (el server
     * lo conoce del query-hlrv2; el param srcid NO viene en la URL, verificado en vivo:
     * params = username, password, sVer=1.0.0, JwtExp, ExpSec).
     */
    fun decodeMqttCredentials(
        mqttUrl: String,
        tables: UstCrypto.Tables,
        srcid: String,
    ): Triple<String, String, String> {
        val uri = java.net.URI(mqttUrl)
        val rawQuery = uri.rawQuery.replace("&amp;", "&")
        val params = rawQuery.split("&").mapNotNull {
            val idx = it.indexOf('=')
            if (idx <= 0) null else it.substring(0, idx) to it.substring(idx + 1)
        }.toMap()
        // Base64 en params de URL: decodificar SOLO %XX (URLDecoder convertira '+' en
        // espacio y corromperia el base64 -> "Illegal base64 character 20").
        fun percentDecode(v: String): String {
            val sb = StringBuilder(v.length)
            var i = 0
            while (i < v.length) {
                val c = v[i]
                if (c == '%' && i + 2 < v.length) {
                    sb.append(v.substring(i + 1, i + 3).toInt(16).toChar())
                    i += 3
                } else {
                    sb.append(c)
                    i += 1
                }
            }
            return sb.toString()
        }
        val username = percentDecode(params["username"].orEmpty())
        val password = percentDecode(params["password"].orEmpty())
        if (srcid.isBlank()) throw ServiceQueryException("mqtt credentials: empty srcid")
        val decodedUser = UstCrypto.decodeCiphertext(username, srcid, tables)
        val decodedPass = UstCrypto.decodeCiphertext(password, srcid, tables)
        return Triple(decodedUser, decodedPass, srcid)
    }
}
