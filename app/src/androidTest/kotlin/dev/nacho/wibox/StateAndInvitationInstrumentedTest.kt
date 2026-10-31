package dev.nacho.wibox

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.hasTestTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.nacho.wibox.data.AppScenario
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StateAndInvitationInstrumentedTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun loadingScenarioKeepsExplicitLoadingState() {
        launch(AppScenario.LOADING)
        compose.awaitTag("tab_devices")
        compose.tag("loading_content").assertIsDisplayed()
    }

    @Test
    fun emptyScenarioCoversDevicesEventsAndGallery() {
        launch(AppScenario.EMPTY)
        compose.awaitTag("empty_add_device")
        compose.tag("empty_add_device").assertIsDisplayed()
        compose.tag("tab_events").performClick()
        compose.tag("empty_content").assertIsDisplayed()
        compose.tag("tab_gallery").performClick()
        compose.tag("empty_content").assertIsDisplayed()
        compose.tag("tab_settings").performClick()
        compose.tag("settings_content").assertIsDisplayed()
    }

    @Test
    fun errorScenarioOffersManualRetry() {
        launch(AppScenario.ERROR)
        compose.awaitTag("error_content")
        compose.tag("retry_button").assertIsDisplayed().performClick()
        compose.tag("error_content").assertIsDisplayed()
    }

    @Test
    fun unsupportedScenarioNeverPresentsLivePreviewOrPhysicalSuccess() {
        launch(AppScenario.UNSUPPORTED)
        compose.awaitTag("device_list")
        compose.tag("device_preview_DEMO7410001").performClick()
        compose.awaitTag("preview_area")
        compose.onNodeWithText("Preview is unsupported", substring = true, useUnmergedTree = true).assertIsDisplayed()
        compose.tag("preview_unlock").assertIsNotEnabled()
        compose.tag("top_back").performClick()
        compose.tag("action_unlock_DEMO7410001").performClick()
        compose.waitUntil {
            compose.onAllNodesWithText("unsupported in local demo mode", substring = true, useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun invitationParsesBindsAndPersistsSharedDevice() {
        launch(AppScenario.NORMAL)
        compose.awaitTag("add_device_button")
        compose.tag("add_device_button").performClick()
        compose.tag("add_new_device").performClick()
        compose.tag("intro_scan_button").performClick()
        compose.tag("code_input").performTextInput(
            "https://example.invalid/invite?InviteCode=LOCAL&OwnerId=owner&OemId=G0021" +
                "&AppVersion=1&DeviceId=DEMO_SHARED_999&From=demo&AppId=2021",
        )
        compose.tag("validate_code_button").performClick()
        compose.awaitTag("screen_share_bind")
        compose.tag("bind_auth_code").assertIsNotEnabled()
        compose.tag("bind_submit").performClick()
        compose.awaitTag("device_list")
        compose.tag("device_list").performScrollToNode(hasTestTag("device_card_DEMO_SHARED_999"))
        compose.tag("device_card_DEMO_SHARED_999").assertIsDisplayed()
    }

    private fun launch(scenario: AppScenario) {
        compose.setContent { WiboxApp(buildRepository(scenario)) }
    }
}
