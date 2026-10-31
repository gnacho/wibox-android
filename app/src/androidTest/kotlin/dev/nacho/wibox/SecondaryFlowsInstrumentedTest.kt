package dev.nacho.wibox

import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SecondaryFlowsInstrumentedTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun callGalleryVideoAboutAndDebugRemainReachable() {
        launch()
        compose.awaitTag("tab_events")
        compose.tag("tab_events").performClick()
        compose.awaitTag("event_event-001")
        compose.tag("event_event-001").performClick()
        compose.awaitTag("screen_push_call")
        compose.tag("call_accept").performClick()
        compose.awaitTag("event_list")
        compose.tag("tab_gallery").performClick()
        compose.awaitTag("media_media-002")
        compose.tag("media_media-002").performClick()
        compose.awaitTag("screen_image_pager")
        compose.tag("open_video_player").performClick()
        compose.awaitTag("screen_video_player")
        compose.tag("video_play_pause").performClick()
        compose.tag("video_mute").performClick()
        compose.tag("top_back").performClick()
        compose.tag("top_back").performClick()
        compose.tag("tab_settings").performClick()
        compose.tag("about_button").performClick()
        compose.awaitTag("about_app_name")
        repeat(5) { compose.tag("about_app_name").performClick() }
        compose.awaitTag("screen_debug")
        compose.tag("debug_restart").assertExists()
    }

    @Test
    fun configurationCreatesSharesAndDeletesLocalInvitation() {
        launch()
        compose.awaitTag("device_config_DEMO7410001")
        compose.tag("device_config_DEMO7410001").performClick()
        compose.awaitTag("config_share", 30000)
        compose.tag("config_scroll").performScrollToNode(hasTestTag("config_share"))
        compose.tag("config_share").performClick()
        compose.awaitTag("screen_share_manage", 30000)
        compose.tag("share_create_invitation").performClick()
        compose.awaitTag("screen_share_device", 30000)
        compose.tag("share_link_button").assertIsDisplayed()
        compose.tag("top_back").performClick()
        compose.awaitTag("invitation_delete_invitation-1")
        compose.tag("invitation_delete_invitation-1").performClick()
        compose.tag("dialog_confirm").performClick()
        compose.awaitTag("empty_content")
        compose.awaitTag("share_create_invitation")
    }

    private fun launch() {
        compose.setContent { WiboxApp(buildRepository()) }
    }
}
