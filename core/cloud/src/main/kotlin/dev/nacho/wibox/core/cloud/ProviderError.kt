package dev.nacho.wibox.core.cloud

enum class ProviderRetryPolicy {
    SAFE_READ_RETRY,
    MANUAL_RETRY,
    DO_NOT_RETRY,
    REAUTHENTICATE,
    RENEGOTIATE,
    RESYNC,
}

sealed class ProviderError(
    override val message: String,
    val operation: String,
    val capability: CapabilityKey? = null,
    val retryPolicy: ProviderRetryPolicy,
    val outcome: ActionOutcome? = null,
    val providerCode: Int? = null,
    val causeSafe: String? = null,
) : Exception(message) {
    class Authentication(operation: String, capability: CapabilityKey? = null) :
        ProviderError("Authentication failed", operation, capability, ProviderRetryPolicy.REAUTHENTICATE)

    class Authorization(operation: String, capability: CapabilityKey? = null) :
        ProviderError("Authorization failed", operation, capability, ProviderRetryPolicy.MANUAL_RETRY)

    class PlatformPermission(operation: String, capability: CapabilityKey? = null) :
        ProviderError("Platform permission required", operation, capability, ProviderRetryPolicy.MANUAL_RETRY)

    class NotFound(operation: String, capability: CapabilityKey? = null) :
        ProviderError("Resource not found", operation, capability, ProviderRetryPolicy.DO_NOT_RETRY)

    class Conflict(operation: String, capability: CapabilityKey? = null) :
        ProviderError("Provider conflict", operation, capability, ProviderRetryPolicy.MANUAL_RETRY)

    class InvitationUsed(operation: String, capability: CapabilityKey? = null) :
        ProviderError(
            "Invitation already used or linked",
            operation,
            capability,
            ProviderRetryPolicy.DO_NOT_RETRY,
            providerCode = 100151007,
        )

    class RateLimit(operation: String, capability: CapabilityKey? = null) :
        ProviderError("Provider rate limit", operation, capability, ProviderRetryPolicy.SAFE_READ_RETRY)

    class Http(operation: String, capability: CapabilityKey? = null, statusCode: Int) :
        ProviderError("HTTP $statusCode", operation, capability, ProviderRetryPolicy.MANUAL_RETRY, providerCode = statusCode)

    class Network(operation: String, capability: CapabilityKey? = null) :
        ProviderError("Network failure", operation, capability, ProviderRetryPolicy.SAFE_READ_RETRY)

    class Timeout(operation: String, capability: CapabilityKey? = null) :
        ProviderError("Provider timeout", operation, capability, ProviderRetryPolicy.SAFE_READ_RETRY)

    class Parse(operation: String, capability: CapabilityKey? = null) :
        ProviderError("Invalid or incomplete provider response", operation, capability, ProviderRetryPolicy.DO_NOT_RETRY)

    class Persistence(operation: String, capability: CapabilityKey? = null) :
        ProviderError("Provider persistence failure", operation, capability, ProviderRetryPolicy.MANUAL_RETRY)

    class InvalidInput(operation: String, capability: CapabilityKey? = null) :
        ProviderError("Invalid provider input", operation, capability, ProviderRetryPolicy.DO_NOT_RETRY)

    class Unsupported(operation: String, capability: CapabilityKey? = null, reason: String? = null) :
        ProviderError(
            "Unsupported provider operation${reason?.let { ": $it" } ?: ""}",
            operation,
            capability,
            ProviderRetryPolicy.DO_NOT_RETRY,
            causeSafe = reason,
        )

    class Unknown(operation: String, capability: CapabilityKey? = null, providerCode: Int? = null, causeSafe: String? = null) :
        ProviderError("Unknown provider error", operation, capability, ProviderRetryPolicy.MANUAL_RETRY, providerCode = providerCode, causeSafe = causeSafe)

    class Closed(operation: String) :
        ProviderError("Provider is closed", operation, retryPolicy = ProviderRetryPolicy.DO_NOT_RETRY)

    class EventGap(operation: String, capability: CapabilityKey? = null) :
        ProviderError("Event gap requires resynchronization", operation, capability, ProviderRetryPolicy.RESYNC)

    class UnknownOutcome(operation: String, capability: CapabilityKey? = null, causeSafe: String? = null) :
        ProviderError(
            "Physical action outcome is unknown",
            operation,
            capability,
            ProviderRetryPolicy.DO_NOT_RETRY,
            outcome = ActionOutcome.unknown(operation),
            causeSafe = causeSafe,
        )
}
