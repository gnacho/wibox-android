package dev.nacho.wibox.externalapi

import dev.nacho.wibox.core.cloud.ActionAcknowledgementLevel
import dev.nacho.wibox.core.cloud.ActionOutcome as CoreActionOutcome
import dev.nacho.wibox.core.cloud.CapabilityEvidence
import dev.nacho.wibox.core.cloud.CapabilityScope as CoreCapabilityScope
import dev.nacho.wibox.core.cloud.CapabilitySnapshot as CoreCapability
import dev.nacho.wibox.core.cloud.CapabilitySupport as CoreCapabilitySupport
import dev.nacho.wibox.core.cloud.DeviceDataSource
import dev.nacho.wibox.core.cloud.DevicePresence
import dev.nacho.wibox.core.cloud.DeviceRole
import dev.nacho.wibox.core.cloud.DeviceSnapshot as CoreDeviceSnapshot
import dev.nacho.wibox.core.cloud.PreparationState
import dev.nacho.wibox.core.cloud.ProviderConnectionKind
import dev.nacho.wibox.core.cloud.ProviderDescriptor
import dev.nacho.wibox.core.cloud.ProviderError
import dev.nacho.wibox.core.cloud.ProviderRetryPolicy
import dev.nacho.wibox.core.cloud.ShadowSnapshot as CoreShadowSnapshot
import dev.nacho.wibox.externalapi.aidl.ActionOutcome as WireActionOutcome
import dev.nacho.wibox.externalapi.aidl.ApiError as WireApiError
import dev.nacho.wibox.externalapi.aidl.CapabilitySnapshot as WireCapability
import dev.nacho.wibox.externalapi.aidl.DeviceRef as WireDeviceRef
import dev.nacho.wibox.externalapi.aidl.DeviceSnapshot as WireDeviceSnapshot
import dev.nacho.wibox.externalapi.aidl.ProviderExtension as WireProviderExtension
import dev.nacho.wibox.externalapi.aidl.ProviderSnapshot as WireProviderSnapshot
import dev.nacho.wibox.externalapi.aidl.SessionSummary as WireSessionSummary
import dev.nacho.wibox.externalapi.aidl.ShadowSnapshot as WireShadowSnapshot
import dev.nacho.wibox.externalapi.aidl.StockShadowFields as WireStockShadowFields
import dev.nacho.wibox.externalapi.aidl.UnknownFieldDescriptor as WireUnknownField
import dev.nacho.wibox.externalapi.aidl.WireAckLevel
import dev.nacho.wibox.externalapi.aidl.WireApiErrorCode
import dev.nacho.wibox.externalapi.aidl.WireConnectionKind
import dev.nacho.wibox.externalapi.aidl.WireDataSource
import dev.nacho.wibox.externalapi.aidl.WireDeviceRole
import dev.nacho.wibox.externalapi.aidl.WirePresence
import dev.nacho.wibox.externalapi.aidl.WirePreparationState
import dev.nacho.wibox.externalapi.aidl.WireProviderOrigin
import dev.nacho.wibox.externalapi.aidl.WireRetryPolicy
import dev.nacho.wibox.externalapi.aidl.WireSessionState
import dev.nacho.wibox.externalapi.aidl.WireCapabilityScope
import dev.nacho.wibox.externalapi.aidl.WireSupport
import dev.nacho.wibox.externalapi.aidl.WireAvailability
import dev.nacho.wibox.externalapi.aidl.WireUpgradeState
import dev.nacho.wibox.externalapi.aidl.UpgradeInfo as WireUpgradeInfo
import dev.nacho.wibox.core.cloud.AppUpgradeInfo

/**
 * Mappers from the app provider model (core.cloud) to the Wire AIDL
 * parcelables. Mappers never copy transport details, URLs, headers or
 * credentials into the safe message, and they preserve Unsupported as
 * Unsupported instead of collapsing it into another state.
 */
internal object ExternalApiMappers {
    private const val SAFE_MESSAGE_LIMIT = 256

    fun apiError(
        code: Int,
        retryPolicy: Int,
        safeMessage: String,
        capabilityKey: String? = null,
        retryAfterMs: Long? = null,
    ): WireApiError = WireApiError(
        code = code,
        retryPolicy = retryPolicy,
        capabilityKey = capabilityKey?.take(128),
        outcome = null,
        safeMessage = safeMessage.take(SAFE_MESSAGE_LIMIT),
        retryAfterMs = retryAfterMs,
        providerCode = null,
    )

    fun providerError(error: ProviderError): WireApiError {
        val (code, policy) = when (error) {
            is ProviderError.Unsupported -> WireApiErrorCode.UNSUPPORTED to WireRetryPolicy.DO_NOT_RETRY
            is ProviderError.NotFound -> WireApiErrorCode.NOT_FOUND to WireRetryPolicy.DO_NOT_RETRY
            is ProviderError.Conflict -> WireApiErrorCode.CONFLICT to WireRetryPolicy.MANUAL_RETRY
            is ProviderError.Authentication -> WireApiErrorCode.AUTHENTICATION to WireRetryPolicy.REAUTHENTICATE
            is ProviderError.Authorization -> WireApiErrorCode.PROVIDER_AUTHORIZATION to WireRetryPolicy.MANUAL_RETRY
            is ProviderError.PlatformPermission -> WireApiErrorCode.PROVIDER_AUTHORIZATION to WireRetryPolicy.MANUAL_RETRY
            is ProviderError.RateLimit -> WireApiErrorCode.RATE_LIMITED to WireRetryPolicy.SAFE_READ_RETRY
            is ProviderError.Network -> WireApiErrorCode.NETWORK to WireRetryPolicy.SAFE_READ_RETRY
            is ProviderError.Timeout -> WireApiErrorCode.TIMEOUT to WireRetryPolicy.SAFE_READ_RETRY
            is ProviderError.Parse -> WireApiErrorCode.PARSE to WireRetryPolicy.DO_NOT_RETRY
            is ProviderError.Persistence -> WireApiErrorCode.PERSISTENCE to WireRetryPolicy.MANUAL_RETRY
            is ProviderError.InvalidInput -> WireApiErrorCode.INVALID_INPUT to WireRetryPolicy.DO_NOT_RETRY
            is ProviderError.Http -> WireApiErrorCode.INTERNAL to WireRetryPolicy.MANUAL_RETRY
            is ProviderError.EventGap -> WireApiErrorCode.EVENT_GAP to WireRetryPolicy.RESYNC
            is ProviderError.UnknownOutcome ->
                WireApiErrorCode.OUTCOME_UNKNOWN to WireRetryPolicy.DO_NOT_RETRY
            is ProviderError.Closed -> WireApiErrorCode.PROVIDER_NOT_READY to WireRetryPolicy.DO_NOT_RETRY
            is ProviderError.Unknown -> WireApiErrorCode.INTERNAL to WireRetryPolicy.MANUAL_RETRY
        }
        return WireApiError(
            code = code,
            retryPolicy = policy,
            capabilityKey = error.capability?.id?.take(128),
            outcome = error.outcome?.let { ackLevel(it.level) },
            safeMessage = error.causeSafe?.take(SAFE_MESSAGE_LIMIT) ?: error.message.take(SAFE_MESSAGE_LIMIT),
            retryAfterMs = null,
            providerCode = error.providerCode?.toString(),
        )
    }

    fun providerSnapshot(
        descriptor: ProviderDescriptor,
        preparation: PreparationState,
        nowEpochMs: Long,
    ): WireProviderSnapshot = WireProviderSnapshot(
        providerId = descriptor.id.id.take(64),
        profileVersion = descriptor.profileVersion.take(32),
        connectionKind = connectionKind(descriptor.connectionKind),
        preparationState = preparationWire(preparation),
        session = WireSessionSummary(
            state = WireSessionState.READY,
            sessionGeneration = 0L,
            expiresAtEpochMs = null,
        ),
        simulated = descriptor.connectionKind == ProviderConnectionKind.DEMO,
        observedAtEpochMs = nowEpochMs,
    )

    fun capability(
        capability: CoreCapability,
        descriptor: ProviderDescriptor,
        nowEpochMs: Long,
    ): WireCapability = WireCapability(
        key = capability.key.id.take(128),
        scope = scopeWire(capability.scope),
        support = supportWire(capability.support),
        availability = availability(capability, descriptor),
        evidence = capability.declaredEvidence.name.lowercase().take(64),
        origin = capability.declaredOrigin.name.lowercase().take(64),
        profileVersion = capability.profileVersion.take(32),
        observedAtEpochMs = nowEpochMs,
        revalidateAfterEpochMs = capability.revalidateAtMillis,
        limitations = capability.limitations.sorted().map { it.take(256) }.take(20),
        extensions = emptyList(),
    )

    /**
     * Honest availability: only contract-implemented evidence is AVAILABLE,
     * local implementations on the demo provider are DEMO_ONLY, unknown
     * evidence stays UNKNOWN and everything else stays UNSUPPORTED.
     */
    fun availability(capability: CoreCapability, descriptor: ProviderDescriptor): Int = when (capability.declaredEvidence) {
        CapabilityEvidence.IMPLEMENTED_CONTRACT, CapabilityEvidence.IMPLEMENTED_OFFLINE -> WireAvailability.AVAILABLE
        CapabilityEvidence.LOCAL_ONLY ->
            if (descriptor.connectionKind == ProviderConnectionKind.DEMO) WireAvailability.DEMO_ONLY else WireAvailability.UNSUPPORTED
        CapabilityEvidence.STATIC_EVIDENCE_ONLY, CapabilityEvidence.UNKNOWN -> WireAvailability.UNKNOWN
        CapabilityEvidence.UNSUPPORTED, CapabilityEvidence.DESIGN_REQUIRED, CapabilityEvidence.NOT_IMPLEMENTED ->
            WireAvailability.UNSUPPORTED
    }

    fun device(
        device: CoreDeviceSnapshot,
        descriptor: ProviderDescriptor,
        nowEpochMs: Long,
    ): WireDeviceSnapshot = WireDeviceSnapshot(
        device = WireDeviceRef(
            providerId = device.device.provider.id.take(64),
            deviceId = device.device.id.take(128),
            channel = null,
        ),
        displayName = device.displayName?.take(128),
        declaredModel = (device.declaredModel ?: device.firmwareVersion?.let { "model:$it" })?.take(128),
        role = roleWire(device.role),
        presence = presenceWire(device.presence),
        lastObservedAtEpochMs = device.lastObservedAtMillis,
        source = dataSourceWire(device.source),
        stale = !device.fresh,
        simulated = descriptor.connectionKind == ProviderConnectionKind.DEMO,
        capabilityKeys = device.capabilities.map { it.key.id.take(128) }.take(100),
    )

    fun shadow(
        shadow: CoreShadowSnapshot,
        descriptor: ProviderDescriptor,
        nowEpochMs: Long,
    ): WireShadowSnapshot = WireShadowSnapshot(
        device = WireDeviceRef(
            providerId = shadow.device.provider.id.take(64),
            deviceId = shadow.device.id.take(128),
            channel = null,
        ),
        stockFields = shadow.state?.let { state ->
            WireStockShadowFields(
                deviceState = null,
                f1State = state.f1State,
                switchState = state.switchState?.state,
                privateModeState = state.privateModeState,
                fisheyeChannelState = state.fisheyeChannelState,
                lockState = state.locks?.firstOrNull()?.state,
                alarmZoneMode = state.alarmZoneMode,
                smartRelay = state.smartRelays?.firstOrNull()?.status,
            )
        },
        logicalPath = shadow.path?.take(256),
        timestampEpochMs = shadow.timestampMillis,
        sequence = shadow.sequence,
        source = dataSourceWire(shadowSource(shadow)),
        stale = !shadow.fresh,
        simulated = descriptor.connectionKind == ProviderConnectionKind.DEMO,
        extensions = emptyList<WireProviderExtension>(),
        unknownFields = (shadow.unknownFields + shadow.responseUnknownFields)
            .map { (name, value) -> WireUnknownField(name.take(128), observedType(value)) }
            .take(50),
    )

    fun actionOutcome(
        requestId: String,
        wireActionType: Int,
        outcome: CoreActionOutcome,
        descriptor: ProviderDescriptor,
        nowEpochMs: Long,
    ): WireActionOutcome = WireActionOutcome(
        requestId = requestId.take(64),
        actionType = wireActionType,
        device = WireDeviceRef(
            providerId = descriptor.id.id.take(64),
            deviceId = outcome.commandId.take(128),
            channel = null,
        ),
        ackLevel = ackLevel(outcome.level),
        origin = providerOrigin(descriptor.connectionKind),
        simulated = descriptor.connectionKind == ProviderConnectionKind.DEMO,
        occurredAtEpochMs = nowEpochMs,
        providerCode = outcome.providerCode?.toString()?.take(64),
        observationEventId = outcome.observation?.eventId?.take(128),
    )

    fun upgradeInfo(info: AppUpgradeInfo, nowEpochMs: Long): WireUpgradeInfo = WireUpgradeInfo(
        state = when (info.flag) {
            "1" -> WireUpgradeState.AVAILABLE
            "0" -> WireUpgradeState.UP_TO_DATE
            else -> WireUpgradeState.UNKNOWN
        },
        currentVersion = "unknown",
        availableVersion = info.version.takeIf { it.isNotBlank() }?.take(64),
        priority = info.priority.takeIf { it != 0 },
        description = info.description.take(SAFE_MESSAGE_LIMIT).takeIf { it.isNotBlank() },
        checkedAtEpochMs = nowEpochMs,
    )

    fun ackLevel(level: ActionAcknowledgementLevel): Int = when (level) {
        ActionAcknowledgementLevel.LOCAL_ACCEPTED -> WireAckLevel.LOCAL_ACCEPTED
        ActionAcknowledgementLevel.PROVIDER_ACCEPTED -> WireAckLevel.PROVIDER_ACCEPTED
        ActionAcknowledgementLevel.DEVICE_ACKNOWLEDGED -> WireAckLevel.DEVICE_ACKNOWLEDGED
        ActionAcknowledgementLevel.PHYSICAL_OBSERVED -> WireAckLevel.PHYSICAL_OBSERVED
        ActionAcknowledgementLevel.REJECTED -> WireAckLevel.REJECTED
        ActionAcknowledgementLevel.UNKNOWN_OUTCOME -> WireAckLevel.UNKNOWN_OUTCOME
    }

    fun connectionKind(kind: ProviderConnectionKind): Int = when (kind) {
        ProviderConnectionKind.STOCK -> WireConnectionKind.STOCK
        ProviderConnectionKind.MQTT -> WireConnectionKind.MQTT
        ProviderConnectionKind.SELF_HOSTED -> WireConnectionKind.SELF_HOSTED
        ProviderConnectionKind.DEMO -> WireConnectionKind.DEMO
    }

    fun providerOrigin(kind: ProviderConnectionKind): Int = when (kind) {
        ProviderConnectionKind.STOCK -> WireProviderOrigin.STOCK
        ProviderConnectionKind.MQTT -> WireProviderOrigin.MQTT
        ProviderConnectionKind.SELF_HOSTED -> WireProviderOrigin.SELF_HOSTED
        ProviderConnectionKind.DEMO -> WireProviderOrigin.DEMO
    }

    fun preparationWire(state: PreparationState): Int = when (state) {
        PreparationState.IDLE -> WirePreparationState.IDLE
        PreparationState.PREPARING -> WirePreparationState.PREPARING
        PreparationState.READY -> WirePreparationState.READY
        PreparationState.DEGRADED -> WirePreparationState.DEGRADED
        PreparationState.AUTHENTICATION_REQUIRED -> WirePreparationState.AUTHENTICATION_REQUIRED
        PreparationState.FAILED -> WirePreparationState.FAILED
        PreparationState.CLOSED -> WirePreparationState.CLOSED
    }

    private fun scopeWire(scope: CoreCapabilityScope): Int = when (scope) {
        CoreCapabilityScope.PROVIDER -> WireCapabilityScope.PROVIDER
        CoreCapabilityScope.ACCOUNT -> WireCapabilityScope.ACCOUNT
        CoreCapabilityScope.DEVICE -> WireCapabilityScope.DEVICE
        CoreCapabilityScope.CHANNEL -> WireCapabilityScope.CHANNEL
        CoreCapabilityScope.SESSION -> WireCapabilityScope.SESSION
    }

    private fun supportWire(support: CoreCapabilitySupport): Int = when (support) {
        CoreCapabilitySupport.REQUIRED -> WireSupport.REQUIRED
        CoreCapabilitySupport.OPTIONAL -> WireSupport.OPTIONAL
        CoreCapabilitySupport.UNSUPPORTED -> WireSupport.UNSUPPORTED
        CoreCapabilitySupport.UNKNOWN -> WireSupport.UNKNOWN
    }

    private fun roleWire(role: DeviceRole): Int = when (role) {
        DeviceRole.OWNER -> WireDeviceRole.OWNER
        DeviceRole.SHARED -> WireDeviceRole.SHARED
        DeviceRole.UNKNOWN -> WireDeviceRole.UNKNOWN
    }

    private fun presenceWire(presence: DevicePresence): Int = when (presence) {
        DevicePresence.ONLINE -> WirePresence.ONLINE
        DevicePresence.OFFLINE -> WirePresence.OFFLINE
        DevicePresence.UNKNOWN -> WirePresence.UNKNOWN
    }

    private fun dataSourceWire(source: DeviceDataSource): Int = when (source) {
        DeviceDataSource.CACHE -> WireDataSource.CACHE
        DeviceDataSource.CLOUD -> WireDataSource.CLOUD
        DeviceDataSource.LOCAL -> WireDataSource.LOCAL
        DeviceDataSource.MERGED -> WireDataSource.MERGED
    }

    private fun shadowSource(shadow: CoreShadowSnapshot): DeviceDataSource = when {
        shadow.timestampMillis == null -> DeviceDataSource.LOCAL
        shadow.fresh -> DeviceDataSource.LOCAL
        else -> DeviceDataSource.CACHE
    }

    /** Observed type of an unknown field value, without exposing the value itself. */
    private fun observedType(value: String): String = when {
        value.length > 1024 -> "oversized"
        value.matches(Regex("-?\\d+")) -> "integer"
        value.matches(Regex("-?\\d+\\.\\d+")) -> "decimal"
        value.equals("true", true) || value.equals("false", true) -> "boolean"
        else -> "string"
    }
}
