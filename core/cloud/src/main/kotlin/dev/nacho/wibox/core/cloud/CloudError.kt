package dev.nacho.wibox.core.cloud

sealed class CloudError(override val message: String) : Exception(message) {
    class MissingToken : CloudError("No usable OAuth token is available")

    data class Authentication(val operation: String) :
        CloudError("Authentication failed during $operation")

    data class Authorization(val operation: String) :
        CloudError("Authorization failed during $operation")

    data class Http(val operation: String, val statusCode: Int) :
        CloudError("HTTP $statusCode during $operation")

    data class Provider(val operation: String, val providerCode: Int) :
        CloudError("Provider error $providerCode during $operation")

    data class Network(val operation: String) :
        CloudError("Network failure during $operation")

    data class Timeout(val operation: String) :
        CloudError("Timeout during $operation")

    data class Parse(val operation: String) :
        CloudError("Invalid or incomplete response during $operation")

    data class Persistence(val operation: String) :
        CloudError("Token persistence failure during $operation")

    data class InvalidInput(val operation: String) :
        CloudError("Invalid input for $operation")

    data class Unsupported(val operation: String, val reason: String) :
        CloudError("Unsupported operation $operation: $reason")

    class Closed : CloudError("The cloud client is closed")
}
