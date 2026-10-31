package dev.nacho.wibox.core.cloud

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.Headers
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import java.io.IOException
import java.net.SocketTimeoutException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UpgradeContractTest {
    @Test
    fun requestMatchesAttempt3AndParsesEveryObservedField() = runTest {
        lateinit var captured: HttpRequestData
        val engine = MockEngine { request ->
            captured = request
            respond(Fixtures.text("upgrade_success.xml"), HttpStatusCode.OK, xmlHeaders())
        }
        val cloud = testCloud(engine, RecordingTokenStore(), TestClock())
        val request = upgradeRequest()

        val result = cloud.checkAppUpgrade(request)

        assertEquals(HttpMethod.Get, captured.method)
        assertEquals("/webs/version/app/version_check.jsp", captured.url.encodedPath)
        assertEquals(listOf("os", "appid", "version", "clientid"), captured.url.parameters.names().toList())
        assertEquals("android", captured.url.parameters["os"])
        assertEquals("2021", captured.url.parameters["appid"])
        assertEquals("18", captured.url.parameters["version"])
        assertEquals("fixture-upgrade-client", captured.url.parameters["clientid"])
        assertEquals("en-US", captured.headers["Accept-Language"])
        assertNull(captured.headers["token"])
        assertNull(captured.headers["Authorization"])
        assertNull(captured.headers["Cookie"])
        assertEquals("tdkcloud", result.flag)
        assertEquals(0, result.result)
        assertEquals(0, result.priority)
        assertEquals("5.1.11", result.version)
        assertEquals("", result.url)
        assertEquals("", result.description)
        assertEquals(0, result.configVersion)
        assertEquals("", result.configUrl)
        assertEquals("", result.configPicture)
        assertFalse(request.toString().contains("fixture-upgrade-client"))
        assertTrue(cloud.capabilities.supports(CloudCapability.APP_UPGRADE_CHECK))
        cloud.close()
    }

    @Test
    fun encodesReservedClientIdCharactersWithoutChangingQueryShape() = runTest {
        lateinit var captured: HttpRequestData
        val engine = MockEngine { request ->
            captured = request
            respond(Fixtures.text("upgrade_success.xml"), HttpStatusCode.OK, xmlHeaders())
        }
        val cloud = testCloud(engine, RecordingTokenStore(), TestClock())
        val clientId = "fixture/id+%?=&#"

        cloud.checkAppUpgrade(upgradeRequest().copy(clientId = clientId))

        assertEquals(listOf("os", "appid", "version", "clientid"), captured.url.parameters.names().toList())
        assertEquals(clientId, captured.url.parameters["clientid"])
        assertEquals(
            "os=android&appid=2021&version=18&clientid=fixture%2Fid%2B%25%3F%3D%26%23",
            captured.url.encodedQuery,
        )
        cloud.close()
    }

    @Test
    fun parsesNonEmptyUrlsDescriptionAndConfig() = runTest {
        val engine = MockEngine {
            respond(Fixtures.text("upgrade_nonempty.xml"), HttpStatusCode.OK, xmlHeaders())
        }
        val cloud = testCloud(engine, RecordingTokenStore(), TestClock())

        val result = cloud.checkAppUpgrade(upgradeRequest())

        assertEquals(3, result.priority)
        assertEquals("5.1.12", result.version)
        assertEquals("https://unit.test/fixture-upgrade.apk", result.url)
        assertEquals("Fixture & upgrade", result.description)
        assertEquals(7, result.configVersion)
        assertEquals("https://unit.test/fixture-config.json", result.configUrl)
        assertEquals("https://unit.test/fixture-picture.png", result.configPicture)
        cloud.close()
    }

    @Test
    fun rejectsNon200AndUnobservedContentTypes() = runTest {
        var requests = 0
        val non200 = MockEngine {
            requests += 1
            respond(Fixtures.text("upgrade_success.xml"), HttpStatusCode.Created, xmlHeaders())
        }
        val cloud = testCloud(non200, RecordingTokenStore(), TestClock())
        assertFailsWith<CloudError.Http> { cloud.checkAppUpgrade(upgradeRequest()) }
        assertEquals(1, requests)
        cloud.close()

        for (contentType in listOf("application/json", "text/xml")) {
            val engine = MockEngine {
                respond(Fixtures.text("upgrade_success.xml"), HttpStatusCode.OK, headersOf("Content-Type", contentType))
            }
            val typedCloud = testCloud(engine, RecordingTokenStore(), TestClock())
            assertFailsWith<CloudError.Parse> { typedCloud.checkAppUpgrade(upgradeRequest()) }
            typedCloud.close()
        }
    }

    @Test
    fun rejectsMalformedMissingAndInvalidIntegerXml() = runTest {
        for (fixture in listOf("upgrade_malformed.xml", "upgrade_missing.xml", "upgrade_invalid_integer.xml")) {
            val engine = MockEngine {
                respond(Fixtures.text(fixture), HttpStatusCode.OK, xmlHeaders())
            }
            val cloud = testCloud(engine, RecordingTokenStore(), TestClock())
            assertFailsWith<CloudError.Parse> { cloud.checkAppUpgrade(upgradeRequest()) }
            cloud.close()
        }
    }

    @Test
    fun rejectsDoctypeXxeAndXIncludeWithoutResolvingContent() = runTest {
        for (fixture in listOf("upgrade_xxe.xml", "upgrade_xinclude.xml")) {
            val engine = MockEngine {
                respond(Fixtures.text(fixture), HttpStatusCode.OK, xmlHeaders())
            }
            val cloud = testCloud(engine, RecordingTokenStore(), TestClock())
            val error = assertFailsWith<CloudError.Parse> { cloud.checkAppUpgrade(upgradeRequest()) }
            assertFalse(error.toString().contains("fixture-secret"))
            cloud.close()
        }
    }

    @Test
    fun nonZeroProviderResultIsUnsupportedNotSuccess() = runTest {
        val engine = MockEngine {
            respond(Fixtures.text("upgrade_nonzero.xml"), HttpStatusCode.OK, xmlHeaders())
        }
        val cloud = testCloud(engine, RecordingTokenStore(), TestClock())

        assertFailsWith<CloudError.Unsupported> { cloud.checkAppUpgrade(upgradeRequest()) }
        cloud.close()
    }

    @Test
    fun mapsTransportFailuresAndPropagatesCancellation() = runTest {
        val timeoutCloud = testCloud(
            MockEngine { throw SocketTimeoutException("fixture-timeout") },
            RecordingTokenStore(),
            TestClock(),
        )
        assertFailsWith<CloudError.Timeout> { timeoutCloud.checkAppUpgrade(upgradeRequest()) }
        timeoutCloud.close()

        val networkCloud = testCloud(
            MockEngine { throw IOException("fixture-network") },
            RecordingTokenStore(),
            TestClock(),
        )
        assertFailsWith<CloudError.Network> { networkCloud.checkAppUpgrade(upgradeRequest()) }
        networkCloud.close()

        val cancellationCloud = testCloud(
            MockEngine { throw CancellationException("fixture-cancel") },
            RecordingTokenStore(),
            TestClock(),
        )
        assertFailsWith<CancellationException> { cancellationCloud.checkAppUpgrade(upgradeRequest()) }
        cancellationCloud.close()
    }

    @Test
    fun validatesUpgradeBaseAndInputsBeforeNetwork() = runTest {
        var requests = 0
        val engine = MockEngine {
            requests += 1
            respond(Fixtures.text("upgrade_success.xml"), HttpStatusCode.OK, xmlHeaders())
        }
        val cloud = testCloud(engine, RecordingTokenStore(), TestClock())

        assertFailsWith<CloudError.InvalidInput> {
            cloud.checkAppUpgrade(upgradeRequest().copy(baseUrl = "http://unit.test/"))
        }
        assertFailsWith<CloudError.InvalidInput> {
            cloud.checkAppUpgrade(upgradeRequest().copy(baseUrl = "https://user@unit.test/"))
        }
        assertFailsWith<CloudError.InvalidInput> { cloud.checkAppUpgrade(upgradeRequest().copy(currentVersion = 0)) }
        assertFailsWith<CloudError.InvalidInput> { cloud.checkAppUpgrade(upgradeRequest().copy(clientId = " ")) }
        assertFailsWith<CloudError.InvalidInput> { cloud.checkAppUpgrade(upgradeRequest().copy(acceptLanguage = "en-US\r\nfixture")) }
        assertEquals(0, requests)
        cloud.close()
    }

    @Test
    fun ignoresSetCookieAndNeverTouchesTokenStore() = runTest {
        val store = RecordingTokenStore()
        val captured = mutableListOf<HttpRequestData>()
        val engine = MockEngine { request ->
            captured += request
            respond(Fixtures.text("upgrade_success.xml"), HttpStatusCode.OK, xmlHeaders(setCookie = true))
        }
        val cloud = testCloud(engine, store, TestClock())

        cloud.checkAppUpgrade(upgradeRequest())
        cloud.checkAppUpgrade(upgradeRequest())

        assertEquals(2, captured.size)
        assertTrue(captured.all { it.headers["Cookie"] == null })
        assertEquals(0, store.loads)
        assertTrue(store.saves.isEmpty())
        cloud.close()
    }

    @Test
    fun closedClientRejectsUpgradeBeforeNetwork() = runTest {
        var requests = 0
        val engine = MockEngine {
            requests += 1
            respond(Fixtures.text("upgrade_success.xml"), HttpStatusCode.OK, xmlHeaders())
        }
        val cloud = testCloud(engine, RecordingTokenStore(), TestClock())
        cloud.close()

        assertFailsWith<CloudError.Closed> { cloud.checkAppUpgrade(upgradeRequest()) }
        assertEquals(0, requests)
    }

    private fun upgradeRequest(): AppUpgradeRequest = AppUpgradeRequest(
        baseUrl = "https://unit.test/",
        currentVersion = 18,
        clientId = "fixture-upgrade-client",
    )

    private fun xmlHeaders(setCookie: Boolean = false): Headers = Headers.build {
        append("Content-Type", "text/xml;charset=UTF-8")
        if (setCookie) append("Set-Cookie", "fixture-cookie-not-real")
    }
}
