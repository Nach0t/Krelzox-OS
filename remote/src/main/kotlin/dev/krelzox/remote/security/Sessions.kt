package dev.krelzox.remote.security

import java.security.MessageDigest
import java.security.SecureRandom

/** What a paired browser may do. Viewing and control are granted separately. */
enum class ControlState { NONE, REQUESTED, GRANTED, DENIED }

data class Session(
    val id: String,
    /** Secret sent to the browser in an HttpOnly cookie. Only its hash is kept. */
    val tokenHash: String,
    val address: String,
    val label: String,
    val createdAt: Long,
    val lastActivity: Long,
    val control: ControlState = ControlState.NONE,
    /** Last time a command was used; granted control lapses after [Sessions.controlIdleMs] without one. */
    val lastCommand: Long = 0,
)

/**
 * Pairing and sessions for Krelzox Remote.
 *
 * - A temporary 6-digit PIN is shown on the TV. Typing it in the browser proves the
 *   person can see the screen; it changes after every successful pairing and after
 *   too many failures.
 * - Wrong PINs are rate-limited per address (lockout) and globally (PIN rotation).
 * - Control is never part of pairing: it is requested by the browser and approved on
 *   the TV, per session.
 * - Sessions expire after [idleTimeoutMs] without activity and can be revoked.
 *
 * Pure logic with an injectable clock and random source, so it is unit-tested.
 */
class Sessions(
    private val clock: () -> Long = System::currentTimeMillis,
    private val random: SecureRandom = SecureRandom(),
    val idleTimeoutMs: Long = 10 * 60_000L,
    val controlIdleMs: Long = 10 * 60_000L,
    private val maxSessions: Int = 4,
) {
    sealed interface PairResult {
        data class Ok(val session: Session, val token: String) : PairResult
        data object WrongPin : PairResult
        data class Locked(val retryInMs: Long) : PairResult
        data object TooManySessions : PairResult
    }

    private val sessions = LinkedHashMap<String, Session>()
    private val failures = HashMap<String, MutableList<Long>>()
    private val lockedUntil = HashMap<String, Long>()
    private var globalFailures = 0

    var pin: String = newPin()
        private set

    @Synchronized
    fun all(): List<Session> {
        expire()
        return sessions.values.toList()
    }

    @Synchronized
    fun pair(pinAttempt: String, address: String, label: String): PairResult {
        expire()
        val now = clock()
        lockedUntil[address]?.let { until ->
            if (now < until) return PairResult.Locked(until - now)
            lockedUntil.remove(address)
        }
        if (!constantTimeEquals(pinAttempt.trim(), pin)) {
            val recent = failures.getOrPut(address) { ArrayList() }
            recent += now
            recent.removeAll { now - it > FAILURE_WINDOW_MS }
            if (recent.size >= MAX_FAILURES_PER_ADDRESS) {
                lockedUntil[address] = now + LOCKOUT_MS
                recent.clear()
            }
            if (++globalFailures >= MAX_GLOBAL_FAILURES) rotatePin()
            return PairResult.WrongPin
        }
        if (sessions.size >= maxSessions) return PairResult.TooManySessions
        failures.remove(address)
        val token = hex(bytes(32))
        val session = Session(
            id = hex(bytes(6)),
            tokenHash = sha256(token),
            address = address,
            label = label,
            createdAt = now,
            lastActivity = now,
        )
        sessions[session.id] = session
        rotatePin() // single use
        return PairResult.Ok(session, token)
    }

    /** The session for [token] if it is valid and comes from the same address; refreshes activity. */
    @Synchronized
    fun authenticate(token: String?, address: String): Session? {
        if (token.isNullOrBlank()) return null
        expire()
        val hash = sha256(token)
        val session = sessions.values.firstOrNull { constantTimeEquals(it.tokenHash, hash) } ?: return null
        if (session.address != address) return null
        val touched = session.copy(lastActivity = clock())
        sessions[session.id] = touched
        return touched
    }

    /** The live session [id] (refreshing its activity), or null if it expired or was revoked. */
    @Synchronized
    fun touch(id: String): Session? = update(id) { it.copy(lastActivity = clock()) }

    @Synchronized
    fun get(id: String): Session? {
        expire()
        return sessions[id]
    }

    @Synchronized
    fun requestControl(id: String): Session? = update(id) {
        if (it.control == ControlState.GRANTED) it else it.copy(control = ControlState.REQUESTED)
    }

    @Synchronized
    fun decideControl(id: String, allow: Boolean): Session? = update(id) {
        it.copy(control = if (allow) ControlState.GRANTED else ControlState.DENIED, lastCommand = clock())
    }

    /** Records that [id] used its control (keeps the grant alive). */
    @Synchronized
    fun commandUsed(id: String): Session? = update(id) { clock().let { now -> it.copy(lastCommand = now, lastActivity = now) } }

    /** Removes control from every session (master switch turned off on the TV). */
    @Synchronized
    fun revokeAllControl() {
        for ((id, s) in sessions.toList()) sessions[id] = s.copy(control = ControlState.NONE)
    }

    @Synchronized
    fun revoke(id: String): Boolean = sessions.remove(id) != null

    @Synchronized
    fun revokeAll() {
        sessions.clear()
        rotatePin()
    }

    @Synchronized
    fun rotatePin() {
        pin = newPin()
        globalFailures = 0
    }

    private fun update(id: String, change: (Session) -> Session): Session? {
        expire()
        val current = sessions[id] ?: return null
        return change(current).also { sessions[id] = it }
    }

    private fun expire() {
        val now = clock()
        sessions.values.removeAll { now - it.lastActivity > idleTimeoutMs }
        // Unused control lapses: the browser must ask again and be approved on the TV.
        for ((id, s) in sessions.toList()) {
            if (s.control == ControlState.GRANTED && now - s.lastCommand > controlIdleMs) {
                sessions[id] = s.copy(control = ControlState.NONE)
            }
        }
    }

    private fun newPin(): String = (random.nextInt(900_000) + 100_000).toString()

    private fun bytes(n: Int) = ByteArray(n).also(random::nextBytes)

    companion object {
        const val MAX_FAILURES_PER_ADDRESS = 5
        const val MAX_GLOBAL_FAILURES = 20
        const val FAILURE_WINDOW_MS = 10 * 60_000L
        const val LOCKOUT_MS = 60_000L

        fun sha256(text: String): String = hex(MessageDigest.getInstance("SHA-256").digest(text.toByteArray()))

        private fun hex(bytes: ByteArray) = bytes.joinToString("") { "%02x".format(it) }

        private fun constantTimeEquals(a: String, b: String): Boolean =
            MessageDigest.isEqual(a.toByteArray(), b.toByteArray())
    }
}
