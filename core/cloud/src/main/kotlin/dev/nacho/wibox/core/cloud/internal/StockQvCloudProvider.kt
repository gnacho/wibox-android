package dev.nacho.wibox.core.cloud.internal

import dev.nacho.wibox.core.cloud.ActionCommand
import dev.nacho.wibox.core.cloud.ActionOutcome
import dev.nacho.wibox.core.cloud.ActionPort
import dev.nacho.wibox.core.cloud.AppUpgradeInfo
import dev.nacho.wibox.core.cloud.AppUpgradeRequest
import dev.nacho.wibox.core.cloud.CapabilityKey
import dev.nacho.wibox.core.cloud.CapabilitySnapshot
import dev.nacho.wibox.core.cloud.CloudCredentials
import dev.nacho.wibox.core.cloud.CloudEvent
import dev.nacho.wibox.core.cloud.CloudEventType
import dev.nacho.wibox.core.cloud.CloudSession
import dev.nacho.wibox.core.cloud.CredentialMaterial
import dev.nacho.wibox.core.cloud.DevicePort
import dev.nacho.wibox.core.cloud.DeviceRef
import dev.nacho.wibox.core.cloud.DeviceSnapshot
import dev.nacho.wibox.core.cloud.EventEnvelope
import dev.nacho.wibox.core.cloud.EventPort
import dev.nacho.wibox.core.cloud.EventSource
import dev.nacho.wibox.core.cloud.EventStreamItem
import dev.nacho.wibox.core.cloud.MaintenancePort
import dev.nacho.wibox.core.cloud.PreparationState
import dev.nacho.wibox.core.cloud.ProviderCapabilityCatalog
import dev.nacho.wibox.core.cloud.ProviderConnectionKind
import dev.nacho.wibox.core.cloud.ProviderDescriptor
import dev.nacho.wibox.core.cloud.ProviderError
import dev.nacho.wibox.core.cloud.ProviderEventType
import dev.nacho.wibox.core.cloud.ProviderExtension
import dev.nacho.wibox.core.cloud.ProviderId
import dev.nacho.wibox.core.cloud.ProviderSessionContext
import dev.nacho.wibox.core.cloud.SessionHandle
import dev.nacho.wibox.core.cloud.SessionPort
import dev.nacho.wibox.core.cloud.SessionState
import dev.nacho.wibox.core.cloud.ShadowAcknowledgement
import dev.nacho.wibox.core.cloud.ShadowSnapshot
import dev.nacho.wibox.core.cloud.ShadowSubscriptionAck
import dev.nacho.wibox.core.cloud.ShadowUnsubscribeReason
import dev.nacho.wibox.core.cloud.WiboxCloud
import dev.nacho.wibox.core.cloud.WiboxProvider
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal class StockQvCloudProvider(
    private val cloud: WiboxCloud,
    private val sessionContext: ProviderSessionContext,
    private val closeClientWhenClosed: Boolean,
) : WiboxProvider, SessionPort, DevicePort, EventPort, ActionPort, MaintenancePort {
    private val closed = AtomicBoolean(false)
    private val prepareMutex = Mutex()
    private val opaqueId = UUID.randomUUID().toString()

    override val descriptor = ProviderDescriptor(
        id = ProviderId.STOCK_QVCLOUD,
        profileVersion = "1",
        connectionKind = ProviderConnectionKind.STOCK,
        extensions = setOf(
            ProviderExtension.OPAQUE_ACCOUNT,
            ProviderExtension.DYNAMIC_DEVICE_CREDENTIALS,
            ProviderExtension.QVCLOUD_SHADOW,
            ProviderExtension.FCM_ALARM,
            ProviderExtension.INVITATION_PERMISSIONS,
            ProviderExtension.P2P_MEDIA,
        ),
    )
    override val capabilities: List<CapabilitySnapshot> = ProviderCapabilityCatalog.stockQvCloud
    override val session: SessionPort get() = this
    override val devices: DevicePort get() = this
    override val events: EventPort get() = this
    override val actions: ActionPort get() = this
    override val maintenance: MaintenancePort get() = this

    init {
        if (sessionContext.installationId.isBlank()) {
            throw ProviderError.InvalidInput("stockQvCloud", CapabilityKey("core.lifecycle"))
        }
    }

    override suspend fun prepare(): PreparationState = prepareMutex.withLock {
        ensureOpen("prepare")
        val restored = restoreSession()
        ensureOpen("prepare")
        return if (restored == null) PreparationState.AUTHENTICATION_REQUIRED else PreparationState.READY
    }

    override suspend fun authenticate(material: CredentialMaterial): SessionHandle = providerCall(
        "authenticate",
        CapabilityKey("auth.oauth_password"),
    ) {
        cloud.authenticate(CloudCredentials(material.identifier, material.secret)).toHandle(SessionState.AUTHENTICATED)
    }

    override suspend fun restoreSession(): SessionHandle? = providerCall(
        "restoreSession",
        CapabilityKey("auth.session_restore"),
    ) {
        cloud.restoreSession()?.toHandle(SessionState.RESTORED)
    }

    override suspend fun refreshSession(): SessionHandle = providerCall(
        "refreshSession",
        CapabilityKey("auth.oauth_refresh"),
    ) {
        cloud.refreshSession().toHandle(SessionState.REFRESHED)
    }

    override suspend fun logout() {
        ensureOpen("logout")
        throw ProviderError.Unsupported("logout", CapabilityKey("auth.logout"))
    }

    override suspend fun getShadow(deviceId: String): ShadowSnapshot = providerCall(
        "getDeviceShadow",
        CapabilityKey("shadow.get"),
    ) {
        cloud.getDeviceShadow(deviceId).toSnapshot()
    }

    override suspend fun getShadows(deviceIds: List<String>): List<ShadowSnapshot> = providerCall(
        "getDeviceShadows",
        CapabilityKey("shadow.multi"),
    ) {
        cloud.getDeviceShadows(deviceIds).map { it.toSnapshot() }
    }

    override suspend fun subscribeShadow(deviceIds: List<String>): ShadowSubscriptionAck = providerCall(
        "subscribeDeviceShadows",
        CapabilityKey("shadow.subscribe"),
    ) {
        cloud.subscribeDeviceShadows(deviceIds).toSubscriptionAck()
    }

    override suspend fun unsubscribeShadow(
        deviceIds: List<String>,
        reason: ShadowUnsubscribeReason,
    ): ShadowSubscriptionAck = providerCall(
        "unsubscribeDeviceShadows",
        CapabilityKey("shadow.unsubscribe"),
    ) {
        cloud.unsubscribeDeviceShadows(deviceIds, reason).toSubscriptionAck()
    }

    override suspend fun listDevices(): List<DeviceSnapshot> {
        ensureOpen("listDevices")
        throw ProviderError.Unsupported("listDevices", CapabilityKey("devices.list"))
    }

    override suspend fun discoverRegionalServices() {
        ensureOpen("discoverRegionalServices")
        throw ProviderError.Unsupported("discoverRegionalServices", CapabilityKey("discovery.regional_services"))
    }

    override fun parseAlarm(payload: Map<String, String>): EventEnvelope {
        ensureOpen("parseAlarmEvent")
        return try {
            cloud.parseAlarmEvent(payload).toEnvelope()
        } catch (error: Throwable) {
            throw error.toProviderError("parseAlarmEvent", CapabilityKey("events.alarm_parser"))
        }
    }

    override fun observeEvents(): Flow<EventStreamItem> = flow {
        ensureOpen("observeEvents")
        try {
            cloud.observeEvents().collect {
                throw ProviderError.Unsupported("observeEvents", CapabilityKey("events.realtime"))
            }
            throw ProviderError.Unsupported("observeEvents", CapabilityKey("events.realtime"))
        } catch (error: Throwable) {
            throw error.toProviderError("observeEvents", CapabilityKey("events.realtime"))
        }
    }

    override suspend fun execute(command: ActionCommand): ActionOutcome {
        ensureOpen("performAction")
        val capability = when (command.type) {
            dev.nacho.wibox.core.cloud.ActionType.UNLOCK -> "actions.unlock"
            dev.nacho.wibox.core.cloud.ActionType.F1 -> "actions.f1"
            dev.nacho.wibox.core.cloud.ActionType.CALL_GUARD -> "actions.call_guard"
            dev.nacho.wibox.core.cloud.ActionType.CALL_ACCEPT -> "actions.call_accept"
            dev.nacho.wibox.core.cloud.ActionType.CALL_REJECT -> "actions.call_reject"
            dev.nacho.wibox.core.cloud.ActionType.START_CALL, dev.nacho.wibox.core.cloud.ActionType.STOP_CALL -> "calls.lifecycle"
        }
        throw ProviderError.Unsupported("performAction", CapabilityKey(capability))
    }

    override suspend fun checkAppUpgrade(request: AppUpgradeRequest): AppUpgradeInfo = providerCall(
        "checkAppUpgrade",
        CapabilityKey("app.update_check"),
    ) {
        cloud.checkAppUpgrade(request)
    }

    private fun CloudSession.toHandle(state: SessionState): SessionHandle = SessionHandle(
        provider = ProviderId.STOCK_QVCLOUD,
        installationId = sessionContext.installationId,
        accountAlias = sessionContext.accountAlias,
        environment = sessionContext.environment,
        state = state,
        opaqueId = opaqueId,
        validUntilMillis = accessTokenExpiresAtMillis,
    )

    private fun dev.nacho.wibox.core.cloud.DeviceShadow.toSnapshot(): ShadowSnapshot = ShadowSnapshot(
        device = DeviceRef(ProviderId.STOCK_QVCLOUD, device.id),
        requestedFields = setOf("Device_state"),
        returnedFields = if (state == null) emptySet() else setOf("Device_state"),
        state = state,
        path = path,
        timestampMillis = timestampMillis,
        fresh = true,
        unknownFields = unknownFields,
        responseUnknownFields = responseUnknownFields,
    )

    private fun ShadowAcknowledgement.toSubscriptionAck(): ShadowSubscriptionAck = ShadowSubscriptionAck(
        data = data,
        path = path,
        timestampMillis = timestampMillis,
        unknownFields = unknownFields,
    )

    private fun CloudEvent.toEnvelope(): EventEnvelope = EventEnvelope(
        eventId = alarmId,
        provider = ProviderId.STOCK_QVCLOUD,
        type = when (type) {
            CloudEventType.MOTION -> ProviderEventType.MOTION
            CloudEventType.INCOMING_CALL -> ProviderEventType.INCOMING_CALL
            CloudEventType.CALL_ANSWERED -> ProviderEventType.CALL_ANSWERED
        },
        device = deviceId?.let { DeviceRef(ProviderId.STOCK_QVCLOUD, it) },
        channel = channel,
        cameraName = cameraName,
        unreadCount = unreadCount,
        occurredAtRaw = alarmTime,
        receivedAtMillis = null,
        attachments = emptyList(),
        unknownFields = unknownFields,
        cursor = null,
        source = EventSource.FCM,
        duplicate = false,
    )

    private suspend fun <T> providerCall(
        operation: String,
        capability: CapabilityKey,
        block: suspend () -> T,
    ): T {
        ensureOpen(operation)
        return try {
            block()
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            throw error.toProviderError(operation, capability)
        }
    }

    private fun ensureOpen(operation: String) {
        if (closed.get()) throw ProviderError.Closed(operation)
    }

    override fun close() {
        if (closed.compareAndSet(false, true) && closeClientWhenClosed) {
            cloud.close()
        }
    }
}
