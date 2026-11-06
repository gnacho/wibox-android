package dev.nacho.wibox.core.cloud

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.single
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class StockProviderOperationsTest {
    @Test
    fun listDevicesMapsCloudDevicesAndDropsInvalidIdentifiers() = runTest {
        val cloud = FakeCloud().apply {
            devices = listOf(
                CloudDevice("fixture-device-001"),
                CloudDevice("fixture-device-001"),
                CloudDevice(" "),
                CloudDevice("fixture-device-002"),
            )
        }
        val provider = provider(cloud)

        val snapshots = provider.devices.listDevices()

        assertEquals(listOf("fixture-device-001", "fixture-device-002"), snapshots.map { it.device.id })
        assertTrue(snapshots.all { it.device.provider == ProviderId.STOCK_QVCLOUD })
        assertTrue(snapshots.all { it.source == DeviceDataSource.CLOUD })
        assertTrue(snapshots.all { it.fresh })
        assertTrue(snapshots.all { it.displayName == null && it.declaredModel == null })
        provider.close()
    }

    @Test
    fun listDevicesKeepsUnsupportedAsATypedProviderError() = runTest {
        val cloud = FakeCloud().apply { listError = CloudError.Unsupported("listDevices", "fixture") }
        val provider = provider(cloud)

        val error = assertFailsWith<ProviderError.Unsupported> { provider.devices.listDevices() }

        assertEquals("listDevices", error.operation)
        assertEquals("devices.list", error.capability?.id)
        provider.close()
    }

    @Test
    fun executeMapsSupportedActionsAndReportsProviderAcceptance() = runTest {
        val expected = mapOf(
            ActionType.UNLOCK to CloudAction.UNLOCK,
            ActionType.F1 to CloudAction.F1,
            ActionType.CALL_GUARD to CloudAction.CALL_GUARD,
            ActionType.START_CALL to CloudAction.START_CALL,
            ActionType.STOP_CALL to CloudAction.STOP_CALL,
        )
        for ((type, action) in expected) {
            val cloud = FakeCloud()
            val provider = provider(cloud)

            val outcome = provider.actions.execute(command(type))

            assertEquals(listOf(action), cloud.actions)
            assertEquals(listOf("fixture-device-001"), cloud.actionDeviceIds)
            assertEquals(ActionAcknowledgementLevel.PROVIDER_ACCEPTED, outcome.level)
            assertEquals(0, outcome.providerCode)
            assertEquals("fixture-command", outcome.commandId)
            provider.close()
        }
    }

    @Test
    fun executeMapsNonZeroAcknowledgementToRejection() = runTest {
        val cloud = FakeCloud().apply { actionAck = CloudActionAcknowledgement(10401, null) }
        val provider = provider(cloud)

        val outcome = provider.actions.execute(command(ActionType.UNLOCK))

        assertEquals(ActionAcknowledgementLevel.REJECTED, outcome.level)
        assertEquals(10401, outcome.providerCode)
        provider.close()
    }

    @Test
    fun executeRejectsCallAcceptAndRejectBeforeTouchingTheCloud() = runTest {
        val expected = mapOf(
            ActionType.CALL_ACCEPT to "actions.call_accept",
            ActionType.CALL_REJECT to "actions.call_reject",
        )
        for ((type, capability) in expected) {
            val cloud = FakeCloud()
            val provider = provider(cloud)

            val error = assertFailsWith<ProviderError.Unsupported> { provider.actions.execute(command(type)) }

            assertEquals(capability, error.capability?.id)
            assertTrue(cloud.actions.isEmpty())
            provider.close()
        }
    }

    @Test
    fun executeMapsCloudFailures() = runTest {
        val cloud = FakeCloud().apply { actionError = CloudError.Network("performAction") }
        val provider = provider(cloud)

        val error = assertFailsWith<ProviderError.Network> { provider.actions.execute(command(ActionType.UNLOCK)) }

        assertEquals("actions.unlock", error.capability?.id)
        provider.close()
    }

    @Test
    fun observeEventsForwardsCloudEventsAsEnvelopes() = runTest {
        val cloud = FakeCloud().apply {
            events = flow {
                emit(
                    CloudEvent(
                        type = CloudEventType.INCOMING_CALL,
                        alarmId = "fixture-alarm",
                        deviceId = "fixture-device-001",
                        channel = 1,
                        cameraName = "Fixture entrance",
                        unreadCount = 1,
                        alarmTime = "fixture-time",
                    ),
                )
            }
        }
        val provider = provider(cloud)

        val items = provider.events.observeEvents().toList()

        val envelope = (items.single() as EventStreamItem.Envelope).event
        assertEquals(ProviderEventType.INCOMING_CALL, envelope.type)
        assertEquals(ProviderId.STOCK_QVCLOUD, envelope.provider)
        assertEquals("fixture-device-001", envelope.device?.id)
        assertEquals(EventSource.FCM, envelope.source)
        provider.close()
    }

    @Test
    fun observeEventsMapsCloudFailuresAndPropagatesCancellation() = runTest {
        val cloud = FakeCloud().apply {
            events = flow { throw CloudError.Unsupported("observeEvents", "fixture") }
        }
        val provider = provider(cloud)

        assertFailsWith<ProviderError.Unsupported> { provider.events.observeEvents().single() }

        cloud.events = flow { throw CancellationException("fixture-cancel") }
        assertFailsWith<CancellationException> { provider.events.observeEvents().single() }
        provider.close()
    }

    private fun provider(cloud: WiboxCloud): WiboxProvider = WiboxProviderFactory.stockQvCloud(
        cloud = cloud,
        sessionContext = ProviderSessionContext(
            installationId = "fixture-installation",
            accountAlias = "fixture-account",
            environment = ProviderEnvironment.TEST,
        ),
        closeClientWhenClosed = false,
    )

    private fun command(type: ActionType): ActionCommand = ActionCommand(
        commandId = "fixture-command",
        device = DeviceRef(ProviderId.STOCK_QVCLOUD, "fixture-device-001"),
        channel = 1,
        type = type,
    )
}

private class FakeCloud : WiboxCloud {
    override val capabilities = CloudCapabilities(emptySet())
    var devices: List<CloudDevice> = emptyList()
    var listError: Throwable? = null
    var actions: List<CloudAction> = emptyList()
    var actionDeviceIds: List<String> = emptyList()
    var actionAck = CloudActionAcknowledgement(0, null)
    var actionError: Throwable? = null
    var events: Flow<CloudEvent> = flow { }

    override suspend fun authenticate(credentials: CloudCredentials): CloudSession = error("unused")
    override suspend fun restoreSession(): CloudSession? = null
    override suspend fun refreshSession(): CloudSession = error("unused")
    override suspend fun getDeviceShadow(deviceId: String): DeviceShadow = error("unused")
    override suspend fun getDeviceShadows(deviceIds: List<String>): List<DeviceShadow> = error("unused")
    override suspend fun subscribeDeviceShadows(deviceIds: List<String>): ShadowAcknowledgement = error("unused")
    override suspend fun unsubscribeDeviceShadows(
        deviceIds: List<String>,
        reason: ShadowUnsubscribeReason,
    ): ShadowAcknowledgement = error("unused")

    override fun parseAlarmEvent(payload: Map<String, String>): CloudEvent = error("unused")
    override fun observeEvents(): Flow<CloudEvent> = events

    override suspend fun listDevices(): List<CloudDevice> {
        listError?.let { throw it }
        return devices
    }

    override suspend fun discoverServices(): Map<CloudService, String> = error("unused")

    override suspend fun performAction(deviceId: String, action: CloudAction): CloudActionAcknowledgement {
        actionError?.let { throw it }
        actions = actions + action
        actionDeviceIds = actionDeviceIds + deviceId
        return actionAck
    }

    override fun close() = Unit
}
