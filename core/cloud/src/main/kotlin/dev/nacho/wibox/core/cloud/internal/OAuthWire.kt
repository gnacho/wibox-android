package dev.nacho.wibox.core.cloud.internal

import dev.nacho.wibox.core.cloud.CloudError
import dev.nacho.wibox.core.cloud.CloudSession
import java.util.Base64
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.jsonPrimitive

@Serializable
internal data class OAuthTokenDto(
    val access_token: String? = null,
    val refresh_token: String? = null,
)

internal object OAuthWire {
    val json: Json = Json { ignoreUnknownKeys = true }

    fun session(body: String, operation: String): CloudSession {
        val dto = try {
            json.decodeFromString<OAuthTokenDto>(body)
        } catch (_: Exception) {
            throw CloudError.Parse(operation)
        }
        val access = dto.access_token?.takeIf { it.isNotBlank() } ?: throw CloudError.Parse(operation)
        val refresh = dto.refresh_token?.takeIf { it.isNotBlank() } ?: throw CloudError.Parse(operation)
        return CloudSession(
            accessToken = access,
            refreshToken = refresh,
            accessTokenExpiresAtMillis = expiry(access, operation),
            refreshTokenExpiresAtMillis = expiry(refresh, operation),
        )
    }

    private fun expiry(token: String, operation: String): Long {
        try {
            val parts = token.split('.')
            require(parts.size == 3)
            val payload = String(Base64.getUrlDecoder().decode(parts[1]), Charsets.UTF_8)
            val element = json.parseToJsonElement(payload) as? JsonObject
            val expires = element?.get("exp")?.jsonPrimitive?.longOrNull
            require(expires != null && expires > 0)
            return Math.multiplyExact(expires, 1_000L)
        } catch (_: Exception) {
            throw CloudError.Parse(operation)
        }
    }
}
