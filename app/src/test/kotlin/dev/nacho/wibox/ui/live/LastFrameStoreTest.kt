package dev.nacho.wibox.ui.live

import org.junit.Assert.assertEquals
import org.junit.Test

class LastFrameStoreTest {
    @Test
    fun frameFileNameKeepsDeviceIdsInsideTheStorageDirectory() {
        assertEquals("front_door.jpg", lastFrameFileName("front/door"))
        assertEquals("device.jpg", lastFrameFileName(""))
        assertEquals("___.jpg", lastFrameFileName("///"))
        assertEquals("plain-1_A.jpg", lastFrameFileName("plain-1_A"))
    }
}
