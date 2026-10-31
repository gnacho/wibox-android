package dev.nacho.wibox

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.hasTestTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LocalFlowInstrumentedTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun startupTabsPreviewActionAndQrAddWorkEndToEndLocally() {
        compose.setContent { WiboxApp(buildRepository()) }
        compose.awaitTag("device_list")
        compose.tag("device_list").assertIsDisplayed()
        compose.tag("action_unlock_DEMO7410001").performClick()
        compose.waitUntil {
            compose.onAllNodesWithText("Local demo acknowledgement only", substring = true, useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        compose.tag("device_preview_DEMO7410001").performClick()
        compose.awaitTag("preview_area")
        compose.awaitEnabled("preview_unlock")
        compose.tag("preview_unlock").assertIsEnabled().performClick()
        compose.awaitTag("preview_action_result")
        compose.tag("preview_snapshot").performClick()
        compose.tag("top_back").performClick()
        compose.awaitTag("add_device_button")
        compose.tag("add_device_button").performClick()
        compose.tag("add_new_device").performClick()
        compose.tag("intro_scan_button").performClick()
        compose.tag("code_input").performTextInput("WIBOX-AP DEMO_NEW_001 authCode 7410")
        compose.tag("validate_code_button").performClick()
        compose.awaitTag("router_ap_next")
        compose.tag("router_ap_next").performClick()
        compose.awaitEnabled("wifi_next")
        compose.tag("wifi_next").assertIsEnabled().performClick()
        compose.awaitTag("bind_submit")
        compose.tag("bind_submit").performClick()
        compose.awaitTag("device_list")
        compose.tag("device_list").performScrollToNode(hasTestTag("device_card_DEMO_NEW_001"))
        compose.tag("device_card_DEMO_NEW_001").assertIsDisplayed()
        compose.tag("tab_events").performClick()
        compose.awaitTag("event_list")
        compose.tag("tab_gallery").performClick()
        compose.awaitTag("gallery_grid")
        compose.tag("tab_settings").performClick()
        compose.awaitTag("settings_content")
        compose.tag("scenario_selector").assertIsDisplayed()
    }
}
