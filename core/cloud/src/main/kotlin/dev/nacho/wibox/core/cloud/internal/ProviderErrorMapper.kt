package dev.nacho.wibox.core.cloud.internal

import dev.nacho.wibox.core.cloud.CapabilityKey
import dev.nacho.wibox.core.cloud.CloudError
import dev.nacho.wibox.core.cloud.ProviderError
import kotlinx.coroutines.CancellationException

internal fun Throwable.toProviderError(operation: String, capability: CapabilityKey): Throwable = when (this) {
    is CancellationException -> this
    is ProviderError -> this
    is CloudError.MissingToken, is CloudError.Authentication -> ProviderError.Authentication(operation, capability)
    is CloudError.Authorization -> ProviderError.Authorization(operation, capability)
    is CloudError.Http -> ProviderError.Http(operation, capability, statusCode)
    is CloudError.Provider -> ProviderError.Unknown(operation, capability, providerCode, "Provider business error")
    is CloudError.Network -> ProviderError.Network(operation, capability)
    is CloudError.Timeout -> ProviderError.Timeout(operation, capability)
    is CloudError.Parse -> ProviderError.Parse(operation, capability)
    is CloudError.Persistence -> ProviderError.Persistence(operation, capability)
    is CloudError.InvalidInput -> ProviderError.InvalidInput(operation, capability)
    is CloudError.Unsupported -> ProviderError.Unsupported(operation, capability, reason)
    is CloudError.Closed -> ProviderError.Closed(operation)
    else -> ProviderError.Unknown(operation, capability)
}
