package dev.nacho.wibox.ui

import androidx.navigation.NavHostController
import dev.nacho.wibox.data.AppScenario
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class AppDestinationRequest(
    val inventoryId: String,
    val deviceId: String? = null,
    val mediaId: String? = null,
    val invitationId: String? = null,
    val callId: String? = null,
    val modify: Boolean = false,
    val mode: String = "device",
    val demoScenario: AppScenario? = null,
)

class AppNavigator {
    private var navController: NavHostController? = null
    private var mainTabHandler: ((MainTab) -> Unit)? = null
    private val route = MutableStateFlow(Routes.LOADING)
    val currentRoute: StateFlow<String> = route.asStateFlow()

    fun attach(controller: NavHostController) {
        navController = controller
        controller.addOnDestinationChangedListener { _, destination, arguments ->
            route.value = destination.route.orEmpty().let { pattern ->
                if (arguments == null || arguments.isEmpty) pattern else "$pattern?$arguments"
            }
        }
    }

    fun setMainTabHandler(handler: (MainTab) -> Unit) {
        mainTabHandler = handler
    }

    fun navigateInventory(id: String) {
        navigateInventory(AppDestinationRequest(id))
    }

    fun navigateInventory(request: AppDestinationRequest) {
        val deviceId = request.deviceId ?: "DEMO7410001"
        val mediaId = request.mediaId ?: "media-001"
        val route = when (request.inventoryId) {
            "loading", "cache" -> Routes.LOADING
            "main_tab", "tab_devices", "tab_events", "tab_event_list", "tab_gallery", "tab_settings" -> Routes.MAIN
            "add_type" -> Routes.ADD_TYPE
            "preview" -> Routes.preview(deviceId)
            "online_type" -> Routes.ONLINE_TYPE
            "product_intro" -> Routes.PRODUCT_INTRO
            "handle_wifi" -> Routes.HANDLE_WIFI
            "device_bind" -> Routes.DEVICE_BIND
            "online_devices" -> Routes.ONLINE_DEVICES
            "scan" -> Routes.SCAN
            "config" -> Routes.config(deviceId)
            "password_modify" -> Routes.passwordModify(deviceId, request.mode)
            "unlock_setting" -> Routes.unlockSetting(deviceId)
            "device_info" -> Routes.deviceInfo(deviceId)
            "about" -> Routes.ABOUT
            "image_pager" -> Routes.imagePager(mediaId)
            "video_player" -> Routes.videoPlayer(mediaId)
            "push_call" -> Routes.pushCall(request.callId ?: "demo-call-$deviceId")
            "debug" -> Routes.DEBUG
            "upgrade" -> Routes.upgrade(deviceId)
            "router" -> Routes.router(request.modify)
            "voice" -> Routes.VOICE
            "wifi_progress" -> Routes.WIFI_PROGRESS
            "share_device" -> Routes.shareDevice(request.invitationId ?: "invitation-1")
            "pairing_fail" -> Routes.PAIRING_FAIL
            "share_manage" -> Routes.shareManage(deviceId)
            "share_bind" -> Routes.SHARE_BIND
            else -> return
        }
        navController?.navigate(route) {
            launchSingleTop = true
            restoreState = true
        }
        when (request.inventoryId) {
            "tab_devices" -> mainTabHandler?.invoke(MainTab.DEVICES)
            "tab_events", "tab_event_list" -> mainTabHandler?.invoke(MainTab.EVENTS)
            "tab_gallery" -> mainTabHandler?.invoke(MainTab.GALLERY)
            "tab_settings" -> mainTabHandler?.invoke(MainTab.SETTINGS)
        }
    }

    fun goMain() {
        mainTabHandler?.invoke(MainTab.DEVICES)
        navController?.navigate(Routes.MAIN) {
            popUpTo(Routes.MAIN) { inclusive = false }
            launchSingleTop = true
        }
    }

    fun goLoading() {
        navController?.navigate(Routes.LOADING) {
            popUpTo(0) { inclusive = true }
        }
    }

    fun back(): Boolean = navController?.popBackStack() ?: false
}
