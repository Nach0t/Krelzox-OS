package dev.krelzox.remote.net

import java.io.DataInputStream
import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.util.Base64

/** Minimal RFC 6455 WebSocket framing: what a browser client needs, nothing more. */
object WebSocket {

    const val OP_CONTINUATION = 0x0
    const val OP_TEXT = 0x1
    const val OP_BINARY = 0x2
    const val OP_CLOSE = 0x8
    const val OP_PING = 0x9
    const val OP_PONG = 0xA

    /** Largest message accepted from a browser (commands are tiny). */
    const val MAX_INCOMING = 64 * 1024

    private const val GUID = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11"

    data class Frame(val opcode: Int, val payload: ByteArray, val fin: Boolean)

    fun acceptKey(clientKey: String): String =
        Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-1").digest((clientKey.trim() + GUID).toByteArray()))

    /**
     * Reads one frame. Client frames must be masked (RFC 6455 §5.1); oversized or
     * unmasked frames throw, which closes the connection.
     */
    fun readFrame(input: InputStream): Frame {
        val data = DataInputStream(input)
        val b0 = data.read()
        if (b0 < 0) throw EOFException()
        val b1 = data.readUnsignedByte()
        val fin = b0 and 0x80 != 0
        val opcode = b0 and 0x0F
        val masked = b1 and 0x80 != 0
        var length = (b1 and 0x7F).toLong()
        if (length == 126L) length = data.readUnsignedShort().toLong()
        else if (length == 127L) length = data.readLong()
        if (!masked) throw IOException("Unmasked client frame")
        if (length < 0 || length > MAX_INCOMING) throw IOException("Frame too large")
        val mask = ByteArray(4).also(data::readFully)
        val payload = ByteArray(length.toInt()).also(data::readFully)
        for (i in payload.indices) payload[i] = (payload[i].toInt() xor mask[i % 4].toInt()).toByte()
        return Frame(opcode, payload, fin)
    }

    /** Reads a complete message, answering pings and joining continuation frames. */
    fun readMessage(input: InputStream, output: OutputStream): Frame {
        var first: Frame? = null
        val parts = ArrayList<ByteArray>()
        var total = 0
        while (true) {
            val frame = readFrame(input)
            when (frame.opcode) {
                OP_PING -> synchronized(output) { writeFrame(output, OP_PONG, frame.payload) }
                OP_PONG -> Unit
                OP_CLOSE -> return frame
                else -> {
                    if (first == null) first = frame
                    parts += frame.payload
                    total += frame.payload.size
                    if (total > MAX_INCOMING) throw IOException("Message too large")
                    if (frame.fin) {
                        val joined = ByteArray(total)
                        var offset = 0
                        for (p in parts) {
                            p.copyInto(joined, offset)
                            offset += p.size
                        }
                        return Frame(first.opcode, joined, true)
                    }
                }
            }
        }
    }

    /** Writes one unmasked server frame. Callers synchronize on [output]. */
    fun writeFrame(output: OutputStream, opcode: Int, payload: ByteArray) {
        val header = when {
            payload.size < 126 -> byteArrayOf((0x80 or opcode).toByte(), payload.size.toByte())
            payload.size <= 0xFFFF -> byteArrayOf(
                (0x80 or opcode).toByte(), 126,
                (payload.size shr 8).toByte(), payload.size.toByte(),
            )
            else -> ByteArray(10).also { h ->
                h[0] = (0x80 or opcode).toByte()
                h[1] = 127
                val len = payload.size.toLong()
                for (i in 0 until 8) h[2 + i] = (len shr (56 - 8 * i)).toByte()
            }
        }
        output.write(header)
        output.write(payload)
        output.flush()
    }
}
