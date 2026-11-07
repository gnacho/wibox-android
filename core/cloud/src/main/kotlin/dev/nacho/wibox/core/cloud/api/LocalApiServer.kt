package dev.nacho.wibox.core.cloud.api

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException

/** Device as exposed by the local API. */
data class ApiDevice(
    val id: String,
    val name: String,
    val model: String,
    val online: Boolean,
    val shared: Boolean,
)

/** What the local API needs from the app. */
interface LocalApiBackend {
    suspend fun devices(): List<ApiDevice>

    /** Returns 0 when the door was opened, any other value is the device code. */
    suspend fun openDoor(deviceId: String): Int
}

/**
 * Small HTTP/1.1 server so other devices on the LAN (Home Assistant, scripts,
 * a relay) can open the door and read the device list without going through the
 * vendor cloud.
 *
 * Kept dependency free on purpose: one accept loop and one coroutine per
 * connection. Every route except /health requires the bearer token.
 */
class LocalApiServer(
    private val port: Int,
    private val token: String,
    private val backend: LocalApiBackend,
    private val scope: CoroutineScope,
    private val log: (String) -> Unit = {},
) {
    private var serverSocket: ServerSocket? = null
    private var acceptJob: Job? = null

    val isRunning: Boolean get() = acceptJob?.isActive == true

    /**
     * Puerto en el que escucha el servidor. Con `port = 0` es el que asigna el
     * sistema, que solo se conoce despues de `start()`.
     */
    val boundPort: Int get() = serverSocket?.localPort ?: port

    fun start(): Boolean {
        if (isRunning) return true
        return runCatching {
            val socket = ServerSocket()
            // Sin esto, reiniciar con conexiones en TIME_WAIT falla al enlazar.
            socket.reuseAddress = true
            socket.bind(InetSocketAddress(InetAddress.getByName("0.0.0.0"), port), 8)
            serverSocket = socket
            acceptJob = scope.launch(Dispatchers.IO) { acceptLoop(socket) }
            log("api listening on 0.0.0.0:${socket.localPort}")
            true
        }.getOrElse {
            log("api start failed: ${it.javaClass.simpleName}: ${it.message}")
            false
        }
    }

    fun stop() {
        acceptJob?.cancel()
        acceptJob = null
        runCatching { serverSocket?.close() }
        serverSocket = null
    }

    private suspend fun acceptLoop(socket: ServerSocket) {
        while (scope.isActive && !socket.isClosed) {
            val client = runCatching { socket.accept() }.getOrNull() ?: break
            scope.launch(Dispatchers.IO) { handle(client) }
        }
    }

    private suspend fun handle(client: Socket) {
        client.use { socket ->
            runCatching {
                socket.soTimeout = 10_000
                val input = BufferedInputStream(socket.getInputStream())
                val request = readRequest(input) ?: return
                val response = route(request)
                writeResponse(socket.getOutputStream(), response)
            }.onFailure {
                if (it !is SocketException) log("api request failed: ${it.message}")
            }
        }
    }

    private data class Request(
        val method: String,
        val path: String,
        val query: Map<String, String>,
        val headers: Map<String, String>,
        val body: String,
    )

    private fun readRequest(input: BufferedInputStream): Request? {
        val requestLine = readLine(input) ?: return null
        val parts = requestLine.split(" ")
        if (parts.size < 2) return null
        val headers = mutableMapOf<String, String>()
        while (true) {
            val line = readLine(input) ?: break
            if (line.isEmpty()) break
            val colon = line.indexOf(':')
            if (colon > 0) {
                headers[line.substring(0, colon).trim().lowercase()] = line.substring(colon + 1).trim()
            }
        }
        val length = headers["content-length"]?.toIntOrNull() ?: 0
        val body = if (length > 0) {
            val buffer = ByteArray(length)
            var read = 0
            while (read < length) {
                val chunk = input.read(buffer, read, length - read)
                if (chunk < 0) break
                read += chunk
            }
            String(buffer, 0, read, Charsets.UTF_8)
        } else {
            ""
        }
        val target = parts[1]
        val path = target.substringBefore('?')
        val query = target.substringAfter('?', "")
            .split('&')
            .filter { it.contains('=') }
            .associate { it.substringBefore('=') to it.substringAfter('=') }
        return Request(parts[0].uppercase(), path, query, headers, body)
    }

    private fun readLine(input: BufferedInputStream): String? {
        val builder = StringBuilder()
        while (true) {
            val value = input.read()
            if (value < 0) return if (builder.isEmpty()) null else builder.toString()
            if (value == '\n'.code) return builder.toString().trimEnd('\r')
            builder.append(value.toChar())
        }
    }

    private data class Response(val status: Int, val body: String)

    private suspend fun route(request: Request): Response {
        if (request.path == "/health") {
            return Response(200, """{"ok":true}""")
        }
        if (!authorized(request)) {
            return Response(401, """{"error":"unauthorized"}""")
        }
        return when (request.path) {
            "/devices" -> {
                val list = backend.devices().joinToString(",") { device ->
                    """{"id":"${device.id}","name":"${escape(device.name)}","model":"${escape(device.model)}","online":${device.online},"shared":${device.shared}}"""
                }
                Response(200, """{"devices":[$list]}""")
            }
            "/open" -> {
                val deviceId = request.query["device"]
                    ?: deviceFromBody(request.body)
                    ?: return Response(400, """{"error":"missing device"}""")
                val result = withContext(Dispatchers.IO) { backend.openDoor(deviceId) }
                if (result == 0) {
                    Response(200, """{"ok":true,"rc":0}""")
                } else {
                    Response(502, """{"ok":false,"rc":$result}""")
                }
            }
            else -> Response(404, """{"error":"not found"}""")
        }
    }

    private fun authorized(request: Request): Boolean {
        if (token.isEmpty()) return true
        val header = request.headers["authorization"]
        if (header != null && header.removePrefix("Bearer ").trim() == token) return true
        return request.query["token"] == token
    }

    private fun deviceFromBody(body: String): String? {
        val key = "\"device\""
        val index = body.indexOf(key)
        if (index < 0) return null
        val start = body.indexOf('"', body.indexOf(':', index) + 1)
        if (start < 0) return null
        val end = body.indexOf('"', start + 1)
        if (end < 0) return null
        return body.substring(start + 1, end)
    }

    private fun escape(value: String): String =
        value.replace("\\", "\\\\").replace("\"", "\\\"")

    private fun writeResponse(output: OutputStream, response: Response) {
        val payload = response.body.toByteArray(Charsets.UTF_8)
        val header = buildString {
            append("HTTP/1.1 ${response.status} ${statusText(response.status)}\r\n")
            append("Content-Type: application/json\r\n")
            append("Content-Length: ${payload.size}\r\n")
            append("Connection: close\r\n\r\n")
        }
        output.write(header.toByteArray(Charsets.UTF_8))
        output.write(payload)
        output.flush()
    }

    private fun statusText(status: Int): String = when (status) {
        200 -> "OK"
        400 -> "Bad Request"
        401 -> "Unauthorized"
        404 -> "Not Found"
        502 -> "Bad Gateway"
        else -> "Error"
    }
}
