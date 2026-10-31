package dev.nacho.wibox.ui

object Graphs {
    const val SESSION = "session"
    const val MAIN = "main_graph"
    const val PAIRING = "pairing_graph"
    const val DEVICE = "device_graph"
    const val SHARING = "sharing_graph"
    const val MEDIA = "media_graph"
    const val SYSTEM = "system_graph"
}

object Routes {
    const val LOADING = "loading"
    const val MAIN = "main"
    const val ADD_TYPE = "add_type"
    const val PRODUCT_INTRO = "product_intro"
    const val SCAN = "scan"
    const val ONLINE_TYPE = "online_type"
    const val ONLINE_DEVICES = "online_devices"
    const val DEVICE_BIND = "device_bind"
    const val SHARE_BIND = "share_bind"
    const val HANDLE_WIFI = "handle_wifi"
    const val ROUTER = "router?modify={modify}"
    const val VOICE = "voice"
    const val WIFI_PROGRESS = "wifi_progress"
    const val PAIRING_FAIL = "pairing_fail"
    const val PREVIEW = "preview/{deviceId}"
    const val CONFIG = "config/{deviceId}"
    const val PASSWORD_MODIFY = "password_modify/{deviceId}/{mode}"
    const val UNLOCK_SETTING = "unlock_setting/{deviceId}"
    const val DEVICE_INFO = "device_info/{deviceId}"
    const val UPGRADE = "upgrade/{deviceId}"
    const val SHARE_MANAGE = "share_manage/{deviceId}"
    const val SHARE_DEVICE = "share_device/{invitationId}"
    const val ABOUT = "about"
    const val DEBUG = "debug"
    const val IMAGE_PAGER = "image_pager/{mediaId}"
    const val VIDEO_PLAYER = "video_player/{mediaId}"
    const val PUSH_CALL = "push_call/{callId}"

    fun preview(deviceId: String) = "preview/$deviceId"
    fun config(deviceId: String) = "config/$deviceId"
    fun passwordModify(deviceId: String, mode: String = "device") = "password_modify/$deviceId/$mode"
    fun unlockSetting(deviceId: String) = "unlock_setting/$deviceId"
    fun deviceInfo(deviceId: String) = "device_info/$deviceId"
    fun upgrade(deviceId: String) = "upgrade/$deviceId"
    fun shareManage(deviceId: String) = "share_manage/$deviceId"
    fun shareDevice(invitationId: String) = "share_device/$invitationId"
    fun imagePager(mediaId: String) = "image_pager/$mediaId"
    fun videoPlayer(mediaId: String) = "video_player/$mediaId"
    fun pushCall(callId: String) = "push_call/$callId"
    fun pushCallForDevice(deviceId: String) = pushCall("demo-call-$deviceId")
    fun router(modify: Boolean = false) = "router?modify=$modify"
}

val inventoryDestinations: Map<String, String> = mapOf(
    "loading" to Routes.LOADING,
    "cache" to Routes.LOADING,
    "main_tab" to Routes.MAIN,
    "add_type" to Routes.ADD_TYPE,
    "preview" to Routes.PREVIEW,
    "online_type" to Routes.ONLINE_TYPE,
    "product_intro" to Routes.PRODUCT_INTRO,
    "handle_wifi" to Routes.HANDLE_WIFI,
    "device_bind" to Routes.DEVICE_BIND,
    "online_devices" to Routes.ONLINE_DEVICES,
    "scan" to Routes.SCAN,
    "config" to Routes.CONFIG,
    "password_modify" to Routes.PASSWORD_MODIFY,
    "unlock_setting" to Routes.UNLOCK_SETTING,
    "device_info" to Routes.DEVICE_INFO,
    "about" to Routes.ABOUT,
    "image_pager" to Routes.IMAGE_PAGER,
    "video_player" to Routes.VIDEO_PLAYER,
    "push_call" to Routes.PUSH_CALL,
    "debug" to Routes.DEBUG,
    "upgrade" to Routes.UPGRADE,
    "router" to Routes.ROUTER,
    "voice" to Routes.VOICE,
    "wifi_progress" to Routes.WIFI_PROGRESS,
    "share_device" to Routes.SHARE_DEVICE,
    "pairing_fail" to Routes.PAIRING_FAIL,
    "share_manage" to Routes.SHARE_MANAGE,
    "share_bind" to Routes.SHARE_BIND,
    "tab_devices" to Routes.MAIN,
    "tab_events" to Routes.MAIN,
    "tab_event_list" to Routes.MAIN,
    "tab_gallery" to Routes.MAIN,
    "tab_settings" to Routes.MAIN,
)

val omittedLegacyScreens = setOf(
    "guide",
    "product_choose",
    "add_method_legacy",
    "wifi_list_legacy",
    "wifi_set_legacy",
)
