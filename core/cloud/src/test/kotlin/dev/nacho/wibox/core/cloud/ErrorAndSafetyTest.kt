package dev.nacho.wibox.core.cloud

import dev.nacho.wibox.core.cloud.internal.RealWiboxCloud
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import java.io.IOException
import java.net.SocketTimeoutException
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ErrorAndSafetyTest {
    @Test
    fun mapsHttpErrorsWithoutRetryOrSecretLeakage() = runTest {
        val cases = listOf(
            401 to CloudError.Authentication::class,
            403 to CloudError.Authorization::class,
            500 to CloudError.Http::class,
        )
        for ((status, expected) in cases) {
            var requests = 0
            val engine = MockEngine {
                requests += 1
                respond("fixture-error", HttpStatusCode.fromValue(status))
            }
            val store = RecordingTokenStore()
            val cloud = testCloud(engine, store, TestClock())

            val error = assertFailsWith<CloudError> {
                cloud.authenticate(CloudCredentials("opaque-fixture-user", "fixture-password-not-real"))
            }

            assertEquals(expected, error::class)
            assertEquals(1, requests)
            assertFalse(error.toString().contains("fixture-password-not-real"))
            assertTrue(store.saves.isEmpty())
            cloud.close()
        }
    }

    @Test
    fun mapsNetworkAndTimeoutWithoutRetry() = runTest {
        val cases = listOf<suspend () -> Unit>(
            { throw IOException("fixture-sensitive-network-detail") },
            { throw SocketTimeoutException("fixture-sensitive-timeout-detail") },
        )
        val expected = listOf(CloudError.Network::class, CloudError.Timeout::class)
        cases.zip(expected).forEach { (failure, errorType) ->
            var requests = 0
            val engine = MockEngine {
                requests += 1
                failure()
                respond("{}", HttpStatusCode.OK)
            }
            val cloud = testCloud(engine, RecordingTokenStore(), TestClock())

            val error = assertFailsWith<CloudError> {
                cloud.authenticate(CloudCredentials("opaque-fixture-user", "fixture-password-not-real"))
            }

            assertEquals(errorType, error::class)
            assertEquals(1, requests)
            assertFalse(error.toString().contains("fixture-sensitive"))
            cloud.close()
        }
    }

    @Test
    fun mapsKtorRequestTimeoutBeforeCancellationAndRedactsUrl() = runTest {
        val engine = MockEngine { request ->
            throw HttpRequestTimeoutException(request)
        }
        val cloud = testCloud(engine, RecordingTokenStore(), TestClock())

        val error = assertFailsWith<CloudError.Timeout> {
            cloud.authenticate(CloudCredentials("opaque-fixture-user", "fixture-password-not-real"))
        }
        assertFalse(error.toString().contains("fixture-password-not-real"))
        cloud.close()
    }

    @Test
    fun rejectsMalformedAndIncompleteOAuthWithoutPersistence() = runTest {
        for (body in listOf("{", "{}")) {
            val store = RecordingTokenStore()
            val engine = MockEngine { respond(body, HttpStatusCode.OK, headersOf("Content-Type", "application/json")) }
            val cloud = testCloud(engine, store, TestClock())

            assertFailsWith<CloudError.Parse> {
                cloud.authenticate(CloudCredentials("opaque-fixture-user", "fixture-password-not-real"))
            }
            assertTrue(store.saves.isEmpty())
            cloud.close()
        }
    }

    @Test
    fun rejectsMalformedIncompleteAndProviderErrorShadowResponses() = runTest {
        val cases = listOf(
            "malformed.json" to CloudError.Parse::class,
            "shadow_incomplete.json" to CloudError.Parse::class,
            "shadow_provider_error.json" to CloudError.Provider::class,
        )
        for ((fixture, expected) in cases) {
            val engine = MockEngine {
                respond(Fixtures.text(fixture), HttpStatusCode.OK, headersOf("Content-Type", "application/json"))
            }
            val cloud = testCloud(engine, RecordingTokenStore(fixtureSession()), TestClock())

            val error = assertFailsWith<CloudError> { cloud.getDeviceShadow("fixture-device-001") }
            assertEquals(expected, error::class)
            if (error is CloudError.Provider) assertEquals(10401, error.providerCode)
            cloud.close()
        }
    }

    @Test
    fun mapsPersistenceFailures() = runTest {
        val loadCloud = testCloud(
            MockEngine { respond("{}", HttpStatusCode.OK) },
            object : TokenStore {
                override suspend fun load(): CloudSession? = throw IOException("fixture-load-failure")
                override suspend fun save(session: CloudSession) = Unit
                override suspend fun clear() = Unit
            },
            TestClock(),
        )
        assertFailsWith<CloudError.Persistence> { loadCloud.restoreSession() }
        loadCloud.close()

        val store = RecordingTokenStore()
        val failingSave = object : TokenStore by store {
            override suspend fun save(session: CloudSession): Unit = throw IOException("fixture-save-failure")
        }
        val saveCloud = testCloud(
            MockEngine { respond(Fixtures.text("oauth_password.json"), HttpStatusCode.OK) },
            failingSave,
            TestClock(),
        )
        assertFailsWith<CloudError.Persistence> {
            saveCloud.authenticate(CloudCredentials("opaque-fixture-user", "fixture-password-not-real"))
        }
        saveCloud.close()
    }

    @Test
    fun validatesInputConfigurationAndCloseWithoutNetwork() = runTest {
        val cloud = testCloud(MockEngine { respond("{}", HttpStatusCode.OK) }, RecordingTokenStore(), TestClock())
        assertFailsWith<CloudError.InvalidInput> { cloud.authenticate(CloudCredentials("", "fixture-password-not-real")) }
        assertFailsWith<CloudError.InvalidInput> { cloud.getDeviceShadow(" ") }
        assertFailsWith<CloudError.InvalidInput> { cloud.getDeviceShadows(emptyList()) }
        cloud.close()
        cloud.close()
        assertFailsWith<CloudError.Closed> { cloud.restoreSession() }

        assertFailsWith<CloudError.InvalidInput> {
            RealWiboxCloud(testConfig().copy(baseUrl = "http://unit.test/"), RecordingTokenStore(), TestClock(), HttpClient(MockEngine { respond("{}", HttpStatusCode.OK) }))
        }
        assertFailsWith<CloudError.InvalidInput> {
            RealWiboxCloud(testConfig().copy(baseUrl = "https://user@unit.test/"), RecordingTokenStore(), TestClock(), HttpClient(MockEngine { respond("{}", HttpStatusCode.OK) }))
        }
    }
}
