package dev.nacho.wibox.externalapi.sdk

import dev.nacho.wibox.externalapi.aidl.ClientHello as WireClientHello
import dev.nacho.wibox.externalapi.aidl.HandshakeResult as WireHandshakeResult
import dev.nacho.wibox.externalapi.aidl.AuthorizationRequest as WireAuthorizationRequest
import dev.nacho.wibox.externalapi.aidl.AuthorizationSnapshot as WireAuthorizationSnapshot
import dev.nacho.wibox.externalapi.aidl.AuthorizationResult as WireAuthorizationResult
import dev.nacho.wibox.externalapi.aidl.ProviderSnapshot as WireProviderSnapshot
import dev.nacho.wibox.externalapi.aidl.ProviderSnapshotResult as WireProviderSnapshotResult
import dev.nacho.wibox.externalapi.aidl.SessionSummary as WireSessionSummary
import dev.nacho.wibox.externalapi.aidl.CapabilityQuery as WireCapabilityQuery
import dev.nacho.wibox.externalapi.aidl.CapabilitySnapshot as WireCapabilitySnapshot
import dev.nacho.wibox.externalapi.aidl.CapabilityPage as WireCapabilityPage
import dev.nacho.wibox.externalapi.aidl.CapabilityPageResult as WireCapabilityPageResult
import dev.nacho.wibox.externalapi.aidl.PageCursor as WirePageCursor
import dev.nacho.wibox.externalapi.aidl.PageRequest as WirePageRequest
import dev.nacho.wibox.externalapi.aidl.DeviceRef as WireDeviceRef
import dev.nacho.wibox.externalapi.aidl.DeviceRequest as WireDeviceRequest
import dev.nacho.wibox.externalapi.aidl.DeviceSnapshot as WireDeviceSnapshot
import dev.nacho.wibox.externalapi.aidl.DevicePage as WireDevicePage
import dev.nacho.wibox.externalapi.aidl.ShadowQuery as WireShadowQuery
import dev.nacho.wibox.externalapi.aidl.ShadowBatchQuery as WireShadowBatchQuery
import dev.nacho.wibox.externalapi.aidl.StockShadowFields as WireStockShadowFields
import dev.nacho.wibox.externalapi.aidl.UnknownFieldDescriptor as WireUnknownFieldDescriptor
import dev.nacho.wibox.externalapi.aidl.ShadowSnapshot as WireShadowSnapshot
import dev.nacho.wibox.externalapi.aidl.ShadowBatch as WireShadowBatch
import dev.nacho.wibox.externalapi.aidl.ProviderExtension as WireProviderExtension
import dev.nacho.wibox.externalapi.aidl.ProviderExtensionValue as WireProviderExtensionValue
import dev.nacho.wibox.externalapi.aidl.EventCursor as WireEventCursor
import dev.nacho.wibox.externalapi.aidl.EventFilter as WireEventFilter
import dev.nacho.wibox.externalapi.aidl.EventSubscriptionRequest as WireEventSubscriptionRequest
import dev.nacho.wibox.externalapi.aidl.EventSubscriptionSnapshot as WireEventSubscriptionSnapshot
import dev.nacho.wibox.externalapi.aidl.EventSubscriptionResult as WireEventSubscriptionResult
import dev.nacho.wibox.externalapi.aidl.EventEnvelope as WireEventEnvelope
import dev.nacho.wibox.externalapi.aidl.AttachmentMetadata as WireAttachmentMetadata
import dev.nacho.wibox.externalapi.aidl.EventBatch as WireEventBatch
import dev.nacho.wibox.externalapi.aidl.EventGap as WireEventGap
import dev.nacho.wibox.externalapi.aidl.EventConnectionState as WireEventConnectionState
import dev.nacho.wibox.externalapi.aidl.ActionRequest as WireActionRequest
import dev.nacho.wibox.externalapi.aidl.ActionOutcome as WireActionOutcome
import dev.nacho.wibox.externalapi.aidl.ActionOutcomeResult as WireActionOutcomeResult
import dev.nacho.wibox.externalapi.aidl.CancellationResult as WireCancellationResult
import dev.nacho.wibox.externalapi.aidl.UpgradeCheckRequest as WireUpgradeCheckRequest
import dev.nacho.wibox.externalapi.aidl.UpgradeInfo as WireUpgradeInfo
import dev.nacho.wibox.externalapi.aidl.ApiError as WireApiError

internal fun WireClientHello.toSdk(): ClientHello = ClientHello(
    packageName = packageName,
    clientApiMajor = clientApiMajor,
    clientApiMinor = clientApiMinor,
    clientSdkVersion = clientSdkVersion,
)

internal fun ClientHello.toWire(): WireClientHello = WireClientHello(
    packageName = packageName,
    clientApiMajor = clientApiMajor,
    clientApiMinor = clientApiMinor,
    clientSdkVersion = clientSdkVersion,
)

internal fun WireHandshakeResult.toSdk(): HandshakeResult = HandshakeResult(
    serverApiMajor = serverApiMajor,
    serverApiMinor = serverApiMinor,
    negotiatedApiMinor = negotiatedApiMinor,
    authorization = authorization?.toSdk(),
    error = error?.toSdk(),
)

internal fun HandshakeResult.toWire(): WireHandshakeResult = WireHandshakeResult(
    serverApiMajor = serverApiMajor,
    serverApiMinor = serverApiMinor,
    negotiatedApiMinor = negotiatedApiMinor,
    authorization = authorization?.toWire(),
    error = error?.toWire(),
)

internal fun WireAuthorizationRequest.toSdk(): AuthorizationRequest = AuthorizationRequest(
    displayName = displayName,
    requestedScopes = requestedScopes,
    requestedDeviceIds = requestedDeviceIds,
)

internal fun AuthorizationRequest.toWire(): WireAuthorizationRequest = WireAuthorizationRequest(
    displayName = displayName,
    requestedScopes = requestedScopes,
    requestedDeviceIds = requestedDeviceIds,
)

internal fun WireAuthorizationSnapshot.toSdk(): AuthorizationSnapshot = AuthorizationSnapshot(
    state = enumFromWire<AuthorizationState>(state),
    grantedScopes = grantedScopes,
    grantedDeviceIds = grantedDeviceIds,
    grantGeneration = grantGeneration,
)

internal fun AuthorizationSnapshot.toWire(): WireAuthorizationSnapshot = WireAuthorizationSnapshot(
    state = state.wireValue,
    grantedScopes = grantedScopes,
    grantedDeviceIds = grantedDeviceIds,
    grantGeneration = grantGeneration,
)

internal fun WireAuthorizationResult.toSdk(): AuthorizationResult = AuthorizationResult(
    authorization = authorization?.toSdk(),
    error = error?.toSdk(),
)

internal fun AuthorizationResult.toWire(): WireAuthorizationResult = WireAuthorizationResult(
    authorization = authorization?.toWire(),
    error = error?.toWire(),
)

internal fun WireProviderSnapshot.toSdk(): ProviderSnapshot = ProviderSnapshot(
    providerId = providerId,
    profileVersion = profileVersion,
    connectionKind = enumFromWire<ConnectionKind>(connectionKind),
    preparationState = enumFromWire<PreparationState>(preparationState),
    session = session.toSdk(),
    simulated = simulated,
    observedAtEpochMs = observedAtEpochMs,
)

internal fun ProviderSnapshot.toWire(): WireProviderSnapshot = WireProviderSnapshot(
    providerId = providerId,
    profileVersion = profileVersion,
    connectionKind = connectionKind.wireValue,
    preparationState = preparationState.wireValue,
    session = session.toWire(),
    simulated = simulated,
    observedAtEpochMs = observedAtEpochMs,
)

internal fun WireProviderSnapshotResult.toSdk(): ProviderSnapshotResult = ProviderSnapshotResult(
    snapshot = snapshot?.toSdk(),
    error = error?.toSdk(),
)

internal fun ProviderSnapshotResult.toWire(): WireProviderSnapshotResult = WireProviderSnapshotResult(
    snapshot = snapshot?.toWire(),
    error = error?.toWire(),
)

internal fun WireSessionSummary.toSdk(): SessionSummary = SessionSummary(
    state = enumFromWire<SessionState>(state),
    sessionGeneration = sessionGeneration,
    expiresAtEpochMs = expiresAtEpochMs,
)

internal fun SessionSummary.toWire(): WireSessionSummary = WireSessionSummary(
    state = state.wireValue,
    sessionGeneration = sessionGeneration,
    expiresAtEpochMs = expiresAtEpochMs,
)

internal fun WireCapabilityQuery.toSdk(): CapabilityQuery = CapabilityQuery(
    page = page.toSdk(),
    deviceId = deviceId,
    namespace = namespace,
)

internal fun CapabilityQuery.toWire(): WireCapabilityQuery = WireCapabilityQuery(
    page = page.toWire(),
    deviceId = deviceId,
    namespace = namespace,
)

internal fun WireCapabilitySnapshot.toSdk(): CapabilitySnapshot = CapabilitySnapshot(
    key = key,
    scope = enumFromWire<CapabilityScope>(scope),
    support = enumFromWire<Support>(support),
    availability = enumFromWire<Availability>(availability),
    evidence = evidence,
    origin = origin,
    profileVersion = profileVersion,
    observedAtEpochMs = observedAtEpochMs,
    revalidateAfterEpochMs = revalidateAfterEpochMs,
    limitations = limitations,
    extensions = extensions.map { it.toSdk() },
)

internal fun CapabilitySnapshot.toWire(): WireCapabilitySnapshot = WireCapabilitySnapshot(
    key = key,
    scope = scope.wireValue,
    support = support.wireValue,
    availability = availability.wireValue,
    evidence = evidence,
    origin = origin,
    profileVersion = profileVersion,
    observedAtEpochMs = observedAtEpochMs,
    revalidateAfterEpochMs = revalidateAfterEpochMs,
    limitations = limitations,
    extensions = extensions.map { it.toWire() },
)

internal fun WireCapabilityPage.toSdk(): CapabilityPage = CapabilityPage(
    items = items.map { it.toSdk() },
    nextCursor = nextCursor?.toSdk(),
    observedAtEpochMs = observedAtEpochMs,
)

internal fun CapabilityPage.toWire(): WireCapabilityPage = WireCapabilityPage(
    items = items.map { it.toWire() },
    nextCursor = nextCursor?.toWire(),
    observedAtEpochMs = observedAtEpochMs,
)

internal fun WireCapabilityPageResult.toSdk(): CapabilityPageResult = CapabilityPageResult(
    page = page?.toSdk(),
    error = error?.toSdk(),
)

internal fun CapabilityPageResult.toWire(): WireCapabilityPageResult = WireCapabilityPageResult(
    page = page?.toWire(),
    error = error?.toWire(),
)

internal fun WirePageCursor.toSdk(): PageCursor = PageCursor(
    generation = generation,
    offset = offset,
)

internal fun PageCursor.toWire(): WirePageCursor = WirePageCursor(
    generation = generation,
    offset = offset,
)

internal fun WirePageRequest.toSdk(): PageRequest = PageRequest(
    requestId = requestId,
    cursor = cursor?.toSdk(),
    limit = limit,
)

internal fun PageRequest.toWire(): WirePageRequest = WirePageRequest(
    requestId = requestId,
    cursor = cursor?.toWire(),
    limit = limit,
)

internal fun WireDeviceRef.toSdk(): DeviceRef = DeviceRef(
    providerId = providerId,
    deviceId = deviceId,
    channel = channel,
)

internal fun DeviceRef.toWire(): WireDeviceRef = WireDeviceRef(
    providerId = providerId,
    deviceId = deviceId,
    channel = channel,
)

internal fun WireDeviceRequest.toSdk(): DeviceRequest = DeviceRequest(
    requestId = requestId,
    device = device.toSdk(),
)

internal fun DeviceRequest.toWire(): WireDeviceRequest = WireDeviceRequest(
    requestId = requestId,
    device = device.toWire(),
)

internal fun WireDeviceSnapshot.toSdk(): DeviceSnapshot = DeviceSnapshot(
    device = device.toSdk(),
    displayName = displayName,
    declaredModel = declaredModel,
    role = enumFromWire<DeviceRole>(role),
    presence = enumFromWire<Presence>(presence),
    lastObservedAtEpochMs = lastObservedAtEpochMs,
    source = enumFromWire<DataSource>(source),
    stale = stale,
    simulated = simulated,
    capabilityKeys = capabilityKeys,
)

internal fun DeviceSnapshot.toWire(): WireDeviceSnapshot = WireDeviceSnapshot(
    device = device.toWire(),
    displayName = displayName,
    declaredModel = declaredModel,
    role = role.wireValue,
    presence = presence.wireValue,
    lastObservedAtEpochMs = lastObservedAtEpochMs,
    source = source.wireValue,
    stale = stale,
    simulated = simulated,
    capabilityKeys = capabilityKeys,
)

internal fun WireDevicePage.toSdk(): DevicePage = DevicePage(
    items = items.map { it.toSdk() },
    nextCursor = nextCursor?.toSdk(),
    source = enumFromWire<DataSource>(source),
    observedAtEpochMs = observedAtEpochMs,
)

internal fun DevicePage.toWire(): WireDevicePage = WireDevicePage(
    items = items.map { it.toWire() },
    nextCursor = nextCursor?.toWire(),
    source = source.wireValue,
    observedAtEpochMs = observedAtEpochMs,
)

internal fun WireShadowQuery.toSdk(): ShadowQuery = ShadowQuery(
    requestId = requestId,
    device = device.toSdk(),
    fieldNames = fieldNames,
    freshness = enumFromWire<Freshness>(freshness),
)

internal fun ShadowQuery.toWire(): WireShadowQuery = WireShadowQuery(
    requestId = requestId,
    device = device.toWire(),
    fieldNames = fieldNames,
    freshness = freshness.wireValue,
)

internal fun WireShadowBatchQuery.toSdk(): ShadowBatchQuery = ShadowBatchQuery(
    requestId = requestId,
    queries = queries.map { it.toSdk() },
)

internal fun ShadowBatchQuery.toWire(): WireShadowBatchQuery = WireShadowBatchQuery(
    requestId = requestId,
    queries = queries.map { it.toWire() },
)

internal fun WireStockShadowFields.toSdk(): StockShadowFields = StockShadowFields(
    deviceState = deviceState,
    f1State = f1State,
    switchState = switchState,
    privateModeState = privateModeState,
    fisheyeChannelState = fisheyeChannelState,
    lockState = lockState,
    alarmZoneMode = alarmZoneMode,
    smartRelay = smartRelay,
)

internal fun StockShadowFields.toWire(): WireStockShadowFields = WireStockShadowFields(
    deviceState = deviceState,
    f1State = f1State,
    switchState = switchState,
    privateModeState = privateModeState,
    fisheyeChannelState = fisheyeChannelState,
    lockState = lockState,
    alarmZoneMode = alarmZoneMode,
    smartRelay = smartRelay,
)

internal fun WireUnknownFieldDescriptor.toSdk(): UnknownFieldDescriptor = UnknownFieldDescriptor(
    name = name,
    observedType = observedType,
)

internal fun UnknownFieldDescriptor.toWire(): WireUnknownFieldDescriptor = WireUnknownFieldDescriptor(
    name = name,
    observedType = observedType,
)

internal fun WireShadowSnapshot.toSdk(): ShadowSnapshot = ShadowSnapshot(
    device = device.toSdk(),
    stockFields = stockFields?.toSdk(),
    logicalPath = logicalPath,
    timestampEpochMs = timestampEpochMs,
    sequence = sequence,
    source = enumFromWire<DataSource>(source),
    stale = stale,
    simulated = simulated,
    extensions = extensions.map { it.toSdk() },
    unknownFields = unknownFields.map { it.toSdk() },
)

internal fun ShadowSnapshot.toWire(): WireShadowSnapshot = WireShadowSnapshot(
    device = device.toWire(),
    stockFields = stockFields?.toWire(),
    logicalPath = logicalPath,
    timestampEpochMs = timestampEpochMs,
    sequence = sequence,
    source = source.wireValue,
    stale = stale,
    simulated = simulated,
    extensions = extensions.map { it.toWire() },
    unknownFields = unknownFields.map { it.toWire() },
)

internal fun WireShadowBatch.toSdk(): ShadowBatch = ShadowBatch(
    items = items.map { it.toSdk() },
    missingDeviceIds = missingDeviceIds,
)

internal fun ShadowBatch.toWire(): WireShadowBatch = WireShadowBatch(
    items = items.map { it.toWire() },
    missingDeviceIds = missingDeviceIds,
)

internal fun WireProviderExtension.toSdk(): ProviderExtension = ProviderExtension(
    namespacedKey = namespacedKey,
    value = value.toSdk(),
)

internal fun ProviderExtension.toWire(): WireProviderExtension = WireProviderExtension(
    namespacedKey = namespacedKey,
    value = value.toWire(),
)

internal fun WireProviderExtensionValue.toSdk(): ProviderExtensionValue = ProviderExtensionValue(
    type = enumFromWire<ExtensionValueType>(type),
    booleanValue = booleanValue,
    integerValue = integerValue,
    decimalValue = decimalValue,
    stringValue = stringValue,
)

internal fun ProviderExtensionValue.toWire(): WireProviderExtensionValue = WireProviderExtensionValue(
    type = type.wireValue,
    booleanValue = booleanValue,
    integerValue = integerValue,
    decimalValue = decimalValue,
    stringValue = stringValue,
)

internal fun WireEventCursor.toSdk(): EventCursor = EventCursor(
    streamGeneration = streamGeneration,
    sequence = sequence,
)

internal fun EventCursor.toWire(): WireEventCursor = WireEventCursor(
    streamGeneration = streamGeneration,
    sequence = sequence,
)

internal fun WireEventFilter.toSdk(): EventFilter = EventFilter(
    deviceIds = deviceIds,
    channels = channels,
    eventTypes = eventTypes.map { enumFromWire<EventType>(it) },
)

internal fun EventFilter.toWire(): WireEventFilter = WireEventFilter(
    deviceIds = deviceIds,
    channels = channels,
    eventTypes = eventTypes.map { it.wireValue },
)

internal fun WireEventSubscriptionRequest.toSdk(): EventSubscriptionRequest = EventSubscriptionRequest(
    after = after?.toSdk(),
    filter = filter.toSdk(),
    maximumBatchSize = maximumBatchSize,
)

internal fun EventSubscriptionRequest.toWire(): WireEventSubscriptionRequest = WireEventSubscriptionRequest(
    after = after?.toWire(),
    filter = filter.toWire(),
    maximumBatchSize = maximumBatchSize,
)

internal fun WireEventSubscriptionSnapshot.toSdk(): EventSubscriptionSnapshot = EventSubscriptionSnapshot(
    state = state.toSdk(),
    resumeCursor = resumeCursor?.toSdk(),
    replayFromCache = replayFromCache,
    liveSupported = liveSupported,
)

internal fun EventSubscriptionSnapshot.toWire(): WireEventSubscriptionSnapshot = WireEventSubscriptionSnapshot(
    state = state.toWire(),
    resumeCursor = resumeCursor?.toWire(),
    replayFromCache = replayFromCache,
    liveSupported = liveSupported,
)

internal fun WireEventSubscriptionResult.toSdk(): EventSubscriptionResult = EventSubscriptionResult(
    snapshot = snapshot?.toSdk(),
    error = error?.toSdk(),
)

internal fun EventSubscriptionResult.toWire(): WireEventSubscriptionResult = WireEventSubscriptionResult(
    snapshot = snapshot?.toWire(),
    error = error?.toWire(),
)

internal fun WireEventEnvelope.toSdk(): EventEnvelope = EventEnvelope(
    providerEventId = providerEventId,
    type = enumFromWire<EventType>(type),
    device = device.toSdk(),
    rawTime = rawTime,
    parsedTimeEpochMs = parsedTimeEpochMs,
    receivedAtEpochMs = receivedAtEpochMs,
    origin = enumFromWire<ProviderOrigin>(origin),
    duplicate = duplicate,
    cursor = cursor.toSdk(),
    simulated = simulated,
    extensions = extensions.map { it.toSdk() },
    attachmentMetadata = attachmentMetadata.map { it.toSdk() },
)

internal fun EventEnvelope.toWire(): WireEventEnvelope = WireEventEnvelope(
    providerEventId = providerEventId,
    type = type.wireValue,
    device = device.toWire(),
    rawTime = rawTime,
    parsedTimeEpochMs = parsedTimeEpochMs,
    receivedAtEpochMs = receivedAtEpochMs,
    origin = origin.wireValue,
    duplicate = duplicate,
    cursor = cursor.toWire(),
    simulated = simulated,
    extensions = extensions.map { it.toWire() },
    attachmentMetadata = attachmentMetadata.map { it.toWire() },
)

internal fun WireAttachmentMetadata.toSdk(): AttachmentMetadata = AttachmentMetadata(
    kind = kind,
    available = available,
    expiresAtEpochMs = expiresAtEpochMs,
)

internal fun AttachmentMetadata.toWire(): WireAttachmentMetadata = WireAttachmentMetadata(
    kind = kind,
    available = available,
    expiresAtEpochMs = expiresAtEpochMs,
)

internal fun WireEventBatch.toSdk(): EventBatch = EventBatch(
    items = items.map { it.toSdk() },
    latestCursor = latestCursor?.toSdk(),
)

internal fun EventBatch.toWire(): WireEventBatch = WireEventBatch(
    items = items.map { it.toWire() },
    latestCursor = latestCursor?.toWire(),
)

internal fun WireEventGap.toSdk(): EventGap = EventGap(
    lastValidCursor = lastValidCursor?.toSdk(),
    resumeCursor = resumeCursor?.toSdk(),
    reason = enumFromWire<GapReason>(reason),
    detectedAtEpochMs = detectedAtEpochMs,
    resyncRequired = resyncRequired,
    recoverability = enumFromWire<GapRecoverability>(recoverability),
)

internal fun EventGap.toWire(): WireEventGap = WireEventGap(
    lastValidCursor = lastValidCursor?.toWire(),
    resumeCursor = resumeCursor?.toWire(),
    reason = reason.wireValue,
    detectedAtEpochMs = detectedAtEpochMs,
    resyncRequired = resyncRequired,
    recoverability = recoverability.wireValue,
)

internal fun WireEventConnectionState.toSdk(): EventConnectionState = EventConnectionState(
    state = enumFromWire<EventStreamState>(state),
    observedAtEpochMs = observedAtEpochMs,
)

internal fun EventConnectionState.toWire(): WireEventConnectionState = WireEventConnectionState(
    state = state.wireValue,
    observedAtEpochMs = observedAtEpochMs,
)

internal fun WireActionRequest.toSdk(): ActionRequest = ActionRequest(
    requestId = requestId,
    device = device.toSdk(),
    type = enumFromWire<ActionType>(type),
    parameters = parameters.map { it.toSdk() },
    expiresAtEpochMs = expiresAtEpochMs,
)

internal fun ActionRequest.toWire(): WireActionRequest = WireActionRequest(
    requestId = requestId,
    device = device.toWire(),
    type = type.wireValue,
    parameters = parameters.map { it.toWire() },
    expiresAtEpochMs = expiresAtEpochMs,
)

internal fun WireActionOutcome.toSdk(): ActionOutcome = ActionOutcome(
    requestId = requestId,
    actionType = enumFromWire<ActionType>(actionType),
    device = device.toSdk(),
    ackLevel = enumFromWire<AckLevel>(ackLevel),
    origin = enumFromWire<ProviderOrigin>(origin),
    simulated = simulated,
    occurredAtEpochMs = occurredAtEpochMs,
    providerCode = providerCode,
    observationEventId = observationEventId,
)

internal fun ActionOutcome.toWire(): WireActionOutcome = WireActionOutcome(
    requestId = requestId,
    actionType = actionType.wireValue,
    device = device.toWire(),
    ackLevel = ackLevel.wireValue,
    origin = origin.wireValue,
    simulated = simulated,
    occurredAtEpochMs = occurredAtEpochMs,
    providerCode = providerCode,
    observationEventId = observationEventId,
)

internal fun WireActionOutcomeResult.toSdk(): ActionOutcomeResult = ActionOutcomeResult(
    outcome = outcome?.toSdk(),
    error = error?.toSdk(),
)

internal fun ActionOutcomeResult.toWire(): WireActionOutcomeResult = WireActionOutcomeResult(
    outcome = outcome?.toWire(),
    error = error?.toWire(),
)

internal fun WireCancellationResult.toSdk(): CancellationResult = CancellationResult(
    requestId = requestId,
    state = enumFromWire<CancellationState>(state),
    error = error?.toSdk(),
)

internal fun CancellationResult.toWire(): WireCancellationResult = WireCancellationResult(
    requestId = requestId,
    state = state.wireValue,
    error = error?.toWire(),
)

internal fun WireUpgradeCheckRequest.toSdk(): UpgradeCheckRequest = UpgradeCheckRequest(
    requestId = requestId,
    languageTag = languageTag,
)

internal fun UpgradeCheckRequest.toWire(): WireUpgradeCheckRequest = WireUpgradeCheckRequest(
    requestId = requestId,
    languageTag = languageTag,
)

internal fun WireUpgradeInfo.toSdk(): UpgradeInfo = UpgradeInfo(
    state = enumFromWire<UpgradeState>(state),
    currentVersion = currentVersion,
    availableVersion = availableVersion,
    priority = priority,
    description = description,
    checkedAtEpochMs = checkedAtEpochMs,
)

internal fun UpgradeInfo.toWire(): WireUpgradeInfo = WireUpgradeInfo(
    state = state.wireValue,
    currentVersion = currentVersion,
    availableVersion = availableVersion,
    priority = priority,
    description = description,
    checkedAtEpochMs = checkedAtEpochMs,
)

internal fun WireApiError.toSdk(): ApiError = ApiError(
    code = enumFromWire<ApiErrorCode>(code),
    retryPolicy = enumFromWire<RetryPolicy>(retryPolicy),
    capabilityKey = capabilityKey,
    outcome = outcome?.let { enumFromWire<AckLevel>(it) },
    safeMessage = safeMessage,
    retryAfterMs = retryAfterMs,
    providerCode = providerCode,
)

internal fun ApiError.toWire(): WireApiError = WireApiError(
    code = code.wireValue,
    retryPolicy = retryPolicy.wireValue,
    capabilityKey = capabilityKey,
    outcome = outcome?.wireValue,
    safeMessage = safeMessage,
    retryAfterMs = retryAfterMs,
    providerCode = providerCode,
)
