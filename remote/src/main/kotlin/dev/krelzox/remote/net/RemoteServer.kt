package dev.krelzox.remote.net

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import dev.krelzox.remote.control.Command
import dev.krelzox.remote.control.CommandParser
import dev.krelzox.remote.security.ControlState
import dev.krelzox.remote.security.Session
import dev.krelzox.remote.security.Sessions
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.util.concurrent.ConcurrentHashMap

/** One encoded screen image. */
class Frame(val seq: Long, val jpeg: ByteArray, val width: Int, val height: Int)

/** Latest-frame mailbox: viewers always get the newest image, never a backlog. */
class FrameHub {
    private val lock = Object()
    @Volatile var latest: Frame? = null
        private set

    fun publish(frame: Frame) = synchronized(lock) {
        latest = frame
        lock.notifyAll()
    }

    /** Waits up to [timeoutMs] for a frame newer than [afterSeq]. */
    fun awaitNewer(afterSeq: Long, timeoutMs: Long): Frame? = synchronized(lock) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while ((latest?.seq ?: -1) <= afterSeq) {
            val left = deadline - System.currentTimeMillis()
            if (left <= 0) return null
            lock.wait(left)
        }
        latest
    }

    /** Sleeps up to [ms], returning early on any new frame or [wake]. */
    fun pause(ms: Long) = synchronized(lock) { lock.wait(ms) }

    fun wake() = synchronized(lock) { lock.notifyAll() }
}

/** What the server needs from the device. Implemented on Android, faked in tests. */
interface Device {
    /** Whether a screen capture is running (granted on the TV). */
    val canView: Boolean
    /** Whether the accessibility service that performs input is enabled. */
    val canControl: Boolean
    val screenWidth: Int
    val screenHeight: Int
    /** Number of browsers watching; capture runs only while it is > 0. */
    fun viewersChanged(count: Int)
    fun execute(command: Command): Boolean
    fun apps(): List<Pair<String, String>>
    fun appIcon(packageName: String): ByteArray?
    /** A paired browser asks for control: ask the person at the TV. */
    fun askControl(session: Session)
    /** An unpaired browser opened the page: show the PIN on the TV. */
    fun pairingRequested(address: String)
}

/** Switches set on the TV. */
data class Policy(val viewEnabled: Boolean, val controlEnabled: Boolean)

/**
 * Krelzox Remote HTTP + WebSocket server. Binds only to [bindAddress] (the device's LAN
 * address) and refuses anything outside [LanGuard]'s rules. Plain threads: there are at
 * most a handful of clients.
 */
class RemoteServer(
    private val bindAddress: InetAddress,
    private val port: Int,
    private val prefixLength: Int,
    private val sessions: Sessions,
    private val hub: FrameHub,
    private val device: Device,
    private val policy: () -> Policy,
    private val asset: (String) -> ByteArray?,
    /** Lets tests run on loopback; production always uses [LanGuard.sameSubnet]. */
    private val clientAllowed: (String) -> Boolean = { LanGuard.sameSubnet(it, bindAddress.hostAddress!!, prefixLength) },
    private val onChange: () -> Unit = {},
    /** Diagnostics (rejected clients, failed requests). Never receives tokens or PINs. */
    private val log: (String) -> Unit = {},
) {
    private val localIp = bindAddress.hostAddress!!
    private var serverSocket: ServerSocket? = null
    private val connections = ConcurrentHashMap<Socket, Connection>()
    private val pinShownAt = ConcurrentHashMap<String, Long>()

    /** Live WebSocket connections, for the TV panel. */
    val connected: List<Session> get() = connections.values.mapNotNull { sessions.get(it.sessionId) }.distinctBy { it.id }

    @Volatile private var running = false

    fun start() {
        val socket = ServerSocket()
        socket.reuseAddress = true
        socket.bind(InetSocketAddress(bindAddress, port), 16)
        serverSocket = socket
        running = true
        thread("krx-accept") { acceptLoop(socket) }
        thread("krx-watchdog") { watchdog() }
    }

    fun stop() {
        running = false
        runCatching { serverSocket?.close() }
        connections.keys.forEach { runCatching { it.close() } }
        connections.clear()
        hub.wake()
        device.viewersChanged(0)
    }

    /** Closes every connection of [sessionId] (after revoking it). */
    fun disconnect(sessionId: String) {
        connections.filterValues { it.sessionId == sessionId }.keys.forEach { runCatching { it.close() } }
    }

    /**
     * Pushes the current permissions to every browser (after a TV-side change). Called
     * from the main thread (panel, approval prompt, its timeout), so the socket writes
     * happen on a worker thread: Android forbids network I/O on the main thread.
     */
    fun broadcastState() {
        thread("krx-state") {
            connections.values.forEach { it.sendState() }
            hub.wake()
        }
    }

    private fun acceptLoop(server: ServerSocket) {
        while (running) {
            val client = try {
                server.accept()
            } catch (e: IOException) {
                break
            }
            val address = normalize(client.inetAddress)
            if (!clientAllowed(address)) {
                log("Rejected connection from $address (outside the local network)")
                runCatching { client.close() } // not from this LAN: no response at all
                continue
            }
            client.soTimeout = 30_000
            client.tcpNoDelay = true
            thread("krx-client") { handle(client, address) }
        }
    }

    private fun watchdog() {
        while (running) {
            Thread.sleep(5_000)
            var changed = false
            for ((socket, conn) in connections) {
                if (sessions.get(conn.sessionId) == null) {
                    runCatching { socket.close() }
                    changed = true
                }
            }
            if (changed) onChange()
        }
    }

    private fun handle(socket: Socket, address: String) {
        try {
            val input = BufferedInputStream(socket.getInputStream())
            val output = socket.getOutputStream()
            val request = readRequest(input) ?: return socket.close()
            if (!LanGuard.validHost(request.headers["host"], localIp, port)) {
                return respond(output, 421, "text/plain", "Misdirected request".toByteArray())
            }
            route(request, socket, input, output, address)
        } catch (e: Exception) {
            log("Request from $address failed: ${e.javaClass.simpleName}: ${e.message}")
        } finally {
            // Every request ends here; a WebSocket only returns once it has closed.
            runCatching { socket.close() }
        }
    }

    private fun route(r: Request, socket: Socket, input: InputStream, output: OutputStream, address: String) {
        val path = r.path.substringBefore('?')
        when {
            r.method == "GET" && path in STATIC -> {
                val (file, type) = STATIC.getValue(path)
                val body = asset(file) ?: return respond(output, 404, "text/plain", "Not found".toByteArray())
                respond(output, 200, type, body)
            }
            r.method == "POST" && path == "/api/pair" -> {
                if (!sameOrigin(r)) return forbidden(output)
                val pin = form(r.body)["pin"].orEmpty()
                when (val result = sessions.pair(pin, address, label(r.headers["user-agent"]))) {
                    is Sessions.PairResult.Ok -> {
                        onChange()
                        respond(
                            output, 200, JSON, json { addProperty("ok", true) },
                            extra = "Set-Cookie: $COOKIE=${result.token}; Path=/; HttpOnly; SameSite=Strict\r\n",
                        )
                    }
                    Sessions.PairResult.WrongPin -> respond(output, 401, JSON, json { addProperty("error", "pin") })
                    is Sessions.PairResult.Locked -> respond(output, 429, JSON, json { addProperty("error", "locked"); addProperty("retryMs", result.retryInMs) })
                    Sessions.PairResult.TooManySessions -> respond(output, 409, JSON, json { addProperty("error", "full") })
                }
            }
            r.method == "GET" && path == "/api/session" -> {
                val s = session(r, address)
                if (s == null) {
                    // Show the PIN on the TV, at most every 30 s per address.
                    val now = System.currentTimeMillis()
                    if (now - (pinShownAt[address] ?: 0L) > 30_000) {
                        pinShownAt[address] = now
                        device.pairingRequested(address)
                    }
                }
                respond(output, 200, JSON, json { addProperty("paired", s != null) })
            }
            r.method == "POST" && path == "/api/logout" -> {
                if (!sameOrigin(r)) return forbidden(output)
                session(r, address)?.let { sessions.revoke(it.id); disconnect(it.id); onChange() }
                respond(output, 200, JSON, json { addProperty("ok", true) }, extra = "Set-Cookie: $COOKIE=; Path=/; Max-Age=0; HttpOnly; SameSite=Strict\r\n")
            }
            r.method == "GET" && path == "/api/apps" -> {
                val s = session(r, address) ?: return respond(output, 401, JSON, json { addProperty("error", "auth") })
                if (!controlAllowed(s)) return forbidden(output)
                val arr = com.google.gson.JsonArray()
                device.apps().forEach { (pkg, name) -> arr.add(JsonObject().apply { addProperty("package", pkg); addProperty("name", name) }) }
                respond(output, 200, JSON, arr.toString().toByteArray())
            }
            r.method == "GET" && path == "/api/icon" -> {
                val s = session(r, address) ?: return respond(output, 401, JSON, json { addProperty("error", "auth") })
                if (!controlAllowed(s)) return forbidden(output)
                val pkg = query(r.path)["package"].orEmpty()
                val png = device.appIcon(pkg) ?: return respond(output, 404, "text/plain", ByteArray(0))
                respond(output, 200, "image/png", png, cache = true)
            }
            r.method == "GET" && path == "/ws" -> {
                val s = session(r, address) ?: return respond(output, 401, JSON, json { addProperty("error", "auth") })
                if (!LanGuard.validOrigin(r.headers["origin"], localIp, port) ||
                    r.headers["upgrade"]?.lowercase() != "websocket"
                ) {
                    return forbidden(output)
                }
                val key = r.headers["sec-websocket-key"] ?: return forbidden(output)
                output.write(
                    ("HTTP/1.1 101 Switching Protocols\r\nUpgrade: websocket\r\nConnection: Upgrade\r\n" +
                        "Sec-WebSocket-Accept: ${WebSocket.acceptKey(key)}\r\n\r\n").toByteArray(),
                )
                output.flush()
                socket.soTimeout = 0
                Connection(s.id, socket, input, output).run()
            }
            else -> respond(output, 404, "text/plain", "Not found".toByteArray())
        }
    }

    private fun controlAllowed(s: Session) = policy().controlEnabled && s.control == ControlState.GRANTED && device.canControl

    private fun viewAllowed() = policy().viewEnabled && device.canView

    private inner class Connection(
        val sessionId: String,
        val socket: Socket,
        val input: InputStream,
        val output: OutputStream,
    ) {
        @Volatile private var acked = true
        @Volatile private var open = true

        fun run() {
            connections[socket] = this
            onChange()
            updateViewers()
            sendState()
            val sender = thread("krx-frames") { frameLoop() }
            try {
                while (open) {
                    val msg = WebSocket.readMessage(input, output)
                    if (msg.opcode == WebSocket.OP_CLOSE) break
                    if (msg.opcode != WebSocket.OP_TEXT) continue
                    val session = sessions.touch(sessionId) ?: break
                    onMessage(session, String(msg.payload))
                }
            } catch (_: Exception) {
            } finally {
                open = false
                connections.remove(socket)
                runCatching { socket.close() }
                hub.wake()
                sender.interrupt()
                updateViewers()
                onChange()
            }
        }

        private fun onMessage(session: Session, text: String) {
            val type = runCatching { JsonParser.parseString(text).asJsonObject.get("type")?.asString }.getOrNull()
            when (type) {
                "ack" -> {
                    acked = true
                    hub.wake()
                }
                "requestControl" -> {
                    if (!policy().controlEnabled || !device.canControl) return sendState()
                    val updated = sessions.requestControl(sessionId) ?: return
                    if (updated.control == ControlState.REQUESTED) device.askControl(updated)
                    onChange()
                    sendState()
                }
                "cmd" -> {
                    if (!controlAllowed(session)) return sendState()
                    val command = CommandParser.parse(text) ?: return
                    sessions.commandUsed(sessionId)
                    device.execute(command)
                }
                "ping" -> send(WebSocket.OP_TEXT, json { addProperty("type", "pong") })
            }
        }

        private fun frameLoop() {
            var lastSeq = -1L
            while (open) {
                // Flow control: one frame in flight per browser, so the image never lags.
                if (!viewAllowed() || !acked) {
                    hub.pause(250)
                    continue
                }
                val frame = hub.awaitNewer(lastSeq, 1_000) ?: continue
                if (!open) break
                acked = false
                lastSeq = frame.seq
                if (!send(WebSocket.OP_BINARY, frame.jpeg)) break
            }
        }

        fun sendState() {
            val s = sessions.get(sessionId) ?: return
            val p = policy()
            send(
                WebSocket.OP_TEXT,
                json {
                    addProperty("type", "state")
                    addProperty("view", p.viewEnabled && device.canView)
                    addProperty("viewEnabled", p.viewEnabled)
                    addProperty("controlEnabled", p.controlEnabled && device.canControl)
                    addProperty("control", s.control.name)
                    addProperty("width", device.screenWidth)
                    addProperty("height", device.screenHeight)
                    addProperty("idleTimeoutMs", sessions.idleTimeoutMs)
                },
            )
        }

        fun send(opcode: Int, payload: ByteArray): Boolean = try {
            synchronized(output) { WebSocket.writeFrame(output, opcode, payload) }
            true
        } catch (_: IOException) {
            open = false
            false
        }
    }

    private fun updateViewers() = device.viewersChanged(connections.size)

    private fun session(r: Request, address: String): Session? = sessions.authenticate(cookie(r.headers["cookie"]), address)

    /** State-changing POSTs must come from this server's own page. */
    private fun sameOrigin(r: Request) = LanGuard.validOrigin(r.headers["origin"], localIp, port)

    private fun forbidden(output: OutputStream) = respond(output, 403, JSON, json { addProperty("error", "forbidden") })

    private class Request(val method: String, val path: String, val headers: Map<String, String>, val body: ByteArray)

    private fun readRequest(input: InputStream): Request? {
        val head = ByteArrayOutputStream()
        var matched = 0
        while (matched < 4) {
            val b = input.read()
            if (b < 0) return null
            head.write(b)
            matched = when {
                (matched == 0 || matched == 2) && b == '\r'.code -> matched + 1
                (matched == 1 || matched == 3) && b == '\n'.code -> matched + 1
                b == '\r'.code -> 1
                else -> 0
            }
            if (head.size() > MAX_HEADER) return null
        }
        val lines = head.toString(Charsets.ISO_8859_1.name()).split("\r\n")
        val parts = lines.first().split(' ')
        if (parts.size != 3 || !parts[2].startsWith("HTTP/1.")) return null
        val headers = HashMap<String, String>()
        for (line in lines.drop(1)) {
            val i = line.indexOf(':')
            if (i > 0) headers[line.substring(0, i).trim().lowercase()] = line.substring(i + 1).trim()
        }
        val length = headers["content-length"]?.toIntOrNull() ?: 0
        if (length < 0 || length > MAX_BODY) return null
        val body = ByteArray(length)
        var read = 0
        while (read < length) {
            val n = input.read(body, read, length - read)
            if (n < 0) return null
            read += n
        }
        return Request(parts[0], parts[1], headers, body)
    }

    private fun respond(
        output: OutputStream,
        status: Int,
        type: String,
        body: ByteArray,
        extra: String = "",
        cache: Boolean = false,
    ) {
        val reason = REASONS[status] ?: "OK"
        val header = buildString {
            append("HTTP/1.1 $status $reason\r\n")
            append("Content-Type: $type\r\n")
            append("Content-Length: ${body.size}\r\n")
            append("Connection: close\r\n")
            append(if (cache) "Cache-Control: private, max-age=3600\r\n" else "Cache-Control: no-store\r\n")
            append("X-Content-Type-Options: nosniff\r\n")
            append("X-Frame-Options: DENY\r\n")
            append("Referrer-Policy: no-referrer\r\n")
            append(
                "Content-Security-Policy: default-src 'self'; img-src 'self' blob:; " +
                    "connect-src 'self' ws://$localIp:$port; style-src 'self'; script-src 'self'; frame-ancestors 'none'\r\n",
            )
            append(extra)
            append("\r\n")
        }
        output.write(header.toByteArray(Charsets.ISO_8859_1))
        output.write(body)
        output.flush()
    }

    private fun json(block: JsonObject.() -> Unit) = JsonObject().apply(block).toString().toByteArray()

    private fun thread(name: String, block: () -> Unit) = Thread({
        try {
            block()
        } catch (_: InterruptedException) {
        }
    }, name).apply {
        isDaemon = true
        start()
    }

    companion object {
        const val COOKIE = "krx"
        private const val JSON = "application/json"
        private const val MAX_HEADER = 8 * 1024
        private const val MAX_BODY = 4 * 1024

        private val STATIC = mapOf(
            "/" to ("index.html" to "text/html; charset=utf-8"),
            "/app.js" to ("app.js" to "text/javascript; charset=utf-8"),
            "/app.css" to ("app.css" to "text/css; charset=utf-8"),
            "/icon.svg" to ("icon.svg" to "image/svg+xml"),
        )

        private val REASONS = mapOf(
            200 to "OK", 401 to "Unauthorized", 403 to "Forbidden", 404 to "Not Found",
            409 to "Conflict", 421 to "Misdirected Request", 429 to "Too Many Requests",
        )

        /**
         * Text form of a client address. A dual-stack server socket can report IPv4
         * clients as IPv4-mapped IPv6 (::ffff:a.b.c.d); those are unwrapped to IPv4.
         */
        fun normalize(address: InetAddress): String {
            val bytes = address.address
            if (bytes.size == 16 && bytes.take(10).all { it == 0.toByte() } && bytes[10] == 0xFF.toByte() && bytes[11] == 0xFF.toByte()) {
                return bytes.drop(12).joinToString(".") { (it.toInt() and 0xFF).toString() }
            }
            return address.hostAddress ?: ""
        }

        fun cookie(header: String?): String? = header?.split(';')
            ?.map { it.trim() }
            ?.firstOrNull { it.startsWith("$COOKIE=") }
            ?.substringAfter('=')
            ?.takeIf { it.isNotBlank() }

        fun form(body: ByteArray): Map<String, String> = String(body).split('&').mapNotNull { pair ->
            val i = pair.indexOf('=')
            if (i <= 0) null else URLDecoder.decode(pair.substring(0, i), "UTF-8") to URLDecoder.decode(pair.substring(i + 1), "UTF-8")
        }.toMap()

        fun query(path: String): Map<String, String> = form(path.substringAfter('?', "").toByteArray())

        /** Short device label from a User-Agent, e.g. "Chrome · Android". */
        fun label(userAgent: String?): String {
            val ua = userAgent.orEmpty()
            val os = when {
                "Android" in ua -> "Android"
                "iPhone" in ua -> "iPhone"
                "iPad" in ua -> "iPad"
                "Windows" in ua -> "Windows"
                "Mac OS" in ua -> "macOS"
                "Linux" in ua -> "Linux"
                else -> "?"
            }
            val browser = when {
                "Edg/" in ua -> "Edge"
                "Firefox/" in ua -> "Firefox"
                "Chrome/" in ua -> "Chrome"
                "Safari/" in ua -> "Safari"
                else -> "Browser"
            }
            return "$browser · $os"
        }
    }
}
