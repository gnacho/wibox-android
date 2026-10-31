package dev.nacho.wibox

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivityInstrumentedTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun activityStartsWithoutLoginAndExposesFourMainTabs() {
        compose.awaitTag("tab_devices")
        compose.tag("tab_devices").assertIsDisplayed()
        compose.tag("tab_events").assertIsDisplayed().performClick()
        compose.tag("tab_gallery").assertIsDisplayed().performClick()
        compose.tag("tab_settings").assertIsDisplayed().performClick()
        compose.awaitTag("settings_content")
        compose.tag("settings_content").assertIsDisplayed()
    }
}
