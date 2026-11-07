package dev.nacho.wibox.ui.live

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.ByteArrayOutputStream
import java.io.File

object LastFrameStore {
    private val _revision = MutableStateFlow(0L)
    val revision: StateFlow<Long> = _revision

    fun load(context: Context, deviceId: String): Bitmap? {
        val bytes = LastFrameFiles.read(directory(context), deviceId) ?: return null
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }

    fun save(context: Context, deviceId: String, bitmap: Bitmap) {
        val output = ByteArrayOutputStream()
        check(bitmap.compress(Bitmap.CompressFormat.JPEG, 90, output))
        LastFrameFiles.write(directory(context), deviceId, output.toByteArray())
        _revision.value++
    }

    private fun directory(context: Context): File = File(context.filesDir, "last-frames")
}

/**
 * File plumbing of the last decoded frame, split from the bitmap codec so the
 * persistence can be exercised without Android. The frame is written to disk, not kept
 * in memory, so the device card still has a thumbnail after leaving the live screen.
 */
internal object LastFrameFiles {

    fun read(directory: File, deviceId: String): ByteArray? {
        val file = File(directory, lastFrameFileName(deviceId))
        if (!file.isFile) return null
        return runCatching { file.readBytes() }.getOrNull()
    }

    fun write(directory: File, deviceId: String, bytes: ByteArray) {
        directory.mkdirs()
        val file = File(directory, lastFrameFileName(deviceId))
        val temporary = File(directory, "${file.name}.tmp")
        temporary.writeBytes(bytes)
        if (!temporary.renameTo(file)) {
            temporary.copyTo(file, overwrite = true)
            temporary.delete()
        }
    }
}

internal fun lastFrameFileName(deviceId: String): String {
    val safe = deviceId.replace(Regex("[^A-Za-z0-9._-]"), "_")
    return "${safe.ifBlank { "device" }}.jpg"
}
