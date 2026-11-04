package dev.nacho.wibox.core.cloud.diag

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Buzon de diagnostico en memoria: las ultimas lineas de la apertura de sesion
 * visibles en la pantalla del live, para poder depurar sin adb.
 */
object LiveDiag {
    private const val MAX_LINES = 9

    private val stamp = SimpleDateFormat("HH:mm:ss", Locale.US)
    private val _lines = MutableStateFlow<List<String>>(emptyList())

    val lines: StateFlow<List<String>> = _lines

    fun add(message: String) {
        val line = stamp.format(Date()) + " " + message
        _lines.value = (_lines.value + line).takeLast(MAX_LINES)
        android.util.Log.d("WIBOX-DIAG", message)
    }

    fun clear() {
        _lines.value = emptyList()
    }
}
