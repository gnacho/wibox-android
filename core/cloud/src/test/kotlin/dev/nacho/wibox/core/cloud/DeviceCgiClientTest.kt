package dev.nacho.wibox.core.cloud.devicecgi

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DeviceCgiClientTest {

    private fun xmlHeaders() = headersOf("Content-Type", "application/xml")

    private fun client(body: String): DeviceCgiClient {
        val engine = MockEngine { respond(body, HttpStatusCode.OK, xmlHeaders()) }
        return DeviceCgiClient(HttpClient(engine), port = 80)
    }

    @Test
    fun envelopeCarriesDeviceCredentials() {
        val envelope = client("<envelope/>").envelope("get.product.info", "abc123")

        assertTrue(envelope.contains("<command>get.product.info</command>"))
        assertTrue(envelope.contains("<password>abc123</password>"))
        assertTrue(envelope.contains("<passwordencode>1</passwordencode>"))
        assertTrue(envelope.contains("<username>adminapp2</username>"))
    }

    @Test
    fun deviceStatusParsesCurrentAndLatestFirmware() = runTest {
        val body = """
            <envelope><body><error>0</error><content>
              <info><version>2.4.1</version><releasedate>2025-06-01</releasedate>
                <model>IDS7938</model><mac>00:11:22:33:44:55</mac></info>
              <wifiinfo><ssid>casa</ssid><rssi>-58</rssi></wifiinfo>
              <content><version>2.5.0</version><releasetime>2025-09-10</releasetime></content>
            </content></body></envelope>
        """.trimIndent()

        val info = client(body).deviceStatus("192.168.1.10", "hash")

        assertEquals("2.4.1", info.version)
        assertEquals("2025-06-01", info.releaseDate)
        assertEquals("IDS7938", info.model)
        assertEquals("00:11:22:33:44:55", info.mac)
        assertEquals("2.5.0", info.latestVersion)
    }

    @Test
    fun productInfoHasNoLatestVersion() = runTest {
        val body = """
            <envelope><body><error>0</error><content>
              <info><version>2.4.1</version><releasedate>2025-06-01</releasedate>
                <model>IDS7938</model><mac>AA:BB</mac></info>
            </content></body></envelope>
        """.trimIndent()

        val info = client(body).productInfo("10.0.0.5", "hash")

        assertEquals("2.4.1", info.version)
        assertEquals("IDS7938", info.model)
        assertNull(info.latestVersion)
    }

    @Test
    fun nonZeroErrorThrows() = runTest {
        val body = "<envelope><body><error>5</error><content /></body></envelope>"

        assertFailsWith<DeviceCgiException> {
            client(body).deviceStatus("192.168.1.10", "hash")
        }
    }

    @Test
    fun connectionFailureIsWrapped() = runTest {
        val engine = MockEngine { throw java.io.IOException("connection refused") }
        val cgi = DeviceCgiClient(HttpClient(engine))

        val error = assertFailsWith<DeviceCgiException> {
            cgi.productInfo("192.168.1.99", "hash")
        }
        assertTrue(error.message!!.contains("no se pudo contactar"))
    }
}
