package dev.nacho.wibox.core.cloud

data class WiboxCloudConfig(
    val baseUrl: String,
    val clientId: String,
    val regionId: String,
    val clientType: Int = 3,
    val oemId: String = "G0021",
    val appId: Int = 2021,
    val clientFlag: Int = 2,
    val requestTimeoutMillis: Long = 20_000,
    val connectTimeoutMillis: Long = 20_000,
    val socketTimeoutMillis: Long = 20_000,
)

class CloudCredentials(
    val username: String,
    val password: String,
) {
    override fun toString(): String = "CloudCredentials(username=<redacted>, password=<redacted>)"
}

data class CloudSession(
    val accessToken: String,
    val refreshToken: String,
    val accessTokenExpiresAtMillis: Long,
    val refreshTokenExpiresAtMillis: Long,
) {
    override fun toString(): String =
        "CloudSession(accessToken=<redacted>, refreshToken=<redacted>, " +
            "accessTokenExpiresAtMillis=$accessTokenExpiresAtMillis, " +
            "refreshTokenExpiresAtMillis=$refreshTokenExpiresAtMillis)"
}

fun interface CloudClock {
    fun nowMillis(): Long
}

interface TokenStore {
    suspend fun load(): CloudSession?
    suspend fun save(session: CloudSession)
    suspend fun clear()
}
