package dev.nacho.wibox.data.demo

import dev.nacho.wibox.core.cloud.ActionCommand
import dev.nacho.wibox.core.cloud.ActionOutcome
import dev.nacho.wibox.core.cloud.ActionPort
import dev.nacho.wibox.core.cloud.ActionType
import dev.nacho.wibox.core.cloud.AppUpgradeInfo
import dev.nacho.wibox.core.cloud.AppUpgradeRequest
import dev.nacho.wibox.core.cloud.CapabilityKey
import dev.nacho.wibox.core.cloud.CapabilitySnapshot
import dev.nacho.wibox.core.cloud.DevicePermission
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
import dev.nacho.wibox.core.cloud.ProviderConnectionKind
import dev.nacho.wibox.core.cloud.ProviderDescriptor
import dev.nacho.wibox.core.cloud.ProviderEnvironment
import dev.nacho.wibox.core.cloud.ProviderError
import dev.nacho.wibox.core.cloud.ProviderEventType
import dev.nacho.wibox.core.cloud.ProviderId
import dev.nacho.wibox.core.cloud.SessionHandle
import dev.nacho.wibox.core.cloud.SessionPort
import dev.nacho.wibox.core.cloud.SessionState
import dev.nacho.wibox.core.cloud.ShadowSnapshot
import dev.nacho.wibox.core.cloud.ShadowSubscriptionAck
import dev.nacho.wibox.core.cloud.ShadowUnsubscribeReason
import dev.nacho.wibox.core.cloud.WiboxProvider
import dev.nacho.wibox.data.AppOperations
import dev.nacho.wibox.data.AppPreferences
import dev.nacho.wibox.data.AppScenario
import dev.nacho.wibox.data.BindRequest
import dev.nacho.wibox.data.DiscoveredRecord
import dev.nacho.wibox.data.InvitationRecord
import dev.nacho.wibox.data.MediaKind
import dev.nacho.wibox.data.MediaRecord
import dev.nacho.wibox.data.ParsedInvitation
import dev.nacho.wibox.data.RepositorySnapshot
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import dev.nacho.wibox.data.VideoMode
import dev.nacho.wibox.data.TalkMode

class DemoWiboxProvider(
    initialSnapshot: DemoSnapshot = DemoFixtures.initial(),
    private val persist: suspend (DemoSnapshot) -> Unit = {},
) : WiboxProvider, SessionPort, DevicePort, EventPort, ActionPort, MaintenancePort, AppOperations {
    override val descriptor = ProviderDescriptor(
        id = ProviderId.DEMO,
        profileVersion = "local",
        connectionKind = ProviderConnectionKind.DEMO,
    )
    override val capabilities: List<CapabilitySnapshot> = demoCapabilities()
    override val session: SessionPort get() = this
    override val devices: DevicePort get() = this
    override val events: EventPort get() = this
    override val actions: ActionPort get() = this
    override val maintenance: MaintenancePort get() = this

    private val mutex = Mutex()
    private val snapshot = MutableStateFlow(initialSnapshot)
    private val repositorySnapshot = MutableStateFlow(initialSnapshot.toRepositorySnapshot())
    private val liveEvents = MutableSharedFlow<EventEnvelope>(extraBufferCapacity = 16)
    private val closed = AtomicBoolean(false)
    private val providerScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val localHandle = SessionHandle(
        provider = ProviderId.DEMO,
        installationId = "local-demo-installation",
        accountAlias = "local-demo",
        environment = ProviderEnvironment.TEST,
        state = SessionState.RESTORED,
        opaqueId = "local-demo-session",
        validUntilMillis = Long.MAX_VALUE,
    )

    override suspend fun prepare(): PreparationState {
        ensureOpen("prepare")
        return PreparationState.READY
    }

    override suspend fun authenticate(material: CredentialMaterial): SessionHandle {
        throw ProviderError.Unsupported("authenticate", CapabilityKey("auth.oauth_password"), "Local demo has no cloud login")
    }

    override suspend fun restoreSession(): SessionHandle {
        ensureOpen("restoreSession")
        return localHandle
    }

    override suspend fun refreshSession(): SessionHandle {
        throw ProviderError.Unsupported("refreshSession", CapabilityKey("auth.oauth_refresh"), "Local demo has no OAuth tokens")
    }

    override suspend fun logout() {
        throw ProviderError.Unsupported("logout", CapabilityKey("auth.logout"), "Local demo has no remote session to revoke")
    }

    override suspend fun getShadow(deviceId: String): ShadowSnapshot {
        checkOperation("getShadow")
        val device = snapshot.value.devices.firstOrNull { it.id == deviceId }
            ?: throw ProviderError.NotFound("getShadow")
        return ShadowSnapshot(
            device = DeviceRef(ProviderId.DEMO, device.id),
            requestedFields = setOf("Device_state"),
            returnedFields = if (device.online) setOf("Device_state") else emptySet(),
            state = localShadowState(device),
            path = "/local/demo/status",
            timestampMillis = DemoFixtures.BASE_TIME,
            fresh = true,
            unknownFields = emptyMap(),
            responseUnknownFields = emptyMap(),
        )
    }

    override suspend fun getShadows(deviceIds: List<String>): List<ShadowSnapshot> = deviceIds.map { getShadow(it) }

    override suspend fun subscribeShadow(deviceIds: List<String>): ShadowSubscriptionAck {
        checkOperation("subscribeShadow")
        if (deviceIds.isEmpty()) throw ProviderError.InvalidInput("subscribeShadow")
        return ShadowSubscriptionAck("local-demo-only", "/local/demo/sub", DemoFixtures.BASE_TIME, emptyMap())
    }

    override suspend fun unsubscribeShadow(
        deviceIds: List<String>,
        reason: ShadowUnsubscribeReason,
    ): ShadowSubscriptionAck {
        checkOperation("unsubscribeShadow")
        if (deviceIds.isEmpty()) throw ProviderError.InvalidInput("unsubscribeShadow")
        return ShadowSubscriptionAck("local-demo-only", "/local/demo/unsub", DemoFixtures.BASE_TIME, emptyMap())
    }

    override suspend fun listDevices(): List<DeviceSnapshot> {
        checkOperation("listDevices")
        delay(120)
        return when (snapshot.value.settings.scenario) {
            DemoScenario.EMPTY -> emptyList()
            else -> snapshot.value.devices.map { it.toProviderDevice(snapshot.value.settings.scenario) }
        }
    }

    override suspend fun discoverRegionalServices() {
        throw ProviderError.Unsupported("discoverRegionalServices", CapabilityKey("discovery.regional_services"))
    }

    override fun parseAlarm(payload: Map<String, String>): EventEnvelope {
        ensureOpen("parseAlarm")
        val type = when (payload["AlarmEvent"]?.toIntOrNull()) {
            2 -> ProviderEventType.MOTION
            19 -> ProviderEventType.INCOMING_CALL
            20 -> ProviderEventType.CALL_ANSWERED
            null -> throw ProviderError.Parse("parseAlarm")
            else -> throw ProviderError.Unsupported("parseAlarm", CapabilityKey("events.alarm_parser"), "Unknown local alarm type")
        }
        val unread = payload["NotReadCount"]?.toIntOrNull() ?: throw ProviderError.Parse("parseAlarm")
        val known = setOf("AlarmEvent", "AlarmId", "DevUmid", "SensorChNo", "CameraName", "NotReadCount", "AlarmTime")
        return EventEnvelope(
            eventId = payload["AlarmId"],
            provider = ProviderId.DEMO,
            type = type,
            device = payload["DevUmid"]?.let { DeviceRef(ProviderId.DEMO, it) },
            channel = payload["SensorChNo"]?.toIntOrNull() ?: -1,
            cameraName = payload["CameraName"],
            unreadCount = unread,
            occurredAtRaw = payload["AlarmTime"],
            receivedAtMillis = DemoFixtures.BASE_TIME,
            attachments = emptyList(),
            unknownFields = payload.filterKeys { it !in known },
            cursor = null,
            source = EventSource.LOCAL,
            duplicate = false,
        )
    }

    override fun observeEvents(): Flow<EventStreamItem> = flow {
        ensureOpen("observeEvents")
        emit(EventStreamItem.Connected(ProviderId.DEMO))
        liveEvents.collect { emit(EventStreamItem.Envelope(it)) }
    }

    override suspend fun execute(command: ActionCommand): ActionOutcome {
        checkOperation("performAction")
        val device = snapshot.value.devices.firstOrNull { it.id == command.device.id }
            ?: throw ProviderError.NotFound("performAction")
        if (!device.online) throw ProviderError.Network("performAction")
        if (snapshot.value.settings.scenario == DemoScenario.UNSUPPORTED) {
            throw ProviderError.Unsupported("performAction", CapabilityKey("actions.unlock"), "Disabled by local demo scenario")
        }
        val permitted = when (command.type) {
            ActionType.UNLOCK -> device.permissions.unlock
            ActionType.F1 -> device.permissions.f1
            else -> true
        }
        if (!permitted) throw ProviderError.Authorization("performAction")
        when (snapshot.value.settings.scenario) {
            DemoScenario.ACTION_PENDING -> delay(1_500)
            DemoScenario.ACTION_FAILURE -> return ActionOutcome.rejected(command.commandId)
            else -> delay(180)
        }
        return ActionOutcome.localAccepted(command.commandId)
    }

    override suspend fun checkAppUpgrade(request: AppUpgradeRequest): AppUpgradeInfo {
        throw ProviderError.Unsupported(
            "checkAppUpgrade",
            CapabilityKey("app.update_check"),
            "No real app upgrade configuration exists in local demo",
        )
    }

    override val snapshots: StateFlow<RepositorySnapshot> = repositorySnapshot.asStateFlow()

    override suspend fun replaceSnapshot(snapshot: RepositorySnapshot) {
        throw ProviderError.Unsupported("replaceSnapshot", CapabilityKey("security.persistence"), "Use initializeDemo for local restore")
    }

    override suspend fun mergeInvitations(invitations: List<InvitationRecord>) = mutate {
        updateLocked(snapshot.value.copy(invitations = invitations.map { it.toDemoInvitation() }))
    }

    suspend fun initializeDemo(snapshot: DemoSnapshot) {
        ensureOpen("initializeDemo")
        mutex.withLock { updateLocked(snapshot) }
    }

    override suspend fun refreshDevices() {
        checkOperation("refreshDevices")
        delay(120)
    }

    override suspend fun discoverCandidates(): List<DiscoveredRecord> {
        checkOperation("discoverCandidates")
        delay(450)
        if (snapshot.value.settings.scenario == DemoScenario.EMPTY) return emptyList()
        val existing = snapshot.value.devices.mapTo(mutableSetOf()) { it.id }
        return listOf(
            DiscoveredRecord("DEMO7410100", "7410"),
            DiscoveredRecord("DEMO9440200", "9440"),
        ).filterNot { it.id in existing }
    }

    override suspend fun bind(request: BindRequest): DeviceSnapshot = mutate {
        validateNewDevice(request.uid, request.displayName)
        val device = DemoDevice(
            id = request.uid,
            name = request.displayName.trim(),
            model = request.declaredModel,
            online = true,
            shared = request.shared,
        )
        updateLocked(snapshot.value.copy(devices = snapshot.value.devices + device))
        device.toProviderDevice(snapshot.value.settings.scenario)
    }

    override suspend fun acceptInvitation(invitation: ParsedInvitation, displayName: String): DeviceSnapshot = mutate {
        validateNewDevice(invitation.deviceId, displayName)
        val device = DemoDevice(
            id = invitation.deviceId,
            name = displayName.trim(),
            model = "shared",
            online = true,
            shared = true,
        )
        updateLocked(snapshot.value.copy(devices = snapshot.value.devices + device))
        device.toProviderDevice(snapshot.value.settings.scenario)
    }

    /**
     * Sustituye la lista de dispositivos por los dispositivos reales del cloud Fermax
     * (get-device-list tras aceptar una invitacion). Limpia eventos/media/invitaciones
     * de dispositivos que desaparecen. No-op si la lista viene vacia (idempotente).
     */
    suspend fun mergeCloudDevices(cloudDevices: List<DemoDevice>) {
        if (cloudDevices.isEmpty()) return
        mutate {
            val ids = cloudDevices.map { it.id }.toSet()
            val previous = snapshot.value.devices.associateBy { it.id }
            updateLocked(
                snapshot.value.copy(
                    devices = cloudDevices.map { device ->
                        device.copy(unlockPassword = previous[device.id]?.unlockPassword ?: device.unlockPassword)
                    },
                    events = snapshot.value.events.filter { it.deviceId in ids },
                    media = snapshot.value.media.filter { it.deviceId in ids },
                    invitations = snapshot.value.invitations.filter { it.deviceId in ids },
                ),
            )
        }
    }

    /** Guarda las credenciales de la cuenta nologin del cloud (persisten con el snapshot). */
    suspend fun saveCloudCredentials(account: String, password: String) = mutate {
        updateLocked(
            snapshot.value.copy(
                settings = snapshot.value.settings.copy(
                    cloudAccount = account,
                    cloudPassword = password,
                ),
            ),
        )
    }

    /** Credenciales cloud guardadas (null si todavia no hay accept completado). */
    fun cloudCredentials(): Pair<String, String>? {
        val settings = snapshot.value.settings
        val account = settings.cloudAccount ?: return null
        val password = settings.cloudPassword ?: return null
        return account to password
    }

    fun unlockPassword(deviceId: String): String? =
        snapshot.value.devices.firstOrNull { it.id == deviceId }?.unlockPassword

    override suspend fun renameDevice(deviceId: String, displayName: String): DeviceSnapshot = mutate {
        if (displayName.isBlank()) throw ProviderError.InvalidInput("renameDevice")
        if (snapshot.value.devices.any { it.id != deviceId && it.name.equals(displayName.trim(), true) }) {
            throw ProviderError.Conflict("renameDevice")
        }
        val current = snapshot.value.devices.firstOrNull { it.id == deviceId }
            ?: throw ProviderError.NotFound("renameDevice")
        val updated = current.copy(name = displayName.trim())
        updateLocked(snapshot.value.copy(devices = snapshot.value.devices.map { if (it.id == deviceId) updated else it }))
        updated.toProviderDevice(snapshot.value.settings.scenario)
    }

    override suspend fun updateDevice(
        deviceId: String,
        transform: (DeviceSnapshot) -> DeviceSnapshot,
    ): DeviceSnapshot = mutate {
        val current = snapshot.value.devices.firstOrNull { it.id == deviceId }
            ?: throw ProviderError.NotFound("updateDevice")
        val updated = transform(current.toProviderDevice(snapshot.value.settings.scenario)).toDemoDevice(current)
        updateLocked(snapshot.value.copy(devices = snapshot.value.devices.map { if (it.id == deviceId) updated else it }))
        updated.toProviderDevice(snapshot.value.settings.scenario)
    }

    override suspend fun deleteDevice(deviceId: String) = mutate {
        updateLocked(
            snapshot.value.copy(
                devices = snapshot.value.devices.filterNot { it.id == deviceId },
                events = snapshot.value.events.filterNot { it.deviceId == deviceId },
                media = snapshot.value.media.filterNot { it.deviceId == deviceId },
                invitations = snapshot.value.invitations.filterNot { it.deviceId == deviceId },
            ),
        )
    }

    override suspend fun updatePreferences(transform: (AppPreferences) -> AppPreferences) = mutate {
        val updated = transform(snapshot.value.settings.toAppPreferences()).toDemoSettings()
        updateLocked(snapshot.value.copy(settings = updated))
    }

    override suspend fun setScenario(scenario: AppScenario) {
        updatePreferences { it.copy(scenario = scenario) }
    }

    override suspend fun captureMedia(deviceId: String, kind: MediaKind): MediaRecord = mutate {
        val next = snapshot.value.media.size + 1
        val media = DemoMedia(
            id = "local-media-${System.currentTimeMillis()}-$next",
            deviceId = deviceId,
            type = if (kind == MediaKind.VIDEO) DemoMediaType.VIDEO else DemoMediaType.PHOTO,
            timestampMillis = DemoFixtures.BASE_TIME + next * 1_000L,
            durationSeconds = if (kind == MediaKind.VIDEO) 5 else 0,
        )
        updateLocked(snapshot.value.copy(media = listOf(media) + snapshot.value.media))
        media.toMediaRecord()
    }

    override suspend fun deleteEvents(ids: Set<String>) = mutate {
        updateLocked(snapshot.value.copy(events = snapshot.value.events.filterNot { it.id in ids }))
    }

    override suspend fun deleteMedia(ids: Set<String>) = mutate {
        updateLocked(
            snapshot.value.copy(
                media = snapshot.value.media.filterNot { it.id in ids },
                events = snapshot.value.events.map { event ->
                    if (event.mediaId in ids) event.copy(mediaId = null) else event
                },
            ),
        )
    }

    override suspend fun createInvitation(
        deviceId: String,
        displayName: String,
        permissions: Set<DevicePermission>,
    ): InvitationRecord = mutate {
        val device = snapshot.value.devices.firstOrNull { it.id == deviceId }
            ?: throw ProviderError.NotFound("createInvitation")
        if (snapshot.value.invitations.count { it.deviceId == deviceId } >= 100) {
            throw ProviderError.Unsupported("createInvitation", CapabilityKey("invitations.manage"), "Local invitation limit reached")
        }
        val index = snapshot.value.invitations.mapNotNull { it.id.substringAfter("invitation-").toIntOrNull() }.maxOrNull()?.plus(1) ?: 1
        val invitation = DemoInvitation(
            id = "invitation-$index",
            deviceId = deviceId,
            name = displayName.trim().ifEmpty { "Guest $index" },
            code = "LOCAL-$index-${device.id}",
            createdAtMillis = DemoFixtures.BASE_TIME,
            expiresAtMillis = DemoFixtures.BASE_TIME + 7 * 86_400_000L,
            permissions = permissions.toDemoPermissions(),
        )
        updateLocked(snapshot.value.copy(invitations = snapshot.value.invitations + invitation))
        invitation.toInvitationRecord()
    }

    override suspend fun updateInvitation(
        id: String,
        displayName: String,
        permissions: Set<DevicePermission>,
    ): InvitationRecord = mutate {
        val current = snapshot.value.invitations.firstOrNull { it.id == id }
            ?: throw ProviderError.NotFound("updateInvitation")
        val updated = current.copy(
            name = displayName.trim().ifEmpty { current.name },
            permissions = permissions.toDemoPermissions(),
        )
        updateLocked(
            snapshot.value.copy(
                invitations = snapshot.value.invitations.map { if (it.id == id) updated else it },
            ),
        )
        updated.toInvitationRecord()
    }

    override suspend fun deleteInvitation(id: String) = mutate {
        updateLocked(snapshot.value.copy(invitations = snapshot.value.invitations.filterNot { it.id == id }))
    }

    override suspend fun resetDemo() = mutate {
        updateLocked(DemoFixtures.initial())
    }

    override suspend fun emitIncomingCall(deviceId: String) {
        ensureOpen("emitIncomingCall")
        val device = snapshot.value.devices.firstOrNull { it.id == deviceId } ?: throw ProviderError.NotFound("emitIncomingCall")
        liveEvents.emit(
            EventEnvelope(
                eventId = "local-call-${System.currentTimeMillis()}",
                provider = ProviderId.DEMO,
                type = ProviderEventType.INCOMING_CALL,
                device = DeviceRef(ProviderId.DEMO, deviceId),
                channel = 1,
                cameraName = device.name,
                unreadCount = 1,
                occurredAtRaw = "local-demo",
                receivedAtMillis = System.currentTimeMillis(),
                attachments = emptyList(),
                unknownFields = emptyMap(),
                cursor = null,
                source = EventSource.LOCAL,
                duplicate = false,
            ),
        )
    }

    override fun close() {
        if (closed.compareAndSet(false, true)) {
            providerScope.cancel()
        }
    }

    private suspend fun <T> mutate(block: suspend () -> T): T {
        ensureOpen("mutate")
        return mutex.withLock {
            val result = block()
            persist(snapshot.value)
            result
        }
    }

    private suspend fun updateLocked(value: DemoSnapshot) {
        snapshot.value = value
        repositorySnapshot.value = value.toRepositorySnapshot()
    }

    private fun validateNewDevice(uid: String, name: String) {
        if (uid.isBlank() || name.isBlank()) throw ProviderError.InvalidInput("bind")
        if (snapshot.value.devices.any { it.id.equals(uid, true) || it.name.equals(name.trim(), true) }) {
            throw ProviderError.Conflict("bind")
        }
    }

    private fun ensureOpen(operation: String) {
        if (closed.get()) throw ProviderError.Closed(operation)
    }

    private fun checkOperation(operation: String) {
        ensureOpen(operation)
        if (snapshot.value.settings.scenario == DemoScenario.ERROR) {
            throw ProviderError.Network(operation)
        }
    }
}

private fun DeviceSnapshot.toDemoDevice(original: DemoDevice): DemoDevice = original.copy(
    name = displayName ?: original.name,
    model = declaredModel ?: original.model,
    shared = role == dev.nacho.wibox.core.cloud.DeviceRole.SHARED,
    online = presence == dev.nacho.wibox.core.cloud.DevicePresence.ONLINE,
    firmwareCurrent = firmwareVersion ?: original.firmwareCurrent,
    firmwareLatest = metadata["firmwareLatest"] ?: original.firmwareLatest,
    callAlerts = metadata["callAlerts"]?.toBoolean() ?: original.callAlerts,
    motionAlerts = metadata["motionAlerts"]?.toBoolean() ?: original.motionAlerts,
    f1Seconds = metadata["f1Seconds"]?.toIntOrNull() ?: original.f1Seconds,
    videoMode = metadata["videoMode"]?.let { value -> VideoMode.entries.firstOrNull { it.name == value } } ?: original.videoMode,
    talkMode = metadata["talkMode"]?.let { value -> TalkMode.entries.firstOrNull { it.name == value } } ?: original.talkMode,
    unlockPassword = metadata["unlockPassword"] ?: original.unlockPassword,
)
