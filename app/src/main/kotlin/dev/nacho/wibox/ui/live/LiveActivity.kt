package dev.nacho.wibox.ui.live

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import dev.nacho.wibox.WiboxApplication

/** Activity del live real (solo remoto: el timbre esta a kilometros). */
class LiveActivity : ComponentActivity() {

    private var controller: LiveController? = null
    private var deviceId: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        deviceId = intent.getStringExtra(EXTRA_DEVICE_ID) ?: ""
        val autoStart = intent.getBooleanExtra(EXTRA_AUTO_START, false)
        val autoUnlock = intent.getBooleanExtra(EXTRA_AUTO_UNLOCK, false)
        val channel = intent.getIntExtra(EXTRA_CHANNEL, 1)
        val stream = intent.getIntExtra(EXTRA_STREAM, 1)
        dev.nacho.wibox.core.cloud.quii.LiveTuning.apParam =
            intent.getIntExtra(EXTRA_AP, 2).let { if (it < 0) null else it }
        dev.nacho.wibox.core.cloud.quii.LiveTuning.inner = intent.getBooleanExtra(EXTRA_INNER, false)
        dev.nacho.wibox.core.cloud.quii.LiveTuning.probeOrder = intent.getIntExtra(EXTRA_PROBE, 0)
        dev.nacho.wibox.core.cloud.quii.LiveTuning.cgiCommand = intent.getStringExtra(EXTRA_CGI)
        dev.nacho.wibox.core.cloud.quii.LiveTuning.passwordMode = intent.getIntExtra(EXTRA_PWD, 0)
        dev.nacho.wibox.core.cloud.quii.LiveTuning.colorMode = intent.getIntExtra(EXTRA_COLOR, 1)
        dev.nacho.wibox.core.cloud.quii.LiveTuning.fixedPort = intent.getIntExtra(EXTRA_PORT, 0)
        android.util.Log.d("WIBOX-LIVE", "onCreate device='$deviceId' autoStart=$autoStart ch=$channel st=$stream ap=${dev.nacho.wibox.core.cloud.quii.LiveTuning.apParam} inner=${dev.nacho.wibox.core.cloud.quii.LiveTuning.inner}")
        val container = (application as WiboxApplication).container
        val liveController = LiveController(container, applicationContext)
        controller = liveController
        LiveSessionService.start(applicationContext)
        if (autoStart && deviceId.isNotBlank()) {
            android.util.Log.d("WIBOX-LIVE", "auto start -> ${'$'}deviceId")
            liveController.start(deviceId, autoUnlock, channel, stream)
        }
        setContent {
            MaterialTheme {
                LiveScreen(
                    deviceId = deviceId,
                    controller = liveController,
                    onSurfaceCreated = { liveController.setSurface(it) },
                    onSurfaceDestroyed = { liveController.setSurface(null) },
                )
            }
        }
    }

    override fun onStop() {
        super.onStop()
        // Con la pantalla encendida, salir de la app significa que el usuario ya no
        // esta mirando: se suelta la sesion para no dejar el timbre ocupado. Con la
        // pantalla apagada se mantiene, que es el caso de uso del servicio.
        val power = getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
        if (power.isInteractive) {
            android.util.Log.d("WIBOX-LIVE", "onStop con pantalla encendida: cierro la sesion")
            controller?.pause()
            LiveSessionService.stop(applicationContext)
        } else {
            android.util.Log.d("WIBOX-LIVE", "onStop con pantalla apagada: mantengo la sesion")
        }
    }

    override fun onDestroy() {
        controller?.destroy()
        controller = null
        LiveSessionService.stop(applicationContext)
        super.onDestroy()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val requested = intent.getStringExtra(EXTRA_DEVICE_ID) ?: return
        if (requested != deviceId) {
            controller?.destroy()
            controller = null
            finish()
            start(this, requested)
            return
        }
        controller?.start(requested)
    }

    companion object {
        private const val EXTRA_DEVICE_ID = "device_id"
        private const val EXTRA_AUTO_START = "auto_start"
        private const val EXTRA_AUTO_UNLOCK = "auto_unlock"
        private const val EXTRA_CHANNEL = "channel"
        private const val EXTRA_STREAM = "stream"
        private const val EXTRA_AP = "ap"
        private const val EXTRA_INNER = "inner"
        private const val EXTRA_PROBE = "probe"
        private const val EXTRA_CGI = "cgi"
        private const val EXTRA_PWD = "pwd"
        private const val EXTRA_COLOR = "color"
        private const val EXTRA_PORT = "port"

        fun start(context: Context, deviceId: String) {
            val intent = Intent(context, LiveActivity::class.java)
                .putExtra(EXTRA_DEVICE_ID, deviceId)
                .putExtra(EXTRA_AUTO_START, true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            context.startActivity(intent)
        }
    }
}

@Composable
private fun LiveScreen(
    deviceId: String,
    controller: LiveController,
    onSurfaceCreated: (Surface) -> Unit,
    onSurfaceDestroyed: () -> Unit,
) {
    val state by controller.state.collectAsState()
    val videoReady by controller.videoReady.collectAsState()
    Column(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) {
            AndroidView(
                factory = { context ->
                    SurfaceView(context).apply {
                        holder.addCallback(object : SurfaceHolder.Callback {
                            override fun surfaceCreated(holder: SurfaceHolder) {
                                onSurfaceCreated(holder.surface)
                            }

                            override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) = Unit

                            override fun surfaceDestroyed(holder: SurfaceHolder) {
                                onSurfaceDestroyed()
                            }
                        })
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )
            when (val current = state) {
                is LiveUiState.Connecting -> StatusOverlay(current.message)
                is LiveUiState.Error -> StatusOverlay("Error: ${current.message}")
                is LiveUiState.Idle -> StatusOverlay("En espera")
                is LiveUiState.Live -> if (!videoReady) WaitingOverlay()
            }
            LiveDiagPanel()
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(
                onClick = { controller.start(deviceId) },
                modifier = Modifier.weight(1f),
            ) { Text("En vivo") }
            OutlinedButton(
                onClick = { controller.stop() },
                modifier = Modifier.weight(1f),
            ) { Text("Parar") }
            Button(
                onClick = { controller.unlock() },
                modifier = Modifier.weight(1f),
            ) { Text("Abrir") }
        }
    }
}

@Composable
private fun LiveDiagPanel() {
    val lines by dev.nacho.wibox.core.cloud.diag.LiveDiag.lines.collectAsState()
    if (lines.isEmpty()) return
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(8.dp),
        verticalArrangement = Arrangement.Top,
    ) {
        Text(
            text = lines.joinToString("\n"),
            color = Color(0xFFB2FFB2),
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier
                .background(Color(0xAA000000))
                .padding(6.dp),
        )
    }
}

@Composable
private fun WaitingOverlay() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Text(
            text = "Conectado. Esperando imagen del timbre...",
            color = Color.White,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier
                .padding(16.dp)
                .background(Color(0x99000000))
                .padding(horizontal = 12.dp, vertical = 8.dp),
        )
    }
}

@Composable
private fun StatusOverlay(text: String) {
    Box(
        modifier = Modifier.fillMaxSize().background(Color(0xCC101010)),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = text, color = Color.White, style = MaterialTheme.typography.bodyLarge)
    }
}
