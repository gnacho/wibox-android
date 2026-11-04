package dev.nacho.wibox.core.cloud

import org.junit.Assert.assertEquals
import org.junit.Test

class DevicePermissionCodecTest {
    @Test
    fun encodeUsesTheStockOrder() {
        assertEquals(
            "1,3,4,1001,1002,1003",
            DevicePermissionCodec.encode(DevicePermission.entries.toSet()),
        )
        assertEquals("1,1001", DevicePermissionCodec.encode(setOf(DevicePermission.UNLOCK, DevicePermission.PREVIEW)))
        assertEquals("", DevicePermissionCodec.encode(emptySet()))
    }

    @Test
    fun decodeIgnoresUnknownValuesAndAcceptsAll() {
        assertEquals(
            setOf(DevicePermission.PREVIEW, DevicePermission.UNLOCK),
            DevicePermissionCodec.decode("1001,1,999"),
        )
        assertEquals(DevicePermission.entries.toSet(), DevicePermissionCodec.decode("all"))
    }
}
