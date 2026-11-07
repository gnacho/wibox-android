package dev.nacho.wibox.ui.live

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LastFrameStoreTest {

    private fun temporaryDirectory(): File = Files.createTempDirectory("wibox-frames").toFile()

    @Test
    fun frameFileNameKeepsDeviceIdsInsideTheStorageDirectory() {
        assertEquals("front_door.jpg", lastFrameFileName("front/door"))
        assertEquals("device.jpg", lastFrameFileName(""))
        assertEquals("___.jpg", lastFrameFileName("///"))
        assertEquals("plain-1_A.jpg", lastFrameFileName("plain-1_A"))
    }

    @Test
    fun aSavedFrameIsReadBackAfterTheLiveScreenIsGone() {
        val directory = temporaryDirectory()
        val frame = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 1, 2, 3)

        LastFrameFiles.write(directory, "front/door", frame)

        // Lo que queda tras salir del directo es el fichero: una lectura nueva, sin
        // ningun estado en memoria, tiene que devolver la misma imagen.
        assertArrayEquals(frame, LastFrameFiles.read(directory, "front/door"))
        assertTrue(File(directory, "front_door.jpg").isFile)
    }

    @Test
    fun aNewFrameReplacesThePreviousOne() {
        val directory = temporaryDirectory()
        LastFrameFiles.write(directory, "dev-1", byteArrayOf(1, 1, 1))

        LastFrameFiles.write(directory, "dev-1", byteArrayOf(2, 2))

        assertArrayEquals(byteArrayOf(2, 2), LastFrameFiles.read(directory, "dev-1"))
    }

    @Test
    fun writingLeavesNoTemporaryFileBehind() {
        val directory = temporaryDirectory()

        LastFrameFiles.write(directory, "dev-1", byteArrayOf(9))

        val leftovers = directory.listFiles().orEmpty().map { it.name }.filter { it.endsWith(".tmp") }
        assertTrue("temporary files left: $leftovers", leftovers.isEmpty())
    }

    @Test
    fun anAbsentFrameIsNull() {
        assertNull(LastFrameFiles.read(temporaryDirectory(), "never-seen"))
    }

    @Test
    fun eachDeviceKeepsItsOwnFrame() {
        val directory = temporaryDirectory()
        LastFrameFiles.write(directory, "dev-1", byteArrayOf(1))
        LastFrameFiles.write(directory, "dev-2", byteArrayOf(2))

        assertArrayEquals(byteArrayOf(1), LastFrameFiles.read(directory, "dev-1"))
        assertArrayEquals(byteArrayOf(2), LastFrameFiles.read(directory, "dev-2"))
    }
}
