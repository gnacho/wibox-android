package dev.nacho.wibox.core.cloud

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

class SessionBehaviorTest {
    @Test
    fun restoresOnceAndUsesFreshCachedAccessToken() = runTest {
        val initial = fixtureSession()
        val store = RecordingTokenStore(initial)
        val paths = mutableListOf<String>()
        val engine = MockEngine { request ->
            paths += request.url.encodedPath
            respond(Fixtures.text("shadow_status.json"), HttpStatusCode.OK, headersOf("Content-Type", "application/json"))
        }
        val cloud = testCloud(engine, store, TestClock())

        assertSame(initial, cloud.restoreSession())
        assertSame(initial, cloud.restoreSession())
        cloud.getDeviceShadow("fixture-device-001")

        assertEquals(1, store.loads)
        assertEquals(listOf("/openapi-tdk/device/status"), paths)
        cloud.close()
    }

    @Test
    fun automaticRefreshAtFiveMinuteBoundaryPrecedesShadowRequest() = runTest {
        val initial = fixtureSession()
        val store = RecordingTokenStore(initial)
        val requests = mutableListOf<io.ktor.client.request.HttpRequestData>()
        val engine = MockEngine { request ->
            requests += request
            val fixture = if (request.url.encodedPath == "/qvoauthv2/token") "oauth_refresh.json" else "shadow_status.json"
            respond(Fixtures.text(fixture), HttpStatusCode.OK, headersOf("Content-Type", "application/json"))
        }
        val cloud = testCloud(engine, store, TestClock(1_800_003_300_000L))

        cloud.getDeviceShadow("fixture-device-001")

        assertEquals(listOf("/qvoauthv2/token", "/openapi-tdk/device/status"), requests.map { it.url.encodedPath })
        assertEquals("refresh_token", requests[0].url.parameters["grant_type"])
        assertEquals(initial.accessToken, requests[0].headers["token"])
        assertEquals(store.saves.single().accessToken, requests[1].headers["token"])
        cloud.close()
    }

    @Test
    fun concurrentRefreshUsesSingleFlightAndOnePersistenceWrite() = runTest {
        val store = RecordingTokenStore(fixtureSession())
        var requests = 0
        val engine = MockEngine {
            requests += 1
            delay(50)
            respond(Fixtures.text("oauth_refresh.json"), HttpStatusCode.OK, headersOf("Content-Type", "application/json"))
        }
        val cloud = testCloud(engine, store, TestClock(1_800_003_300_000L))

        val sessions = List(12) {
            async(Dispatchers.Default) { cloud.refreshSession() }
        }.awaitAll()

        assertEquals(1, requests)
        assertEquals(1, store.saves.size)
        assertEquals(12, sessions.size)
        assertEquals(1, sessions.map { it.accessToken }.distinct().size)
        cloud.close()
    }

    @Test
    fun blankRestoredTokensFailBeforeNetwork() = runTest {
        val invalid = fixtureSession().copy(accessToken = "", refreshToken = "")
        var requests = 0
        val engine = MockEngine {
            requests += 1
            respond("{}", HttpStatusCode.OK)
        }
        val cloud = testCloud(engine, RecordingTokenStore(invalid), TestClock())

        assertFailsWith<CloudError.MissingToken> { cloud.getDeviceShadow("fixture-device-001") }
        assertEquals(0, requests)
        cloud.close()
    }

    @Test
    fun expiredRefreshTokenDoesNotFallbackToPasswordOrNetwork() = runTest {
        val initial = fixtureSession().copy(
            accessTokenExpiresAtMillis = 1_800_000_000_000L,
            refreshTokenExpiresAtMillis = 1_800_000_000_000L,
        )
        var requests = 0
        val engine = MockEngine {
            requests += 1
            respond("{}", HttpStatusCode.OK)
        }
        val cloud = testCloud(engine, RecordingTokenStore(initial), TestClock())

        assertFailsWith<CloudError.MissingToken> { cloud.getDeviceShadow("fixture-device-001") }
        assertEquals(0, requests)
        cloud.close()
    }
}
