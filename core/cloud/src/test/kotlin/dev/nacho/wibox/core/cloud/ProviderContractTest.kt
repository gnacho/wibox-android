package dev.nacho.wibox.core.cloud

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.single
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class ProviderContractTest {
    @Test
    fun catalogContains64CapabilitiesWithoutPromotingDeclaredSupport() = runTest {
        val provider = provider(FakeLegacyCloud())

        assertEquals(64, provider.capabilities.size)
        assertEquals(64, provider.capabilities.map { it.key.id }.distinct().size)
        assertTrue(provider.capabilities.all { it.evidenceReferences.isNotEmpty() })
        assertTrue(provider.capabilities.all { it.profileVersion.isNotBlank() })
        assertTrue(provider.capabilities.all { it.device == null && it.channel == null && it.revalidateAtMillis == null })
        val upgrade = provider.capabilities.single { it.key.id == "app.update_check" }
        assertEquals(CapabilityEvidence.STATIC_EVIDENCE_ONLY, upgrade.declaredEvidence)
        assertEquals(CapabilityEvidence.IMPLEMENTED_CONTRACT, upgrade.operationEvidence)
        assertEquals(EvidenceOrigin.DYNAMIC_CAPTURE, upgrade.operationOrigin)
        val unlock = provider.capabilities.single { it.key.id == "actions.unlock" }
        assertEquals(CapabilityEvidence.UNSUPPORTED, unlock.operationEvidence)
        assertNull(unlock.operation)
        val negotiation = provider.capabilities.single { it.key.id == "core.capability_negotiation" }
        assertEquals(CapabilityEvidence.LOCAL_ONLY, negotiation.operationEvidence)
        assertEquals(
            setOf(
                "core.lifecycle", "auth.oauth_password", "auth.oauth_refresh", "auth.session_restore",
                "shadow.get", "shadow.multi", "shadow.subscribe", "shadow.unsubscribe",
                "events.alarm_parser", "app.update_check",
            ),
            provider.capabilities.mapNotNull { it.operation?.let { operation -> operation.capabilityId } }.toSet(),
        )
        assertEquals(ProviderId.STOCK_QVCLOUD, provider.descriptor.id)
        assertEquals(ProviderConnectionKind.STOCK, provider.descriptor.connectionKind)
        provider.close()
    }

    @Test
    fun upgradeDelegatesToLegacyWithoutDuplicatingProtocol() = runTest {
        val cloud = FakeLegacyCloud()
        val provider = provider(cloud)
        val request = upgradeRequest()

        val result = provider.maintenance.checkAppUpgrade(request)

        assertSame(cloud.upgradeInfo, result)
        assertEquals(1, cloud.upgradeCalls)
        assertSame(request, cloud.lastUpgradeRequest)
        provider.close()
    }

    @Test
    fun providerErrorsPreserveCategoryRetryAndCapability() = runTest {
        val cases = listOf(
            CloudError.Authentication("checkAppUpgrade") to ProviderError.Authentication::class,
            CloudError.Authorization("checkAppUpgrade") to ProviderError.Authorization::class,
            CloudError.Network("checkAppUpgrade") to ProviderError.Network::class,
            CloudError.Timeout("checkAppUpgrade") to ProviderError.Timeout::class,
            CloudError.Parse("checkAppUpgrade") to ProviderError.Parse::class,
            CloudError.InvalidInput("checkAppUpgrade") to ProviderError.InvalidInput::class,
            CloudError.Unsupported("checkAppUpgrade", "fixture") to ProviderError.Unsupported::class,
        )
        for ((legacy, expected) in cases) {
            val cloud = FakeLegacyCloud().apply { error = legacy }
            val provider = provider(cloud)

            val error = assertFailsWith<ProviderError> { provider.maintenance.checkAppUpgrade(upgradeRequest()) }

            assertEquals(expected, error::class)
            assertEquals("checkAppUpgrade", error.operation)
            assertEquals("app.update_check", error.capability?.id)
            assertEquals(1, cloud.upgradeCalls)
            provider.close()
        }
    }

    @Test
    fun sessionsAreOpaqueAndCredentialsAreRedacted() = runTest {
        val cloud = FakeLegacyCloud()
        val session = requireNotNull(cloud.session)
        val provider = provider(cloud)
        val material = CredentialMaterial("fixture-user", "fixture-secret")

        val authenticated = provider.session.authenticate(material)
        val restored = provider.session.restoreSession()
        val refreshed = provider.session.refreshSession()

        assertEquals(SessionState.AUTHENTICATED, authenticated.state)
        assertEquals(SessionState.RESTORED, restored?.state)
        assertEquals(SessionState.REFRESHED, refreshed.state)
        assertEquals(authenticated.opaqueId, refreshed.opaqueId)
        assertFalse(authenticated.toString().contains(session.accessToken))
        assertFalse(authenticated.toString().contains(session.refreshToken))
        assertFalse(authenticated.toString().contains(authenticated.opaqueId))
        assertFalse(material.toString().contains("fixture-secret"))
        assertFailsWith<ProviderError.Unsupported> { provider.session.logout() }
        provider.close()
    }

    @Test
    fun shadowOperationsMapToNeutralSnapshotsAndAck() = runTest {
        val cloud = FakeLegacyCloud()
        val provider = provider(cloud)

        val single = provider.devices.getShadow("fixture-device-001")
        val multi = provider.devices.getShadows(listOf("fixture-device-001"))
        val subscribed = provider.devices.subscribeShadow(listOf("fixture-device-001"))
        val unsubscribed = provider.devices.unsubscribeShadow(
            listOf("fixture-device-001"),
            ShadowUnsubscribeReason.DEVICE_UNBIND,
        )

        assertEquals("fixture-device-001", single.device.id)
        assertEquals(setOf("Device_state"), single.requestedFields)
        assertEquals(emptySet(), single.returnedFields)
        assertTrue(single.fresh)
        assertEquals(1, multi.size)
        assertEquals(cloud.ack.data, subscribed.data)
        assertEquals(cloud.ack.timestampMillis, unsubscribed.timestampMillis)
        assertEquals(4, cloud.shadowCalls)
        provider.close()
    }

    @Test
    fun alarmParserCreatesEnvelopeAndRealtimeKeepsUnsupportedAndCancellation() = runTest {
        val cloud = FakeLegacyCloud()
        val provider = provider(cloud)
        val envelope = provider.events.parseAlarm(
            mapOf(
                "AlarmId" to "fixture-alarm",
                "DevUmid" to "fixture-device-001",
                "AlarmEvent" to "19",
                "NotReadCount" to "1",
                "AlarmTime" to "fixture-time",
                "FutureField" to "fixture-value",
            ),
        )

        assertEquals(ProviderEventType.INCOMING_CALL, envelope.type)
        assertEquals(EventSource.FCM, envelope.source)
        assertEquals("fixture-value", envelope.unknownFields["FutureField"])
        assertFailsWith<ProviderError.Unsupported> { provider.events.observeEvents().single() }

        cloud.eventFlow = flow { throw CancellationException("fixture-cancel") }
        assertFailsWith<CancellationException> { provider.events.observeEvents().single() }
        provider.close()
    }

    @Test
    fun unsupportedActionsFailBeforeLegacyExecutionAndAckLevelsStayDistinct() = runTest {
        val cloud = FakeLegacyCloud()
        val provider = provider(cloud)
        val command = actionCommand(ActionType.CALL_ACCEPT)

        val error = assertFailsWith<ProviderError.Unsupported> { provider.actions.execute(command) }

        assertEquals(ProviderRetryPolicy.DO_NOT_RETRY, error.retryPolicy)
        assertEquals(0, cloud.actionCalls)
        val observation = eventEnvelope()
        assertFalse(ActionOutcome.localAccepted("fixture").canClaimPhysicalEffect())
        assertFalse(ActionOutcome.providerAccepted("fixture").canClaimPhysicalEffect())
        assertFalse(ActionOutcome.deviceAcknowledged("fixture").canClaimPhysicalEffect())
        assertFalse(ActionOutcome.physicalObserved("fixture", null).canClaimPhysicalEffect())
        assertTrue(ActionOutcome.physicalObserved("fixture", observation).canClaimPhysicalEffect())
        assertFalse(ActionOutcome.unknown("fixture").canClaimPhysicalEffect())
        provider.close()
    }

    @Test
    fun listDevicesReturnsTheLegacyResultAndDiscoveryStaysUnsupported() = runTest {
        val provider = provider(FakeLegacyCloud())

        assertEquals(emptyList(), provider.devices.listDevices())
        assertFailsWith<ProviderError.Unsupported> { provider.devices.discoverRegionalServices() }
        provider.close()
    }

    @Test
    fun prepareUsesRestoredSessionAndCloseIsSingleOwner() = runTest {
        val cloud = FakeLegacyCloud()
        val provider = provider(cloud, closeClientWhenClosed = true)

        assertEquals(PreparationState.READY, provider.prepare())
        provider.close()
        provider.close()

        assertEquals(1, cloud.closeCalls)
        assertFailsWith<ProviderError.Closed> { provider.session.restoreSession() }
        assertEquals(ProviderId.STOCK_QVCLOUD, provider.descriptor.id)

        val unownedCloud = FakeLegacyCloud().apply { session = null }
        val unowned = provider(unownedCloud, closeClientWhenClosed = false)
        assertEquals(PreparationState.AUTHENTICATION_REQUIRED, unowned.prepare())
        unowned.close()
        assertEquals(0, unownedCloud.closeCalls)
    }

    @Test
    fun prepareDoesNotReturnReadyAfterConcurrentClose() = runTest {
        val cloud = FakeLegacyCloud()
        val restoreStarted = CompletableDeferred<Unit>()
        val allowRestore = CompletableDeferred<Unit>()
        cloud.restoreHook = {
            restoreStarted.complete(Unit)
            allowRestore.await()
        }
        val provider = provider(cloud)

        val preparation = async(SupervisorJob()) { provider.prepare() }
        restoreStarted.await()
        provider.close()
        allowRestore.complete(Unit)

        assertFailsWith<ProviderError.Closed> { preparation.await() }
    }

    @Test
    fun legacyApiRemainsUsableAndPublicProviderApiHasNoKtorTypes() = runTest {
        val cloud = FakeLegacyCloud()
        val provider = provider(cloud, closeClientWhenClosed = false)

        assertSame(cloud.upgradeInfo, cloud.checkAppUpgrade(upgradeRequest()))
        val ports = listOf(
            WiboxProvider::class.java,
            SessionPort::class.java,
            DevicePort::class.java,
            EventPort::class.java,
            ActionPort::class.java,
            MaintenancePort::class.java,
        )
        for (port in ports) {
            for (method in port.methods) {
                assertFalse(method.returnType.name.startsWith("io.ktor"))
                assertTrue(method.parameterTypes.none { it.name.startsWith("io.ktor") })
            }
        }
        provider.close()
    }

    private fun provider(
        cloud: FakeLegacyCloud,
        closeClientWhenClosed: Boolean = false,
    ): WiboxProvider = WiboxProviderFactory.stockQvCloud(
        cloud = cloud,
        sessionContext = ProviderSessionContext(
            installationId = "fixture-installation",
            accountAlias = "fixture-account",
            environment = ProviderEnvironment.TEST,
        ),
        closeClientWhenClosed = closeClientWhenClosed,
    )

    private fun upgradeRequest(): AppUpgradeRequest = AppUpgradeRequest(
        baseUrl = "https://unit.test/",
        currentVersion = 18,
        clientId = "fixture-upgrade-client",
    )

    private fun actionCommand(type: ActionType = ActionType.UNLOCK): ActionCommand = ActionCommand(
        commandId = "fixture-command",
        device = DeviceRef(ProviderId.STOCK_QVCLOUD, "fixture-device-001"),
        channel = 1,
        type = type,
    )

    private fun eventEnvelope(): EventEnvelope = EventEnvelope(
        eventId = "fixture-alarm",
        provider = ProviderId.STOCK_QVCLOUD,
        type = ProviderEventType.INCOMING_CALL,
        device = DeviceRef(ProviderId.STOCK_QVCLOUD, "fixture-device-001"),
        channel = 1,
        cameraName = "Fixture entrance",
        unreadCount = 1,
        occurredAtRaw = "fixture-time",
        receivedAtMillis = null,
        attachments = emptyList(),
        unknownFields = emptyMap(),
        cursor = null,
        source = EventSource.FCM,
        duplicate = false,
    )
}

private class FakeLegacyCloud : WiboxCloud {
    override val capabilities = CloudCapabilities(emptySet())
    var error: Throwable? = null
    var session: CloudSession? = CloudSession(
        accessToken = "fixture-access-secret",
        refreshToken = "fixture-refresh-secret",
        accessTokenExpiresAtMillis = 1_800_003_600_000L,
        refreshTokenExpiresAtMillis = 1_800_007_200_000L,
    )
    var upgradeCalls = 0
    var lastUpgradeRequest: AppUpgradeRequest? = null
    var shadowCalls = 0
    var actionCalls = 0
    var closeCalls = 0
    var restoreHook: suspend () -> Unit = {}
    var eventFlow: Flow<CloudEvent> = flow {
        throw CloudError.Unsupported("observeEvents", "fixture unsupported")
    }
    val upgradeInfo = AppUpgradeInfo("tdkcloud", 0, 0, "5.1.11", "", "", 0, "", "")
    val shadow = DeviceShadow(
        device = CloudDevice("fixture-device-001"),
        state = null,
        path = "/fixture",
        timestampMillis = 123L,
        unknownFields = mapOf("fields.Future" to "fixture"),
        responseUnknownFields = mapOf("Future" to "fixture"),
    )
    val ack = ShadowAcknowledgement("fixture-ack", "/fixture", 456L)

    private fun failIfNeeded() {
        error?.let { throw it }
    }

    override suspend fun authenticate(credentials: CloudCredentials): CloudSession {
        failIfNeeded()
        return requireNotNull(session)
    }

    override suspend fun restoreSession(): CloudSession? {
        failIfNeeded()
        restoreHook()
        return session
    }

    override suspend fun refreshSession(): CloudSession {
        failIfNeeded()
        return requireNotNull(session)
    }

    override suspend fun getDeviceShadow(deviceId: String): DeviceShadow {
        failIfNeeded()
        shadowCalls += 1
        return shadow
    }

    override suspend fun getDeviceShadows(deviceIds: List<String>): List<DeviceShadow> {
        failIfNeeded()
        shadowCalls += 1
        return listOf(shadow)
    }

    override suspend fun subscribeDeviceShadows(deviceIds: List<String>): ShadowAcknowledgement {
        failIfNeeded()
        shadowCalls += 1
        return ack
    }

    override suspend fun unsubscribeDeviceShadows(
        deviceIds: List<String>,
        reason: ShadowUnsubscribeReason,
    ): ShadowAcknowledgement {
        failIfNeeded()
        shadowCalls += 1
        return ack
    }

    override fun parseAlarmEvent(payload: Map<String, String>): CloudEvent {
        failIfNeeded()
        return CloudEvent(
            type = CloudEventType.INCOMING_CALL,
            alarmId = payload["AlarmId"],
            deviceId = payload["DevUmid"],
            channel = 1,
            cameraName = "Fixture entrance",
            unreadCount = 1,
            alarmTime = payload["AlarmTime"],
            unknownFields = payload.filterKeys { it == "FutureField" },
        )
    }

    override fun observeEvents(): Flow<CloudEvent> = eventFlow

    override suspend fun listDevices(): List<CloudDevice> {
        failIfNeeded()
        return emptyList()
    }

    override suspend fun discoverServices(): Map<CloudService, String> {
        failIfNeeded()
        return emptyMap()
    }

    override suspend fun performAction(deviceId: String, action: CloudAction): CloudActionAcknowledgement {
        actionCalls += 1
        failIfNeeded()
        return CloudActionAcknowledgement(0, "fixture")
    }

    override suspend fun checkAppUpgrade(request: AppUpgradeRequest): AppUpgradeInfo {
        upgradeCalls += 1
        lastUpgradeRequest = request
        failIfNeeded()
        return upgradeInfo
    }

    override fun close() {
        closeCalls += 1
    }
}
