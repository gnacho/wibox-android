package dev.nacho.wibox.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RoutesTest {
    @Test
    fun everyReachableInventoryScreenHasARouteOrDocumentedRepresentation() {
        val expected = setOf(
            "loading", "cache", "main_tab", "add_type", "preview", "online_type",
            "product_intro", "handle_wifi", "device_bind", "online_devices", "scan",
            "config", "password_modify", "unlock_setting", "device_info", "about",
            "image_pager", "video_player", "push_call", "debug", "upgrade", "router",
            "voice", "wifi_progress", "share_device", "pairing_fail", "share_manage",
            "share_bind", "tab_devices", "tab_events", "tab_event_list", "tab_gallery",
            "tab_settings",
        )
        assertEquals(expected, inventoryDestinations.keys)
        assertEquals(33, inventoryDestinations.size)
    }

    @Test
    fun legacyScreensWithoutInboundNavigationAreNotLinked() {
        omittedLegacyScreens.forEach { legacy ->
            assertFalse(inventoryDestinations.containsKey(legacy))
        }
        assertTrue(omittedLegacyScreens.contains("wifi_set_legacy"))
    }

    @Test
    fun deviceAndMediaHelpersKeepIdsInsideTheirExpectedRoutes() {
        assertEquals("preview/DEMO_001", Routes.preview("DEMO_001"))
        assertEquals("config/DEMO_001", Routes.config("DEMO_001"))
        assertEquals("password_modify/DEMO_001/unlock", Routes.passwordModify("DEMO_001", "unlock"))
        assertEquals("router?modify=true", Routes.router(true))
        assertEquals("image_pager/media-001", Routes.imagePager("media-001"))
        assertEquals("push_call/DEMO_001", Routes.pushCall("DEMO_001"))
    }
}
