package dev.nacho.wibox.core.cloud

import dev.nacho.wibox.core.cloud.internal.RealWiboxCloud
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Wire side of the stock contract that lives outside the OAuth/Shadow HTTP client:
 * the account directory (get-device-list), the physical action transport and the
 * push payloads that carry alarms. Each seam is exercised with a double, so the
 * client behaviour is proven without a device or credentials.
 */
class RealCloudWiringTest {
    @Test
    fun listDevicesMapsTheAccountDirectoryAndDropsInvalidIdentifiers() = runTest {
        val cloud = wiredCloud(
            deviceDirectory = CloudDeviceDirectory {
                listOf(
                    CloudDevice(
                        id = "23123csfewyk",
                        name = "ProbeOPO",
                        model = "IDS7938",
                        channelNumber = 1,
                        shared = true,
                        powers = "1,3,4,1001",
                    ),
                    CloudDevice(id = "23123csfewyk", name = "duplicate"),
                    CloudDevice(id = " "),
                )
            },
        )

        val devices = cloud.listDevices()

        assertEquals(listOf("23123csfewyk"), devices.map { it.id })
        assertEquals("ProbeOPO", devices.single().name)
        assertEquals("IDS7938", devices.single().model)
        assertEquals("1,3,4,1001", devices.single().powers)
        assertTrue(devices.single().shared)
        assertTrue(cloud.capabilities.supports(CloudCapability.DEVICE_LIST))
        cloud.close()
    }

    @Test
    fun listDevicesStaysUnsupportedWithoutAnAccountDirectory() = runTest {
        val cloud = wiredCloud()

        val error = assertFailsWith<CloudError.Unsupported> { cloud.listDevices() }

        assertEquals("listDevices", error.operation)
        assertFalse(cloud.capabilities.supports(CloudCapability.DEVICE_LIST))
        cloud.close()
    }

    @Test
    fun listDevicesMapsTheFailuresOfTheAccountChannel() = runTest {
        val timedOut = wiredCloud(
            deviceDirectory = CloudDeviceDirectory { throw UserAuthTimeoutException("get-device-list") },
        )
        assertFailsWith<CloudError.Timeout> { timedOut.listDevices() }
        timedOut.close()

        val rejected = wiredCloud(
            deviceDirectory = CloudDeviceDirectory { throw UserAuthException("get-device-list", 100100003) },
        )
        val error = assertFailsWith<CloudError.Provider> { rejected.listDevices() }
        assertEquals(100100003, error.providerCode)
        rejected.close()

        val broken = wiredCloud(deviceDirectory = CloudDeviceDirectory { error("fixture transport") })
        assertFailsWith<CloudError.Network> { broken.listDevices() }
        broken.close()
    }

    @Test
    fun performActionDelegatesToTheInjectedTransportAndKeepsItsCode() = runTest {
        val calls = mutableListOf<Pair<String, CloudAction>>()
        val cloud = wiredCloud(
            actionTransport = CloudActionTransport { deviceId, action ->
                calls += deviceId to action
                CloudActionAcknowledgement(providerCode = 0, data = null)
            },
        )

        val ack = cloud.performAction("23123csfewyk", CloudAction.UNLOCK)

        assertEquals(0, ack.providerCode)
        assertNull(ack.data)
        assertEquals(listOf("23123csfewyk" to CloudAction.UNLOCK), calls)
        assertTrue(cloud.capabilities.supports(CloudCapability.PHYSICAL_ACTIONS))
        cloud.close()
    }

    @Test
    fun performActionValidatesTheDeviceAndNeedsATransport() = runTest {
        val cloud = wiredCloud()

        assertFailsWith<CloudError.InvalidInput> {
            cloud.performAction(" ", CloudAction.UNLOCK)
        }
        val error = assertFailsWith<CloudError.Unsupported> {
            cloud.performAction("23123csfewyk", CloudAction.UNLOCK)
        }
        assertEquals("performAction", error.operation)
        assertFalse(cloud.capabilities.supports(CloudCapability.PHYSICAL_ACTIONS))
        cloud.close()
    }

    @Test
    fun performActionMapsTransportFailures() = runTest {
        val cloud = wiredCloud(
            actionTransport = CloudActionTransport { _, _ -> throw CloudError.Timeout("performAction") },
        )
        assertFailsWith<CloudError.Timeout> { cloud.performAction("23123csfewyk", CloudAction.UNLOCK) }
        cloud.close()

        val broken = wiredCloud(actionTransport = CloudActionTransport { _, _ -> error("fixture transport") })
        assertFailsWith<CloudError.Network> { broken.performAction("23123csfewyk", CloudAction.UNLOCK) }
        broken.close()
    }

    @Test
    fun observeEventsEmitsParsedPushPayloadsAndSkipsUnknownOnes() = runTest {
        val payloads = flow {
            emit(fixtureMap("alarm_motion.json"))
            emit(mapOf("AlarmEvent" to "99"))
            emit(fixtureMap("alarm_call.json"))
        }
        val cloud = wiredCloud(alarmPayloads = payloads)

        val events = cloud.observeEvents().toList()

        assertEquals(
            listOf(CloudEventType.MOTION, CloudEventType.INCOMING_CALL),
            events.map { it.type },
        )
        assertEquals("fixture-device-001", events.last().deviceId)
        assertEquals(1, events.last().channel)
        // No push socket exists in the vendor SDK: the stream is fed, not opened.
        assertFalse(cloud.capabilities.supports(CloudCapability.REALTIME_EVENTS))
        cloud.close()
    }

    @Test
    fun observeEventsIsEmptyWithoutAPushSource() = runTest {
        val cloud = wiredCloud()

        assertEquals(emptyList(), cloud.observeEvents().toList())
        cloud.close()
    }

    @Test
    fun stockProviderSurfacesTheAccountFieldsAndTheActionCode() = runTest {
        val cloud = wiredCloud(
            deviceDirectory = CloudDeviceDirectory {
                listOf(
                    CloudDevice(
                        id = "23123csfewyk",
                        name = "ProbeOPO",
                        model = "IDS7938",
                        channelNumber = 2,
                        shared = true,
                        powers = "1,3,1001",
                    ),
                )
            },
            actionTransport = CloudActionTransport { _, _ ->
                CloudActionAcknowledgement(providerCode = 0, data = null)
            },
        )
        val provider = WiboxProviderFactory.stockQvCloud(
            cloud = cloud,
            sessionContext = ProviderSessionContext(installationId = "fixture-installation"),
        )

        val snapshot = provider.devices.listDevices().single()

        assertEquals("23123csfewyk", snapshot.device.id)
        assertEquals("ProbeOPO", snapshot.displayName)
        assertEquals("IDS7938", snapshot.declaredModel)
        assertEquals(DeviceRole.SHARED, snapshot.role)
        assertEquals(
            setOf(DevicePermission.PREVIEW, DevicePermission.PLAYBACK, DevicePermission.UNLOCK),
            snapshot.permissions,
        )
        assertEquals(listOf(1, 2), snapshot.channels.map { it.channel })

        val outcome = provider.actions.execute(
            ActionCommand(
                commandId = "fixture-command",
                device = DeviceRef(ProviderId.STOCK_QVCLOUD, "23123csfewyk"),
                channel = 1,
                type = ActionType.UNLOCK,
            ),
        )
        assertEquals(ActionAcknowledgementLevel.PROVIDER_ACCEPTED, outcome.level)
        assertEquals(0, outcome.providerCode)
        provider.close()
    }

    @Test
    fun userManagementDelegatesToTheAccountChannelAndKeepsItsErrors() = runTest {
        val created = mutableListOf<Pair<String, Set<DevicePermission>>>()
        val administration = FakeUserAdministration(
            onCreate = { deviceId, name, permissions ->
                created += name to permissions
                CloudUserInvitation(
                    id = "g-new",
                    deviceId = deviceId,
                    displayName = name,
                    shareCode = "NEW12345",
                    expiresAtMillis = 1,
                    permissions = permissions,
                    invitePage = "https://share.example/invite?InviteCode=NEW12345",
                    ownerId = "2041769",
                )
            },
        )
        val cloud = wiredCloud(userAdministration = administration)

        val users = cloud.listUsers("23123csfewyk")
        assertEquals(listOf("g1"), users.map { it.id })
        val invitation = cloud.createUserInvitation(
            "23123csfewyk",
            "Ana",
            setOf(DevicePermission.PREVIEW, DevicePermission.UNLOCK),
        )
        assertEquals("g-new", invitation.id)
        assertEquals("NEW12345", invitation.shareCode)
        assertEquals(listOf("Ana" to setOf(DevicePermission.PREVIEW, DevicePermission.UNLOCK)), created)
        cloud.updateUserInvitation("g-new", "Ana editada", setOf(DevicePermission.PREVIEW))
        cloud.revokeUserInvitation("g-new")
        assertEquals("g-new", administration.updated)
        assertEquals("g-new", administration.revoked)
        assertTrue(cloud.capabilities.supports(CloudCapability.USER_MANAGEMENT))
        cloud.close()
    }

    @Test
    fun userManagementStaysUnsupportedWithoutAnAccountChannel() = runTest {
        val cloud = wiredCloud()

        val error = assertFailsWith<CloudError.Unsupported> { cloud.listUsers("23123csfewyk") }
        assertEquals("listUsers", error.operation)
        assertFailsWith<CloudError.Unsupported> { cloud.revokeUserInvitation("g1") }
        assertFailsWith<CloudError.InvalidInput> { cloud.listUsers(" ") }
        assertFalse(cloud.capabilities.supports(CloudCapability.USER_MANAGEMENT))
        cloud.close()
    }

    @Test
    fun aGuestAccountSurfacesAsNotAuthorized() = runTest {
        val cloud = wiredCloud(
            userAdministration = FakeUserAdministration(
                onList = { throw CloudError.Authorization("listUsers") },
            ),
        )

        assertFailsWith<CloudError.Authorization> { cloud.listUsers("23123csfewyk") }
        cloud.close()

        val broken = wiredCloud(userAdministration = FakeUserAdministration(onList = { error("fixture") }))
        assertFailsWith<CloudError.Network> { broken.listUsers("23123csfewyk") }
        broken.close()
    }

    private fun wiredCloud(
        deviceDirectory: CloudDeviceDirectory? = null,
        actionTransport: CloudActionTransport? = null,
        alarmPayloads: CloudAlarmPayloads? = null,
        userAdministration: CloudUserAdministration? = null,
    ): RealWiboxCloud = RealWiboxCloud(
        config = testConfig(),
        tokenStore = RecordingTokenStore(fixtureSession()),
        clock = TestClock(),
        httpClient = HttpClient(MockEngine { respond("{}", HttpStatusCode.OK) }),
        deviceDirectory = deviceDirectory,
        actionTransport = actionTransport,
        alarmPayloads = alarmPayloads,
        userAdministration = userAdministration,
    )

    private class FakeUserAdministration(
        private val onList: suspend () -> List<CloudUserInvitation> = {
            listOf(
                CloudUserInvitation(
                    id = "g1",
                    deviceId = "23123csfewyk",
                    displayName = "Ana",
                    shareCode = "ABC12345",
                    expiresAtMillis = 1_700_000_000_000L,
                    permissions = setOf(DevicePermission.PREVIEW),
                ),
            )
        },
        private val onCreate: suspend (String, String, Set<DevicePermission>) -> CloudUserInvitation = { _, _, _ ->
            error("create not expected")
        },
    ) : CloudUserAdministration {
        var updated: String? = null
        var revoked: String? = null

        override suspend fun listUsers(deviceId: String): List<CloudUserInvitation> = onList()

        override suspend fun createInvitation(
            deviceId: String,
            displayName: String,
            permissions: Set<DevicePermission>,
        ): CloudUserInvitation = onCreate(deviceId, displayName, permissions)

        override suspend fun updateInvitation(
            id: String,
            displayName: String,
            permissions: Set<DevicePermission>,
        ) {
            updated = id
        }

        override suspend fun revokeInvitation(id: String) {
            revoked = id
        }
    }

    private fun fixtureMap(name: String): Map<String, String> =
        Json.parseToJsonElement(Fixtures.text(name)).jsonObject.mapValues { it.value.jsonPrimitive.content }
}
