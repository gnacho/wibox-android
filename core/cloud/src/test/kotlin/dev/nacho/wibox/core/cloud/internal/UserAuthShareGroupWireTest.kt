package dev.nacho.wibox.core.cloud.internal

import dev.nacho.wibox.core.cloud.DevicePermission
import dev.nacho.wibox.core.cloud.UserAuthCloud
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking

/**
 * Wire shape of the user management commands. The stock app posts every share group
 * command to the authenticated duplex endpoint (`/auth/user;jus_duplex=up`), with the
 * kebab-case content of the XStream beans, so this double asserts both the target and
 * the fields while a canned down response feeds the parser.
 */
class UserAuthShareGroupWireTest {

    @Test
    fun createInvitationPostsTheAuthenticatedEnvelopeAndParsesTheShareGroup() = runBlocking {
        val pending = AtomicReference<String?>(null)
        val postedPath = AtomicReference<String?>(null)
        val postedBody = AtomicReference<String?>(null)
        val engine = MockEngine { request ->
            when {
                request.method == HttpMethod.Get && request.headers["Cookie"] == null -> respond(
                    content = "",
                    status = HttpStatusCode.OK,
                    headers = headersOf("Set-Cookie", "jsessionid=share-test; Path=/"),
                )

                request.method == HttpMethod.Get -> {
                    while (pending.get() == null) delay(1)
                    val command = pending.getAndSet(null)
                    respond(
                        content = "<envelope><header><flag>tdkcloud</flag><version>v1.24</version>" +
                            "<command>$command</command><seq>0</seq><result>0</result></header>" +
                            "<content>" +
                            "<share-group-id>g-new</share-group-id>" +
                            "<share-code>NEW12345</share-code>" +
                            "<invite-page>https://share.example/invite?InviteCode=NEW12345</invite-page>" +
                            "<expire-minutes>5</expire-minutes>" +
                            "<device-id>testdevice01</device-id>" +
                            "</content></envelope>",
                        status = HttpStatusCode.OK,
                    )
                }

                request.method == HttpMethod.Post -> {
                    postedPath.set(request.url.toString())
                    postedBody.set((request.body as? io.ktor.http.content.TextContent)?.text.orEmpty())
                    pending.set("generate-nologin-share-invitation")
                    respond("", HttpStatusCode.OK)
                }

                else -> respond("", HttpStatusCode.NotFound)
            }
        }
        val client = HttpClient(engine)
        val cloud = UserAuthCloud(
            UserAuthWire(client, "https://auth.test", "003-2021-test", commandTimeoutMs = 3_000),
        )

        val created = cloud.createShareGroup(
            deviceId = "testdevice01",
            displayName = "Ana",
            permissions = setOf(DevicePermission.PREVIEW, DevicePermission.UNLOCK),
        )

        assertEquals("g-new", created.group.id)
        assertEquals("NEW12345", created.group.shareCode)
        assertEquals("https://share.example/invite?InviteCode=NEW12345", created.invitePage)
        assertEquals(5, created.expireMinutes)
        val path = postedPath.get().orEmpty()
        assertTrue(path.contains("/auth/user"), "expected the authenticated endpoint, got $path")
        assertTrue(path.contains("jus_duplex=up"), "expected the duplex up channel, got $path")
        val body = postedBody.get().orEmpty()
        client.close()
        assertTrue(
            body.contains("<command>generate-nologin-share-invitation</command>"),
            "unexpected command body: $body",
        )
        assertTrue(body.contains("<device-id>testdevice01</device-id>"), "missing device id: $body")
        assertTrue(body.contains("<share-group-memo>Ana</share-group-memo>"), "missing name: $body")
        assertTrue(body.contains("<powers>1,1001</powers>"), "missing powers: $body")
    }

    @Test
    fun listShareGroupsReadsTheNestedGroupsOfTheGateway() = runBlocking {
        val pending = AtomicReference<String?>(null)
        val engine = MockEngine { request ->
            when {
                request.method == HttpMethod.Get && request.headers["Cookie"] == null -> respond(
                    content = "",
                    status = HttpStatusCode.OK,
                    headers = headersOf("Set-Cookie", "jsessionid=list-test; Path=/"),
                )

                request.method == HttpMethod.Get -> {
                    while (pending.get() == null) delay(1)
                    val command = pending.getAndSet(null)
                    respond(
                        content = "<envelope><header><flag>tdkcloud</flag><command>$command</command>" +
                            "<result>0</result></header><content>" +
                            "<share-group><share-group-id>g1</share-group-id>" +
                            "<share-group-memo>Ana</share-group-memo><powers>1,1001</powers>" +
                            "<share-code>ABC12345</share-code><expire-time>1700000000</expire-time>" +
                            "</share-group></content></envelope>",
                        status = HttpStatusCode.OK,
                    )
                }

                request.method == HttpMethod.Post -> {
                    pending.set("get-share-group-list")
                    respond("", HttpStatusCode.OK)
                }

                else -> respond("", HttpStatusCode.NotFound)
            }
        }
        val client = HttpClient(engine)
        val cloud = UserAuthCloud(
            UserAuthWire(client, "https://auth.test", "003-2021-test", commandTimeoutMs = 3_000),
        )

        val groups = cloud.listShareGroups("testdevice01")

        assertEquals(1, groups.size)
        assertEquals("g1", groups.single().id)
        assertEquals("Ana", groups.single().name)
        assertEquals("1,1001", groups.single().powers)
        client.close()
    }
}
