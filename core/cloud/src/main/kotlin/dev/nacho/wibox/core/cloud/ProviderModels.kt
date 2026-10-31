package dev.nacho.wibox.core.cloud

enum class ProviderId(val id: String) {
    STOCK_QVCLOUD("stock-qvcloud"),
    MQTT("mqtt"),
    SELF_HOSTED("self-hosted"),
    DEMO("demo"),
}

enum class ProviderConnectionKind {
    STOCK,
    MQTT,
    SELF_HOSTED,
    DEMO,
}

enum class ProviderExtension {
    OPAQUE_ACCOUNT,
    DYNAMIC_DEVICE_CREDENTIALS,
    QVCLOUD_SHADOW,
    FCM_ALARM,
    INVITATION_PERMISSIONS,
    P2P_MEDIA,
}

data class ProviderDescriptor(
    val id: ProviderId,
    val profileVersion: String,
    val connectionKind: ProviderConnectionKind,
    val extensions: Set<ProviderExtension> = emptySet(),
)

enum class ProviderEnvironment {
    PRODUCTION,
    STAGING,
    TEST,
}

data class ProviderSessionContext(
    val installationId: String,
    val accountAlias: String? = null,
    val environment: ProviderEnvironment = ProviderEnvironment.PRODUCTION,
) {
    override fun toString(): String =
        "ProviderSessionContext(installationId=<redacted>, accountAlias=<redacted>, environment=$environment)"
}

enum class SessionState {
    RESTORED,
    AUTHENTICATED,
    REFRESHED,
    CLOSED_LOCAL,
}

data class SessionHandle(
    val provider: ProviderId,
    val installationId: String,
    val accountAlias: String?,
    val environment: ProviderEnvironment,
    val state: SessionState,
    val opaqueId: String,
    val validUntilMillis: Long,
) {
    override fun toString(): String =
        "SessionHandle(provider=$provider, installationId=<redacted>, accountAlias=<redacted>, " +
            "environment=$environment, state=$state, opaqueId=<redacted>, validUntilMillis=$validUntilMillis)"
}

class CredentialMaterial(
    val identifier: String,
    val secret: String,
) {
    override fun toString(): String = "CredentialMaterial(identifier=<redacted>, secret=<redacted>)"
}

enum class PreparationState {
    IDLE,
    PREPARING,
    READY,
    DEGRADED,
    AUTHENTICATION_REQUIRED,
    FAILED,
    CLOSED,
}
