package dev.nacho.wibox.ui.live

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File

object LastFrameStore {
    private val _revision = MutableStateFlow(0L)
    val revision: StateFlow<Long> = _revision

    fun load(context: Context, deviceId: String): Bitmap? {
        val file = file(context, deviceId)
        return if (file.isFile) BitmapFactory.decodeFile(file.absolutePath) else null
    }

    fun save(context: Context, deviceId: String, bitmap: Bitmap) {
        val file = file(context, deviceId)
        file.parentFile?.mkdirs()
        val temporary = File(file.parentFile, "${file.name}.tmp")
        temporary.outputStream().buffered().use { output ->
            check(bitmap.compress(Bitmap.CompressFormat.JPEG, 90, output))
        }
        if (!temporary.renameTo(file)) {
            temporary.copyTo(file, overwrite = true)
            temporary.delete()
        }
        _revision.value++
    }

    private fun file(context: Context, deviceId: String): File =
        File(File(context.filesDir, "last-frames"), lastFrameFileName(deviceId))
}

internal fun lastFrameFileName(deviceId: String): String {
    val safe = deviceId.replace(Regex("[^A-Za-z0-9._-]"), "_")
    return "${safe.ifBlank { "device" }}.jpg"
}
