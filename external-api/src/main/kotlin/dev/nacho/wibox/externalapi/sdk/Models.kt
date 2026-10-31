package dev.nacho.wibox.externalapi.sdk

import dev.nacho.wibox.externalapi.aidl.validateItems
import dev.nacho.wibox.externalapi.aidl.validateUtf8

data class ClientHello(
    val packageName: String,
    val clientApiMajor: Int,
    val clientApiMinor: Int,
    val clientSdkVersion: String,
) {
    init {
        validateUtf8("packageName", packageName, 128, required = true)
        validateUtf8("clientSdkVersion", clientSdkVersion, 32, required = true)
        require(clientApiMajor >= 1) { "clientApiMajor must be positive" }
        require(clientApiMinor >= 0) { "clientApiMinor must be nonnegative" }
    }
}

data class HandshakeResult(
    val serverApiMajor: Int,
    val serverApiMinor: Int,
    val negotiatedApiMinor: Int,
    val authorization: AuthorizationSnapshot?,
    val error: ApiError?,
) {
    init {
        require(serverApiMajor >= 1) { "serverApiMajor must be positive" }
        require(serverApiMinor >= 0 && negotiatedApiMinor >= 0) { "API minors must be nonnegative" }
        require(error == null || authorization == null) { "Error and authorization are mutually exclusive" }
    }
}

data class AuthorizationRequest(
    val displayName: String,
    val requestedScopes: List<String>,
    val requestedDeviceIds: List<String>,
) {
    init {
        validateUtf8("displayName", displayName, 128, required = true)
        validateItems("requestedScopes", requestedScopes, maxItems = 10)
        validateItems("requestedDeviceIds", requestedDeviceIds, maxItems = 20)
        requestedDeviceIds.forEachIndexed { index, value -> validateUtf8("requestedDeviceIds[$index]", value, 128) }
    }
}

data class AuthorizationSnapshot(
    val state: AuthorizationState,
    val grantedScopes: List<String>,
    val grantedDeviceIds: List<String>,
    val grantGeneration: Long,
) {
    init {
        validateItems("grantedScopes", grantedScopes, maxItems = 10)
        validateItems("grantedDeviceIds", grantedDeviceIds, maxItems = 100)
        grantedDeviceIds.forEachIndexed { index, value -> validateUtf8("grantedDeviceIds[$index]", value, 128) }
        require(grantGeneration >= 0) { "grantGeneration must be nonnegative" }
    }
}

data class AuthorizationResult(
    val authorization: AuthorizationSnapshot?,
    val error: ApiError?,
) {
    init {
        require((authorization != null) xor (error != null)) { "Exactly one payload or error is required" }
    }
}

data class ProviderSnapshot(
    val providerId: String,
    val profileVersion: String,
    val connectionKind: ConnectionKind,
    val preparationState: PreparationState,
    val session: SessionSummary,
    val simulated: Boolean,
    val observedAtEpochMs: Long,
) {
    init {
        validateUtf8("providerId", providerId, 64, required = true)
        validateUtf8("profileVersion", profileVersion, 32, required = true)
    }
}

data class ProviderSnapshotResult(
    val snapshot: ProviderSnapshot?,
    val error: ApiError?,
) {
    init {
        require((snapshot != null) xor (error != null)) { "Exactly one payload or error is required" }
    }
}

data class SessionSummary(
    val state: SessionState,
    val sessionGeneration: Long,
    val expiresAtEpochMs: Long?,
) {
    init {
        require(sessionGeneration >= 0) { "sessionGeneration must be nonnegative" }
    }
}

data class CapabilityQuery(
    val page: PageRequest,
    val deviceId: String?,
    val namespace: String?,
) {
    init {
        validateUtf8("deviceId", deviceId, 128, required = false)
        validateUtf8("namespace", namespace, 128, required = false)
    }
}

data class CapabilitySnapshot(
    val key: String,
    val scope: CapabilityScope,
    val support: Support,
    val availability: Availability,
    val evidence: String,
    val origin: String,
    val profileVersion: String,
    val observedAtEpochMs: Long,
    val revalidateAfterEpochMs: Long?,
    val limitations: List<String>,
    val extensions: List<ProviderExtension>,
) {
    init {
        validateUtf8("key", key, 128, required = true)
        validateUtf8("evidence", evidence, 64, required = true)
        validateUtf8("origin", origin, 64, required = true)
        validateUtf8("profileVersion", profileVersion, 32, required = true)
        validateItems("limitations", limitations, maxItems = 10)
        limitations.forEachIndexed { index, value -> validateUtf8("limitations[$index]", value, 256) }
        validateItems("extensions", extensions, maxItems = 20)
    }
}

data class CapabilityPage(
    val items: List<CapabilitySnapshot>,
    val nextCursor: PageCursor?,
    val observedAtEpochMs: Long,
) {
    init {
        validateItems("items", items, maxItems = 50)
    }
}

data class CapabilityPageResult(
    val page: CapabilityPage?,
    val error: ApiError?,
) {
    init {
        require((page != null) xor (error != null)) { "Exactly one payload or error is required" }
    }
}

data class PageCursor(
    val generation: Long,
    val offset: Int,
) {
    init {
        require(generation >= 0) { "generation must be nonnegative" }
        require(offset >= 0) { "offset must be nonnegative" }
    }
}

data class PageRequest(
    val requestId: String,
    val cursor: PageCursor?,
    val limit: Int,
) {
    init {
        validateUtf8("requestId", requestId, 64, required = true)
        require(limit >= 1) { "limit is below minimum" }
        require(limit <= 20) { "limit is above maximum" }
    }
}

data class DeviceRef(
    val providerId: String,
    val deviceId: String,
    val channel: Int?,
) {
    init {
        validateUtf8("providerId", providerId, 64, required = true)
        validateUtf8("deviceId", deviceId, 128, required = true)
    }
}

data class DeviceRequest(
    val requestId: String,
    val device: DeviceRef,
) {
    init {
        validateUtf8("requestId", requestId, 64, required = true)
    }
}

data class DeviceSnapshot(
    val device: DeviceRef,
    val displayName: String?,
    val declaredModel: String?,
    val role: DeviceRole,
    val presence: Presence,
    val lastObservedAtEpochMs: Long?,
    val source: DataSource,
    val stale: Boolean,
    val simulated: Boolean,
    val capabilityKeys: List<String>,
) {
    init {
        validateUtf8("displayName", displayName, 128, required = false)
        validateUtf8("declaredModel", declaredModel, 128, required = false)
        validateItems("capabilityKeys", capabilityKeys, maxItems = 100)
        capabilityKeys.forEachIndexed { index, value -> validateUtf8("capabilityKeys[$index]", value, 128) }
    }
}

data class DevicePage(
    val items: List<DeviceSnapshot>,
    val nextCursor: PageCursor?,
    val source: DataSource,
    val observedAtEpochMs: Long,
) {
    init {
        validateItems("items", items, maxItems = 20)
    }
}

data class ShadowQuery(
    val requestId: String,
    val device: DeviceRef,
    val fieldNames: List<String>,
    val freshness: Freshness,
) {
    init {
        validateUtf8("requestId", requestId, 64, required = true)
        validateItems("fieldNames", fieldNames, maxItems = 32)
        fieldNames.forEachIndexed { index, value -> validateUtf8("fieldNames[$index]", value, 128) }
    }
}

data class ShadowBatchQuery(
    val requestId: String,
    val queries: List<ShadowQuery>,
) {
    init {
        validateUtf8("requestId", requestId, 64, required = true)
        validateItems("queries", queries, maxItems = 20, minItems = 1)
    }
}

data class StockShadowFields(
    val deviceState: Int?,
    val f1State: Int?,
    val switchState: Int?,
    val privateModeState: Int?,
    val fisheyeChannelState: Int?,
    val lockState: Int?,
    val alarmZoneMode: Int?,
    val smartRelay: Int?,
) {
}

data class UnknownFieldDescriptor(
    val name: String,
    val observedType: String,
) {
    init {
        validateUtf8("name", name, 128, required = true)
        validateUtf8("observedType", observedType, 32, required = true)
    }
}

data class ShadowSnapshot(
    val device: DeviceRef,
    val stockFields: StockShadowFields?,
    val logicalPath: String?,
    val timestampEpochMs: Long?,
    val sequence: Long?,
    val source: DataSource,
    val stale: Boolean,
    val simulated: Boolean,
    val extensions: List<ProviderExtension>,
    val unknownFields: List<UnknownFieldDescriptor>,
) {
    init {
        validateUtf8("logicalPath", logicalPath, 128, required = false)
        validateItems("extensions", extensions, maxItems = 20)
        validateItems("unknownFields", unknownFields, maxItems = 50)
    }
}

data class ShadowBatch(
    val items: List<ShadowSnapshot>,
    val missingDeviceIds: List<String>,
) {
    init {
        validateItems("items", items, maxItems = 20)
        validateItems("missingDeviceIds", missingDeviceIds, maxItems = 20)
        missingDeviceIds.forEachIndexed { index, value -> validateUtf8("missingDeviceIds[$index]", value, 128) }
    }
}

data class ProviderExtension(
    val namespacedKey: String,
    val value: ProviderExtensionValue,
) {
    init {
        validateUtf8("namespacedKey", namespacedKey, 128, required = true)
    }
}

data class ProviderExtensionValue(
    val type: ExtensionValueType,
    val booleanValue: Boolean?,
    val integerValue: Long?,
    val decimalValue: Double?,
    val stringValue: String?,
) {
    init {
        validateUtf8("stringValue", stringValue, 1024, required = false)
        when (type) {
            ExtensionValueType.BOOLEAN -> require(booleanValue != null && integerValue == null && decimalValue == null && stringValue == null) { "BOOLEAN requires only booleanValue" }
            ExtensionValueType.INTEGER -> require(integerValue != null && booleanValue == null && decimalValue == null && stringValue == null) { "INTEGER requires only integerValue" }
            ExtensionValueType.DECIMAL -> require(decimalValue != null && decimalValue.isFinite() && booleanValue == null && integerValue == null && stringValue == null) { "DECIMAL requires only a finite decimalValue" }
            ExtensionValueType.STRING -> require(stringValue != null && booleanValue == null && integerValue == null && decimalValue == null) { "STRING requires only stringValue" }
            ExtensionValueType.UNKNOWN -> Unit
        }
    }
}

data class EventCursor(
    val streamGeneration: Long,
    val sequence: Long,
) {
    init {
        require(streamGeneration >= 0) { "streamGeneration must be nonnegative" }
        require(sequence >= 0) { "sequence must be nonnegative" }
    }
}

data class EventFilter(
    val deviceIds: List<String>,
    val channels: List<Int>,
    val eventTypes: List<EventType>,
) {
    init {
        validateItems("deviceIds", deviceIds, maxItems = 20)
        deviceIds.forEachIndexed { index, value -> validateUtf8("deviceIds[$index]", value, 128) }
        validateItems("channels", channels, maxItems = 20)
        validateItems("eventTypes", eventTypes, maxItems = 20)
    }
}

data class EventSubscriptionRequest(
    val after: EventCursor?,
    val filter: EventFilter,
    val maximumBatchSize: Int,
) {
    init {
        require(maximumBatchSize >= 1) { "maximumBatchSize is below minimum" }
        require(maximumBatchSize <= 10) { "maximumBatchSize is above maximum" }
    }
}

data class EventSubscriptionSnapshot(
    val state: EventConnectionState,
    val resumeCursor: EventCursor?,
    val replayFromCache: Boolean,
    val liveSupported: Boolean,
) {
}

data class EventSubscriptionResult(
    val snapshot: EventSubscriptionSnapshot?,
    val error: ApiError?,
) {
    init {
        require((snapshot != null) xor (error != null)) { "Exactly one payload or error is required" }
    }
}

data class EventEnvelope(
    val providerEventId: String,
    val type: EventType,
    val device: DeviceRef,
    val rawTime: String?,
    val parsedTimeEpochMs: Long?,
    val receivedAtEpochMs: Long,
    val origin: ProviderOrigin,
    val duplicate: Boolean,
    val cursor: EventCursor,
    val simulated: Boolean,
    val extensions: List<ProviderExtension>,
    val attachmentMetadata: List<AttachmentMetadata>,
) {
    init {
        validateUtf8("providerEventId", providerEventId, 128, required = true)
        validateUtf8("rawTime", rawTime, 128, required = false)
        validateItems("extensions", extensions, maxItems = 20)
        validateItems("attachmentMetadata", attachmentMetadata, maxItems = 4)
    }
}

data class AttachmentMetadata(
    val kind: String,
    val available: Boolean,
    val expiresAtEpochMs: Long?,
) {
    init {
        validateUtf8("kind", kind, 32, required = true)
    }
}

data class EventBatch(
    val items: List<EventEnvelope>,
    val latestCursor: EventCursor?,
) {
    init {
        validateItems("items", items, maxItems = 10)
    }
}

data class EventGap(
    val lastValidCursor: EventCursor?,
    val resumeCursor: EventCursor?,
    val reason: GapReason,
    val detectedAtEpochMs: Long,
    val resyncRequired: Boolean,
    val recoverability: GapRecoverability,
) {
}

data class EventConnectionState(
    val state: EventStreamState,
    val observedAtEpochMs: Long,
) {
}

data class ActionRequest(
    val requestId: String,
    val device: DeviceRef,
    val type: ActionType,
    val parameters: List<ProviderExtension>,
    val expiresAtEpochMs: Long,
) {
    init {
        validateUtf8("requestId", requestId, 64, required = true)
        validateItems("parameters", parameters, maxItems = 10)
    }
}

data class ActionOutcome(
    val requestId: String,
    val actionType: ActionType,
    val device: DeviceRef,
    val ackLevel: AckLevel,
    val origin: ProviderOrigin,
    val simulated: Boolean,
    val occurredAtEpochMs: Long,
    val providerCode: String?,
    val observationEventId: String?,
) {
    init {
        validateUtf8("requestId", requestId, 64, required = true)
        validateUtf8("providerCode", providerCode, 64, required = false)
        validateUtf8("observationEventId", observationEventId, 128, required = false)
    }

    val physicalEffectConfirmed: Boolean
        get() = ackLevel == AckLevel.PHYSICAL_OBSERVED && !simulated && origin != ProviderOrigin.DEMO
}

data class ActionOutcomeResult(
    val outcome: ActionOutcome?,
    val error: ApiError?,
) {
    init {
        require((outcome != null) xor (error != null)) { "Exactly one payload or error is required" }
    }
}

data class CancellationResult(
    val requestId: String,
    val state: CancellationState,
    val error: ApiError?,
) {
    init {
        validateUtf8("requestId", requestId, 64, required = true)
    }
}

data class UpgradeCheckRequest(
    val requestId: String,
    val languageTag: String,
) {
    init {
        validateUtf8("requestId", requestId, 64, required = true)
        validateUtf8("languageTag", languageTag, 35, required = true)
        require(languageTag.matches(Regex("[A-Za-z]{1,8}(?:-[A-Za-z0-9]{1,8})*"))) { "languageTag must be BCP 47" }
    }
}

data class UpgradeInfo(
    val state: UpgradeState,
    val currentVersion: String,
    val availableVersion: String?,
    val priority: Int?,
    val description: String?,
    val checkedAtEpochMs: Long,
) {
    init {
        validateUtf8("currentVersion", currentVersion, 64, required = true)
        validateUtf8("availableVersion", availableVersion, 64, required = false)
        validateUtf8("description", description, 1024, required = false)
    }
}

data class ApiError(
    val code: ApiErrorCode,
    val retryPolicy: RetryPolicy,
    val capabilityKey: String?,
    val outcome: AckLevel?,
    val safeMessage: String?,
    val retryAfterMs: Long?,
    val providerCode: String?,
) {
    init {
        validateUtf8("capabilityKey", capabilityKey, 128, required = false)
        validateUtf8("safeMessage", safeMessage, 256, required = false)
        validateUtf8("providerCode", providerCode, 64, required = false)
        require(retryAfterMs == null || retryAfterMs >= 0) { "retryAfterMs must be nonnegative" }
    }
}

typealias Handshake = HandshakeResult
typealias AccessRequest = AuthorizationRequest
typealias Authorization = AuthorizationSnapshot
