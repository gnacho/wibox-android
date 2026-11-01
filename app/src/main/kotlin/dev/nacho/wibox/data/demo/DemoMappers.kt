package dev.nacho.wibox.data.demo

import dev.nacho.wibox.core.cloud.ActionAcknowledgementLevel
import dev.nacho.wibox.core.cloud.CapabilityEvidence
import dev.nacho.wibox.core.cloud.CapabilitySnapshot
import dev.nacho.wibox.core.cloud.ChannelRef
import dev.nacho.wibox.core.cloud.DeviceDataSource
import dev.nacho.wibox.core.cloud.DevicePermission
import dev.nacho.wibox.core.cloud.DevicePresence
import dev.nacho.wibox.core.cloud.DeviceRef
import dev.nacho.wibox.core.cloud.DeviceRole
import dev.nacho.wibox.core.cloud.DeviceSnapshot
import dev.nacho.wibox.core.cloud.DeviceState
import dev.nacho.wibox.core.cloud.EventAttachmentRef
import dev.nacho.wibox.core.cloud.EventEnvelope
import dev.nacho.wibox.core.cloud.EventSource
import dev.nacho.wibox.core.cloud.EvidenceOrigin
import dev.nacho.wibox.core.cloud.ProviderCapabilityCatalog
import dev.nacho.wibox.core.cloud.ProviderEventType
import dev.nacho.wibox.core.cloud.ProviderId
import dev.nacho.wibox.data.AppPreferences
import dev.nacho.wibox.data.AppScenario
import dev.nacho.wibox.data.InvitationRecord
import dev.nacho.wibox.data.MediaKind
import dev.nacho.wibox.data.MediaRecord
import dev.nacho.wibox.data.RepositorySnapshot

internal fun DemoDevice.toProviderDevice(scenario: DemoScenario): DeviceSnapshot {
    val permissions = buildSet {
        if (this@toProviderDevice.permissions.preview) add(DevicePermission.PREVIEW)
        if (this@toProviderDevice.permissions.playback) add(DevicePermission.PLAYBACK)
        if (this@toProviderDevice.permissions.alarms) add(DevicePermission.ALARM)
        if (this@toProviderDevice.permissions.unlock) add(DevicePermission.UNLOCK)
        if (this@toProviderDevice.permissions.f1) add(DevicePermission.F1)
        add(DevicePermission.CALL_GUARD)
    }
    val reference = DeviceRef(ProviderId.DEMO, id)
    return DeviceSnapshot(
        device = reference,
        displayName = name,
        declaredModel = model,
        role = if (shared) DeviceRole.SHARED else DeviceRole.OWNER,
        permissions = permissions,
        channels = listOf(ChannelRef(reference, 1)),
        presence = if (online) DevicePresence.ONLINE else DevicePresence.OFFLINE,
        lastObservedAtMillis = DemoFixtures.BASE_TIME,
        firmwareVersion = firmwareCurrent,
        metadata = mapOf(
            "firmwareLatest" to firmwareLatest,
            "callAlerts" to callAlerts.toString(),
            "motionAlerts" to motionAlerts.toString(),
            "f1Seconds" to f1Seconds.toString(),
            "videoMode" to videoMode.name,
            "talkMode" to talkMode.name,
        ),
        source = DeviceDataSource.LOCAL,
        fresh = scenario != DemoScenario.OFFLINE,
    )
}

internal fun DemoEvent.toProviderEvent(): EventEnvelope = EventEnvelope(
    eventId = id,
    provider = ProviderId.DEMO,
    type = when (type) {
        DemoEventType.MOTION -> ProviderEventType.MOTION
        DemoEventType.INCOMING_CALL -> ProviderEventType.INCOMING_CALL
        DemoEventType.CALL_ANSWERED -> ProviderEventType.CALL_ANSWERED
    },
    device = DeviceRef(ProviderId.DEMO, deviceId),
    channel = channel,
    cameraName = null,
    unreadCount = 0,
    occurredAtRaw = timestampMillis.toString(),
    receivedAtMillis = timestampMillis,
    attachments = mediaId?.let { listOf(EventAttachmentRef(it)) }.orEmpty(),
    unknownFields = emptyMap(),
    cursor = null,
    source = EventSource.LOCAL,
    duplicate = false,
)

internal fun DemoMedia.toMediaRecord(): MediaRecord = MediaRecord(
    id = id,
    deviceId = deviceId,
    kind = if (type == DemoMediaType.VIDEO) MediaKind.VIDEO else MediaKind.PHOTO,
    timestampMillis = timestampMillis,
    durationSeconds = durationSeconds,
    synthetic = true,
)

internal fun DemoInvitation.toInvitationRecord(): InvitationRecord = InvitationRecord(
    id = id,
    deviceId = deviceId,
    displayName = name,
    code = code,
    createdAtMillis = createdAtMillis,
    expiresAtMillis = expiresAtMillis,
    permissions = permissions.toDevicePermissions(),
)

internal fun DemoPermissions.toDevicePermissions(): Set<DevicePermission> = buildSet {
    if (preview) add(DevicePermission.PREVIEW)
    if (playback) add(DevicePermission.PLAYBACK)
    if (alarms) add(DevicePermission.ALARM)
    if (unlock) add(DevicePermission.UNLOCK)
    if (f1) add(DevicePermission.F1)
    if (callGuard) add(DevicePermission.CALL_GUARD)
}

internal fun Set<DevicePermission>.toDemoPermissions(): DemoPermissions = DemoPermissions(
    preview = DevicePermission.PREVIEW in this,
    playback = DevicePermission.PLAYBACK in this,
    alarms = DevicePermission.ALARM in this,
    unlock = DevicePermission.UNLOCK in this,
    f1 = DevicePermission.F1 in this,
    callGuard = DevicePermission.CALL_GUARD in this,
)

internal fun DemoSettings.toAppPreferences(): AppPreferences = AppPreferences(
    sound = sound,
    vibration = vibration,
    quickUnlock = quickUnlock,
    biometricUnlock = biometricUnlock,
    termsAccepted = termsAccepted,
    androidHintDismissed = androidHintDismissed,
    scenario = when (scenario) {
        DemoScenario.NORMAL -> AppScenario.NORMAL
        DemoScenario.LOADING -> AppScenario.LOADING
        DemoScenario.EMPTY -> AppScenario.EMPTY
        DemoScenario.ERROR -> AppScenario.ERROR
        DemoScenario.UNSUPPORTED -> AppScenario.UNSUPPORTED
        DemoScenario.OFFLINE -> AppScenario.OFFLINE
        DemoScenario.PERMISSION -> AppScenario.PERMISSION
        DemoScenario.ACTION_PENDING -> AppScenario.ACTION_PENDING
        DemoScenario.ACTION_FAILURE -> AppScenario.ACTION_FAILURE
    },
)

internal fun DemoSnapshot.toRepositorySnapshot(): RepositorySnapshot = RepositorySnapshot(
    devices = devices.map { it.toProviderDevice(settings.scenario) },
    events = events.map { it.toProviderEvent() },
    media = media.map { it.toMediaRecord() },
    invitations = invitations.map { it.toInvitationRecord() },
    preferences = settings.toAppPreferences(),
)

internal fun demoCapabilities(): List<CapabilitySnapshot> = ProviderCapabilityCatalog.stockQvCloud.map { capability ->
    val isUpgrade = capability.key.id == "app.update_check"
    capability.copy(
        declaredOrigin = EvidenceOrigin.DECLARATION_LOCAL,
        declaredEvidence = if (isUpgrade) CapabilityEvidence.UNSUPPORTED else CapabilityEvidence.LOCAL_ONLY,
        operation = null,
        operationEvidence = if (isUpgrade) CapabilityEvidence.UNSUPPORTED else CapabilityEvidence.LOCAL_ONLY,
        operationOrigin = EvidenceOrigin.DECLARATION_LOCAL,
        limitations = capability.limitations + "local_demo_only_no_stock_evidence",
    )
}

internal fun localShadowState(device: DemoDevice): DeviceState? = if (device.online) {
    DeviceState(
        f1State = 0,
        switchState = null,
        privateModeState = 0,
        fisheyeChannelState = null,
        locks = null,
        alarmZoneMode = 0,
        smartRelays = null,
    )
} else {
    null
}

internal fun localAcknowledgementLevel(): ActionAcknowledgementLevel = ActionAcknowledgementLevel.LOCAL_ACCEPTED

internal fun AppPreferences.toDemoSettings(): DemoSettings = DemoSettings(
    sound = sound,
    vibration = vibration,
    quickUnlock = quickUnlock,
    biometricUnlock = biometricUnlock,
    termsAccepted = termsAccepted,
    androidHintDismissed = androidHintDismissed,
    scenario = when (scenario) {
        AppScenario.NORMAL -> DemoScenario.NORMAL
        AppScenario.LOADING -> DemoScenario.LOADING
        AppScenario.EMPTY -> DemoScenario.EMPTY
        AppScenario.ERROR -> DemoScenario.ERROR
        AppScenario.UNSUPPORTED -> DemoScenario.UNSUPPORTED
        AppScenario.OFFLINE -> DemoScenario.OFFLINE
        AppScenario.PERMISSION -> DemoScenario.PERMISSION
        AppScenario.ACTION_PENDING -> DemoScenario.ACTION_PENDING
        AppScenario.ACTION_FAILURE -> DemoScenario.ACTION_FAILURE
    },
)
