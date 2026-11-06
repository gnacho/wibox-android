package dev.nacho.wibox.core.cloud

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EventAndUnsupportedTest {
    @Test
    fun parsesDocumentedAlarmTypesAndPreservesUnknownFields() = runTest {
        val cloud = testCloud(unusedEngine(), RecordingTokenStore(), TestClock())

        val call = cloud.parseAlarmEvent(fixtureMap("alarm_call.json"))
        val motion = cloud.parseAlarmEvent(fixtureMap("alarm_motion.json"))
        val answered = cloud.parseAlarmEvent(fixtureMap("alarm_answered.json"))

        assertEquals(CloudEventType.INCOMING_CALL, call.type)
        assertEquals("fixture-device-001", call.deviceId)
        assertEquals(1, call.channel)
        assertEquals(2, call.unreadCount)
        assertEquals("fixture-preserved", call.unknownFields["FutureField"])
        assertEquals(CloudEventType.MOTION, motion.type)
        assertEquals(-1, motion.channel)
        assertEquals(CloudEventType.CALL_ANSWERED, answered.type)
        assertEquals(-1, answered.channel)
        cloud.close()
    }

    @Test
    fun rejectsMalformedAndUndocumentedAlarmPayloads() = runTest {
        val cloud = testCloud(unusedEngine(), RecordingTokenStore(), TestClock())
        val valid = fixtureMap("alarm_call.json")

        assertFailsWith<CloudError.Parse> {
            cloud.parseAlarmEvent(valid + ("NotReadCount" to "invalid"))
        }
        assertFailsWith<CloudError.Unsupported> {
            cloud.parseAlarmEvent(valid + ("AlarmEvent" to "99"))
        }
        cloud.close()
    }

    @Test
    fun capabilitiesAndUnsupportedOperationsNeverClaimRuntimeSupport() = runTest {
        var requests = 0
        val engine = MockEngine {
            requests += 1
            respond("{}", HttpStatusCode.OK)
        }
        val cloud = testCloud(engine, RecordingTokenStore(fixtureSession()), TestClock())

        assertTrue(cloud.capabilities.supports(CloudCapability.OAUTH_PASSWORD))
        assertTrue(cloud.capabilities.supports(CloudCapability.SHADOW_HTTP))
        assertFalse(cloud.capabilities.supports(CloudCapability.REALTIME_EVENTS))
        assertFalse(cloud.capabilities.supports(CloudCapability.DEVICE_LIST))
        assertFalse(cloud.capabilities.supports(CloudCapability.PHYSICAL_ACTIONS))
        assertEquals(emptyList(), cloud.observeEvents().toList())
        assertFailsWith<CloudError.Unsupported> { cloud.listDevices() }
        assertFailsWith<CloudError.Unsupported> { cloud.discoverServices() }
        assertFailsWith<CloudError.Unsupported> {
            cloud.performAction("fixture-device-001", CloudAction.UNLOCK)
        }
        assertEquals(0, requests)
        cloud.close()
    }

    private fun fixtureMap(name: String): Map<String, String> =
        Json.parseToJsonElement(Fixtures.text(name)).jsonObject.mapValues { it.value.jsonPrimitive.content }

    private fun unusedEngine(): MockEngine = MockEngine { respond("{}", HttpStatusCode.OK) }
}
