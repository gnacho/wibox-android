package dev.nacho.wibox.core.cloud.quii

import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Tunel RB-UDP del stack Quvii (puente UDP hacia el peer P2P que transporta el
 * protocolo QUII). Puerto del repositorio de referencia quii-helper
 * (protocols/rbudp: tunnel/session.py + lifecycle.py + control/{dispatcher,handshake,
 * flow,progress,play_status}.py + live/play_probes.py), fiel al wire capturado.
 *
 * Maquina de estados (status words de 28 bytes, checksum nativo de RbUdpPackets):
 * BOOTSTRAP(FFFF4100) -> SYN_ACK(FFFF4900) -> ack BOOTSTRAP(delta 0x44) ->
 * wrapped connect(0x120101) -> ESTABLISHED(FFFF0900) -> flow(FFFF0D00) ->
 * QUII setup (wrapped data) -> POST_SETUP(FED80900) -> ack(delta 0x30) ->
 * QUII play (wrapped data) -> POST_PLAY(FEB80900) -> ack(delta 0x34) ->
 * STREAM_READY(FF480900) -> ack(delta 0x3C) + play sync ->
 * PLAY_SYNC(FF780900) stages STAGE2..6 -> quii ready -> media (0x0500/0x1900 @0x1C).
 */
class RbUdpTunnel(
    private val socket: DatagramSocket,
    private val peer: InetSocketAddress,
    val p2pSessionFlag: String,
    private val connectTimeoutMs: Long = 15_000L,
    private val enablePlayProbes: Boolean = true,
    private val dataEncodeKey: ByteArray? = null,
    private val dynamicPassword: String? = null,
    private val quiiChannel: Int = 0,
    private val quiiStream: Int = 0,
) {
    class Rejected(message: String) : Exception(message)

    /** Estado de la lane logica (una sola stream en esta implementacion). */
    private var laneWord4 = 0
    private var laneWord8 = 0
    private var laneLocalId = 0
    private var laneRemoteId = 0
    private var rxStreamNextRemoteId: Int? = null
    private var rxStreamPackets = 0
    private var rxKcpBuffer = ByteArray(0)
    private var connectId = 0
    private var peerLogicId = 0

    private var flowSent = false
    private var progressSent = false
    private var setupProbeSent = false
    private var pendingEstablished = false
    private var quiiPlaySent = false
    private var quiiSetupSent = false
    private var connectSent = false

    /** dest_id asignado por el device en la respuesta KcpConnect (handle_connect_frame). */
    private var quiiDestId = 0
    private val quiiNextSeq = AtomicInteger(2)

    // Play sync
    private val playSyncActive = AtomicBoolean(false)
    private var playSyncStage = 0
    private var playSyncLocalId = 0
    private var playSyncRemoteId = 0
    private var playSyncSentCount = 0
    private var probeStage = 0
    private val nonce = AtomicInteger((0 until 0x10000).random())

    val connected = CompletableDeferred<Unit>()
    val quiiReady = CompletableDeferred<Unit>()
    private val stopped = AtomicBoolean(false)

    /** Bytes QUII descapsulados del tunel (media/comandos hacia la sesion live). */
    val quiiIncoming = kotlinx.coroutines.flow.MutableSharedFlow<ByteArray>(extraBufferCapacity = 4096)

    /** Callbacks de fases para la sesion live (setup/play los envia la sesion). */
    var onEstablished: (() -> Unit)? = null
    var onSetupAck: (() -> Unit)? = null
    var onPlayAck: (() -> Unit)? = null

    private var rxJob: Job? = null
    private var playSyncJob: Job? = null

    fun start(scope: CoroutineScope) {
        socket.soTimeout = 500
        rxJob = scope.launch(Dispatchers.IO) { receiveLoop() }
        // Bootstrap control (word4=0, word8=0x01000000 bootstrap lane0, ids 0/0)
        sendControl(RbUdpConstants.CONTROL_BOOTSTRAP_STATUS, word4 = 0, word8 = 0x01000000, localId = 0, remoteId = 0)
    }

    /** Espera conectado (logical connect response); el connect lo lanza el SYN_ACK. */
    suspend fun awaitConnected() {
        withTimeoutOrNull(connectTimeoutMs) { connected.await() }
            ?: throw Rejected("RB-UDP tunnel not connected within ${connectTimeoutMs}ms")
    }

    fun close() {
        stopped.set(true)
    }

    // ------------------------------------------------------------------ TX

    private fun sendUdp(packet: ByteArray) {
        val data = packet.copyOf(packet.size)
        socket.send(DatagramPacket(data, data.size, peer))
    }

    private fun sendControl(statusWord: Int, word4: Int, word8: Int, localId: Int, remoteId: Int) {
        sendUdp(RbUdpPackets.buildControl(word4, word8, localId, remoteId, statusWord))
    }

    private fun sendWrapped(inner: ByteArray, tag8: ByteArray, statusWord: Int = RbUdpConstants.WRAPPED_STATUS) {
        val packet = RbUdpPackets.buildWrapped(
            innerPacket = inner,
            word4 = laneWord4,
            word8 = laneWord8,
            localId = laneLocalId,
            remoteId = laneRemoteId,
            statusWord = statusWord,
            tag8 = tag8,
        )
        println("WIBOX-TUNNEL WRAPPED-OUT w4=%08x w8=%08x l=%08x r=%08x inner=${inner.size}B head=${inner.take(24).joinToString("") { "%02x".format(it) }}".format(laneWord4, laneWord8, laneLocalId, laneRemoteId))
        sendUdp(packet)
        // Native RBUDP avanza conn+0x9c con la longitud del inner tras cada envio
        laneLocalId = (laneLocalId + inner.size) and 0xFFFFFFFF.toInt()
    }

    /** Envia el comando interno connect dentro de un wrapped (una sola vez). */
    private fun sendWrappedConnect() {
        if (connectSent) return
        connectSent = true
        val inner = RbUdpPackets.buildKcpConnect(srcId = RbUdpConstants.SRC_ID_BASE, channel = 0)
        sendWrapped(inner, tag8 = RbUdpConstants.hexBytes(CONNECT_TAG_HEX))
    }

    /** RB data frame (cmd 0x120103) con los ids reales lane->device y seq incremental. */
    private fun sendWrappedData(
        payload: ByteArray,
        seq: Int = quiiNextSeq.getAndIncrement(),
        tag8: ByteArray = RbUdpConstants.hexBytes(DATA_TAG_HEX),
    ) {
        val inner = RbUdpPackets.buildRbData(
            payload = payload,
            srcId = RbUdpConstants.SRC_ID_BASE,
            destId = quiiDestId,
            seq = seq,
        )
        println("WIBOX-TUNNEL DATA-OUT seq=$seq destId=%08x payload=${payload.size}B head=${payload.take(4).joinToString("") { "%02x".format(it) }}".format(quiiDestId))
        sendWrapped(inner, tag8 = tag8)
    }

    private fun sendDataAck(payloadLength: Int, seq: Int) {
        val inner = RbUdpPackets.buildRbDataAck(
            payloadLength = payloadLength,
            seq = seq,
            srcId = laneLocalId,
            destId = laneRemoteId,
        )
        sendWrapped(inner, tag8 = RbUdpConstants.hexBytes(DATA_TAG_HEX))
    }

    /** ACK de control con delta remoto (session.py _send_offset_ack). */
    private fun sendOffsetAck(status: Int, remoteDelta: Int, control: RbControl) {
        sendControl(
            statusWord = status,
            word4 = control.word8 + remoteDelta,
            word8 = control.word4,
            localId = laneLocalId,
            remoteId = laneRemoteId,
        )
    }

    private fun sendFragmentAck(control: RbControl) {
        // data-ack interno + control de fragment ack (flow/heartbeat con ids>1)
        sendDataAck(payloadLength = 0, seq = 0)
        sendControl(
            statusWord = RbUdpConstants.CONTROL_HEARTBEAT_REQUEST_STATUS,
            word4 = control.word8,
            word8 = control.word4,
            localId = laneLocalId,
            remoteId = laneRemoteId,
        )
    }

    // ------------------------------------------------------------------ RX

    private suspend fun receiveLoop() {
        val buf = ByteArray(4096)
        val packet = DatagramPacket(buf, buf.size)
        while (!stopped.get() && kotlinx.coroutines.currentCoroutineContext().isActive) {
            try {
                socket.receive(packet)
            } catch (_: java.net.SocketTimeoutException) {
                continue
            } catch (_: Exception) {
                if (stopped.get()) break else continue
            }
            val raw = packet.data.copyOf(packet.length)
            if (raw.size < 4) continue
            if (readU32Le(raw, 0) != RbUdpConstants.MARKER.toInt()) continue
            val status = readU32Le(raw, 0x14)
            when {
                // (2) datos RB-UDP (stream/direct): payload en 0x1C
                raw.size >= 0x1C && (status and 0x1000) != 0 -> handleDataPayload(raw)
                // (3-4) wrapped fragment o payload directo
                raw.size >= 0x20 -> {
                    val innerMarker = readU32Le(raw, 0x1C)
                    if (innerMarker != -1) {
                        when (status and 0xFFFF) {
                            STREAM_STATUS16 -> handleStreamPayload(raw)
                            DIRECT_STATUS16 -> handleDataPayload(raw)
                            else -> handleWrapped(raw)
                        }
                    } else {
                        handleWrapped(raw)
                    }
                }
                // (5) control de 28 bytes
                raw.size == 28 -> dispatchControl(RbUdpPackets.parseControl(raw))
                // filler 0xBB y longitudes raras: descartados
            }
        }
    }

    private suspend fun handleStreamPayload(raw: ByteArray) {
        val payload = raw.copyOfRange(0x1C, raw.size)
        if (payload.isEmpty() || payload.all { it == 0xBB.toByte() }) return
        if (quiiPlaySent) quiiIncoming.emit(payload)
    }

    private suspend fun handleDataPayload(raw: ByteArray) {
        val status16 = readU32Le(raw, 0x14) and 0xFFFF
        if (status16 != STREAM_STATUS16 && status16 != DIRECT_STATUS16) return
        val buffer = raw.copyOfRange(0x1C, raw.size)
        if (buffer.isEmpty()) return
        val packetLocalId = readU32Le(raw, 0x0C).toInt()
        val expected = rxStreamNextRemoteId
        if (expected == null || packetLocalId == expected) {
            rxStreamNextRemoteId = packetLocalId + buffer.size
            rxKcpBuffer += buffer
            if (rxStreamPackets < 12) {
                println("WIBOX-TUNNEL STREAM-RX status=%08x local=%08x remote=%08x w4=%08x w8=%08x payload=${buffer.size}B head=${buffer.take(32).joinToString("") { "%02x".format(it) }}".format(readU32Le(raw, 0x14).toInt(), packetLocalId, readU32Le(raw, 0x10).toInt(), readU32Le(raw, 0x04).toInt(), readU32Le(raw, 0x08).toInt()))
            }
            // receive/stream.py feed(): conserva un frame KCP parcial para el siguiente paquete.
            var offset = 0
            var anyFrame = false
            while (offset + 0x18 <= rxKcpBuffer.size) {
                if (readU32Le(rxKcpBuffer, offset).toInt() != -1) break
                val packetLen = readU32Le(rxKcpBuffer, offset + 4).toInt()
                if (packetLen < 0x18 || packetLen > 20 * 1024 * 1024) break
                if (packetLen > rxKcpBuffer.size - offset) break
                dispatchKcpFrame(rxKcpBuffer.copyOfRange(offset, offset + packetLen))
                anyFrame = true
                offset += packetLen
            }
            rxKcpBuffer = rxKcpBuffer.copyOfRange(offset, rxKcpBuffer.size)
            if (!anyFrame && offset == 0 && rxKcpBuffer.size >= 4 && readU32Le(rxKcpBuffer, 0).toInt() != -1) {
                quiiIncoming.emit(rxKcpBuffer)
                rxKcpBuffer = ByteArray(0)
            }
        }
        // ACK acumulativo del stream: tambien para duplicados y paquetes fuera de orden.
        val ackRemoteId = rxStreamNextRemoteId ?: return
        sendControl(
            RbUdpConstants.CONTROL_ESTABLISHED_STATUS,
            word4 = readU32Le(raw, 0x08).toInt(),
            word8 = readU32Le(raw, 0x04).toInt(),
            localId = laneLocalId,
            remoteId = ackRemoteId,
        )
        rxStreamPackets++
        if (rxStreamPackets <= 12 || rxStreamPackets % 100 == 0) {
            println("WIBOX-TUNNEL STREAM-ACK next=%08x local=%08x inOrder=${expected == null || packetLocalId == expected}".format(ackRemoteId, packetLocalId))
        }
    }

    private suspend fun dispatchKcpFrame(frame: ByteArray) {
        when (readU32Le(frame, 0x14)) {
            RbUdpConstants.CMD_CONNECT -> handleConnectFrameBytes(frame)
            RbUdpConstants.CMD_DATA -> {
                val parsed = try {
                    RbUdpPackets.parseRbData(frame)
                } catch (_: Exception) {
                    return
                }
                if (RbUdpPackets.isRbDataAck(frame)) {
                    // handle_setup_ack_frame: ack con seq==0 = respuesta al setup
                    if (parsed.seq == 0) {
                        quiiSetupSent = true
                        onSetupAck?.invoke()
                        if (parsed.payload.isNotEmpty()) quiiIncoming.emit(parsed.payload)
                    }
                    return
                }
                if (parsed.payload.isNotEmpty()) {
                    if (parsed.seq < 10 || parsed.seq % 100 == 0) {
                        println("WIBOX-TUNNEL DATA-IN seq=${parsed.seq} payload=${parsed.payload.size}B head=${parsed.payload.take(4).joinToString("") { "%02x".format(it) }}")
                    }
                    if (parsed.seq == 0 && parsed.payload.size == 32) {
                        println("WIBOX-TUNNEL SETUP-RESP ${parsed.payload.joinToString("") { "%02x".format(it) }}")
                    }
                    quiiIncoming.emit(parsed.payload)
                }
            }
        }
    }

    private suspend fun handleConnectFrameBytes(frame: ByteArray) {
        val response = RbUdpPackets.parseKcpConnectResponse(frame)
        println("WIBOX-TUNNEL CONNECT-FRAME connectId=%08x typeFlag=${response.typeFlag} destId=%08x".format(response.connectId, response.destId))
        if (response.connectId == RbUdpConstants.SRC_ID_BASE &&
            response.typeFlag == 1 &&
            response.destId != 0
        ) {
            quiiDestId = response.destId
            if (!setupProbeSent) {
                setupProbeSent = true
                quiiSetupSent = true
                // Quii setup EN CLARO (crypto_mode=0) enmarcado como RB data seq=0
                sendWrappedData(QuiiEnvelope.buildSetup(), seq = 0)
            }
        }
    }

    private suspend fun handleWrapped(raw: ByteArray) {
        if (raw.size == 164) return
        val wrapped = try {
            RbUdpPackets.parseWrapped(raw)
        } catch (_: Exception) {
            println("WIBOX-TUNNEL WRAPPED-PARSE-FAIL len=${raw.size}")
            return
        }
        val inner = wrapped.innerPacket
        val parsed = try {
            RbUdpPackets.parseRbData(inner)
        } catch (_: Exception) {
            println("WIBOX-TUNNEL RBPARSE-FAIL len=${inner.size} head=${inner.take(8).joinToString("") { "%02x".format(it) }}")
            return
        }
        if (RbUdpPackets.isRbDataAck(inner)) {
            // frame_handler.handle_setup_ack_frame: ack con seq==0 = respuesta al setup
            if (parsed.seq == 0) {
                println("WIBOX-TUNNEL SETUP-ACK seq=0 payload=${parsed.payload.size}B clear=${parsed.payload.isNotEmpty() && (parsed.payload[0].toInt() and 0xFF) == 0xA9}")
                quiiSetupSent = true
                onSetupAck?.invoke()
                if (parsed.payload.isNotEmpty()) quiiIncoming.emit(parsed.payload)
            }
            return
        }
        if (parsed.command == RbUdpConstants.CMD_DATA && parsed.payload.isNotEmpty()) {
            println("WIBOX-TUNNEL DATA-IN seq=${parsed.seq} payload=${parsed.payload.size}B head=${parsed.payload.take(4).joinToString("") { "%02x".format(it) }}")
            // Native OnP2PRequData NO ackea data frames entrantes (fiabilidad del stream exterior)
            quiiIncoming.emit(parsed.payload)
        } else if (parsed.command == RbUdpConstants.CMD_CONNECT) {
            // frame_handler.handle_connect_frame
            handleConnectFrameBytes(inner)
        }
    }

    // ---------------------------------------------------- maquina de estados

    private fun dispatchControl(c: RbControl) {
        // (1) guardar ultimo control; (2) syn-ack fuera de lane
        val status16 = c.statusWord and 0xFFFF
        if (c.statusWord == RbUdpConstants.CONTROL_SYN_ACK_STATUS && c.remoteId != 0) {
            println("WIBOX-TUNNEL SYN_ACK word4=%08x word8=%08x remoteId=%08x".format(c.word4, c.word8, c.remoteId))
            // handshake.py: lane.word4=control.word8, lane.word8=bootstrap(0),
            // local_id=remote_id=control.remote_id; luego ESTABLISHED + wrapped connect
            laneWord4 = c.word8
            laneWord8 = 0x01000000 // handshake.py: lane.word8 = bootstrap_word8 (lane0)
            laneLocalId = c.remoteId
            laneRemoteId = c.remoteId
            sendControl(
                statusWord = RbUdpConstants.CONTROL_ESTABLISHED_STATUS,
                word4 = laneWord4,
                word8 = laneWord8,
                localId = laneLocalId,
                remoteId = laneRemoteId,
            )
            sendWrappedConnect()
            connected.complete(Unit)
            return
        }
        if (c.statusWord == RbUdpConstants.CONTROL_POST_CONNECT_STATUS) {
            println("WIBOX-TUNNEL CTRL POST_CONNECT word4=%08x word8=%08x l=%08x r=%08x".format(c.word4, c.word8, c.localId, c.remoteId))
            peerLogicId = c.word4
            sendOffsetAck(RbUdpConstants.CONTROL_POST_CONNECT_ACK_STATUS, remoteDelta = 0x38, control = c)
            return
        }
        when (c.statusWord) {
            RbUdpConstants.CONTROL_ESTABLISHED_STATUS -> handleEstablished(c)
            RbUdpConstants.CONTROL_PROGRESS_STATUS -> handleProgress(c)
            RbUdpConstants.CONTROL_POST_SETUP_STATUS -> {
                sendOffsetAck(RbUdpConstants.CONTROL_POST_SETUP_ACK_STATUS, remoteDelta = 0x30, control = c)
                val cb = onSetupAck
                quiiSetupSent = true
                cb?.invoke()
            }
            RbUdpConstants.CONTROL_POST_PLAY_STATUS -> {
                sendOffsetAck(RbUdpConstants.CONTROL_POST_PLAY_ACK_STATUS, remoteDelta = 0x34, control = c)
                val cb = onPlayAck
                quiiPlaySent = true
                cb?.invoke()
            }
            RbUdpConstants.CONTROL_STREAM_READY_STATUS -> {
                sendOffsetAck(RbUdpConstants.CONTROL_STREAM_READY_ACK_STATUS, remoteDelta = 0x3C, control = c)
                startPlaySync(c)
            }
            RbUdpConstants.CONTROL_PLAY_SYNC_STATUS -> handlePlaySync(c)
            RbUdpConstants.CONTROL_PLAY_LATE_STATUS -> resendPlayStageAck()
            else -> if (status16 == HEARTBEAT_STATUS16) handleHeartbeat(c)
        }
        if (pendingEstablished) {
            pendingEstablished = false
            sendControl(
                statusWord = RbUdpConstants.CONTROL_ESTABLISHED_STATUS,
                word4 = laneWord4,
                word8 = laneWord8,
                localId = laneLocalId,
                remoteId = laneRemoteId,
            )
        }
    }

    private fun handleHeartbeat(c: RbControl) {
        // dispatcher.py (5): ids>1 -> fragment ack; si no -> flow control una vez
        if (c.localId > 1 && c.remoteId > 1) {
            sendFragmentAck(c)
        } else {
            handleFlow(c)
        }
    }

    private fun handleFlow(c: RbControl) {
        // progress.py handle_flow_control
        if (c.localId == 1 && peerLogicId != 0 && !flowSent) {
            sendControl(
                statusWord = RbUdpConstants.CONTROL_FLOW_STATUS,
                word4 = laneWord4,
                word8 = laneWord8,
                localId = peerLogicId,
                remoteId = 1,
            )
            flowSent = true
        }
    }

    private fun handleEstablished(c: RbControl) {
        // progress.py handle_established_control: tras play enviado y ids validos,
        // ack play-established + arranque del play sync (base = control.local_id)
        if (quiiPlaySent && c.localId > 1 && c.remoteId > 1) {
            sendControl(
                statusWord = RbUdpConstants.CONTROL_PLAY_ESTABLISHED_ACK_STATUS,
                word4 = laneWord4,
                word8 = laneWord8,
                localId = c.remoteId,
                remoteId = c.localId,
            )
            startPlaySync(c)
        }
    }

    private fun handleProgress(c: RbControl) {
        // progress.py handle_progress_control: (a) reconnect hint; (b) progress ack
        val peer = peerLogicId
        if (peer != 0 && c.remoteId == peer && !progressSent && !setupProbeSent) {
            sendWrappedConnect()
            setupProbeSent = true
        }
        if (peer != 0 && c.localId == peer && c.remoteId == peer && !progressSent) {
            sendControl(
                statusWord = RbUdpConstants.CONTROL_PROGRESS_STATUS,
                word4 = laneWord4,
                word8 = laneWord8,
                localId = peer,
                remoteId = peer,
            )
            progressSent = true
            sendWrappedConnect()
        }
    }

    // ------------------------------------------------------------ play sync

    private fun startPlaySync(c: RbControl) {
        if (!playSyncActive.compareAndSet(false, true)) return
        // progress.py start_play_sync_from_control: base = control.local_id
        val base = if (c.localId > 1) c.localId else c.remoteId
        playSyncLocalId = (base + PLAY_SYNC_LOCAL_BIAS) and 0xFFFFFFFF.toInt()
        playSyncRemoteId = (base + PLAY_SYNC_STEP) and 0xFFFFFFFF.toInt()
        playSyncStage = 0
        playSyncSentCount = 0
        probeStage = 0
        playSyncJob?.cancel()
        playSyncJob = CoroutineScope(Dispatchers.IO).launch {
            while (!stopped.get() && isActive && !quiiReady.isCompleted) {
                // reenvio del inner de play (sync) + probes por umbral
                playSyncSentCount++
                maybeSendPlayProbe()
                delay(100)
            }
        }
    }

    private fun handlePlaySync(c: RbControl) {
        // session.py _handle_play_sync_status: avanza un stage por cada PLAY_SYNC
        val statuses = PLAY_SYNC_STAGE_STATUSES
        if (playSyncStage >= statuses.size) {
            quiiReady.complete(Unit)
            return
        }
        sendPlayStageAck(statuses[playSyncStage])
        playSyncStage++
        // deltas/biases acumulados por transicion de stage (constants.py)
        when (playSyncStage) {
            1 -> playSyncRemoteId = (playSyncRemoteId + STAGE2_REMOTE_BIAS)
            2 -> playSyncRemoteId = (playSyncRemoteId + STAGE3_REMOTE_BIAS)
            3 -> {
                playSyncLocalId = (playSyncLocalId + STAGE4_LOCAL_DELTA)
                playSyncRemoteId = (playSyncRemoteId + STAGE4_REMOTE_BIAS)
            }
            4 -> playSyncRemoteId = (playSyncRemoteId + STAGE5_REMOTE_BIAS)
            5 -> playSyncRemoteId = (playSyncRemoteId + STAGE6_REMOTE_BIAS)
        }
        if (playSyncStage >= statuses.size) quiiReady.complete(Unit)
    }

    private fun resendPlayStageAck() {
        // play_status.py handle_play_late_status: reenvia el ack del stage actual
        val idx = (playSyncStage - 1).coerceIn(0, PLAY_SYNC_STAGE_STATUSES.size - 1)
        sendPlayStageAck(PLAY_SYNC_STAGE_STATUSES[idx])
    }

    private fun sendPlayStageAck(statusWord: Int) {
        sendControl(
            statusWord = statusWord,
            word4 = laneWord4,
            word8 = laneWord8,
            localId = playSyncLocalId,
            remoteId = playSyncRemoteId,
        )
    }

    private fun maybeSendPlayProbe() {
        // live/play_probes.py maybe_send_play_probe (umbral 18/35/48)
        if (!enablePlayProbes) return
        when (probeStage) {
            0 -> if (playSyncSentCount >= 18) {
                sendPlayProbe(seq = 2, localDelta = -0x88, remoteDelta = +0x118, probe = PROBE2_PAYLOAD)
                probeStage = 1
            }
            1 -> if (playSyncSentCount >= 35) {
                playSyncLocalId = (playSyncLocalId + STAGE2_LOCAL_DELTA)
                playSyncRemoteId = (playSyncRemoteId + STAGE2_REMOTE_BIAS)
                sendPlayProbe(seq = 3, localDelta = 0, remoteDelta = -0x12, probe = PROBE3_PAYLOAD)
                probeStage = 2
            }
            2 -> if (playSyncSentCount >= 48) {
                sendPlayProbe(seq = 4, localDelta = 0, remoteDelta = -0x152, probe = PROBE4_PAYLOAD)
                probeStage = 3
            }
        }
    }

    private fun sendPlayProbe(seq: Int, localDelta: Int, remoteDelta: Int, probe: ByteArray) {
        val local = (playSyncLocalId + localDelta)
        val remote = (playSyncRemoteId + remoteDelta)
        val inner = RbUdpPackets.buildRbData(
            payload = probe,
            srcId = RbUdpConstants.SRC_ID_BASE,
            destId = PROBE_INNER_DEST,
            seq = seq,
        )
        sendUdp(
            RbUdpPackets.buildWrapped(
                innerPacket = inner,
                word4 = laneWord4,
                word8 = laneWord8,
                localId = local,
                remoteId = remote,
                statusWord = RbUdpConstants.WRAPPED_STATUS,
                tag8 = RbUdpConstants.hexBytes(PROBE_TAG_HEX),
            ),
        )
    }

    // ------------------------------------------------- API sesion QUII live

    /** Transporte de la sesion QUII: encapsula el inner QUII en wrapped data (RB frame). */
    fun sendQuii(bytes: ByteArray) {
        sendWrappedData(bytes)
    }

    /**
     * Camino directo (quii-helper live/sender.py): el setup ya lo envio el tunel al
     * recibir el connect frame. Devuelve false si no hay credenciales/destino.
     */
    fun sendQuiiSetupFramed(): Boolean = quiiDestId != 0

    /** Espera activa a que el device responda el wrapped connect (quiiDestId != 0). */
    suspend fun awaitQuiiDestId(timeoutMs: Long = 12_000): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (quiiDestId == 0 && System.currentTimeMillis() < deadline && !stopped.get()) {
            kotlinx.coroutines.delay(100)
        }
        return quiiDestId != 0
    }

    /** Play cifrado (crypto_mode=2) enmarcado: adminapp + dynamic_password + path. */
    fun sendQuiiPlayFramed(): Boolean {
        val key = dataEncodeKey ?: return false
        val pass = dynamicPassword ?: return false
        if (quiiDestId == 0) return false
        val path = "/mode=real&idc=$quiiChannel&ids=$quiiStream&ap=2"
        val body = QuiiEnvelope.playBody("adminapp", pass, path)
        val packet = QuiiEnvelope.encryptPacket(
            packetType = QuiiEnvelope.TYPE_PLAY,
            body = body,
            key = key,
            b13 = quiiChannel and 0xFF,
            b14 = (quiiChannel shr 8) and 0xFF,
            flag15 = 1,
            flag16 = (quiiStream - 1) and 0xFF,
            timestamp = System.currentTimeMillis() / 1000,
        )
        sendWrappedData(packet, seq = 1, tag8 = RbUdpConstants.hexBytes(PLAY_TAG_HEX))
        quiiPlaySent = true
        return true
    }

    /** Keepalive cifrado (crypto_mode=2, tipo 0x00) con seq incremental. */
    fun sendQuiiKeepaliveFramed(): Boolean {
        val key = dataEncodeKey ?: return false
        if (quiiDestId == 0) return false
        val packet = QuiiEnvelope.buildKeepalive(key, System.currentTimeMillis() / 1000)
        sendWrappedData(packet)
        return true
    }

    /** Marca el play enviado (gate del established/play sync de progress.py). */
    fun markPlaySent() {
        quiiPlaySent = true
    }

    companion object {
        private const val CONNECT_TAG_HEX = "696f6e3a3a4f7065" // "ion::Ope"
        private const val DATA_TAG_HEX = "6164282920207374" // "ad()  st"
        private const val PLAY_TAG_HEX = "61006e0064007200"
        private const val PROBE_TAG_HEX = "0000803f0050c3c7"
        private const val STREAM_STATUS16 = 0x0500
        private const val DIRECT_STATUS16 = 0x1900
        private const val HEARTBEAT_STATUS16 = 0x0D00
        private const val PROBE_INNER_DEST = 0x0B030000
        private const val PLAY_SYNC_LOCAL_BIAS = 0x140
        private const val PLAY_SYNC_STEP = 0x58C
        private const val STAGE2_LOCAL_DELTA = 0x88
        private const val STAGE2_REMOTE_BIAS = -0xF2
        private const val STAGE3_REMOTE_BIAS = -0x152
        private const val STAGE4_LOCAL_DELTA = 0x88
        private const val STAGE4_REMOTE_BIAS = 0xA1
        private const val STAGE5_REMOTE_BIAS = -0xF7
        private const val STAGE6_REMOTE_BIAS = -0x202
        private val PLAY_SYNC_STAGE_STATUSES = intArrayOf(
            0xFB540900.toInt(),
            0xFAD40900.toInt(),
            0xFE0D0900.toInt(),
            0xFC0C0900.toInt(),
            0xFB7F0900.toInt(),
        )
        private val PROBE2_PAYLOAD = RbUdpConstants.hexBytes(
            "c92a42d0f7ae17f0e7452a46ecfe5c4bc34b116aea1895dea5faec8f5a051f12" +
                "628d07bf32b0020b7bc3fdee22102946aff8e68130346b50e4e7ce77f85693" +
                "a266aeb42f1bfc291abe335ac2254602be",
        )
        private val PROBE3_PAYLOAD = RbUdpConstants.hexBytes(
            "939cd332393f73adee029583c588f74b17a6d27bb938de0c904c2a9a4499830c" +
                "cf41af8852573ff6841eb9e753bbe64e55a11539403019424d80b692aaf030" +
                "d1d0148a96a307f1a1c34ab4722c9b4303",
        )
        private val PROBE4_PAYLOAD = RbUdpConstants.hexBytes(
            "900ed22985e6500a6f60b4670871d7fdcded3931d7cfcca249494621adf3fba4" +
                "e795166764cac338b143da5d0749726952aacd7f96452f0e5fd5c429c9ca10" +
                "6564340dca828431e2a6e3cf30b3c88fe5",
        )

        fun readU32Le(b: ByteArray, off: Int): Int =
            (b[off].toInt() and 0xFF) or
                ((b[off + 1].toInt() and 0xFF) shl 8) or
                ((b[off + 2].toInt() and 0xFF) shl 16) or
                ((b[off + 3].toInt() and 0xFF) shl 24)

        fun readU16Le(b: ByteArray, off: Int): Int =
            (b[off].toInt() and 0xFF) or ((b[off + 1].toInt() and 0xFF) shl 8)
    }
}
