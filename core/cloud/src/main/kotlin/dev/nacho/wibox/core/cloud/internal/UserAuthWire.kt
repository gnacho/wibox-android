package dev.nacho.wibox.core.cloud.internal

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.prepareGet
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import android.util.Log
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

/**
 * Transporte duplex del canal userauth (Quvii / tdkcloud).
 *
 * Protocolo verificado contra el servidor real:
 *  - El primer GET .../auth/nologin;jus_duplex=down devuelve Set-Cookie jsessionid.
 *  - Los comandos se envían por POST .../auth/{nologin|user};jus_duplex=up con esa cookie
 *    y devuelven 200 con cuerpo vacío.
 *  - La respuesta llega por el canal down (long-poll ~30 s) con el envelope XML.
 *
 * La cookie jsessionid es obligatoria: sin ella el gateway responde 404.
 */
class UserAuthWire(
    private val httpClient: HttpClient,
    private val baseUrl: String,
    private val clientId: String,
    private val commandTimeoutMs: Long = 75_000,
) {
    private val downUrl = "${baseUrl.trimEnd('/')}/auth/nologin;jus_duplex=down"
    private val nologinUpUrl = "${baseUrl.trimEnd('/')}/auth/nologin;jus_duplex=up"
    private val userUpUrl = "${baseUrl.trimEnd('/')}/auth/user;jus_duplex=up"

    @Volatile
    private var jsessionid: String? = null

    /** Obtiene (y cachea) la cookie de sesión leyendo el Set-Cookie del primer down. */
    suspend fun ensureSession(): String {
        jsessionid?.let { return it }
        // Leemos solo las cabeceras: el down es long-poll y no queremos esperar su cuerpo.
        val cookie = httpClient.prepareGet(downUrl).execute { response ->
            response.headers.getAll("Set-Cookie")
                ?.firstNotNullOfOrNull { extractJsessionid(it) }
        }
        val session = cookie ?: error("userauth down did not return a jsessionid cookie")
        jsessionid = session
        return session
    }

    /**
     * Ejecuta un comando userauth: abre un colector down, lanza el POST up y espera
     * el envelope de respuesta cuyo header.command coincide con el esperado.
     */
    suspend fun command(
        command: String,
        content: Map<String, String>,
        session: String = "",
        userData: String = "",
        authenticated: Boolean = false,
        contentClass: String? = null,
    ): UserAuthResponse = coroutineScope {
        val sessionId = ensureSession()
        Log.d("WIBOX-ACCEPT", "command START cmd=" + command + " content=" + content)
        val cookie = "jsessionid=$sessionId"
        val response = CompletableDeferred<UserAuthResponse>()

        // Colector down: reintenta el long-poll hasta recibir el comando esperado.
        val downJob = launch {
            while (isActive && !response.isCompleted) {
                runCatching {
                    val body = httpClient.get(downUrl) { header("Cookie", cookie) }.bodyAsText()
                    if (body.isNotBlank()) {
                        val parsed = UserAuthEnvelope.parse(body)
                        Log.d("WIBOX-ACCEPT", "DOWN RESPONSE result=${parsed.result} command=${parsed.command}")
                        if (parsed.command == command) response.complete(parsed)
                    }
                }
            }
        }

        try {
            val envelope = UserAuthEnvelope.build(
                UserAuthHeader(
                    command = command,
                    clientId = clientId,
                    session = session,
                    userData = userData,
                ),
                LinkedHashMap(content),
                contentClass,
            )
            httpClient.post(if (authenticated) userUpUrl else nologinUpUrl) {
                header("Cookie", cookie)
                header("charset", "utf-8")
                header("Accept-Language", "en-US")
                header("Connection", "Keep-Alive")
                header("User-Agent", "okhttp/3.9.0")
                contentType(ContentType("application", "xml"))
                Log.d("WIBOX-ACCEPT", "UP SENDING body=" + envelope.take(2000))
            setBody(envelope)
            }
            withTimeout(commandTimeoutMs) { response.await() }
        } finally {
            downJob.cancelAndJoin()
        }
    }

    private fun extractJsessionid(setCookie: String): String? {
        val match = Regex("jsessionid=([^;]+)").find(setCookie) ?: return null
        return match.groupValues[1]
    }
}
