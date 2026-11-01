package dev.nacho.wibox

import android.graphics.Bitmap
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import dev.nacho.wibox.data.AppScenario
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File

/**
 * Captures every implemented visual surface from the running app on the emulator.
 * Screenshots are taken with UiAutomator into the target app external files dir,
 * then pulled with adb and hashed by the analysis manifest builder.
 */
class CaptureSurfacesTest {

    @get:Rule
    val compose = createComposeRule()

    private lateinit var device: UiDevice
    private lateinit var captureDir: File

    @Before
    fun setUp() {
        device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        captureDir = File(
            InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null),
            "captures",
        )
        captureDir.mkdirs()
    }

    private fun launch(scenario: AppScenario = AppScenario.NORMAL) {
        compose.setContent { WiboxApp(buildRepository(scenario)) }
    }

    private fun captureSurface(name: String) {
        // Semantics settle before the pixels do: without this the screenshot can
        // capture the frame of the previous screen.
        compose.waitForIdle()
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        device.waitForIdle(500)
        Thread.sleep(400)
        val file = File(captureDir, "CAP-$name-360-font1-light-demo.png")
        val saved = device.takeScreenshot(file)
        assertTrue("screenshot not saved: $file", saved)
        println("CAPTURED $name")
    }

    @Test
    fun captureMainTabs() {
        launch()
        compose.awaitTag("device_list")
        captureSurface("main-devices")
        compose.tag("tab_events").performClick()
        compose.awaitTag("event_list")
        captureSurface("main-events")
        compose.tag("tab_gallery").performClick()
        compose.awaitTag("gallery_grid")
        captureSurface("main-gallery")
        compose.tag("tab_settings").performClick()
        compose.awaitTag("settings_content")
        captureSurface("main-settings")
    }

    @Test
    fun captureSessionLoading() {
        launch(AppScenario.LOADING)
        compose.awaitTag("loading_content")
        captureSurface("session-loading")
    }

    @Test
    fun capturePairingCoreFlow() {
        launch()
        compose.awaitTag("device_list")
        compose.tag("add_device_button").performClick()
        compose.awaitTag("screen_add_type")
        captureSurface("add-type")
        compose.tag("add_new_device").performClick()
        compose.awaitTag("screen_product_intro")
        captureSurface("product-intro")
        compose.tag("intro_scan_button").performClick()
        compose.awaitTag("screen_scan")
        captureSurface("scan")
        compose.tag("code_input").performTextInput("WIBOX-AP DEMO_NEW_001 authCode 7410")
        compose.tag("validate_code_button").performClick()
        compose.awaitTag("router_ap_next")
        captureSurface("router")
        compose.tag("router_ap_next").performClick()
        compose.awaitTag("screen_wifi_progress")
        captureSurface("wifi-progress")
        compose.awaitEnabled("wifi_next")
        compose.tag("wifi_next").performClick()
        compose.awaitTag("screen_device_bind")
        captureSurface("device-bind")
    }

    @Test
    fun captureOnlineTypeAndDevices() {
        launch()
        compose.awaitTag("device_list")
        compose.tag("add_device_button").performClick()
        compose.awaitTag("screen_add_type")
        compose.tag("add_online_device").performClick()
        compose.awaitTag("screen_online_type")
        captureSurface("online-type")
        compose.tag("scan_online_button").performClick()
        compose.awaitTag("screen_online_devices")
        captureSurface("online-devices")
    }

    @Test
    fun captureHandleWifi() {
        launch()
        compose.awaitTag("device_list")
        compose.tag("add_device_button").performClick()
        compose.tag("add_new_device").performClick()
        compose.tag("intro_scan_button").performClick()
        compose.tag("code_input").performTextInput("WIBOX-AP DEMO_NEW_001 authCode 7410")
        compose.tag("validate_code_button").performClick()
        compose.awaitTag("router_ap_next")
        compose.tag("router_ap_next").performClick()
        compose.awaitTag("screen_wifi_progress")
        compose.tag("wifi_manual_button").performClick()
        compose.awaitTag("screen_handle_wifi")
        captureSurface("handle-wifi")
    }

    @Test
    fun captureVoiceProvisioning() {
        launch()
        compose.awaitTag("device_list")
        compose.tag("add_device_button").performClick()
        compose.tag("add_new_device").performClick()
        compose.tag("intro_scan_button").performClick()
        compose.tag("code_input").performTextInput("WIBOX-AP DEMO_NEW_001 authCode 7410")
        compose.tag("validate_code_button").performClick()
        compose.awaitTag("router_voice_next")
        compose.tag("router_voice_next").performClick()
        compose.awaitTag("screen_voice")
        captureSurface("voice")
    }

    @Test
    fun capturePairingFailure() {
        launch()
        compose.awaitTag("device_list")
        compose.tag("add_device_button").performClick()
        compose.tag("add_new_device").performClick()
        compose.tag("intro_scan_button").performClick()
        compose.tag("code_input").performTextInput("WIBOX-AP DEMO_NEW_001 authCode 7410")
        compose.tag("validate_code_button").performClick()
        compose.awaitTag("router_ap_next")
        compose.tag("router_ap_next").performClick()
        compose.awaitTag("screen_wifi_progress")
        compose.tag("simulate_pairing_failure").performClick()
        compose.awaitTag("screen_pairing_fail")
        captureSurface("pairing-fail")
    }

    @Test
    fun captureShareBind() {
        launch()
        compose.awaitTag("device_list")
        compose.tag("add_device_button").performClick()
        compose.tag("add_new_device").performClick()
        compose.tag("intro_scan_button").performClick()
        compose.tag("code_input").performTextInput(
            "https://example.invalid/invite?InviteCode=LOCAL&OwnerId=owner&OemId=G0021" +
                "&AppVersion=1&DeviceId=DEMO_SHARED_999&From=demo&AppId=2021",
        )
        compose.tag("validate_code_button").performClick()
        compose.awaitTag("screen_share_bind")
        captureSurface("share-bind")
    }

    @Test
    fun captureDeviceFlow() {
        launch()
        compose.awaitTag("device_list")
        compose.tag("device_preview_DEMO7410001").performClick()
        compose.awaitTag("preview_area")
        captureSurface("preview")
        compose.tag("preview_panels").performClick()
        captureSurface("preview-panels")
        compose.tag("top_back").performClick()
        compose.awaitTag("device_list")
        compose.tag("device_config_DEMO7410001").performClick()
        compose.awaitTag("screen_config")
        compose.awaitTag("config_info", 60000)
        captureSurface("config")
        compose.tag("config_scroll").performScrollToNode(hasTestTag("config_info"))
        compose.tag("config_info").performClick()
        compose.awaitTag("screen_device_info")
        captureSurface("device-info")
        compose.tag("top_back").performClick()
        compose.awaitTag("screen_config")
        compose.tag("config_scroll").performScrollToNode(hasTestTag("config_password"))
        compose.tag("config_password").performClick()
        compose.awaitTag("screen_password_modify")
        captureSurface("password-modify")
        compose.tag("top_back").performClick()
        compose.awaitTag("screen_config")
        compose.tag("config_scroll").performScrollToNode(hasTestTag("config_unlock_setting"))
        compose.tag("config_unlock_setting").performClick()
        compose.awaitTag("screen_unlock_setting")
        captureSurface("unlock-setting")
        compose.tag("top_back").performClick()
        compose.awaitTag("screen_config")
        compose.tag("config_scroll").performScrollToNode(hasTestTag("config_upgrade"))
        compose.tag("config_upgrade").performClick()
        compose.awaitTag("screen_upgrade")
        captureSurface("upgrade")
        compose.tag("top_back").performClick()
        compose.awaitTag("screen_config")
        compose.tag("config_scroll").performScrollToNode(hasTestTag("config_share"))
        compose.tag("config_share").performClick()
        compose.awaitTag("screen_share_manage")
        captureSurface("share-manage")
        compose.tag("share_create_invitation").performClick()
        compose.awaitTag("invitation_save", 30000)
        compose.tag("invitation_save").performClick()
        compose.awaitTag("screen_share_device")
        captureSurface("share-device")
    }

    @Test
    fun captureMediaFlow() {
        launch()
        compose.awaitTag("device_list")
        compose.tag("tab_gallery").performClick()
        compose.awaitTag("gallery_grid")
        compose.tag("media_media-002").performClick()
        compose.awaitTag("screen_image_pager")
        captureSurface("image-pager")
        compose.tag("open_video_player").performClick()
        compose.awaitTag("screen_video_player")
        captureSurface("video-player")
    }

    @Test
    fun captureCallStates() {
        launch()
        compose.awaitTag("device_list")
        compose.tag("tab_events").performClick()
        compose.awaitTag("event_list")
        compose.tag("event_event-001").performClick()
        compose.awaitTag("screen_push_call")
        captureSurface("push-call-ringing")
        compose.tag("call_accept").performClick()
        compose.awaitTag("call_pending")
        captureSurface("push-call-accepted")
    }

    @Test
    fun captureAboutAndDebug() {
        launch()
        compose.awaitTag("device_list")
        compose.tag("tab_settings").performClick()
        compose.tag("about_button").performClick()
        compose.awaitTag("screen_about")
        captureSurface("about")
        repeat(5) { compose.tag("about_app_name").performClick() }
        compose.awaitTag("screen_debug")
        captureSurface("debug")
    }
}
