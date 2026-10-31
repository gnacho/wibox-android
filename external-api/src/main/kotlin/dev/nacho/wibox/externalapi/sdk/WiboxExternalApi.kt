package dev.nacho.wibox.externalapi.sdk

object WiboxExternalApi {
    const val WIRE_MAJOR = 1
    const val WIRE_MINOR = 0
    const val SDK_VERSION = "1.0.0"
    const val SERVICE_PACKAGE = "dev.nacho.wibox"
    const val SERVICE_CLASS = "dev.nacho.wibox.externalapi.WiboxExternalApiService"
    const val BIND_ACTION = "dev.nacho.wibox.externalapi.BIND"
}

class WiboxApiException(val error: ApiError) : Exception(error.safeMessage ?: "Wibox API error: ${error.code}")

internal fun sdkError(
    code: ApiErrorCode,
    retryPolicy: RetryPolicy,
    message: String,
    outcome: AckLevel? = null,
): WiboxApiException = WiboxApiException(
    ApiError(
        code = code,
        retryPolicy = retryPolicy,
        capabilityKey = null,
        outcome = outcome,
        safeMessage = message,
        retryAfterMs = null,
        providerCode = null,
    ),
)
