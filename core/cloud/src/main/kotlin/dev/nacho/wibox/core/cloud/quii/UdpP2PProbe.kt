package dev.nacho.wibox.core.cloud.quii

import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.SocketTimeoutException
import kotlin.random.Random

/** Peers/credenciales devueltos por p2pconnect (claves JSON capturadas del codec). */
data class P2PConnectInfo(
    val result: Int,
    val resultMessage: String?,
    val deviceId: String,
    val sessionFlag: String?,
    val responseSessionId: Long,
    val nettype: Int,
    val netSubtype: Int,
    val publicIp: String?,
    val publicUdpPort: Int,
    val localUdpPort: Int,
    val localIps: List<String>,
    val utdPublicIp: String?,
    val utdPublicUdpPort: Int,
)

data class P2PTestTarget(
    val kind: String,
    val host: String,
    val port: Int,
    val mode: Int,
)

data class P2PTestResponse(
    val resultCode: Int,
    val sessionFlag: String,
    val address: String,
    val port: Int,
    val statusCode: Int,
    val testId: Int,
) {
    val ok: Boolean get() = resultCode == 0 && statusCode == 0
}

data class ProbeResult(
    val response: P2PTestResponse,
    val peerAddr: InetSocketAddress,
    val rank: Int,
)

object P2PProbe {

    const val MARKER: Int = 0xFFABEFC1.toInt()
    const val CMD_TEST: Int = 0x00120002
    const val PROBE_SEQ_LAN = 0
    const val PROBE_SEQ_P2P = 1
    const val PROBE_SEQ_TRANS = 2

    /** targets.py: LAN (1a privada de loc-ip) -> p2p (publica) -> trans (UTD relay). */
    fun iterTargets(info: P2PConnectInfo, forceTrans: Int = 0): List<P2PTestTarget> {
        val targets = mutableListOf<P2PTestTarget>()
        val nettype = info.nettype
        info.localIps.firstOrNull { ip ->
            runCatching { InetAddress.getByName(ip).isSiteLocalAddress }.getOrDefault(false)
        }?.let { ip ->
            if (info.localUdpPort > 0) targets.add(P2PTestTarget("lan", ip, info.localUdpPort, 0))
        }
        if (forceTrans == 0 && (nettype and 0x1) == 0 && !info.publicIp.isNullOrBlank() && info.publicUdpPort > 0) {
            targets.add(P2PTestTarget("p2p", info.publicIp, info.publicUdpPort, 0))
        }
        if ((nettype and 0x2) == 0 && !info.utdPublicIp.isNullOrBlank() && info.utdPublicUdpPort > 0) {
            targets.add(P2PTestTarget("trans", info.utdPublicIp, info.utdPublicUdpPort, 1))
        }
        return targets
    }

    /** Frame de sondeo 0xA4 (test_packets.py, offsets verificados). */
    fun buildP2pTestPacket(
        probeSeq: Int,
        sessionFlag: String,
        responseSessionId: Long,
        localUdpPort: Int,
        targetIp: String,
        targetUdpPort: Int,
        testMode: Int,
        rand16: Int = Random.nextInt(0x10000),
    ): ByteArray {
        val p = ByteArray(0xA4)
        fun u32(off: Int, v: Int) = RbUdpPackets.let { QPut.u32(p, off, v) }
        fun u16(off: Int, v: Int) = QPut.u16(p, off, v)
        u32(0x00, MARKER)
        u32(0x1C, -1)
        u32(0x20, 0x88)
        u16(0x1A, 0xA4)
        u16(0x2A, rand16)
        u16(0x2C, 0x100)
        u16(0x2E, 0x0000)
        u32(0x30, CMD_TEST)
        u32(0x38, probeSeq)
        u32(0x40, 0x60)
        putCString(p, 0x44, 0x40, sessionFlag)
        u16(0x84, 0)
        u16(0x86, localUdpPort)
        u16(0x88, 0)
        u16(0x8A, localUdpPort)
        putCString(p, 0x8C, 0x10, targetIp)
        u16(0x9C, targetUdpPort)
        u32(0xA0, testMode)
        return p
    }

    /** Respuesta del sondeo: 2 formatos (KcpLinkPacket con flag o plano). */
    fun parseP2pTestResponse(packet: ByteArray): P2PTestResponse? {
        if (packet.size >= 0xA4 && readU32(packet, 0x30) == CMD_TEST) {
            val flag = readU16(packet, 0x2E)
            return P2PTestResponse(
                resultCode = if (flag == 1) 0 else 1,
                sessionFlag = readCString(packet, 0x44, 0x40),
                address = readCString(packet, 0x8C, 0x10),
                port = readU16(packet, 0x9C),
                statusCode = if (flag == 1) 0 else flag,
                testId = readU32(packet, 0xA0),
            )
        }
        if (packet.size >= 0x88) {
            return P2PTestResponse(
                resultCode = readU32(packet, 0x20),
                sessionFlag = readCString(packet, 0x28, 0x40),
                address = readCString(packet, 0x70, 0x10),
                port = readU16(packet, 0x80),
                statusCode = readU16(packet, 0x82),
                testId = readU32(packet, 0x84),
            )
        }
        return null
    }

    private fun rankOf(ip: String): Int = when {
        runCatching { InetAddress.getByName(ip).isSiteLocalAddress }.getOrDefault(false) -> 3
        ip.startsWith("127.") -> 2
        else -> 1
    }

    /**
     * run_udp_probe (udp_probe.py): 3 intentos por target; LAN (rank 3) corta en
     * seco; en otro caso se queda el mejor rank. Devuelve el peer ganador.
     */
    fun runProbe(
        socket: DatagramSocket,
        info: P2PConnectInfo,
        targetSessionFlag: String,
        responseSessionId: Long,
        localUdpPort: Int,
        timeoutMs: Int = 1500,
        attemptsPerTarget: Int = 3,
    ): ProbeResult {
        var best: ProbeResult? = null
        for (target in iterTargets(info)) {
            val packet = buildP2pTestPacket(
                probeSeq = probeSeqFor(target.kind),
                sessionFlag = targetSessionFlag,
                responseSessionId = responseSessionId,
                localUdpPort = localUdpPort,
                targetIp = target.host,
                targetUdpPort = target.port,
                testMode = target.mode,
            )
            val addr = InetSocketAddress(InetAddress.getByName(target.host), target.port)
            repeat(attemptsPerTarget) {
                try {
                    socket.send(DatagramPacket(packet, packet.size, addr))
                    val buf = ByteArray(4096)
                    val rx = DatagramPacket(buf, buf.size)
                    socket.soTimeout = timeoutMs
                    socket.receive(rx)
                    val parsed = parseP2pTestResponse(buf.copyOf(rx.length)) ?: return@repeat
                    if (!parsed.ok || parsed.address.isBlank() || parsed.port <= 0) return@repeat
                    val rank = rankOf(parsed.address)
                    val candidate = ProbeResult(parsed, InetSocketAddress(parsed.address, parsed.port), rank)
                    if (best == null || candidate.rank > best!!.rank) best = candidate
                    if (rank >= 3) return candidate
                } catch (_: SocketTimeoutException) {
                    // siguiente intento
                }
            }
        }
        return best ?: throw java.net.SocketTimeoutException("p2p probe: no peers answered")
    }

    private fun probeSeqFor(kind: String) = when (kind) {
        "lan" -> PROBE_SEQ_LAN
        "p2p" -> PROBE_SEQ_P2P
        else -> PROBE_SEQ_TRANS
    }

    private fun putCString(p: ByteArray, off: Int, max: Int, s: String) {
        val bytes = s.toByteArray(Charsets.UTF_8).copyOf(minOf(max - 1, s.length))
        System.arraycopy(bytes, 0, p, off, bytes.size)
    }

    private fun readCString(p: ByteArray, off: Int, max: Int): String {
        var end = off
        while (end < off + max && end < p.size && p[end] != 0.toByte()) end++
        return String(p, off, end - off, Charsets.UTF_8)
    }

    private fun readU32(p: ByteArray, off: Int): Int =
        (p[off].toInt() and 0xFF) or ((p[off + 1].toInt() and 0xFF) shl 8) or
            ((p[off + 2].toInt() and 0xFF) shl 16) or ((p[off + 3].toInt() and 0xFF) shl 24)

    private fun readU16(p: ByteArray, off: Int): Int =
        (p[off].toInt() and 0xFF) or ((p[off + 1].toInt() and 0xFF) shl 8)

    // helper de escritura que reutiliza la convencion LE de RbUdpPackets
    private object QPut {
        fun u32(p: ByteArray, off: Int, v: Int) {
            p[off] = v.toByte(); p[off + 1] = (v shr 8).toByte(); p[off + 2] = (v shr 16).toByte(); p[off + 3] = (v shr 24).toByte()
        }
        fun u16(p: ByteArray, off: Int, v: Int) {
            p[off] = v.toByte(); p[off + 1] = (v shr 8).toByte()
        }
    }
    data class ProbeResult2(val peerAddr: java.net.InetSocketAddress, val rank: Int, val acked: Boolean)

    /**
     * Probe combinado con hole punch: envia 0xA4 a los targets y CUALQUIER frame del
     * device (request flag=0) se ACK-ea + active packets (el device nos llama primero:
     * sin ACK nunca hay punch). Devuelve la direccion de transporte del device.
     */
    fun runProbeWithHandshake(
        socket: java.net.DatagramSocket,
        info: P2PConnectInfo,
        targetSessionFlag: String,
        responseSessionId: Long,
        localUdpPort: Int,
        timeoutMs: Long = 1500,
        attemptsPerTarget: Int = 3,
    ): ProbeResult2 {
        val targets = iterTargets(info)
        if (targets.isEmpty()) throw java.net.SocketTimeoutException("sin targets de probe")
        var best: ProbeResult2? = null
        val buf = ByteArray(4096)
        outer@ for (target in targets) {
            val probeSeq = when (target.kind) { "lan" -> 0; "p2p" -> 1; else -> 2 }
            val addr = java.net.InetSocketAddress(java.net.InetAddress.getByName(target.host), target.port)
            val packet = buildP2pTestPacket(probeSeq, targetSessionFlag, responseSessionId, localUdpPort, target.host, target.port, target.mode)
            var acked = false
            for (attempt in 0 until attemptsPerTarget) {
                socket.send(java.net.DatagramPacket(packet, packet.size, addr))
                val deadline = System.currentTimeMillis() + timeoutMs
                while (System.currentTimeMillis() < deadline) {
                    val dp = java.net.DatagramPacket(buf, buf.size)
                    try { socket.receive(dp) } catch (e: java.net.SocketTimeoutException) { break }
                    val data = dp.data.copyOfRange(0, dp.length)
                    val frame = parseP2pTransportFrame(data)
                    val sender = java.net.InetSocketAddress(dp.address, dp.port)
                    if (frame == null || !(frame.sessionFlag == targetSessionFlag || targetSessionFlag.startsWith(frame.sessionFlag))) continue
                    if (frame.packetTypeFlag == 1) {
                        // respuesta a nuestro probe
                        val parsed = parseP2pTestResponse(data)
                        if (parsed != null && parsed.ok) {
                            val rank = when { InetAddress.getByName(parsed.address).isSiteLocalAddress -> 3; InetAddress.getByName(parsed.address).isLoopbackAddress -> 2; else -> 1 }
                            return ProbeResult2(sender, rank, acked)
                        }
                        continue
                    }
                    // REQUEST del device (flag=0): ACK + active -> punch completado
                    val ack = buildP2pTransportAck(frame, localUdpPort)
                    socket.send(java.net.DatagramPacket(ack, ack.size, sender))
                    for (tail in intArrayOf(200, 102)) {
                        val act = buildP2pActivePacket(targetSessionFlag, kotlin.random.Random.nextInt(), localUdpPort, tail)
                        socket.send(java.net.DatagramPacket(act, act.size, sender))
                    }
                    acked = true
                    best = ProbeResult2(sender, 2, true)
                }
                if (acked) break@outer
            }
        }
        if (best != null) return best
        throw java.net.SocketTimeoutException("p2p probe: no peers answered")
    }

    /* ===== Active handshake (port de quii-helper handshake/active.py + transport/packets.py) ===== */

        /** Frame de transporte P2P (mismo layout 0xA4 que el probe, cmd 0x120002). */
        data class ParsedP2PTransportFrame(
            val packetTypeFlag: Int,
            val seq: Int,
            val sessionFlag: String,
            val remoteIp: String,
            val remotePort: Int,
            val tailCode: Int,
        ) {
            val isRequest: Boolean get() = packetTypeFlag == 0
        }

        fun parseP2pTransportFrame(packet: ByteArray): ParsedP2PTransportFrame? {
            if (packet.size < 0xA4) return null
            if (readU32(packet, 0x30) != CMD_TEST) return null
            return ParsedP2PTransportFrame(
                packetTypeFlag = readU16(packet, 0x2E),
                seq = readU32(packet, 0x38),
                sessionFlag = readCString(packet, 0x44, 0x40),
                remoteIp = readCString(packet, 0x8C, 0x10),
                remotePort = readU16(packet, 0x9C),
                tailCode = readU32(packet, 0xA0),
            )
        }

        private fun buildP2pTransportPacket(
            sessionFlag: String,
            seq: Int,
            localUdpPort: Int,
            remoteIp: String,
            remotePort: Int,
            tailCode: Int,
            packetTypeFlag: Int = 0,
        ): ByteArray {
            val p = ByteArray(0xA4)
            fun u32(off: Int, v: Int) = QPut.u32(p, off, v)
            fun u16(off: Int, v: Int) = QPut.u16(p, off, v)
            u32(0x00, MARKER)
            u16(0x1A, 0xA4)
            u32(0x1C, -1)
            u32(0x20, 0x88)
            u16(0x2A, Random.nextInt(0x10000))
            u16(0x2C, 0x100)
            u16(0x2E, packetTypeFlag)
            u32(0x30, CMD_TEST)
            u32(0x38, seq)
            u32(0x40, 0x60)
            putCString(p, 0x44, 0x40, sessionFlag)
            u16(0x84, 0)
            u16(0x86, localUdpPort)
            u16(0x88, 0)
            u16(0x8A, localUdpPort)
            putCString(p, 0x8C, 0x10, remoteIp)
            u16(0x9C, remotePort)
            u32(0xA0, tailCode)
            return p
        }

        fun buildP2pActivePacket(sessionFlag: String, seq: Int, localUdpPort: Int, tailCode: Int): ByteArray =
            buildP2pTransportPacket(sessionFlag, seq, localUdpPort, "", 0, tailCode)

        fun buildP2pTransportAck(frame: ParsedP2PTransportFrame, localUdpPort: Int): ByteArray =
            buildP2pTransportPacket(frame.sessionFlag, frame.seq, localUdpPort, frame.remoteIp, frame.remotePort, frame.tailCode, 1)

        /**
         * Handshake activo para peer PUBLICO: escucha frames del device (el device sondea
         * primero hacia nuestro mapping), ACK a cada request + active packets (tails 200/102).
         * Devuelve la direccion de transporte del peer (authoritativa) o peerHint como fallback.
         */
        fun runActiveHandshake(
            socket: DatagramSocket,
            sessionFlag: String,
            timeoutMs: Long,
            peerHint: InetSocketAddress,
        ): InetSocketAddress {
            val localUdpPort = socket.localPort
            socket.soTimeout = 500
            val deadline = System.currentTimeMillis() + timeoutMs
            var peer: InetSocketAddress? = null
            var activeSent = false
            var seenRequests = 0
            val buf = ByteArray(4096)
            fun sendActive(addr: InetSocketAddress) {
                for (tail in intArrayOf(200, 102)) {
                    val pkt = buildP2pActivePacket(sessionFlag, Random.nextInt(), localUdpPort, tail)
                    socket.send(DatagramPacket(pkt, pkt.size, addr))
                }
                activeSent = true
            }
            while (System.currentTimeMillis() < deadline) {
                val dp = DatagramPacket(buf, buf.size)
                try {
                    socket.receive(dp)
                } catch (e: java.net.SocketTimeoutException) {
                    if (!activeSent && peer != null) sendActive(peer)
                    continue
                }
                val data = dp.data.copyOfRange(0, dp.length)
                if (data.size == 28 || data.size == 164 && readU32(data, 0x1C).toInt() != -1) {
                    // posible frame RB-UDP/control del device: ignorar en esta fase
                }
                val frame = parseP2pTransportFrame(data) ?: continue
                if (frame.sessionFlag != sessionFlag && !sessionFlag.startsWith(frame.sessionFlag)) continue
                if (frame.isRequest && frame.remoteIp.isNotBlank() && frame.remotePort > 0) {
                    seenRequests++
                    peer = InetSocketAddress(InetAddress.getByName(frame.remoteIp), frame.remotePort)
                    val ack = buildP2pTransportAck(frame, localUdpPort)
                    socket.send(DatagramPacket(ack, ack.size, peer))
                    if (!activeSent) sendActive(peer)
                    if (seenRequests >= 2) break
                }
            }
            if (peer == null) {
                peer = peerHint
                if (!activeSent) sendActive(peerHint)
            }
            return peer
        }

}
