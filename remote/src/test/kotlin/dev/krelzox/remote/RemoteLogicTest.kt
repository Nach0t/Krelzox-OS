package dev.krelzox.remote

import dev.krelzox.remote.control.Command
import dev.krelzox.remote.control.CommandParser
import dev.krelzox.remote.control.RemoteKey
import dev.krelzox.remote.net.LanGuard
import dev.krelzox.remote.net.RemoteServer
import dev.krelzox.remote.net.WebSocket
import dev.krelzox.remote.security.ControlState
import dev.krelzox.remote.security.Sessions
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.security.SecureRandom
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RemoteLogicTest {

    @Test
    fun onlyPrivateAddressesOfTheSameSubnetAreLocal() {
        assertTrue(LanGuard.sameSubnet("192.168.100.23", "192.168.100.19", 24))
        assertFalse(LanGuard.sameSubnet("192.168.101.23", "192.168.100.19", 24))
        assertFalse(LanGuard.sameSubnet("8.8.8.8", "192.168.100.19", 0))
        assertFalse(LanGuard.sameSubnet("127.0.0.1", "192.168.100.19", 24))
        assertTrue(LanGuard.sameSubnet("10.1.2.3", "10.9.9.9", 8))
        assertFalse(LanGuard.isPrivateIpv4("100.64.0.1")) // carrier-grade NAT is not "my LAN"
        assertFalse(LanGuard.isPrivateIpv4("172.32.0.1"))
        assertFalse(LanGuard.isPrivateIpv4("192.168.1"))
        assertFalse(LanGuard.isPrivateIpv4("192.168.1.256"))
    }

    @Test
    fun hostAndOriginMustBeThisServer() {
        assertTrue(LanGuard.validHost("192.168.100.19:8080", "192.168.100.19", 8080))
        assertFalse(LanGuard.validHost("evil.example:8080", "192.168.100.19", 8080)) // DNS rebinding
        assertFalse(LanGuard.validHost(null, "192.168.100.19", 8080))
        assertTrue(LanGuard.validOrigin("http://192.168.100.19:8080", "192.168.100.19", 8080))
        assertFalse(LanGuard.validOrigin("http://evil.example", "192.168.100.19", 8080))
        assertFalse(LanGuard.validOrigin("null", "192.168.100.19", 8080))
    }

    private fun sessions(now: LongArray = longArrayOf(0)) =
        Sessions(clock = { now[0] }, random = SecureRandom.getInstance("SHA1PRNG").apply { setSeed(42) })

    @Test
    fun pinIsSingleUseAndTokensBindToTheAddress() {
        val s = sessions()
        val pin = s.pin
        val ok = s.pair(pin, "192.168.1.5", "Chrome · Android") as Sessions.PairResult.Ok
        assertNotEquals(pin, s.pin) // rotated after use
        assertEquals(Sessions.PairResult.WrongPin, s.pair(pin, "192.168.1.6", "x"))
        assertNotNull(s.authenticate(ok.token, "192.168.1.5"))
        assertNull(s.authenticate(ok.token, "192.168.1.9")) // stolen cookie from another host
        assertNull(s.authenticate("forged", "192.168.1.5"))
        assertFalse(ok.session.tokenHash.contains(ok.token)) // only the hash is stored
    }

    @Test
    fun repeatedWrongPinsLockTheAddressThenRotateThePin() {
        val now = longArrayOf(0)
        val s = sessions(now)
        repeat(Sessions.MAX_FAILURES_PER_ADDRESS) { assertEquals(Sessions.PairResult.WrongPin, s.pair("000000", "192.168.1.5", "x")) }
        val pin = s.pin
        assertTrue(s.pair(pin, "192.168.1.5", "x") is Sessions.PairResult.Locked)
        now[0] += Sessions.LOCKOUT_MS + 1
        assertTrue(s.pair(pin, "192.168.1.5", "x") is Sessions.PairResult.Ok)

        val before = s.pin
        repeat(Sessions.MAX_GLOBAL_FAILURES) { i -> s.pair("000000", "192.168.2.${i % 200}", "x") }
        assertNotEquals(before, s.pin)
    }

    @Test
    fun controlNeedsApprovalAndSessionsExpireOrAreRevoked() {
        val now = longArrayOf(0)
        val s = sessions(now)
        val ok = s.pair(s.pin, "192.168.1.5", "x") as Sessions.PairResult.Ok
        assertEquals(ControlState.NONE, ok.session.control)
        assertEquals(ControlState.REQUESTED, s.requestControl(ok.session.id)!!.control)
        assertEquals(ControlState.GRANTED, s.decideControl(ok.session.id, true)!!.control)
        s.revokeAllControl()
        assertEquals(ControlState.NONE, s.get(ok.session.id)!!.control)

        // Granted control lapses when unused, while the session itself stays alive.
        s.decideControl(ok.session.id, true)
        now[0] += s.controlIdleMs / 2
        s.commandUsed(ok.session.id)
        now[0] += s.controlIdleMs / 2 + 1
        assertEquals(ControlState.GRANTED, s.touch(ok.session.id)!!.control)
        // The page stays open (pings keep the session) but nothing is controlled.
        now[0] += s.controlIdleMs / 2
        s.touch(ok.session.id)
        now[0] += s.controlIdleMs / 2 + 1
        s.touch(ok.session.id)
        assertEquals(ControlState.NONE, s.get(ok.session.id)!!.control)

        now[0] += s.idleTimeoutMs + 1
        assertNull(s.authenticate(ok.token, "192.168.1.5"))

        val again = s.pair(s.pin, "192.168.1.5", "x") as Sessions.PairResult.Ok
        assertTrue(s.revoke(again.session.id))
        assertNull(s.get(again.session.id))
    }

    @Test
    fun sessionCountIsLimited() {
        val s = sessions()
        repeat(4) { assertTrue(s.pair(s.pin, "192.168.1.$it", "x") is Sessions.PairResult.Ok) }
        assertEquals(Sessions.PairResult.TooManySessions, s.pair(s.pin, "192.168.1.9", "x"))
    }

    @Test
    fun websocketFramesRoundTrip() {
        // RFC 6455 §1.3 example.
        assertEquals("s3pPLMBiTxaQ9kYGzzhZRbK+xOo=", WebSocket.acceptKey("dGhlIHNhbXBsZSBub25jZQ=="))
        val masked = maskedFrame(WebSocket.OP_TEXT, "hello".toByteArray())
        val frame = WebSocket.readFrame(ByteArrayInputStream(masked))
        assertEquals("hello", String(frame.payload))

        val out = ByteArrayOutputStream()
        WebSocket.writeFrame(out, WebSocket.OP_BINARY, ByteArray(70_000) { 7 })
        val bytes = out.toByteArray()
        assertEquals(0x82.toByte(), bytes[0])
        assertEquals(127.toByte(), bytes[1])
        assertEquals(70_000 + 10, bytes.size)
    }

    @Test(expected = java.io.IOException::class)
    fun unmaskedClientFramesAreRejected() {
        WebSocket.readFrame(ByteArrayInputStream(byteArrayOf(0x81.toByte(), 2, 'h'.code.toByte(), 'i'.code.toByte())))
    }

    @Test
    fun commandsAreValidated() {
        assertEquals(Command.Tap(0.5f, 0.25f), CommandParser.parse("""{"type":"cmd","action":"tap","x":0.5,"y":0.25}"""))
        assertEquals(Command.Key(RemoteKey.BACK), CommandParser.parse("""{"type":"cmd","action":"key","key":"back"}"""))
        assertEquals(Command.Launch("org.videolan.vlc"), CommandParser.parse("""{"type":"cmd","action":"launch","package":"org.videolan.vlc"}"""))
        assertNull(CommandParser.parse("""{"type":"cmd","action":"tap","x":1.5,"y":0.2}""")) // off screen
        assertNull(CommandParser.parse("""{"type":"cmd","action":"key","key":"format_disk"}"""))
        assertNull(CommandParser.parse("""{"type":"cmd","action":"launch","package":"x; rm -rf /"}"""))
        assertNull(CommandParser.parse("""{"type":"cmd","action":"text","text":""}"""))
        assertNull(CommandParser.parse("not json"))
        val swipe = CommandParser.parse("""{"type":"cmd","action":"swipe","x1":0,"y1":0,"x2":1,"y2":1,"ms":99999}""") as Command.Swipe
        assertEquals(2_000L, swipe.durationMs)
    }

    @Test
    fun cookiesFormsAndLabels() {
        assertEquals("abc", RemoteServer.cookie("theme=dark; krx=abc; x=1"))
        assertNull(RemoteServer.cookie("krx="))
        assertEquals(mapOf("pin" to "123 456"), RemoteServer.form("pin=123+456".toByteArray()))
        assertEquals("Chrome · Android", RemoteServer.label("Mozilla/5.0 (Linux; Android 14) Chrome/130 Mobile Safari/537"))
        assertEquals("Safari · iPhone", RemoteServer.label("Mozilla/5.0 (iPhone; CPU iPhone OS 17_0) Version/17.0 Safari/604.1"))
    }
}

/** Builds a masked client frame as a browser would. */
internal fun maskedFrame(opcode: Int, payload: ByteArray): ByteArray {
    val mask = byteArrayOf(1, 2, 3, 4)
    val out = ByteArrayOutputStream()
    out.write(0x80 or opcode)
    when {
        payload.size < 126 -> out.write(0x80 or payload.size)
        else -> {
            out.write(0x80 or 126)
            out.write(payload.size shr 8)
            out.write(payload.size and 0xFF)
        }
    }
    out.write(mask)
    payload.forEachIndexed { i, b -> out.write(b.toInt() xor mask[i % 4].toInt()) }
    return out.toByteArray()
}
