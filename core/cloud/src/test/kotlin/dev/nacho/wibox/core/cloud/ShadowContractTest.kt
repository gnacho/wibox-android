package dev.nacho.wibox.core.cloud

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ShadowContractTest {
    @Test
    fun statusUsesPostTokenHeaderAndTypedBody() = runTest {
        val session = fixtureSession()
        lateinit var captured: HttpRequestData
        val engine = MockEngine { request ->
            captured = request
            respond(Fixtures.text("shadow_status.json"), HttpStatusCode.OK, headersOf("Content-Type", "application/json"))
        }
        val cloud = testCloud(engine, RecordingTokenStore(session), TestClock())

        val shadow = cloud.getDeviceShadow("fixture-device-001")

        assertRequest(captured, ProtocolTestPaths.STATUS, session)
        val body = Json.parseToJsonElement((captured.body as TextContent).text).jsonObject
        assertEquals("fixture-device-001", body.getValue("thingId").jsonPrimitive.content)
        assertEquals("Device_state", body.getValue("fieldNames").jsonArray.single().jsonPrimitive.content)
        assertEquals("fixture-device-001", shadow.device.id)
        val state = requireNotNull(shadow.state)
        assertEquals(0, state.f1State)
        assertEquals(1, state.switchState?.state)
        assertEquals(1, state.locks?.single()?.channel)
        assertEquals("fixture-relay", state.smartRelays?.single()?.code)
        assertTrue(state.unknownFields["Future_state"]?.contains("fixture") == true)
        assertEquals("\"fixture-switch\"", state.switchState?.unknownFields?.get("Future_switch"))
        assertEquals("\"fixture-lock\"", state.locks?.single()?.unknownFields?.get("Future_lock"))
        assertEquals("\"fixture-relay-extension\"", state.smartRelays?.single()?.unknownFields?.get("Future_relay"))
        assertTrue(shadow.unknownFields.containsKey("fields.Future_fields"))
        assertTrue(shadow.responseUnknownFields.containsKey("Future_response"))
        assertEquals(1_800_000_000_123L, shadow.timestampMillis)
        cloud.close()
    }

    @Test
    fun multiStatusUsesArrayBodyAndPreservesMissingState() = runTest {
        val session = fixtureSession()
        lateinit var captured: HttpRequestData
        val engine = MockEngine { request ->
            captured = request
            respond(Fixtures.text("shadow_status_multi.json"), HttpStatusCode.OK, headersOf("Content-Type", "application/json"))
        }
        val cloud = testCloud(engine, RecordingTokenStore(session), TestClock())

        val shadows = cloud.getDeviceShadows(listOf("fixture-device-001", "fixture-device-002"))

        assertRequest(captured, ProtocolTestPaths.MULTI, session)
        val body = Json.parseToJsonElement((captured.body as TextContent).text).jsonArray
        assertEquals(2, body.size)
        assertEquals("fixture-device-002", body[1].jsonObject.getValue("thingId").jsonPrimitive.content)
        assertEquals(2, shadows.size)
        assertNull(shadows[1].state)
        cloud.close()
    }

    @Test
    fun subscribeUsesDeviceStateSubscriptionAndReturnsAck() = runTest {
        val session = fixtureSession()
        lateinit var captured: HttpRequestData
        val engine = MockEngine { request ->
            captured = request
            respond(Fixtures.text("shadow_ack.json"), HttpStatusCode.OK, headersOf("Content-Type", "application/json"))
        }
        val cloud = testCloud(engine, RecordingTokenStore(session), TestClock())

        val ack = cloud.subscribeDeviceShadows(listOf("fixture-device-001", "fixture-device-002"))

        assertRequest(captured, ProtocolTestPaths.SUBSCRIBE, session)
        val body = Json.parseToJsonElement((captured.body as TextContent).text).jsonObject
        assertEquals("2021", body.getValue("appId").jsonPrimitive.content)
        assertEquals("fixture-client", body.getValue("clientId").jsonPrimitive.content)
        assertEquals(2, body.getValue("subData").jsonArray.size)
        assertEquals("Device_state", body.getValue("subData").jsonArray[0].jsonObject.getValue("fieldNames").jsonArray.single().jsonPrimitive.content)
        assertEquals("fixture-acknowledgement", ack.data)
        assertEquals("/fixture/shadow/ack", ack.path)
        assertEquals(1_800_000_000_789L, ack.timestampMillis)
        cloud.close()
    }

    @Test
    fun unsubscribeUsesReasonAndEmptyFieldsAndReturnsAck() = runTest {
        val session = fixtureSession()
        lateinit var captured: HttpRequestData
        val engine = MockEngine { request ->
            captured = request
            respond(Fixtures.text("shadow_ack.json"), HttpStatusCode.OK, headersOf("Content-Type", "application/json"))
        }
        val cloud = testCloud(engine, RecordingTokenStore(session), TestClock())

        val ack = cloud.unsubscribeDeviceShadows(
            listOf("fixture-device-001"),
            ShadowUnsubscribeReason.DEVICE_UNBIND,
        )

        assertRequest(captured, ProtocolTestPaths.UNSUBSCRIBE, session)
        val subData = Json.parseToJsonElement((captured.body as TextContent).text).jsonObject
            .getValue("subData").jsonArray.single().jsonObject
        assertEquals("2", subData.getValue("doType").jsonPrimitive.content)
        assertTrue(subData.getValue("fieldNames").jsonArray.isEmpty())
        assertEquals("fixture-acknowledgement", ack.data)
        cloud.close()
    }

    @Test
    fun shadowWithoutTokenDoesNotSendRequest() = runTest {
        var requests = 0
        val engine = MockEngine {
            requests += 1
            respond("{}", HttpStatusCode.OK)
        }
        val cloud = testCloud(engine, RecordingTokenStore(), TestClock())

        assertFailsWith<CloudError.MissingToken> { cloud.getDeviceShadow("fixture-device-001") }
        assertEquals(0, requests)
        cloud.close()
    }

    private fun assertRequest(request: HttpRequestData, path: String, session: CloudSession) {
        assertEquals(HttpMethod.Post, request.method)
        assertEquals(path, request.url.encodedPath)
        assertEquals(session.accessToken, request.headers["token"])
        assertNull(request.headers["Authorization"])
        assertTrue(request.url.parameters.isEmpty())
    }
}

internal object ProtocolTestPaths {
    const val STATUS = "/openapi-tdk/device/status"
    const val MULTI = "/openapi-tdk/device/status/multi"
    const val SUBSCRIBE = "/openapi-tdk/device/status/sub"
    const val UNSUBSCRIBE = "/openapi-tdk/device/status/unsub"
}
