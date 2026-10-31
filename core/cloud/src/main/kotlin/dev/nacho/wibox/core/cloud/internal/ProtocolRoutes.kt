package dev.nacho.wibox.core.cloud.internal

internal object ProtocolRoutes {
    const val OAUTH_TOKEN = "/qvoauthv2/token"
    const val SHADOW_STATUS = "/openapi-tdk/device/status"
    const val SHADOW_STATUS_MULTI = "/openapi-tdk/device/status/multi"
    const val SHADOW_SUBSCRIBE = "/openapi-tdk/device/status/sub"
    const val SHADOW_UNSUBSCRIBE = "/openapi-tdk/device/status/unsub"
    const val DEVICE_STATE_FIELD = "Device_state"
    const val APP_UPGRADE_PATH = "/webs/version/app/version_check.jsp"
}
