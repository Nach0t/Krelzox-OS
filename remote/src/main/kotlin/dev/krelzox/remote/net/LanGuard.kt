package dev.krelzox.remote.net

/**
 * Local-network-only rules. The server binds to the device's private IPv4 address and
 * every request must:
 * - come from an address in the same subnet (the LAN the TV is on);
 * - name the server by its IP and port in `Host` (blocks DNS-rebinding pages);
 * - for WebSockets, come from a page served by this same address (`Origin`).
 */
object LanGuard {

    /** RFC 1918 private ranges. Public, loopback, link-local and CGNAT addresses are refused. */
    fun isPrivateIpv4(address: String): Boolean {
        val b = parse(address) ?: return false
        return b[0] == 10 ||
            (b[0] == 172 && b[1] in 16..31) ||
            (b[0] == 192 && b[1] == 168)
    }

    /** True when [client] is inside [local]/[prefixLength] (and both are private IPv4). */
    fun sameSubnet(client: String, local: String, prefixLength: Int): Boolean {
        if (!isPrivateIpv4(client) || !isPrivateIpv4(local) || prefixLength !in 8..32) return false
        val mask = if (prefixLength == 32) -1 else (-1 shl (32 - prefixLength))
        return (toInt(parse(client)!!) and mask) == (toInt(parse(local)!!) and mask)
    }

    /** `Host` must be exactly the server's address (with or without the port). */
    fun validHost(host: String?, localIp: String, port: Int): Boolean {
        if (host == null) return false
        val h = host.trim().lowercase()
        return h == localIp || h == "$localIp:$port"
    }

    /** `Origin` of a WebSocket handshake must be this server's own page. */
    fun validOrigin(origin: String?, localIp: String, port: Int): Boolean {
        if (origin == null) return false
        val o = origin.trim().lowercase().trimEnd('/')
        return o == "http://$localIp:$port" || (port == 80 && o == "http://$localIp")
    }

    private fun parse(address: String): IntArray? {
        val parts = address.trim().split('.')
        if (parts.size != 4) return null
        val bytes = IntArray(4)
        for (i in 0..3) {
            val p = parts[i]
            if (p.isEmpty() || p.length > 3 || !p.all(Char::isDigit)) return null
            val v = p.toInt()
            if (v > 255) return null
            bytes[i] = v
        }
        return bytes
    }

    private fun toInt(b: IntArray): Int = (b[0] shl 24) or (b[1] shl 16) or (b[2] shl 8) or b[3]
}
