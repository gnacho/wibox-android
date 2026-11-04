package dev.nacho.wibox.core.cloud.quii

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.EOFException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket

/**
 * QUII over a plain TCP socket.
 *
 * The vendor P2P SDK maps a device port to a local listener, so the doorbell
 * protocol runs over 127.0.0.1 exactly like the stock player does. The envelope
 * is self describing (32 byte header plus its declared size), which makes the
 * stream framing trivial.
 */
class TcpQuiiTransport(
    private val host: String,
    private val port: Int,
    private val cryptoKey: ByteArray,
    private val logPrefix: String = "WIBOX-TCP",
) : QuiiTransport {

    private val socket = Socket()

    private var output: OutputStream? = null

    private var input: InputStream? = null

    private val writeLock = Any()

    @Volatile
    var firstBytesHex: String = ""

    fun connect(timeoutMs: Int = 10_000) {
        socket.tcpNoDelay = true
        socket.connect(InetSocketAddress(host, port), timeoutMs)
        socket.soTimeout = 0
        output = socket.getOutputStream()
        input = socket.getInputStream()
        android.util.Log.d(logPrefix, "conectado a $host:$port")
    }

    override suspend fun send(bytes: ByteArray) {
        val stream = output ?: throw IllegalStateException("tcp sin conexion")
        synchronized(writeLock) {
            stream.write(bytes)
            stream.flush()
        }
    }

    override fun incoming(): Flow<ByteArray> = flow {
        val stream = input ?: throw IllegalStateException("tcp sin conexion")
        // The first reply is the setup acknowledgement: 32 clear bytes, no body.
        // The stock client reads exactly 32 bytes and never decrypts them.
        var setupPending = true
        while (true) {
            val header = ByteArray(HEADER_BYTES)
            readFully(stream, header)
            val setupReply = setupPending
            setupPending = false
            val clear = if (setupReply) header else QuiiCrypto.aesCbcDecrypt(cryptoKey, header)
            val parsed = QuiiEnvelope.parseHeader(clear)
            val size = if (setupReply) 0 else parsed.readSize
            if (setupReply) {
                firstBytesHex = header.joinToString("") { "%02x".format(it) }
                android.util.Log.d(
                    logPrefix,
                    "setup reply type=0x${parsed.packetType.toString(16)} status=${clear[9]} crypto=${clear[10]} sha=${clear[11]}",
                )
            } else {
                validate(parsed, size)
            }
            if (size < 0 || size > MAX_PACKET_BYTES) {
                throw IllegalStateException("QUII tamano invalido $size")
            }
            val body = ByteArray(size)
            readFully(stream, body)
            emit(header + body)
        }
    }.flowOn(Dispatchers.IO)

    override fun close() {
        runCatching { socket.close() }
    }

    private fun validate(header: QuiiHeader, size: Int) {
        if (!header.isMedia && header.packetType !in COMMAND_TYPES) {
            throw IllegalStateException("QUII tipo invalido 0x${header.packetType.toString(16)}")
        }
        if (size <= 0 || size > MAX_PACKET_BYTES) {
            throw IllegalStateException("QUII tamano invalido $size")
        }
        if (header.isMedia) {
            require(header.flag15 == 0) { "prefijo de media cifrado no soportado" }
            require(header.commandPayloadSize % 16 == 0) { "prefijo cifrado desalineado" }
            require(header.commandPayloadSize <= size) { "prefijo mayor que el paquete" }
            require(header.mediaPayloadOffset <= size) { "offset de media fuera del paquete" }
        } else {
            require(size % 16 == 0) { "comando cifrado desalineado ($size)" }
            require(size >= 32) { "comando cifrado demasiado corto ($size)" }
        }
    }

    private fun readFully(stream: InputStream, target: ByteArray) {
        var read = 0
        while (read < target.size) {
            val count = stream.read(target, read, target.size - read)
            if (count < 0) throw EOFException("tcp cerrado en $read/${target.size}")
            read += count
        }
    }

    private companion object {
        const val HEADER_BYTES = 32
        const val MAX_PACKET_BYTES = 5 * 1024 * 1024
        val COMMAND_TYPES = setOf(0, 1, 5, 6, 7, 8, 9, 10, 12, 13, 14, 15, 17, 0xAA, 0xFE)
    }
}
