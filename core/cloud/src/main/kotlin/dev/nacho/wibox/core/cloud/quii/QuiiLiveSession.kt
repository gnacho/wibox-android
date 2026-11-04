package dev.nacho.wibox.core.cloud.quii

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

interface QuiiTransport {
    suspend fun send(bytes: ByteArray)

    /** Camino enmarcado (tunel RB-UDP). false = camino LAN legacy. */
    suspend fun sendSetup(): Boolean = false
    suspend fun sendPlay(): Boolean = false
    suspend fun sendKeepalive(): Boolean = false
    fun incoming(): Flow<ByteArray>
    fun close()
}

data class QuiiLiveConfig(
    val username: String,
    val password: String,
    val channel: Int = 0,
    val stream: Int = 0,
    val cryptoKey: ByteArray,
    val keepaliveIntervalMs: Long = 10_000L,
)

class QuiiLiveSession(
    private val transport: QuiiTransport,
    private val config: QuiiLiveConfig,
    private val scope: CoroutineScope,
) {
    sealed class OpenResult {
        data object Success : OpenResult()
        data class Rejected(val status: Int, val cryptoMode: Int, val shaProfile: Int) : OpenResult()
    }

    private val framesFlow = MutableSharedFlow<MediaFrame>(extraBufferCapacity = 256)
    private val commandPackets = MutableSharedFlow<DecodedQuiiPacket>(extraBufferCapacity = 64)
    private var keepaliveJob: Job? = null
    private var readerJob: Job? = null
    private var rxPacketCount = 0
    @Volatile private var commandSequence = 0L
    private val setupAck = CompletableDeferred<ByteArray>()
    private val opened = MutableStateFlow(false)

    /** Motivo de cuelgue que envia el timbre (1 = canal ocupado). */
    val hangUpReason = MutableStateFlow<Int?>(null)

    val frames: Flow<MediaFrame> get() = framesFlow

    /**
     * Comando transparente 0xFE (canal device embebido en el stream QUII).
     * Envía [body] cifrado y espera la respuesta 0xFE descifrada (el reader
     * verifica el SHA256 header+body igual que el nativo).
     */
    suspend fun sendTransparent(body: ByteArray, timeoutMs: Long = 8_000, command: Int): ByteArray = coroutineScope {
        val startSequence = commandSequence
        val response = async(start = CoroutineStart.UNDISPATCHED) {
            withTimeout(timeoutMs) {
                commandPackets.first {
                    it.header.packetType == 0xFE && commandSequence > startSequence &&
                        (it.header.b13 == command || it.header.b14 == command)
                }
            }
        }
        try {
            transport.send(
                QuiiEnvelope.encryptPacket(
                    0xFE,
                    body,
                    config.cryptoKey,
                    b13 = command,
                    timestamp = System.currentTimeMillis() / 1000,
                ),
            )
        } catch (t: Throwable) {
            response.cancel()
            throw t
        }
        response.await().commandBytes ?: ByteArray(0)
    }

    suspend fun open(): OpenResult {
        readerJob = scope.launch { runReader() }
        if (!transport.sendSetup()) {
            transport.send(QuiiEnvelope.buildSetup())
        }
        val setupResponse = withTimeout(8_000) { setupAck.await() }
        require(setupResponse.size >= 12) { "setup response too short" }
        val status = setupResponse[9].toInt() and 0xFF
        val cryptoMode = setupResponse[10].toInt() and 0xFF
        val shaProfile = setupResponse[11].toInt() and 0xFF
        if (setupResponse[0].toInt() and 0xFF != QuiiEnvelope.TYPE_SETUP) {
            return OpenResult.Rejected(status, cryptoMode, shaProfile)
        }
        if (status != 0) return OpenResult.Rejected(status, cryptoMode, shaProfile)
        println("WIBOX-QUII SETUP status=$status crypto=$cryptoMode sha=$shaProfile")
        if (cryptoMode != 2 || shaProfile != 1) return OpenResult.Rejected(status, cryptoMode, shaProfile)

        val body = QuiiEnvelope.playBody(config.username, config.password, QuiiEnvelope.playTarget(config.channel, config.stream))
        // Libera cualquier sesion zombi que el timbre arrastre de intentos previos.
        hangUp()
        delay(300)
        if (!transport.sendPlay()) {
            transport.send(
                QuiiEnvelope.encryptPacket(
                    packetType = QuiiEnvelope.TYPE_PLAY,
                    body = body,
                    key = config.cryptoKey,
                    b13 = config.channel and 0xFF,
                    b14 = (config.channel shr 8) and 0xFF,
                    flag15 = 1,
                    flag16 = (config.stream - 1) and 0xFF,
                    flag17 = if (LiveTuning.inner) 1 else 0,
                ),
            )
        }
        opened.value = true
        println("WIBOX-QUII PLAY SENT")

        if (LiveTuning.probeOrder > 0) {
            delay(500)
            runCatching {
                val probe = sendTransparent(ByteArray(0), command = LiveTuning.probeOrder)
                println(
                    "WIBOX-QUII PROBE order=${LiveTuning.probeOrder} resp=" +
                        probe.joinToString("") { "%02x".format(it) },
                )
            }.onFailure { println("WIBOX-QUII PROBE order=${LiveTuning.probeOrder} fail=$it") }
        }

        keepaliveJob = scope.launch {
            while (opened.value) {
                delay(config.keepaliveIntervalMs)
                if (!opened.value) break
                runCatching {
                    if (!transport.sendKeepalive()) {
                        transport.send(QuiiEnvelope.buildKeepalive(config.cryptoKey))
                    }
                }
            }
        }
        return OpenResult.Success
    }

    /**
     * Reenvia el play: al reiniciar el stream el timbre emite un IDR nuevo, lo que
     * evita la espera de hasta un GOP completo con la pantalla en negro.
     */
    suspend fun resendPlay(): Boolean {
        if (!opened.value) return false
        val ok = runCatching { transport.sendPlay() }.getOrDefault(false)
        println("WIBOX-QUII PLAY RESENT ok=$ok")
        return ok
    }

    /**
     * Orden transparente 1 (hang up): libera la sesion que el timbre tenga
     * abierta. Sin esto el aparato se queda "ocupado" y rechaza el siguiente
     * play con un transparente 0x01.
     */
    suspend fun hangUp() {
        runCatching {
            transport.send(
                QuiiEnvelope.encryptPacket(
                    0xFE,
                    byteArrayOf(0),
                    config.cryptoKey,
                    b13 = 1,
                    timestamp = System.currentTimeMillis() / 1000,
                ),
            )
        }
        println("WIBOX-QUII HANGUP sent")
    }

    suspend fun close() {
        opened.value = false
        keepaliveJob?.cancel()
        readerJob?.cancel()
        hangUp()
        transport.close()
    }

    private suspend fun runReader() {
        runCatching { readLoop() }.onFailure {
            println("WIBOX-QUII RX END ${it.javaClass.simpleName}: ${it.message}")
        }
    }

    private suspend fun readLoop() {
        transport.incoming().collect { blob ->
            val packets = runCatching { QuiiEnvelope.decodeChained(blob, config.cryptoKey) }.getOrNull()
            if ((packets == null || packets.isEmpty()) && blob.size >= 32 && (blob[0].toInt() and 0xFF) == QuiiEnvelope.TYPE_SETUP) {
                if (!setupAck.isCompleted) setupAck.complete(blob.copyOf())
                return@collect
            }
            if (packets == null) {
                println("WIBOX-QUII RX UNDECODED blob=${blob.size}B")
                return@collect
            }
            for (packet in packets) {
                rxPacketCount++
                if (packet.header.packetType !in 0xA0..0xA3 || rxPacketCount <= 10 || rxPacketCount % 100 == 0) {
                    println("WIBOX-QUII RX type=%02x media=${packet.mediaPayload?.size ?: 0} command=${packet.commandBytes?.size ?: 0} header=${packet.headerBytes?.size ?: 0}".format(packet.header.packetType))
                    if (packet.header.packetType !in 0xA0..0xA3) {
                        println("WIBOX-QUII RX CMD hex=" + (packet.commandBytes ?: ByteArray(0)).take(64).joinToString("") { "%02x".format(it) })
                        println("WIBOX-QUII RX HDR hex=" + (packet.headerBytes ?: ByteArray(0)).joinToString("") { "%02x".format(it) })
                    }
                }
                val hb = packet.headerBytes
                if (hb != null && !setupAck.isCompleted) setupAck.complete(hb)
                // Notificacion de cuelgue del timbre: transparente 0xFE con
                // b13 = numero de orden (1 = HANG_UP). El primer byte del cuerpo
                // es el motivo (1 = canal ocupado).
                if (packet.header.packetType == 0xFE && packet.header.b13 == 1) {
                    val reason = packet.commandBytes?.firstOrNull()?.toInt()?.and(0xFF)
                    if (reason != null) {
                        println("WIBOX-QUII HANGUP reason=$reason")
                        hangUpReason.value = reason
                    }
                }
                val media = packet.mediaPayload
                if (media == null || media.isEmpty()) {
                    commandSequence++
                    commandPackets.tryEmit(packet)
                    continue
                }
                for (frame in CPacketDemuxer.demux(media)) {
                    framesFlow.emit(frame)
                }
            }
        }
    }

    fun onIncomingBytes(bytes: ByteArray) {
        if (!setupAck.isCompleted) setupAck.complete(bytes)
    }
}
