package dev.nacho.wibox.core.cloud.internal

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import dev.nacho.wibox.core.cloud.UserAuthCloud
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class UserAuthWireConcurrencyTest {

    @Test
    fun concurrentCommandsDoNotShareDownPoll() = runBlocking {
        val inFlight = AtomicBoolean(false)
        val overlap = AtomicBoolean(false)
        val pendingCommand = AtomicReference<String?>(null)

        val engine = MockEngine { request ->
            when {
                request.method == HttpMethod.Get && request.headers["Cookie"] == null -> {
                    respond(
                        content = "",
                        status = HttpStatusCode.OK,
                        headers = headersOf("Set-Cookie", "jsessionid=test-session; Path=/"),
                    )
                }

                request.method == HttpMethod.Get -> {
                    while (pendingCommand.get() == null) delay(1)
                    val command = pendingCommand.getAndSet(null) ?: "login"
                    inFlight.set(false)
                    respond(
                        content = "<envelope><header><command>$command</command><result>0</result></header>" +
                            "<content><account-id>42</account-id></content></envelope>",
                        status = HttpStatusCode.OK,
                    )
                }

                request.method == HttpMethod.Post -> {
                    if (!inFlight.compareAndSet(false, true)) overlap.set(true)
                    pendingCommand.set("login")
                    respond("", HttpStatusCode.OK)
                }

                else -> respond("", HttpStatusCode.NotFound)
            }
        }

        val client = HttpClient(engine)
        val wire = UserAuthWire(client, "https://auth.test", "003-2021-test", commandTimeoutMs = 3_000)

        coroutineScope {
            val first = async { wire.command("login", mapOf("account" to "one")) }
            val second = async { wire.command("login", mapOf("account" to "two")) }

            assertEquals("42", first.await().content["account-id"])
            assertEquals("42", second.await().content["account-id"])
        }

        assertFalse(overlap.get(), "dos comandos compartieron el down long-poll")
        client.close()
    }

    @Test
    fun concurrentLoginsReuseOneCloudSession() = runBlocking {
        val posts = AtomicInteger(0)
        val pendingCommand = AtomicReference<String?>(null)
        val engine = MockEngine { request ->
            when {
                request.method == HttpMethod.Get && request.headers["Cookie"] == null -> {
                    respond(
                        content = "",
                        status = HttpStatusCode.OK,
                        headers = headersOf("Set-Cookie", "jsessionid=cache-test; Path=/"),
                    )
                }

                request.method == HttpMethod.Get -> {
                    while (pendingCommand.get() == null) delay(1)
                    val command = pendingCommand.getAndSet(null) ?: "login"
                    respond(
                        content = "<envelope><header><command>$command</command><result>0</result></header>" +
                            "<content><account-id>42</account-id></content></envelope>",
                        status = HttpStatusCode.OK,
                    )
                }

                request.method == HttpMethod.Post -> {
                    posts.incrementAndGet()
                    pendingCommand.set("login")
                    respond("", HttpStatusCode.OK)
                }

                else -> respond("", HttpStatusCode.NotFound)
            }
        }
        val client = HttpClient(engine)
        val cloud = UserAuthCloud(UserAuthWire(client, "https://auth.test", "003-2021-test", commandTimeoutMs = 3_000))

        coroutineScope {
            val first = async { cloud.login("account", "password") }
            val second = async { cloud.login("account", "password") }
            assertEquals("42", first.await().accountId)
            assertEquals("42", second.await().accountId)
        }

        assertEquals(1, posts.get(), "el segundo login reabrio el canal cloud")
        client.close()
    }
}
