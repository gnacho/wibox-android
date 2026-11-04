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
import dev.nacho.wibox.core.cloud.UserAuthTimeoutException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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

    private companion object {
        const val TAG = "WIBOX-ACCEPT"
    }

    @Volatile
    private var jsessionid: String? = null

    /** Token de sesion userauth (solo lo emite el server en el login response). */
    @Volatile
    private var authToken: String? = null

    /** El canal duplex no admite dos comandos en vuelo: el down long-poll es compartido. */
    private val commandMutex = Mutex()

    /** Invalida la cookie y el token: la siguiente llamada abre una sesion nueva. */
    fun resetSession() {
        jsessionid = null
        authToken = null
    }

    /** Obtiene (y cachea) la cookie de sesión leyendo el Set-Cookie del primer down. */
    suspend fun ensureSession(): String {
        jsessionid?.let { return it }
        Log.d(TAG, "SESSION BEGIN url=$downUrl")
        val startedAt = android.os.SystemClock.elapsedRealtime()
        var lastError: Exception? = null
        // El movil puede abortar el down long-poll (SocketException / connection abort)
        // al cambiar de red o con WiFi inestable: reintento acotado antes de rendirse.
        for (attempt in 0 until 2) {
            // Leemos solo las cabeceras: el down es long-poll y no queremos esperar su cuerpo.
            val cookie = try {
                httpClient.prepareGet(downUrl).execute { response ->
                    response.headers.getAll("Set-Cookie")
                        ?.firstNotNullOfOrNull { extractJsessionid(it) }
                }
            } catch (error: Exception) {
                lastError = error
                Log.w(TAG, "SESSION RETRY $attempt after " + (android.os.SystemClock.elapsedRealtime() - startedAt) + "ms: " + error.message)
                kotlinx.coroutines.delay(600L + attempt * 600L)
                continue
            }
            val session = cookie ?: run {
                lastError = IllegalStateException("userauth down did not return a jsessionid cookie")
                null
            }
            if (session != null) {
                jsessionid = session
                Log.d(TAG, "SESSION OK after " + (android.os.SystemClock.elapsedRealtime() - startedAt) + "ms")
                return session
            }
            kotlinx.coroutines.delay(600L)
        }
        val error = lastError ?: IllegalStateException("userauth down sin cookie")
        Log.e(TAG, "SESSION ERROR after " + (android.os.SystemClock.elapsedRealtime() - startedAt) + "ms", error)
        throw error
    }

    /**
     * Ejecuta un comando userauth: abre un colector down, lanza el POST up y espera
     * el envelope de respuesta cuyo header.command coincide con el esperado.
     *
     * Fallback verificado en capturas: si el gateway no tiene un down pendiente,
     * devuelve el envelope en el CUERPO del propio POST up, por lo que tambien se
     * intenta completar la respuesta desde ahi.
     */
    suspend fun command(
        command: String,
        content: Map<String, String>,
        session: String = "",
        userData: String = "",
        authenticated: Boolean = false,
        contentClass: String? = null,
        timeoutMs: Long = commandTimeoutMs,
    ): UserAuthResponse = commandMutex.withLock {
        coroutineScope {
        Log.d(TAG, "command ENTER cmd=" + command)
        val sessionId = ensureSession()
        Log.d(TAG, "command START cmd=" + command + " content=" + content)
        val cookie = "jsessionid=$sessionId"
        val response = CompletableDeferred<UserAuthResponse>()
        var polls = 0
        var emptyPollDelayMs = 150L

        // Colector down: reintenta el long-poll hasta recibir el comando esperado.
        val downJob = launch {
            while (isActive && !response.isCompleted) {
                try {
                    val body = httpClient.get(downUrl) { header("Cookie", cookie) }.bodyAsText()
                    if (body.isNotBlank()) {
                        emptyPollDelayMs = 150L
                        val parsed = UserAuthEnvelope.parse(body)
                        Log.d(TAG, "DOWN RESPONSE result=" + parsed.result + " command=" + parsed.command + " polls=" + polls)
                        captureSession(parsed)
                        if (parsed.command == command) response.complete(parsed)
                    } else {
                        polls++
                        if (polls == 1 || polls % 20 == 0) {
                            Log.d(TAG, "DOWN POLL empty polls=" + polls)
                        }
                        delay(emptyPollDelayMs)
                        emptyPollDelayMs = (emptyPollDelayMs * 2).coerceAtMost(1_000L)
                    }
                } catch (error: kotlinx.coroutines.CancellationException) {
                    throw error
                } catch (error: Exception) {
                    polls++
                    Log.d(TAG, "DOWN POLL error polls=" + polls + " " + error::class.java.simpleName + ": " + error.message)
                    delay(emptyPollDelayMs)
                    emptyPollDelayMs = (emptyPollDelayMs * 2).coerceAtMost(1_000L)
                }
            }
        }

        try {
            val envelope = UserAuthEnvelope.build(
                UserAuthHeader(
                    command = command,
                    clientId = clientId,
                    session = session.ifBlank { authToken.orEmpty() },
                    userData = userData,
                ),
                LinkedHashMap(content),
                contentClass,
            )
            Log.d(TAG, "UP SENDING body=" + envelope.take(2000))
            val upResponse = httpClient.post(if (authenticated) userUpUrl else nologinUpUrl) {
                header("Cookie", cookie)
                header("charset", "utf-8")
                header("Accept-Language", "en-US")
                header("Connection", "Keep-Alive")
                header("User-Agent", "okhttp/3.9.0")
                contentType(ContentType("application", "xml"))
                setBody(envelope)
            }
            val upBody = upResponse.bodyAsText()
            Log.d(TAG, "UP DONE status=" + upResponse.status.value + " bodyLen=" + upBody.length)
            if (upBody.isNotBlank()) {
                val fromUp = runCatching { UserAuthEnvelope.parse(upBody) }.getOrNull()
                if (fromUp != null) {
                    Log.d(TAG, "UP BODY ENVELOPE result=" + fromUp.result + " command=" + fromUp.command)
                    captureSession(fromUp)
                    if (fromUp.command == command) response.complete(fromUp)
                } else {
                    Log.d(TAG, "UP BODY raw=" + upBody.take(300))
                }
            }
            try {
                withTimeout(timeoutMs) { response.await() }
            } catch (timeout: kotlinx.coroutines.TimeoutCancellationException) {
                Log.d(TAG, "command TIMEOUT after " + timeoutMs + "ms cmd=" + command)
                throw UserAuthTimeoutException(command)
            }
        } finally {
            downJob.cancelAndJoin()
        }
    }
    }

    private fun extractJsessionid(setCookie: String): String? {
        val match = Regex("jsessionid=([^;]+)").find(setCookie) ?: return null
        return match.groupValues[1]
    }

    /** Guarda el token de sesion si la respuesta trae uno (solo lo emite el login). */
    private fun captureSession(parsed: UserAuthResponse) {
        parsed.session?.takeIf { it.isNotBlank() }?.let { token ->
            if (authToken != token) {
                authToken = token
                Log.d(TAG, "SESSION CAPTURED len=" + token.length + " command=" + parsed.command)
            }
        }
    }
}
