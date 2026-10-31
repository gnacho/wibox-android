package dev.nacho.wibox.externalapi.sdk

interface WireEnum {
    val wireValue: Int
}

internal inline fun <reified T> enumFromWire(value: Int): T where T : Enum<T>, T : WireEnum =
    enumValues<T>().firstOrNull { it.wireValue == value } ?: enumValues<T>().first { it.name == "UNKNOWN" }

enum class AuthorizationState(override val wireValue: Int) : WireEnum {
    UNKNOWN(0),
    NOT_REQUESTED(1),
    PENDING(2),
    AUTHORIZED(3),
    DENIED(4),
    REVOKED(5),
    SIGNATURE_CHANGED(6),
    UID_CHANGED(7),
    SHARED_UID_UNSUPPORTED(8)
}

enum class ConnectionKind(override val wireValue: Int) : WireEnum {
    UNKNOWN(0),
    STOCK(1),
    MQTT(2),
    SELF_HOSTED(3),
    DEMO(4)
}

enum class PreparationState(override val wireValue: Int) : WireEnum {
    UNKNOWN(0),
    IDLE(1),
    PREPARING(2),
    READY(3),
    DEGRADED(4),
    AUTHENTICATION_REQUIRED(5),
    FAILED(6),
    CLOSED(7)
}

enum class SessionState(override val wireValue: Int) : WireEnum {
    UNKNOWN(0),
    UNAVAILABLE(1),
    AUTHENTICATION_REQUIRED(2),
    PREPARING(3),
    READY(4),
    DEGRADED(5),
    CLOSED(6)
}

enum class CapabilityScope(override val wireValue: Int) : WireEnum {
    UNKNOWN(0),
    PROVIDER(1),
    ACCOUNT(2),
    DEVICE(3),
    CHANNEL(4),
    SESSION(5)
}

enum class Support(override val wireValue: Int) : WireEnum {
    UNKNOWN(0),
    REQUIRED(1),
    OPTIONAL(2),
    UNSUPPORTED(3)
}

enum class Availability(override val wireValue: Int) : WireEnum {
    UNKNOWN(0),
    AVAILABLE(1),
    UNSUPPORTED(2),
    DEMO_ONLY(3)
}

enum class DeviceRole(override val wireValue: Int) : WireEnum {
    UNKNOWN(0),
    OWNER(1),
    SHARED(2)
}

enum class Presence(override val wireValue: Int) : WireEnum {
    UNKNOWN(0),
    ONLINE(1),
    OFFLINE(2)
}

enum class DataSource(override val wireValue: Int) : WireEnum {
    UNKNOWN(0),
    CACHE(1),
    CLOUD(2),
    LOCAL(3),
    MERGED(4)
}

enum class Freshness(override val wireValue: Int) : WireEnum {
    UNKNOWN(0),
    ANY(1),
    CACHED(2),
    FRESH(3)
}

enum class ExtensionValueType(override val wireValue: Int) : WireEnum {
    UNKNOWN(0),
    BOOLEAN(1),
    INTEGER(2),
    DECIMAL(3),
    STRING(4)
}

enum class EventType(override val wireValue: Int) : WireEnum {
    UNKNOWN(0),
    MOTION(1),
    INCOMING_CALL(2),
    CALL_ANSWERED(3)
}

enum class ProviderOrigin(override val wireValue: Int) : WireEnum {
    UNKNOWN(0),
    STOCK(1),
    MQTT(2),
    SELF_HOSTED(3),
    DEMO(4)
}

enum class GapReason(override val wireValue: Int) : WireEnum {
    UNKNOWN(0),
    OVERFLOW(1),
    CURSOR_INVALID(2),
    DISCONNECTED(3),
    RESYNC_REQUIRED(4)
}

enum class GapRecoverability(override val wireValue: Int) : WireEnum {
    UNKNOWN(0),
    AVAILABLE(1),
    UNAVAILABLE(2)
}

enum class EventStreamState(override val wireValue: Int) : WireEnum {
    UNKNOWN(0),
    CONNECTED(1),
    RECONNECTING(2),
    RESYNC_REQUIRED(3),
    CLOSED(4),
    UNSUPPORTED(5)
}

enum class ActionType(override val wireValue: Int) : WireEnum {
    UNKNOWN(0),
    UNLOCK(1),
    F1(2),
    CALL_GUARD(3),
    CALL_ACCEPT(4),
    CALL_REJECT(5),
    START_CALL(6),
    STOP_CALL(7)
}

enum class AckLevel(override val wireValue: Int) : WireEnum {
    UNKNOWN(0),
    LOCAL_ACCEPTED(1),
    PROVIDER_ACCEPTED(2),
    DEVICE_ACKNOWLEDGED(3),
    PHYSICAL_OBSERVED(4),
    UNKNOWN_OUTCOME(5),
    REJECTED(6)
}

enum class CancellationState(override val wireValue: Int) : WireEnum {
    UNKNOWN(0),
    CANCELLED_BEFORE_DISPATCH(1),
    CANCELLATION_REQUESTED(2),
    ALREADY_COMPLETED(3),
    NOT_FOUND(4),
    OUTCOME_UNKNOWN(5)
}

enum class UpgradeState(override val wireValue: Int) : WireEnum {
    UNKNOWN(0),
    UP_TO_DATE(1),
    AVAILABLE(2)
}

enum class ApiErrorCode(override val wireValue: Int) : WireEnum {
    UNKNOWN(0),
    NOT_AUTHORIZED(1),
    AUTHORIZATION_PENDING(2),
    AUTHORIZATION_REVOKED(3),
    AUTHORIZATION_DENIED(4),
    PACKAGE_UID_MISMATCH(5),
    SIGNATURE_CHANGED(6),
    UID_CHANGED(7),
    SHARED_UID_UNSUPPORTED(8),
    HANDSHAKE_REQUIRED(9),
    VERSION_UNSUPPORTED(10),
    UNSUPPORTED(11),
    CAPABILITY_UNKNOWN(12),
    SESSION_REQUIRED(13),
    PROVIDER_NOT_READY(14),
    NOT_FOUND(15),
    CONFLICT(16),
    INVALID_INPUT(17),
    PAYLOAD_TOO_LARGE(18),
    RATE_LIMITED(19),
    BUSY(20),
    CANCELLED(21),
    SERVICE_DIED(22),
    CALLBACK_DEAD(23),
    AUTHENTICATION(24),
    PROVIDER_AUTHORIZATION(25),
    NETWORK(26),
    TIMEOUT(27),
    PARSE(28),
    PERSISTENCE(29),
    EVENT_GAP(30),
    OUTCOME_UNKNOWN(31),
    ACTION_REJECTED(32),
    INTERNAL(33)
}

enum class RetryPolicy(override val wireValue: Int) : WireEnum {
    UNKNOWN(0),
    DO_NOT_RETRY(1),
    SAFE_READ_RETRY(2),
    MANUAL_RETRY(3),
    REAUTHENTICATE(4),
    RENEGOTIATE(5),
    RESYNC(6)
}
