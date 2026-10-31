package dev.nacho.wibox.core.cloud

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OAuthContractTest {
    @Test
    fun passwordGrantUsesExactWireContractAndPersistsSession() = runTest {
        val store = RecordingTokenStore()
        lateinit var captured: io.ktor.client.request.HttpRequestData
        val engine = MockEngine { request ->
            captured = request
            respond(Fixtures.text("oauth_password.json"), HttpStatusCode.OK, headersOf("Content-Type", "application/json"))
        }
        val cloud = testCloud(engine, store, TestClock())
        val credentials = CloudCredentials("opaque-fixture-user", "fixture-password-not-real")

        val session = cloud.authenticate(credentials)

        assertEquals(HttpMethod.Get, captured.method)
        assertEquals("/qvoauthv2/token", captured.url.encodedPath)
        assertEquals(
            listOf("grant_type", "client_id", "client_type", "oemid", "appid", "usr", "pwd", "region_id", "client_flag"),
            captured.url.parameters.names().toList(),
        )
        assertEquals("password", captured.url.parameters["grant_type"])
        assertEquals("fixture-client", captured.url.parameters["client_id"])
        assertEquals("3", captured.url.parameters["client_type"])
        assertEquals("G0021", captured.url.parameters["oemid"])
        assertEquals("2021", captured.url.parameters["appid"])
        assertEquals("opaque-fixture-user", captured.url.parameters["usr"])
        assertEquals("fixture-password-not-real", captured.url.parameters["pwd"])
        assertEquals("fixture-region", captured.url.parameters["region_id"])
        assertEquals("2", captured.url.parameters["client_flag"])
        assertEquals("", captured.headers["token"])
        assertNull(captured.headers["Authorization"])
        assertEquals(1_800_003_600_000L, session.accessTokenExpiresAtMillis)
        assertEquals(1_800_007_200_000L, session.refreshTokenExpiresAtMillis)
        assertEquals(listOf(session), store.saves)
        assertFalse(session.toString().contains(session.accessToken))
        assertFalse(session.toString().contains(session.refreshToken))
        assertFalse(credentials.toString().contains("fixture-password-not-real"))
        cloud.close()
    }

    @Test
    fun refreshGrantUsesOnlyRefreshQueryAndAccessTokenHeader() = runTest {
        val initial = fixtureSession()
        val store = RecordingTokenStore(initial)
        lateinit var captured: io.ktor.client.request.HttpRequestData
        val engine = MockEngine { request ->
            captured = request
            respond(Fixtures.text("oauth_refresh.json"), HttpStatusCode.OK, headersOf("Content-Type", "application/json"))
        }
        val cloud = testCloud(engine, store, TestClock(1_800_003_300_000L))

        val session = cloud.refreshSession()

        assertEquals(HttpMethod.Get, captured.method)
        assertEquals("/qvoauthv2/token", captured.url.encodedPath)
        assertEquals(listOf("grant_type", "refresh_token"), captured.url.parameters.names().toList())
        assertEquals("refresh_token", captured.url.parameters["grant_type"])
        assertEquals(initial.refreshToken, captured.url.parameters["refresh_token"])
        assertEquals(initial.accessToken, captured.headers["token"])
        assertNull(captured.headers["Authorization"])
        assertEquals(1_800_005_400_000L, session.accessTokenExpiresAtMillis)
        assertEquals(listOf(session), store.saves)
        cloud.close()
    }

    @Test
    fun refreshWithoutStoredTokenFailsBeforeNetwork() = runTest {
        var requests = 0
        val engine = MockEngine {
            requests += 1
            respond("{}", HttpStatusCode.OK)
        }
        val cloud = testCloud(engine, RecordingTokenStore(), TestClock())

        assertFailsWith<CloudError.MissingToken> { cloud.refreshSession() }
        assertEquals(0, requests)
        cloud.close()
    }
}
