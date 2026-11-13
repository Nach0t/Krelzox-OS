package dev.krelzox.remote

import dev.krelzox.remote.control.Command
import dev.krelzox.remote.control.RemoteKey
import dev.krelzox.remote.net.Device
import dev.krelzox.remote.net.Frame
import dev.krelzox.remote.net.FrameHub
import dev.krelzox.remote.net.Policy
import dev.krelzox.remote.net.RemoteServer
import dev.krelzox.remote.net.WebSocket
import dev.krelzox.remote.security.Session
import dev.krelzox.remote.security.Sessions
import java.io.BufferedInputStream
import java.io.InputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CopyOnWriteArrayList
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Runs the real server on loopback (the LAN check is replaced for the test only) and
 * talks to it with raw HTTP and WebSocket bytes, like a browser.
 */
class RemoteServerTest {

    private class FakeDevice : Device {
        val executed = CopyOnWriteArrayList<Command>()
        val asked = CopyOnWriteArrayList<String>()
        @Volatile var viewers = 0
        override val canView = true
        override val canControl = true
        override val screenWidth = 1920
        override val screenHeight = 1080
        override fun viewersChanged(count: Int) { viewers = count }
        override fun execute(command: Command): Boolean = executed.add(command)
        override fun apps() = listOf("org.example.tv" to "Example TV")
        override fun appIcon(packageName: String): ByteArray? = null
        override fun askControl(session: Session) { asked += session.id }
        override fun pairingRequested(address: String) = Unit
    }

    private val ip = "127.0.0.1"
    private var port = 0
    private lateinit var server: RemoteServer
    private val sessions = Sessions()
    private val hub = FrameHub()
    private val device = FakeDevice()
    private var policy = Policy(viewEnabled = true, controlEnabled = true)

    @Before
    fun start() {
        port = ServerSocket(0).use { it.localPort }
        server = RemoteServer(
            bindAddress = InetAddress.getByName(ip),
            port = port,
            prefixLength = 24,
            sessions = sessions,
            hub = hub,
            device = device,
            policy = { policy },
            asset = { name -> if (name == "index.html") "<html>krelzox</html>".toByteArray() else null },
            clientAllowed = { it == ip },
        )
        server.start()
    }

    @After
    fun stop() = server.stop()

    private fun http(raw: String): Pair<String, String> = Socket(ip, port).use { s ->
        s.getOutputStream().write(raw.toByteArray())
        val text = s.getInputStream().readBytes().toString(Charsets.ISO_8859_1)
        text.substringBefore("\r\n\r\n") to text.substringAfter("\r\n\r\n")
    }

    private fun post(path: String, body: String, origin: String? = "http://$ip:$port", cookie: String? = null) = http(
        "POST $path HTTP/1.1\r\nHost: $ip:$port\r\n" +
            (origin?.let { "Origin: $it\r\n" } ?: "") + (cookie?.let { "Cookie: krx=$it\r\n" } ?: "") +
            "Content-Type: application/x-www-form-urlencoded\r\nContent-Length: ${body.length}\r\n\r\n$body",
    )

    private fun pair(): String {
        val (head, _) = post("/api/pair", "pin=${sessions.pin}")
        assertTrue(head, head.startsWith("HTTP/1.1 200"))
        return Regex("Set-Cookie: krx=([0-9a-f]+);").find(head)!!.groupValues[1]
    }

    @Test
    fun servesThePageButRejectsForeignHostHeaders() {
        val (ok, body) = http("GET / HTTP/1.1\r\nHost: $ip:$port\r\n\r\n")
        assertTrue(ok.startsWith("HTTP/1.1 200"))
        assertTrue(ok.contains("Content-Security-Policy"))
        assertEquals("<html>krelzox</html>", body)
        val (rebound, _) = http("GET / HTTP/1.1\r\nHost: attacker.example\r\n\r\n")
        assertTrue(rebound.startsWith("HTTP/1.1 421"))
    }

    @Test
    fun pairingNeedsTheTvPinAndASameOriginRequest() {
        assertTrue(post("/api/pair", "pin=000000").first.startsWith("HTTP/1.1 401"))
        assertTrue(post("/api/pair", "pin=${sessions.pin}", origin = "http://evil.example").first.startsWith("HTTP/1.1 403"))
        val token = pair()
        assertTrue(token.length == 64)
        val (head, body) = http("GET /api/session HTTP/1.1\r\nHost: $ip:$port\r\nCookie: krx=$token\r\n\r\n")
        assertTrue(head.startsWith("HTTP/1.1 200"))
        assertEquals("""{"paired":true}""", body)
    }

    @Test
    fun appsListNeedsGrantedControl() {
        val token = pair()
        val get = "GET /api/apps HTTP/1.1\r\nHost: $ip:$port\r\nCookie: krx=$token\r\n\r\n"
        assertTrue(http(get).first.startsWith("HTTP/1.1 403"))
        sessions.decideControl(sessions.all().single().id, true)
        val (head, body) = http(get)
        assertTrue(head.startsWith("HTTP/1.1 200"))
        assertTrue(body.contains("org.example.tv"))
    }

    @Test
    fun websocketStreamsFramesAndRunsCommandsOnlyAfterApproval() {
        // Without a session the upgrade is refused.
        assertTrue(http(wsHandshake(null)).first.startsWith("HTTP/1.1 401"))

        val token = pair()
        Socket(ip, port).use { ws ->
            ws.soTimeout = 5_000
            val out = ws.getOutputStream()
            val input = BufferedInputStream(ws.getInputStream())
            out.write(wsHandshake(token).toByteArray())
            assertTrue(readHead(input).startsWith("HTTP/1.1 101"))
            val state = String(WebSocket.readFrame(serverFrame(input)).payload)
            assertTrue(state, state.contains("\"control\":\"NONE\""))

            // A frame is delivered once published.
            val jpeg = byteArrayOf(-1, -40, 1, 2, 3)
            hub.publish(Frame(1, jpeg, 1920, 1080))
            assertArrayEquals(jpeg, WebSocket.readFrame(serverFrame(input)).payload)

            // Commands before approval are ignored.
            out.write(maskedFrame(WebSocket.OP_TEXT, """{"type":"cmd","action":"key","key":"home"}""".toByteArray()))
            out.write(maskedFrame(WebSocket.OP_TEXT, """{"type":"requestControl"}""".toByteArray()))
            // The ignored command answers with the current state (NONE); then the request is acknowledged.
            val states = List(2) { String(WebSocket.readFrame(serverFrame(input)).payload) }
            assertTrue(states.toString(), states[0].contains("\"control\":\"NONE\""))
            assertTrue(states.toString(), states[1].contains("\"control\":\"REQUESTED\""))
            assertTrue(device.executed.isEmpty())
            assertEquals(1, device.asked.size)

            // Approved on the TV: commands now run.
            sessions.decideControl(device.asked.single(), true)
            out.write(maskedFrame(WebSocket.OP_TEXT, """{"type":"cmd","action":"key","key":"home"}""".toByteArray()))
            val deadline = System.currentTimeMillis() + 3_000
            while (device.executed.isEmpty() && System.currentTimeMillis() < deadline) Thread.sleep(20)
            assertEquals(listOf<Command>(Command.Key(RemoteKey.HOME)), device.executed.toList())
            assertEquals(1, device.viewers)
        }
    }

    @Test
    fun crossSiteWebsocketIsRefused() {
        val token = pair()
        val (head, _) = http(wsHandshake(token, origin = "http://evil.example"))
        assertTrue(head.startsWith("HTTP/1.1 403"))
    }

    private fun wsHandshake(token: String?, origin: String = "http://$ip:$port") =
        "GET /ws HTTP/1.1\r\nHost: $ip:$port\r\nUpgrade: websocket\r\nConnection: Upgrade\r\n" +
            "Origin: $origin\r\nSec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ==\r\nSec-WebSocket-Version: 13\r\n" +
            (token?.let { "Cookie: krx=$it\r\n" } ?: "") + "\r\n"

    private fun readHead(input: InputStream): String {
        val sb = StringBuilder()
        while (!sb.endsWith("\r\n\r\n")) sb.append(input.read().toChar())
        return sb.toString()
    }

    /** Server frames are unmasked; re-mask them so the shared reader (client-side rules) accepts them. */
    private fun serverFrame(input: InputStream): InputStream {
        val b0 = input.read()
        val b1 = input.read()
        var len = b1 and 0x7F
        if (len == 126) len = (input.read() shl 8) or input.read()
        else if (len == 127) { var l = 0L; repeat(8) { l = (l shl 8) or input.read().toLong() }; len = l.toInt() }
        val payload = ByteArray(len)
        var read = 0
        while (read < len) read += input.read(payload, read, len - read)
        return maskedFrame(b0 and 0x0F, payload).inputStream()
    }
}
