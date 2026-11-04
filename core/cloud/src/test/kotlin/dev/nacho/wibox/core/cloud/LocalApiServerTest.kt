package dev.nacho.wibox.core.cloud

import dev.nacho.wibox.core.cloud.api.ApiDevice
import dev.nacho.wibox.core.cloud.api.LocalApiBackend
import dev.nacho.wibox.core.cloud.api.LocalApiServer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.net.HttpURLConnection
import java.net.URL

class LocalApiServerTest {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var server: LocalApiServer
    private val port = 18087

    private val backend = object : LocalApiBackend {
        override suspend fun devices() = listOf(
            ApiDevice(id = "dev1", name = "Front door", model = "IDS7938", online = true, shared = true),
        )

        override suspend fun openDoor(deviceId: String) = if (deviceId == "dev1") 0 else 7
    }

    @Before
    fun setUp() {
        server = LocalApiServer(port = port, token = "secret", backend = backend, scope = scope)
        assertTrue(server.start())
        Thread.sleep(200)
    }

    @After
    fun tearDown() {
        server.stop()
        scope.cancel()
    }

    @Test
    fun healthNeedsNoToken() {
        val (code, body) = get("/health")
        assertEquals(200, code)
        assertTrue(body.contains("\"ok\":true"))
    }

    @Test
    fun devicesRequireToken() {
        val (code, _) = get("/devices")
        assertEquals(401, code)
    }

    @Test
    fun devicesListWithToken() {
        val (code, body) = get("/devices?token=secret")
        assertEquals(200, code)
        assertTrue(body.contains("\"id\":\"dev1\""))
        assertTrue(body.contains("\"online\":true"))
    }

    @Test
    fun openDoorWithBearer() {
        val (code, body) = get("/open?device=dev1", bearer = "secret")
        assertEquals(200, code)
        assertTrue(body.contains("\"rc\":0"))
    }

    @Test
    fun openDoorUnknownDeviceReturnsError() {
        val (code, body) = get("/open?device=other", bearer = "secret")
        assertEquals(502, code)
        assertTrue(body.contains("\"rc\":7"))
    }

    @Test
    fun unknownPathReturns404() {
        val (code, _) = get("/nope", bearer = "secret")
        assertEquals(404, code)
    }

    private fun get(path: String, bearer: String? = null): Pair<Int, String> {
        val connection = URL("http://127.0.0.1:$port$path").openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.connectTimeout = 3_000
        connection.readTimeout = 3_000
        if (bearer != null) connection.setRequestProperty("Authorization", "Bearer $bearer")
        val code = connection.responseCode
        val stream = if (code in 200..299) connection.inputStream else connection.errorStream
        val body = stream?.bufferedReader()?.readText().orEmpty()
        connection.disconnect()
        return code to body
    }
}
