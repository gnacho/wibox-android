package dev.nacho.wibox.externalapi.aidl

object WireAuthorizationState {
    const val UNKNOWN = 0
    const val NOT_REQUESTED = 1
    const val PENDING = 2
    const val AUTHORIZED = 3
    const val DENIED = 4
    const val REVOKED = 5
    const val SIGNATURE_CHANGED = 6
    const val UID_CHANGED = 7
    const val SHARED_UID_UNSUPPORTED = 8
}

object WireConnectionKind {
    const val UNKNOWN = 0
    const val STOCK = 1
    const val MQTT = 2
    const val SELF_HOSTED = 3
    const val DEMO = 4
}

object WirePreparationState {
    const val UNKNOWN = 0
    const val IDLE = 1
    const val PREPARING = 2
    const val READY = 3
    const val DEGRADED = 4
    const val AUTHENTICATION_REQUIRED = 5
    const val FAILED = 6
    const val CLOSED = 7
}

object WireSessionState {
    const val UNKNOWN = 0
    const val UNAVAILABLE = 1
    const val AUTHENTICATION_REQUIRED = 2
    const val PREPARING = 3
    const val READY = 4
    const val DEGRADED = 5
    const val CLOSED = 6
}

object WireCapabilityScope {
    const val UNKNOWN = 0
    const val PROVIDER = 1
    const val ACCOUNT = 2
    const val DEVICE = 3
    const val CHANNEL = 4
    const val SESSION = 5
}

object WireSupport {
    const val UNKNOWN = 0
    const val REQUIRED = 1
    const val OPTIONAL = 2
    const val UNSUPPORTED = 3
}

object WireAvailability {
    const val UNKNOWN = 0
    const val AVAILABLE = 1
    const val UNSUPPORTED = 2
    const val DEMO_ONLY = 3
}

object WireDeviceRole {
    const val UNKNOWN = 0
    const val OWNER = 1
    const val SHARED = 2
}

object WirePresence {
    const val UNKNOWN = 0
    const val ONLINE = 1
    const val OFFLINE = 2
}

object WireDataSource {
    const val UNKNOWN = 0
    const val CACHE = 1
    const val CLOUD = 2
    const val LOCAL = 3
    const val MERGED = 4
}

object WireFreshness {
    const val UNKNOWN = 0
    const val ANY = 1
    const val CACHED = 2
    const val FRESH = 3
}

object WireExtensionValueType {
    const val UNKNOWN = 0
    const val BOOLEAN = 1
    const val INTEGER = 2
    const val DECIMAL = 3
    const val STRING = 4
}

object WireEventType {
    const val UNKNOWN = 0
    const val MOTION = 1
    const val INCOMING_CALL = 2
    const val CALL_ANSWERED = 3
}

object WireProviderOrigin {
    const val UNKNOWN = 0
    const val STOCK = 1
    const val MQTT = 2
    const val SELF_HOSTED = 3
    const val DEMO = 4
}

object WireGapReason {
    const val UNKNOWN = 0
    const val OVERFLOW = 1
    const val CURSOR_INVALID = 2
    const val DISCONNECTED = 3
    const val RESYNC_REQUIRED = 4
}

object WireGapRecoverability {
    const val UNKNOWN = 0
    const val AVAILABLE = 1
    const val UNAVAILABLE = 2
}

object WireEventStreamState {
    const val UNKNOWN = 0
    const val CONNECTED = 1
    const val RECONNECTING = 2
    const val RESYNC_REQUIRED = 3
    const val CLOSED = 4
    const val UNSUPPORTED = 5
}

object WireActionType {
    const val UNKNOWN = 0
    const val UNLOCK = 1
    const val F1 = 2
    const val CALL_GUARD = 3
    const val CALL_ACCEPT = 4
    const val CALL_REJECT = 5
    const val START_CALL = 6
    const val STOP_CALL = 7
}

object WireAckLevel {
    const val UNKNOWN = 0
    const val LOCAL_ACCEPTED = 1
    const val PROVIDER_ACCEPTED = 2
    const val DEVICE_ACKNOWLEDGED = 3
    const val PHYSICAL_OBSERVED = 4
    const val UNKNOWN_OUTCOME = 5
    const val REJECTED = 6
}

object WireCancellationState {
    const val UNKNOWN = 0
    const val CANCELLED_BEFORE_DISPATCH = 1
    const val CANCELLATION_REQUESTED = 2
    const val ALREADY_COMPLETED = 3
    const val NOT_FOUND = 4
    const val OUTCOME_UNKNOWN = 5
}

object WireUpgradeState {
    const val UNKNOWN = 0
    const val UP_TO_DATE = 1
    const val AVAILABLE = 2
}

object WireApiErrorCode {
    const val UNKNOWN = 0
    const val NOT_AUTHORIZED = 1
    const val AUTHORIZATION_PENDING = 2
    const val AUTHORIZATION_REVOKED = 3
    const val AUTHORIZATION_DENIED = 4
    const val PACKAGE_UID_MISMATCH = 5
    const val SIGNATURE_CHANGED = 6
    const val UID_CHANGED = 7
    const val SHARED_UID_UNSUPPORTED = 8
    const val HANDSHAKE_REQUIRED = 9
    const val VERSION_UNSUPPORTED = 10
    const val UNSUPPORTED = 11
    const val CAPABILITY_UNKNOWN = 12
    const val SESSION_REQUIRED = 13
    const val PROVIDER_NOT_READY = 14
    const val NOT_FOUND = 15
    const val CONFLICT = 16
    const val INVALID_INPUT = 17
    const val PAYLOAD_TOO_LARGE = 18
    const val RATE_LIMITED = 19
    const val BUSY = 20
    const val CANCELLED = 21
    const val SERVICE_DIED = 22
    const val CALLBACK_DEAD = 23
    const val AUTHENTICATION = 24
    const val PROVIDER_AUTHORIZATION = 25
    const val NETWORK = 26
    const val TIMEOUT = 27
    const val PARSE = 28
    const val PERSISTENCE = 29
    const val EVENT_GAP = 30
    const val OUTCOME_UNKNOWN = 31
    const val ACTION_REJECTED = 32
    const val INTERNAL = 33
}

object WireRetryPolicy {
    const val UNKNOWN = 0
    const val DO_NOT_RETRY = 1
    const val SAFE_READ_RETRY = 2
    const val MANUAL_RETRY = 3
    const val REAUTHENTICATE = 4
    const val RENEGOTIATE = 5
    const val RESYNC = 6
}
