package dev.nacho.wibox.core.cloud

data class AppUpgradeRequest(
    val baseUrl: String,
    val currentVersion: Int,
    val clientId: String,
    val acceptLanguage: String = "en-US",
) {
    override fun toString(): String =
        "AppUpgradeRequest(baseUrl=<redacted>, currentVersion=$currentVersion, " +
            "clientId=<redacted>, acceptLanguage=$acceptLanguage)"
}

data class AppUpgradeInfo(
    val flag: String,
    val result: Int,
    val priority: Int,
    val version: String,
    val url: String,
    val description: String,
    val configVersion: Int,
    val configUrl: String,
    val configPicture: String,
)
