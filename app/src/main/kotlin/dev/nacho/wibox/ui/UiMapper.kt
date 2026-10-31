package dev.nacho.wibox.ui

import dev.nacho.wibox.core.cloud.DeviceSnapshot
import dev.nacho.wibox.core.cloud.EventEnvelope
import dev.nacho.wibox.core.cloud.ProviderConnectionKind
import dev.nacho.wibox.core.cloud.ProviderDescriptor
import dev.nacho.wibox.data.InvitationRecord
import dev.nacho.wibox.data.MediaRecord
import dev.nacho.wibox.data.toLocalInvitationUrl

object UiMapper {
    fun device(snapshot: DeviceSnapshot): DeviceUi = DeviceUi(
        id = snapshot.device.id,
        name = snapshot.displayName ?: snapshot.device.id,
        model = snapshot.declaredModel ?: "unknown",
        role = snapshot.role,
        presence = snapshot.presence,
        permissions = snapshot.permissions,
        lastObservedAtMillis = snapshot.lastObservedAtMillis,
        fresh = snapshot.fresh,
        firmwareCurrent = snapshot.firmwareVersion ?: snapshot.metadata["firmwareCurrent"].orEmpty(),
        firmwareLatest = snapshot.metadata["firmwareLatest"] ?: snapshot.firmwareVersion.orEmpty(),
        callAlerts = snapshot.metadata["callAlerts"]?.toBoolean() ?: true,
        motionAlerts = snapshot.metadata["motionAlerts"]?.toBoolean() ?: true,
        f1Seconds = snapshot.metadata["f1Seconds"]?.toIntOrNull() ?: 3,
        highQuality = snapshot.metadata["highQuality"]?.toBoolean() ?: true,
        fullDuplex = snapshot.metadata["fullDuplex"]?.toBoolean() ?: true,
    )

    fun event(envelope: EventEnvelope): EventUi = EventUi(
        id = envelope.eventId.orEmpty(),
        deviceId = envelope.device?.id.orEmpty(),
        type = envelope.type,
        channel = envelope.channel,
        timestampMillis = envelope.receivedAtMillis ?: envelope.occurredAtRaw?.toLongOrNull() ?: 0,
        mediaId = envelope.attachments.firstOrNull()?.id,
    )

    fun media(record: MediaRecord): MediaUi = MediaUi(
        id = record.id,
        deviceId = record.deviceId,
        kind = record.kind,
        timestampMillis = record.timestampMillis,
        durationSeconds = record.durationSeconds,
        synthetic = record.synthetic,
    )

    fun invitation(record: InvitationRecord): InvitationUi = InvitationUi(
        id = record.id,
        deviceId = record.deviceId,
        displayName = record.displayName,
        code = record.code,
        createdAtMillis = record.createdAtMillis,
        expiresAtMillis = record.expiresAtMillis,
        link = record.toLocalInvitationUrl(),
    )

    fun providerStatus(descriptor: ProviderDescriptor): ProviderStatusUi = ProviderStatusUi(
        label = if (descriptor.connectionKind == ProviderConnectionKind.DEMO) "LOCAL DEMO" else "STOCK QVCLOUD",
        profileVersion = descriptor.profileVersion,
        simulated = descriptor.connectionKind == ProviderConnectionKind.DEMO,
    )
}
