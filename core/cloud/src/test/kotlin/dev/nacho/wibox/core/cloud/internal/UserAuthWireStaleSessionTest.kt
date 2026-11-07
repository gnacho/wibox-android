package dev.nacho.wibox.core.cloud.internal

import dev.nacho.wibox.core.cloud.UserAuthCloud
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlinx.coroutines.runBlocking

/**
 * The gateway answers the up channel with 404 and an empty body when the jsessionid
 * cookie is unknown, which is what happens once a cached session expires. Verified
 * against the live gateway with curl: an invented cookie and no cookie at all both
 * return 404. The wire must open a fresh session and retry instead of waiting for a
 * down response that will never arrive.
 */
class UserAuthWireStaleSessionTest {

    private fun envelope(command: String): String =
        "<envelope><header><flag>tdkcloud</flag><version>v1.24</version>" +
            "<command>$command</command><seq>0</seq><result>0</result></header>" +
            "<content><account>OX-test</account><id>device01</id></content></envelope>"

    @Test
    fun aRejectedUpChannelOpensANewSessionAndRetries() = runBlocking {
        val sessions = AtomicInteger(0)
        val posts = AtomicInteger(0)
        val engine = MockEngine { request ->
            val cookie = request.headers["Cookie"]
            when {
                request.method == HttpMethod.Get && cookie == null -> {
                    val name = if (sessions.incrementAndGet() == 1) "old-session" else "new-session"
                    respond(
                        content = "",
                        status = HttpStatusCode.OK,
                        headers = headersOf("Set-Cookie", "jsessionid=$name; Path=/"),
                    )
                }

                request.method == HttpMethod.Get -> respond(
                    content = envelope("accept-nologin-share-invitation"),
                    status = HttpStatusCode.OK,
                )

                request.method == HttpMethod.Post -> {
                    posts.incrementAndGet()
                    if (cookie.orEmpty().contains("old-session")) {
                        respond("", HttpStatusCode.NotFound)
                    } else {
                        respond("", HttpStatusCode.OK)
                    }
                }

                else -> respond("", HttpStatusCode.NotFound)
            }
        }
        val cloud = UserAuthCloud(
            UserAuthWire(HttpClient(engine), "https://auth.test", "003-2021-test", commandTimeoutMs = 2_000),
        )

        val acceptance = cloud.acceptShareInvitation(
            dev.nacho.wibox.core.cloud.ShareInvitation(
                inviteCode = "CODE1234",
                ownerId = "1",
                oemId = "G0021",
                deviceId = "device01",
                from = "QrInvite",
                appId = 2021,
            ),
            name = "test",
        )

        assertEquals("OX-test", acceptance.account)
        assertEquals(2, posts.get(), "the failed session must be retried exactly once")
        assertEquals(2, sessions.get(), "the retry needs a fresh session cookie")
    }

    @Test
    fun aSecondRejectionFailsInsteadOfWaitingForTheDownTimeout() = runBlocking {
        val posts = AtomicInteger(0)
        val engine = MockEngine { request ->
            when {
                request.method == HttpMethod.Get && request.headers["Cookie"] == null -> respond(
                    content = "",
                    status = HttpStatusCode.OK,
                    headers = headersOf("Set-Cookie", "jsessionid=stale; Path=/"),
                )

                request.method == HttpMethod.Get -> {
                    // A down that never answers: the command must not depend on it.
                    kotlinx.coroutines.delay(60_000)
                    respond("", HttpStatusCode.OK)
                }

                request.method == HttpMethod.Post -> {
                    posts.incrementAndGet()
                    respond("", HttpStatusCode.NotFound)
                }

                else -> respond("", HttpStatusCode.NotFound)
            }
        }
        val cloud = UserAuthCloud(
            UserAuthWire(HttpClient(engine), "https://auth.test", "003-2021-test", commandTimeoutMs = 60_000),
        )

        assertFailsWith<StaleSessionException> {
            cloud.getDeviceList()
        }
        assertEquals(2, posts.get(), "one retry and then the failure surfaces")
    }
}
