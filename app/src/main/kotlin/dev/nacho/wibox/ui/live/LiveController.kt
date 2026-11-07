package dev.nacho.wibox.ui.live

import android.content.Context
import android.graphics.Bitmap
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.MediaCodec
import android.media.MediaFormat
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.PixelCopy
import android.view.Surface
import androidx.lifecycle.LifecycleOwner
import dev.nacho.wibox.core.cloud.quii.H264ParamSets
import dev.nacho.wibox.core.cloud.quii.MediaFrame
import dev.nacho.wibox.core.cloud.quii.QuiiLiveSession
import dev.nacho.wibox.core.cloud.quii.VideoCodecAlignment
import dev.nacho.wibox.core.cloud.quii.VideoPtsClock
import dev.nacho.wibox.data.AppContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import dev.nacho.wibox.core.cloud.diag.LiveDiag
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Estado de la sesion de live real (QUII remoto). */
sealed class LiveUiState {
    data object Idle : LiveUiState()
    data class Connecting(val message: String = "Conectando con el timbre...") : LiveUiState()
    data object Live : LiveUiState()
    data class Error(val message: String) : LiveUiState()
}

/**
 * Controlador del live real: RemoteLiveSession (cloud->p2p->tunel->QUII) y render
 * de H264 via MediaCodec sobre Surface + G711A via AudioTrack. Sesion por pantalla.
 */
class LiveController(
    private val container: AppContainer,
    private val appContext: Context,
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val _state = MutableStateFlow<LiveUiState>(LiveUiState.Idle)
    val state: StateFlow<LiveUiState> = _state
    private val _videoReady = MutableStateFlow(false)
    val videoReady: StateFlow<Boolean> = _videoReady

    /** Se pone a true cuando la vista se cierra sola por tiempo. */
    val timedOut = MutableStateFlow(false)

    private var surface: Surface? = null

    @Volatile
    private var lastFrameAt = 0L

    @Volatile
    private var lastUnlockAt = 0L
    private var sessionJob: Job? = null
    private var liveSession: dev.nacho.wibox.core.cloud.quii.QuiiLiveSession? = null
    private var remoteLive: dev.nacho.wibox.core.cloud.quii.RemoteLiveSession? = null
    private var sdkLive: dev.nacho.wibox.core.cloud.quii.SdkTunnelLiveSession? = null
    private var currentDeviceId: String? = null
    private var codec: MediaCodec? = null
    private var audioTrack: AudioTrack? = null
    private val AUDIO_GAIN = 0.4f
    private val FRAME_STALL_MS = 15_000L
    private val LIVE_MAX_MS = 30_000L
    private val MAX_CONNECT_ATTEMPTS = 8

    /** Espera antes de reconectar cuando la sesion no pinta imagen. */
    private val IMAGE_WAIT_MS = 60_000L
    private val MAX_IMAGE_CYCLES = 10
    private val CONNECT_P2P_ATTEMPTS = 3

    /** Tamano provisional del decoder hasta que el primer frame anuncia el real. */
    private val PROVISIONAL_WIDTH = 640
    private val PROVISIONAL_HEIGHT = 480

    /** Tope de reconfiguraciones por sesion, por si el stream alterna parameter sets. */
    private val MAX_VIDEO_RECONFIGURE = 8

    /** El timbre responde HANG_UP con motivo 1 cuando el canal esta ocupado. */
    private val HANGUP_BUSY = 1

    /** Reintentos automaticos mientras la placa esta ocupada por otra conversacion. */
    private val MAX_BUSY_RETRIES = 20
    private val BUSY_RETRY_MS = 30_000L


    private var videoInputCount = 0
    private var videoOutputCount = 0
    private var videoErrorCount = 0
    private var activeDeviceId = ""
    private var captureInFlight = false
    private var lastCaptureAt = 0L
    @Volatile
    private var videoAwaitingKeyFrame = true
    private var videoSkippedCount = 0
    private var videoParamSetOnlyCount = 0
    private var videoReconfigureCapLogged = false
    private var videoReconfigureFailures = 0

    /**
     * Reloj de presentacion del decoder: avanza con el fps que anuncia el cpacket
     * (el timbre emite a 20 fps) y cuenta tambien los frames que se descartan, que
     * antes comprimian la linea de tiempo.
     */
    private val videoPts = VideoPtsClock()

    /**
     * Lleva los parameter sets y el tamano con los que esta configurado el decoder, y
     * evita reconstruirlo cuando el keyframe repite los mismos (que es lo normal) o
     * cuando ya fallo con esos mismos parametros.
     */
    private val videoAlign = VideoCodecAlignment(maxRebuilds = MAX_VIDEO_RECONFIGURE)

    // SPS/PPS del propio timbre (H264 Main 352x288), extraidos de un IDR real.
    // Necesarios porque al engancharnos a mitad de stream no llegan en el bitstream.
    // Es solo el punto de partida: la sesion puede anunciar otro tamano y el primer
    // keyframe trae los parameter sets reales, que mandan sobre estos.
    private val fallbackSps: ByteArray = hexBytes("474d401e99a05825a100000303e800009c4084")
    private val fallbackPps: ByteArray = hexBytes("48ee3c80")

    private fun hexBytes(hex: String): ByteArray =
        ByteArray(hex.length / 2) {
            ((hex[it * 2].digitToInt(16) shl 4) or hex[it * 2 + 1].digitToInt(16)).toByte()
        }

    fun setSurface(newSurface: Surface?) {
        val previous = surface
        surface = newSurface
        if (newSurface == null) {
            // Pantalla apagada: se conserva el decoder (con SPS/PPS y estado) y la
            // sesion sigue viva. Solo se deja de renderizar.
            android.util.Log.d("WIBOX-LIVE", "surface detached, codec kept, session kept")
            return
        }
        // Rebind del decoder vivo: conserva configuracion y estado, sin esperar keyframe.
        val decoder = codec
        if (decoder != null) {
            runCatching { decoder.setOutputSurface(newSurface) }
                .onSuccess {
                    android.util.Log.d("WIBOX-LIVE", "surface reattached, codec rebound")
                    android.util.Log.d("WIBOX-LIVE", "STATE->Live desde setSurface(rebind)")
                    _state.value = LiveUiState.Live
                }
                .onFailure { error ->
                    android.util.Log.e("WIBOX-LIVE", "setOutputSurface fail, recreating codec", error)
                    releaseVideoCodec()
                    val session = liveSession ?: return
                    scope.launch {
                        runCatching { startVideo(session) }
                            .onSuccess {
                                android.util.Log.d("WIBOX-LIVE", "STATE->Live desde setSurface(recreate ok)")
                                _state.value = LiveUiState.Live
                            }
                            .onFailure { android.util.Log.e("WIBOX-LIVE", "VIDEO resume fail", it) }
                    }
                }
            return
        }
        val session = liveSession ?: run {
            android.util.Log.d("WIBOX-LIVE", "surface nuevo sin codec ni session (ignorado)")
            return
        }
        if (previous == null && _state.value !is LiveUiState.Connecting) {
            android.util.Log.d("WIBOX-LIVE", "surface reattached, restoring video")
            scope.launch {
                runCatching { startVideo(session) }
                    .onSuccess {
                        android.util.Log.d("WIBOX-LIVE", "STATE->Live desde setSurface(restore ok)")
                        _state.value = LiveUiState.Live
                    }
                    .onFailure { android.util.Log.e("WIBOX-LIVE", "VIDEO resume fail", it) }
            }
        } else {
            android.util.Log.d(
                "WIBOX-LIVE",
                "surface nuevo ignorado previous=${previous != null} state=${_state.value}",
            )
        }
    }

    fun start(deviceId: String, autoUnlock: Boolean = false, channel: Int = 1, stream: Int = 2) {
        android.util.Log.d("WIBOX-LIVE", "start($deviceId) state=" + _state.value)
        timedOut.value = false
        // En vivo siempre reconecta: si la sesion esta viva pero sin imagen, el
        // boton tiene que poder rescatar la pantalla en vez de no hacer nada.
        if (_state.value is LiveUiState.Connecting) return
        if (sessionJob?.isActive == true) {
            android.util.Log.d("WIBOX-LIVE", "start: cierro la sesion anterior antes de reconectar")
            sessionJob?.cancel()
            sessionJob = null
            runCatching { releaseVideoCodec() }
            runCatching { sdkLive?.closeNow() }
            sdkLive = null
            activeDeviceId?.let { container.closeSdkPort(it) }
            liveSession = null
            remoteLive = null
        }
        LiveDiag.clear()
        LiveDiag.add("start $deviceId")
        activeDeviceId = deviceId
        sessionJob = scope.launch {
            _state.value = LiveUiState.Connecting("Conectando con el timbre...")
            dev.nacho.wibox.core.cloud.quii.LiveTuning.cgiCommand?.let { cmd ->
                val xml = container.probeCgi(deviceId, cmd)
                println("WIBOX-CGI " + cmd + " => " + xml.replace("\n", " ").replace("\r", ""))
                LiveDiag.add("cgi " + cmd + ": " + xml.replace("\n", " ").take(90))
                _state.value = LiveUiState.Error("CGI " + cmd + ": " + xml.take(400))
                return@launch
            }
            try {
                // El SDK se despierta solo dentro de openSdkTunnelLive. Un awaken en
                // paralelo toca addPortByP2P a la vez y deja al timbre sin media.
                var attempt = 0
                var imageCycles = 0
                var busyRetries = 0
                        while (true) {
                    attempt++
                    _state.value = LiveUiState.Connecting(
                        if (attempt == 1) "Conectando con el timbre..."
                        else "Timbre en reposo. Reintentando ($attempt/$MAX_CONNECT_ATTEMPTS)...",
                    )
                    val opened = runCatching { openSession(deviceId, channel, stream) }
                    val failure = opened.exceptionOrNull()
                    if (failure != null) {
                        val raw = failure.message ?: failure::class.java.simpleName
                        val drowsy = raw.contains("Timeout esperando p2pconnect", ignoreCase = true) ||
                            raw.contains("low power", ignoreCase = true)
                        if (!drowsy || attempt >= MAX_CONNECT_ATTEMPTS) throw failure
                        android.util.Log.d("WIBOX-LIVE", "WAKE retry $attempt/$MAX_CONNECT_ATTEMPTS cause=$raw")
                        LiveDiag.add("reintento $attempt/$MAX_CONNECT_ATTEMPTS: " + raw.take(60))
                        kotlinx.coroutines.delay(5_000)
                        continue
                    }
                    val session = opened.getOrThrow()
                    liveSession = session
                    startAudio()
                    videoInputCount = 0
                    videoOutputCount = 0
                    videoErrorCount = 0
                    videoSkippedCount = 0
                    videoParamSetOnlyCount = 0
                    videoAwaitingKeyFrame = true
                    _videoReady.value = false
                    startVideo(session)
                    _state.value = LiveUiState.Live
                    if (autoUnlock) {
                        scope.launch {
                            kotlinx.coroutines.delay(20_000)
                            unlock()
                        }
                    }
                    var frameCount = 0
                    val collectJob = scope.launch {
                        session.frames.collect { frame ->
                            frameCount++
                            if (frameCount <= 3 || frameCount % 25 == 0) {
                                android.util.Log.d(
                                    "WIBOX-LIVE",
                                    "FRAME count=$frameCount type=${frame::class.java.simpleName}",
                                )
                            }
                            when (frame) {
                                is MediaFrame.Video -> feedVideo(frame)
                                is MediaFrame.Audio -> feedAudio(frame)
                            }
                        }
                    }
                    // Espera imagen, pero corta en cuanto el timbre avisa de cuelgue.
                    var gotImage = false
                    var hangReason: Int? = null
                    val imageDeadline = System.currentTimeMillis() + IMAGE_WAIT_MS
                    while (System.currentTimeMillis() < imageDeadline) {
                        if (_videoReady.value) {
                            gotImage = true
                            break
                        }
                        val reason = sdkLive?.hangUpReason?.value
                        if (reason != null && reason != 0) {
                            hangReason = reason
                            break
                        }
                        kotlinx.coroutines.delay(250)
                    }
                    if (hangReason != null) {
                        android.util.Log.d("WIBOX-LIVE", "HANGUP reason=$hangReason")
                        LiveDiag.add("cuelgue del timbre motivo $hangReason")
                        collectJob.cancel()
                        runCatching { releaseVideoCodec() }
                        runCatching { sdkLive?.close() }
                        sdkLive = null
                        currentDeviceId?.let { container.closeSdkPort(it) }
                        runCatching { session.close() }
                        liveSession = null
                        remoteLive = null
                        if (hangReason == HANGUP_BUSY && busyRetries < MAX_BUSY_RETRIES) {
                            busyRetries++
                            android.util.Log.d(
                                "WIBOX-LIVE",
                                "BUSY retry $busyRetries/$MAX_BUSY_RETRIES en ${BUSY_RETRY_MS / 1000}s",
                            )
                            LiveDiag.add("placa ocupada, reintento $busyRetries/$MAX_BUSY_RETRIES")
                            var left = (BUSY_RETRY_MS / 1000).toInt()
                            while (left > 0) {
                                _state.value = LiveUiState.Connecting(
                                    "Placa ocupada por otra conversacion. Reintentando en $left s...",
                                )
                                kotlinx.coroutines.delay(1_000)
                                left--
                            }
                            attempt = 0
                            imageCycles = 0
                            continue
                        }
                        _state.value = LiveUiState.Error(hangUpMessage(hangReason))
                        stopInternal()
                        return@launch
                    }
                    if (gotImage) {
                        // La vista en vivo tiene duracion maxima, igual que la
                        // previsualizacion de la app oficial: al llegar al limite se
                        // cierra la sesion y se libera el canal del timbre.
                        val liveStartedAt = System.currentTimeMillis()
                        LiveDiag.add("vista limitada a ${LIVE_MAX_MS / 1000}s")
                        // Vigila la sesion entera: el timbre puede colgar despues de
                        // la primera imagen (por ejemplo al abrir la puerta) y dejar
                        // el ultimo frame congelado en pantalla.
                        while (true) {
                            kotlinx.coroutines.delay(2_000)
                            val liveReason = sdkLive?.hangUpReason?.value
                            if (liveReason != null && liveReason != 0) {
                                android.util.Log.d("WIBOX-LIVE", "HANGUP reason=$liveReason en vivo")
                                LiveDiag.add("cuelgue del timbre motivo $liveReason")
                                collectJob.cancel()
                                closeLiveArtifacts(session)
                                _state.value = LiveUiState.Error(liveEndMessage(liveReason))
                                stopInternal()
                                return@launch
                            }
                            if (System.currentTimeMillis() - liveStartedAt >= LIVE_MAX_MS) {
                                android.util.Log.d("WIBOX-LIVE", "vista en vivo agotada a los ${LIVE_MAX_MS} ms")
                                LiveDiag.add("vista cerrada tras ${LIVE_MAX_MS / 1000}s")
                                collectJob.cancel()
                                closeLiveArtifacts(session)
                                _state.value = LiveUiState.Idle
                                timedOut.value = true
                                stopInternal()
                                return@launch
                            }
                            if (System.currentTimeMillis() - lastFrameAt > FRAME_STALL_MS) {
                                android.util.Log.d("WIBOX-LIVE", "FRAME STALL sin frames desde $lastFrameAt")
                                LiveDiag.add("video parado mas de ${FRAME_STALL_MS / 1000}s")
                                collectJob.cancel()
                                closeLiveArtifacts(session)
                                _state.value = LiveUiState.Error(liveEndMessage(null))
                                stopInternal()
                                return@launch
                            }
                        }
                    }
                    imageCycles++
                    android.util.Log.d(
                        "WIBOX-LIVE",
                        "IMAGE retry $imageCycles/$MAX_IMAGE_CYCLES sin imagen en $IMAGE_WAIT_MS ms",
                    )
                    LiveDiag.add("sin imagen, reintento $imageCycles/$MAX_IMAGE_CYCLES")
                    collectJob.cancel()
                    runCatching { releaseVideoCodec() }
                    runCatching { sdkLive?.close() }
                    sdkLive = null
                    currentDeviceId?.let { container.closeSdkPort(it) }
                    runCatching { session.close() }
                    liveSession = null
                    remoteLive = null
                    if (imageCycles >= MAX_IMAGE_CYCLES) {
                        _state.value = LiveUiState.Error(
                            "El timbre no manda imagen: esta en reposo. Se despierta al pulsarlo o al abrirlo desde la app oficial.",
                        )
                        stopInternal()
                        return@launch
                    }
                    _state.value = LiveUiState.Connecting(
                        "Timbre en reposo, esperando imagen ($imageCycles/$MAX_IMAGE_CYCLES)...",
                    )
                    attempt = 0
                    kotlinx.coroutines.delay(1_500)
                    continue
                }
            } catch (error: Exception) {
                android.util.Log.e("WIBOX-LIVE", "LIVE FAIL", error)
                LiveDiag.add("FALLO: " + (error.message ?: error::class.java.simpleName).take(70))
                val raw = error.message ?: error::class.java.simpleName
                val msg = if (raw.contains("Timeout esperando p2pconnect", ignoreCase = true)) {
                    "El timbre sigue en reposo tras $MAX_CONNECT_ATTEMPTS intentos. Se activa al pulsar el timbre o al abrirlo desde la app oficial; vuelve a intentarlo entonces."
                } else raw
                _state.value = LiveUiState.Error(msg)
                stopInternal()
            }
        }
    }

    /**
     * Mensaje de fin de sesion. Si el corte llega poco despues de abrir la puerta
     * el timbre suele cerrar el video el solo: se explica en vez de dar un error.
     */
    private fun liveEndMessage(reason: Int?): String {
        val justUnlocked = System.currentTimeMillis() - lastUnlockAt < 30_000
        if (justUnlocked) {
            return "Puerta abierta. La placa ha cortado el video; pulsa En vivo si quieres verla otra vez."
        }
        return if (reason != null) {
            hangUpMessage(reason)
        } else {
            "El video se ha parado. Pulsa En vivo para reconectar."
        }
    }

    /** Suelta codec, sesion y puerto P2P de la sesion en curso. */
    private suspend fun closeLiveArtifacts(session: QuiiLiveSession?) {
        runCatching { releaseVideoCodec() }
        runCatching { sdkLive?.close() }
        sdkLive = null
        currentDeviceId?.let { container.closeSdkPort(it) }
        session?.let { runCatching { it.close() } }
        liveSession = null
        remoteLive = null
    }

    fun unlock() {
        scope.launch {
            lastUnlockAt = System.currentTimeMillis()
            android.util.Log.d("WIBOX-LIVE", "UNLOCK send")
            val started = System.currentTimeMillis()
            runCatching {
                val sdk = sdkLive
                val remote = remoteLive
                sdk?.unlockDoor() ?: remote?.unlockDoor() ?: -1
            }
                .onSuccess { result ->
                    val took = System.currentTimeMillis() - started
                    android.util.Log.d("WIBOX-LIVE", "UNLOCK error=$result en ${took}ms")
                    LiveDiag.add("abrir puerta rc=$result en ${took}ms")
                    if (result != 0) {
                        _state.value = LiveUiState.Error("No se pudo abrir la puerta (codigo $result).")
                    }
                }
                .onFailure { error ->
                    android.util.Log.d(
                        "WIBOX-LIVE",
                        "UNLOCK FAIL " + error::class.java.simpleName + ": " + error.message,
                    )
                    LiveDiag.add("abrir puerta fallo: " + (error.message ?: "").take(40))
                }
        }
    }

    fun stop() {        scope.launch {
            stopInternal()
            _state.value = LiveUiState.Idle
        }
    }

    /**
     * Nueva via: el SDK propietario hace wake y tunel, nosotros hablamos QUII
     * por TCP local. Si falla, cae a la pila UST/MQTT/KCP propia.
     */
    private suspend fun openSession(
        deviceId: String,
        channel: Int,
        stream: Int,
    ): dev.nacho.wibox.core.cloud.quii.QuiiLiveSession {
        // El SDK abre el puerto del timbre en 127.0.0.1 y en el Honor tarda menos de 1 s.
        // La ruta UST queda de respaldo: en esta red nunca recibe respuesta a p2pconnect.
        currentDeviceId = deviceId
        val sdk = runCatching { container.openSdkTunnelLive(deviceId, channel = channel, stream = stream) }
        sdk.onSuccess {
            sdkLive = it
            remoteLive = null
            android.util.Log.d("WIBOX-LIVE", "LIVE source=sdk-tunnel")
            LiveDiag.add("sesion sdk ok")
            return it.live
        }
        android.util.Log.d("WIBOX-LIVE", "SDK tunnel no: " + (sdk.exceptionOrNull()?.message ?: "?"))
        val remote = runCatching {
            container.openRemoteLive(
                deviceId,
                channel = channel,
                stream = stream,
                p2pAttempts = CONNECT_P2P_ATTEMPTS,
            )
        }
        remote.onSuccess {
            remoteLive = it
            sdkLive = null
            android.util.Log.d("WIBOX-LIVE", "LIVE source=ust-tunnel")
            LiveDiag.add("sesion ust ok")
            return it.live
        }
        android.util.Log.d("WIBOX-LIVE", "UST tunnel no: " + (remote.exceptionOrNull()?.message ?: "?"))
        throw (sdk.exceptionOrNull() ?: remote.exceptionOrNull() ?: IllegalStateException("sin sesion"))
    }

    private suspend fun startVideo(session: dev.nacho.wibox.core.cloud.quii.QuiiLiveSession) {
        var target = surface
        var waited = 0
        while (target == null && waited < 15_000) {
            kotlinx.coroutines.delay(100)
            waited += 100
            target = surface
        }
        if (target == null) {
            android.util.Log.e("WIBOX-LIVE", "VIDEO no surface tras ${waited}ms")
            throw IllegalStateException("sin Surface para el video")
        }
        android.util.Log.d("WIBOX-LIVE", "VIDEO startVideo waited=${waited}ms")
        releaseVideoCodec()
        android.util.Log.d("WIBOX-LIVE", "VIDEO codec released, creando decoder")
        videoAwaitingKeyFrame = true
        videoSkippedCount = 0
        videoParamSetOnlyCount = 0
        videoReconfigureCapLogged = false
        videoReconfigureFailures = 0
        videoPts.reset()
        _videoReady.value = false
        codec = configureVideoCodec(target, PROVISIONAL_WIDTH, PROVISIONAL_HEIGHT, fallbackSps, fallbackPps)
        videoAlign.reset(
            VideoCodecAlignment.Signature(
                sps = fallbackSps,
                pps = fallbackPps,
                width = PROVISIONAL_WIDTH,
                height = PROVISIONAL_HEIGHT,
            ),
        )
    }

    /**
     * Crea y arranca un decoder H264 con los parameter sets y el tamano indicados.
     * El codec devuelto queda listo para recibir bitstream, y el llamador es el
     * responsable de liberarlo.
     */
    private fun configureVideoCodec(
        target: Surface,
        width: Int,
        height: Int,
        sps: ByteArray,
        pps: ByteArray,
    ): MediaCodec {
        val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height)
        format.setByteBuffer("csd-0", java.nio.ByteBuffer.wrap(byteArrayOf(0, 0, 0, 1) + sps))
        format.setByteBuffer("csd-1", java.nio.ByteBuffer.wrap(byteArrayOf(0, 0, 0, 1) + pps))
        val colorMode = dev.nacho.wibox.core.cloud.quii.LiveTuning.colorMode
        runCatching {
            when (colorMode) {
                1 -> {
                    format.setInteger(MediaFormat.KEY_COLOR_STANDARD, MediaFormat.COLOR_STANDARD_BT709)
                    format.setInteger(MediaFormat.KEY_COLOR_RANGE, MediaFormat.COLOR_RANGE_LIMITED)
                    format.setInteger(MediaFormat.KEY_COLOR_TRANSFER, MediaFormat.COLOR_TRANSFER_SDR_VIDEO)
                }
                2 -> {
                    format.setInteger(MediaFormat.KEY_COLOR_STANDARD, MediaFormat.COLOR_STANDARD_BT601_NTSC)
                    format.setInteger(MediaFormat.KEY_COLOR_RANGE, MediaFormat.COLOR_RANGE_LIMITED)
                    format.setInteger(MediaFormat.KEY_COLOR_TRANSFER, MediaFormat.COLOR_TRANSFER_SDR_VIDEO)
                }
                3 -> {
                    format.setInteger(MediaFormat.KEY_COLOR_STANDARD, MediaFormat.COLOR_STANDARD_BT601_PAL)
                    format.setInteger(MediaFormat.KEY_COLOR_RANGE, MediaFormat.COLOR_RANGE_LIMITED)
                    format.setInteger(MediaFormat.KEY_COLOR_TRANSFER, MediaFormat.COLOR_TRANSFER_SDR_VIDEO)
                }
            }
        }.onFailure { android.util.Log.w("WIBOX-LIVE", "color aspects fail", it) }
        val created = runCatching { MediaCodec.createByCodecName("c2.android.avc.decoder") }
            .getOrElse { MediaCodec.createDecoderByType(MediaFormat.MIMETYPE_VIDEO_AVC) }
        try {
            created.configure(format, target, null, 0)
            created.start()
        } catch (error: Exception) {
            runCatching { created.release() }
            throw error
        }
        android.util.Log.d(
            "WIBOX-LIVE",
            "VIDEO csd sps=${sps.size}B pps=${pps.size}B wh=${width}x${height} colorMode=$colorMode codec=${created.name}",
        )
        return created
    }

    private fun releaseVideoCodec() {
        if (codec != null) {
            android.util.Log.d("WIBOX-LIVE", "VIDEO release codec=${codec?.name}")
        }
        try {
            codec?.stop()
        } catch (_: Exception) {
        }
        try {
            codec?.release()
        } catch (_: Exception) {
        }
        codec = null
    }

    /** Busca un NAL de tipo [type] (5 = IDR) recorriendo los start codes Annex-B. */
    private fun hasNalType(bytes: ByteArray, type: Int): Boolean {
        var i = 0
        while (i + 4 < bytes.size) {
            if (bytes[i].toInt() == 0 && bytes[i + 1].toInt() == 0 &&
                bytes[i + 2].toInt() == 0 && bytes[i + 3].toInt() == 1
            ) {
                if (((bytes[i + 4].toInt() and 0x1f)) == type) return true
                i += 4
            } else {
                i++
            }
        }
        return false
    }

    /**
     * Ajusta el decoder al SPS/PPS y al tamano que anuncia [frame]. Devuelve true
     * cuando ha reconstruido el decoder.
     */
    private fun alignVideoCodec(
        frame: MediaFrame.Video,
        inBand: H264ParamSets.ParameterSets,
    ): Boolean {
        val current = videoAlign.configured
        val width = if (frame.width > 0) frame.width else current?.width ?: PROVISIONAL_WIDTH
        val height = if (frame.height > 0) frame.height else current?.height ?: PROVISIONAL_HEIGHT
        val signature = VideoCodecAlignment.Signature(inBand.sps, inBand.pps, width, height)
        when (videoAlign.decide(signature, decoderProducingOutput = videoOutputCount > 0)) {
            // El keyframe repite los parameter sets del stream: el decoder ya es el
            // correcto y reconstruirlo solo cuesta trabajo (y, si falla, un error por
            // GOP durante toda la sesion).
            VideoCodecAlignment.Decision.ALREADY_ALIGNED -> return false
            // El decoder ya usa estos parameter sets y esta pintando: el tamano que
            // anuncia el stream solo corrige el provisional con el que se creo.
            VideoCodecAlignment.Decision.SIZE_ADOPTED -> {
                videoAlign.onSizeAdopted(signature)
                android.util.Log.d(
                    "WIBOX-LIVE",
                    "VIDEO RECONFIG omitido, csd ya coincide con el decoder; wh real $width" + "x$height",
                )
                return false
            }
            // Ya se intento con estos mismos parametros y fallo: no se repite.
            VideoCodecAlignment.Decision.RETRY_SUPPRESSED -> return false
            VideoCodecAlignment.Decision.CAPPED -> {
                if (!videoReconfigureCapLogged) {
                    videoReconfigureCapLogged = true
                    android.util.Log.w(
                        "WIBOX-LIVE",
                        "VIDEO RECONFIG tope alcanzado, se mantiene wh=${current?.width}x${current?.height}",
                    )
                }
                return false
            }
            VideoCodecAlignment.Decision.REBUILD -> Unit
        }
        val target = surface ?: return false
        // El codec viejo se suelta ANTES de configurar el nuevo: crear un segundo
        // decoder sobre la misma Surface lanza IllegalArgumentException en algunos
        // equipos. Se guarda su firma para poder volver a el si la configuracion nueva
        // no arranca y no quedarse sin decoder.
        val previous = current
        releaseVideoCodec()
        val rebuilt = try {
            configureVideoCodec(target, width, height, inBand.sps, inBand.pps)
        } catch (error: Exception) {
            videoAlign.onRebuildFailed(signature)
            videoReconfigureFailures++
            if (videoReconfigureFailures == 1) {
                android.util.Log.e(
                    "WIBOX-LIVE",
                    "VIDEO RECONFIG fallo con $signature: " +
                        "${error::class.java.simpleName}: ${error.message ?: error.toString()}",
                    error,
                )
            } else {
                android.util.Log.w(
                    "WIBOX-LIVE",
                    "VIDEO RECONFIG fallo con $signature: " +
                        "${error::class.java.simpleName}: ${error.message ?: error.toString()}",
                )
            }
            restoreVideoCodec(target, previous)
            return false
        }
        codec = rebuilt
        videoAlign.onRebuilt(signature)
        android.util.Log.d(
            "WIBOX-LIVE",
            "VIDEO RECONFIG n=${videoAlign.rebuildCount} wh=${width}x${height} sps=${inBand.sps.size}B pps=${inBand.pps.size}B",
        )
        return true
    }

    /** Vuelve a levantar el decoder con la firma anterior tras un rebuild fallido. */
    private fun restoreVideoCodec(
        target: Surface,
        previous: VideoCodecAlignment.Signature?,
    ) {
        if (previous == null) return
        val restored = runCatching {
            configureVideoCodec(target, previous.width, previous.height, previous.sps, previous.pps)
        }
        restored.onSuccess {
            codec = it
            android.util.Log.d(
                "WIBOX-LIVE",
                "VIDEO RECONFIG deshecho, se mantiene wh=${previous.width}x${previous.height}",
            )
        }
        restored.onFailure {
            android.util.Log.e(
                "WIBOX-LIVE",
                "VIDEO RECONFIG sin decoder: ${it::class.java.simpleName}: ${it.message ?: it.toString()}",
                it,
            )
        }
    }

    private fun feedVideo(frame: MediaFrame.Video) {
        lastFrameAt = System.currentTimeMillis()
        // El PTS se calcula para cada frame recibido, aunque luego se descarte: asi el
        // reloj no se comprime cuando hay que esperar un keyframe.
        val ptsUs = videoPts.next(frame.fps)
        if (codec == null) {
            videoErrorCount++
            if (videoErrorCount <= 5) {
                android.util.Log.w("WIBOX-LIVE", "VIDEO sin codec, frame descartado n=$videoErrorCount")
            }
            return
        }
        // El decoder arranca con unos parameter sets de referencia porque al
        // engancharnos a mitad de stream no hay frame del que sacarlos. El keyframe
        // trae los del stream real: si no coinciden con los configurados se
        // reconfigura antes de empujarlo, que es lo que evita los macrobloques y el
        // verde saturado de una sesion con otro tamano.
        val inBand = if (frame.keyFrame) H264ParamSets.extractParameterSets(frame.bitstream) else null
        val reconfigured = if (inBand != null) alignVideoCodec(frame, inBand) else false
        val decoder = codec ?: return
        // Los parameter sets en banda solo se dejan pasar cuando el decoder esta
        // configurado con ellos. Si la reconfiguracion no ha sido posible se quitan,
        // como se hacia antes, para no desalinear el decoder con un SPS ajeno.
        val keepInBand = inBand != null && videoAlign.matchesConfigured(inBand.sps, inBand.pps)
        try {
            val bitstream = if (keepInBand) frame.bitstream else H264ParamSets.stripParamSets(frame.bitstream)
            if (bitstream.isEmpty()) {
                videoParamSetOnlyCount++
                if (videoParamSetOnlyCount <= 5 || videoParamSetOnlyCount % 100 == 0) {
                    android.util.Log.d(
                        "WIBOX-LIVE",
                        "VIDEO descartado, solo parameter sets n=$videoParamSetOnlyCount",
                    )
                }
                return
            }
            if (videoAwaitingKeyFrame) {
                val bytes = bitstream
                if (!hasNalType(bytes, 5)) {
                    videoSkippedCount++
                    if (videoSkippedCount <= 5 || videoSkippedCount % 100 == 0) {
                        val t = (bytes.getOrNull(4)?.toInt() ?: 0) and 0x1f
                        android.util.Log.d(
                            "WIBOX-LIVE",
                            "VIDEO skip sin IDR n=$videoSkippedCount nal=$t",
                        )
                    }
                    // Sin IDR el decoder no tiene referencias: empujar P frames
                    // deja el croma arrastrado durante todo el GOP.
                    return
                } else {
                    videoAwaitingKeyFrame = false
                    android.util.Log.d(
                        "WIBOX-LIVE",
                        "VIDEO KEYFRAME recibido tras ${videoSkippedCount} descartes size=${bytes.size} " +
                            "inBand=${keepInBand}",
                    )
                }
            }
            videoInputCount++
            if (videoInputCount <= 5) {
                val head = bitstream.take(16).joinToString("") { "%02x".format(it) }
                android.util.Log.d("WIBOX-LIVE", "VIDEO IN n=$videoInputCount size=${bitstream.size} key=${frame.keyFrame} wh=${frame.width}x${frame.height} fps=${frame.fps} pts=$ptsUs head=$head")
            }
            val index = decoder.dequeueInputBuffer(10_000)
            if (index >= 0) {
                val input = decoder.getInputBuffer(index) ?: return
                input.clear()
                input.put(bitstream)
                val flags = if (frame.keyFrame) MediaCodec.BUFFER_FLAG_KEY_FRAME else 0
                decoder.queueInputBuffer(index, 0, bitstream.size, ptsUs, flags)
            } else {
                if (videoInputCount <= 5) {
                    android.util.Log.d("WIBOX-LIVE", "VIDEO input no disponible")
                }
                // Un decoder recien creado que se pierde su IDR no puede decodificar
                // P frames: se vuelve a esperar keyframe en vez de arrastrar el croma.
                if (reconfigured) videoAwaitingKeyFrame = true
            }
            val info = MediaCodec.BufferInfo()
            while (true) {
                val out = decoder.dequeueOutputBuffer(info, 0)
                if (out < 0) break
                videoOutputCount++
                if (videoOutputCount <= 5) {
                    android.util.Log.d("WIBOX-LIVE", "VIDEO OUT n=$videoOutputCount size=${info.size} flags=${info.flags}")
                }
                decoder.releaseOutputBuffer(out, surface != null)
                if (surface != null) {
                    if (!_videoReady.value) _videoReady.value = true
                    captureLastFrame(frame.width, frame.height)
                }
                if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) break
            }
        } catch (error: Exception) {
            videoErrorCount++
            if (reconfigured) videoAwaitingKeyFrame = true
            if (videoErrorCount <= 5) {
                android.util.Log.e("WIBOX-LIVE", "VIDEO ERROR n=$videoErrorCount", error)
            }
        }
    }

    private fun captureLastFrame(width: Int, height: Int) {
        val source = surface ?: return
        val now = SystemClock.elapsedRealtime()
        if (captureInFlight || now - lastCaptureAt < 1000 || width <= 0 || height <= 0) return
        captureInFlight = true
        lastCaptureAt = now
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        PixelCopy.request(
            source,
            bitmap,
            { result ->
                if (result == PixelCopy.SUCCESS && activeDeviceId.isNotBlank()) {
                    runCatching { LastFrameStore.save(appContext, activeDeviceId, bitmap) }
                        .onFailure { android.util.Log.e("WIBOX-LIVE", "LAST FRAME SAVE", it) }
                }
                bitmap.recycle()
                captureInFlight = false
            },
            mainHandler,
        )
    }

    private fun startAudio() {
        val minBuf = AudioTrack.getMinBufferSize(
            8000,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        ).coerceAtLeast(4096)
        audioTrack = AudioTrack.Builder()
            .setAudioAttributes(
                android.media.AudioAttributes.Builder()
                    .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build(),
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(8000)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build(),
            )
            .setTransferMode(AudioTrack.MODE_STREAM)
            .setBufferSizeInBytes(minBuf)
            .build()
        // El G711A del timbre llega con mucho ruido de fondo. Se reproduce por
        // debajo del nivel nominal para que no sature.
        audioTrack?.setVolume(AUDIO_GAIN)
        audioTrack?.play()
    }

    private fun feedAudio(frame: MediaFrame.Audio) {
        val pcm = when (frame.codec) {
            4 -> alawToPcm(frame.samples) // G711A
            5 -> mulawToPcm(frame.samples)
            else -> frame.samples // PCM directo
        }
        audioTrack?.write(pcm, 0, pcm.size)
    }

    private fun hangUpMessage(reason: Int): String = when (reason) {
        1 -> "La placa esta ocupada con otra conversacion. Prueba en unos minutos."
        2 -> "El timbre esta desconectado o sin cobertura."
        3 -> "El timbre ha alcanzado el maximo de conexiones."
        4 -> "El timbre no soporta este tipo de video."
        5 -> "La grabacion de video del timbre esta desactivada."
        6 -> "El timbre ha agotado el tiempo de espera."
        7 -> "Sin permiso para ver el video de este timbre."
        9 -> "Canal de video no disponible en el timbre."
        10 -> "El timbre esta bloqueado."
        11 -> "El video del timbre esta apagado."
        16 -> "Otro usuario ha tomado la conexion del timbre."
        1000 -> "El timbre esta en modo ahorro de bateria."
        1001 -> "El timbre esta bloqueado."
        else -> "El timbre ha cortado la conexion (motivo $reason)."
    }

    private suspend fun stopInternal() {
        _videoReady.value = false
        try { sdkLive?.close() } catch (_: Exception) {}
        sdkLive = null
        currentDeviceId?.let { container.closeSdkPort(it) }
        try { liveSession?.close() } catch (_: Exception) {}
        liveSession = null
        releaseVideoCodec()
        try { audioTrack?.stop(); audioTrack?.release() } catch (_: Exception) {}
        audioTrack = null
    }

    /**
     * Suelta la sesion en curso sin matar el controlador (la Activity sigue viva).
     * Se llama al salir de la app para no dejar el canal del timbre ocupado.
     */
    fun pause() {
        sessionJob?.cancel()
        sessionJob = null
        runCatching { sdkLive?.closeNow() }
        sdkLive = null
        currentDeviceId?.let { runCatching { container.closeSdkPort(it) } }
        runCatching { releaseVideoCodec() }
        runCatching { audioTrack?.stop(); audioTrack?.release() }
        audioTrack = null
        liveSession = null
        remoteLive = null
        _state.value = LiveUiState.Idle
        android.util.Log.d("WIBOX-LIVE", "pause: sesion cerrada")
    }

    fun destroy() {
        // Cierre en seco antes de cancelar el scope: si solo se cancela, el socket
        // queda abierto y el timbre retiene el canal (BUSY para nosotros y para la
        // app oficial). Hay que soltar el socket y el puerto P2P aqui mismo.
        runCatching { sdkLive?.closeNow() }
        sdkLive = null
        currentDeviceId?.let { runCatching { container.closeSdkPort(it) } }
        runCatching { audioTrack?.stop(); audioTrack?.release() }
        audioTrack = null
        runCatching { releaseVideoCodec() }
        scope.cancel()
    }

    companion object {
        /** A-law (G711) a PCM 16-bit little endian. */
        fun alawToPcm(alaw: ByteArray): ByteArray {
            val out = ByteArray(alaw.size * 2)
            for (i in alaw.indices) {
                val a = alaw[i].toInt() and 0xFF
                val sign = a and 0x80
                val exponent = (a shr 4) and 0x07
                val mantissa = a and 0x0F
                var sample = (mantissa shl (exponent + 3)) + (0x08 shl exponent)
                if (exponent == 0) sample = (mantissa shl 4) + 8
                val value = if (sign != 0) -sample else sample
                out[i * 2] = (value and 0xFF).toByte()
                out[i * 2 + 1] = ((value shr 8) and 0xFF).toByte()
            }
            return out
        }

        /** mu-law (G711) a PCM 16-bit little endian. */
        fun mulawToPcm(mulaw: ByteArray): ByteArray {
            val out = ByteArray(mulaw.size * 2)
            for (i in mulaw.indices) {
                var u = (mulaw[i].toInt() xor 0xFF) and 0xFF
                val exponent = (u shr 4) and 0x07
                val mantissa = u and 0x0F
                var sample = ((mantissa shl (exponent + 3)) + 0x84) shl exponent
                sample -= 0x84
                val value = if (u and 0x80 != 0) -sample else sample
                out[i * 2] = (value and 0xFF).toByte()
                out[i * 2 + 1] = ((value shr 8) and 0xFF).toByte()
            }
            return out
        }
    }
}
