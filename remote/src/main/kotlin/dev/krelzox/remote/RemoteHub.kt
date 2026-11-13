package dev.krelzox.remote

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.widget.Toast
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dev.krelzox.remote.capture.Quality
import dev.krelzox.remote.capture.ScreenCapture
import dev.krelzox.remote.control.Command
import dev.krelzox.remote.control.RemoteAccessibilityService
import dev.krelzox.remote.control.RemoteKey
import dev.krelzox.remote.net.Device
import dev.krelzox.remote.net.FrameHub
import dev.krelzox.remote.net.Policy
import dev.krelzox.remote.net.RemoteServer
import dev.krelzox.remote.security.ControlState
import dev.krelzox.remote.security.Session
import dev.krelzox.remote.security.Sessions
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private val Context.remoteStore by preferencesDataStore(name = "krelzox_remote")

/** A browser as shown in the TV panel. */
data class Client(val id: String, val label: String, val address: String, val control: ControlState, val connected: Boolean)

data class RemoteStatus(
    val running: Boolean = false,
    /** Null when fine; otherwise a reason key ("no_lan", "port", "capture_ended"). */
    val error: String? = null,
    val url: String? = null,
    val pin: String = "",
    val clients: List<Client> = emptyList(),
    val quality: Quality = Quality.MEDIUM,
    val viewEnabled: Boolean = true,
    val controlEnabled: Boolean = true,
    val accessibilityEnabled: Boolean = false,
)

/**
 * Process-wide state of Krelzox Remote: settings, sessions, the server, the capture and
 * the accessibility service. The foreground service owns the lifecycle; the panel and
 * the accessibility overlays read and change it through here.
 */
// Holds only the application context and the accessibility service while it is bound
// (cleared in onUnbind/onDestroy), so these static references cannot leak an activity.
@SuppressLint("StaticFieldLeak")
object RemoteHub {

    lateinit var app: Context
        private set

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val main = Handler(Looper.getMainLooper())

    val sessions = Sessions()
    val frames = FrameHub()

    private val _status = MutableStateFlow(RemoteStatus())
    val status: StateFlow<RemoteStatus> = _status.asStateFlow()

    @Volatile var server: RemoteServer? = null
    @Volatile var capture: ScreenCapture? = null
    @Volatile var screenWidth = 1920
    @Volatile var screenHeight = 1080

    /** Set while the accessibility service is connected. */
    @Volatile var accessibility: RemoteAccessibilityService? = null
        set(value) {
            field = value
            refresh()
        }

    fun init(context: Context) {
        app = context.applicationContext
        scope.launch {
            val prefs = app.remoteStore.data.first()
            _status.update {
                it.copy(
                    quality = Quality.fromKey(prefs[QUALITY]),
                    viewEnabled = prefs[VIEW] ?: true,
                    controlEnabled = prefs[CONTROL] ?: true,
                )
            }
            refresh()
        }
    }

    val policy: Policy get() = _status.value.let { Policy(it.viewEnabled, it.controlEnabled) }

    fun setRunning(url: String?, error: String?) {
        _status.update { it.copy(running = url != null, url = url, error = error) }
        refresh()
    }

    /** Recomputes the panel view of sessions, PIN and accessibility state. */
    fun refresh() {
        val connectedIds = server?.connected?.map { it.id }?.toSet().orEmpty()
        _status.update { s ->
            s.copy(
                pin = sessions.pin,
                clients = sessions.all().map { Client(it.id, it.label, it.address, it.control, it.id in connectedIds) },
                accessibilityEnabled = accessibility != null,
            )
        }
    }

    fun setQuality(q: Quality) {
        _status.update { it.copy(quality = q) }
        capture?.setQuality(q)
        scope.launch { app.remoteStore.edit { it[QUALITY] = q.key } }
    }

    fun setViewEnabled(enabled: Boolean) {
        _status.update { it.copy(viewEnabled = enabled) }
        scope.launch { app.remoteStore.edit { it[VIEW] = enabled } }
        server?.broadcastState()
    }

    fun setControlEnabled(enabled: Boolean) {
        _status.update { it.copy(controlEnabled = enabled) }
        if (!enabled) {
            sessions.revokeAllControl()
            accessibility?.dismissApproval()
        }
        scope.launch { app.remoteStore.edit { it[CONTROL] = enabled } }
        server?.broadcastState()
        refresh()
    }

    fun decideControl(sessionId: String, allow: Boolean) {
        sessions.decideControl(sessionId, allow && policy.controlEnabled)
        server?.broadcastState()
        refresh()
    }

    fun disconnect(sessionId: String) {
        sessions.revoke(sessionId)
        server?.disconnect(sessionId)
        refresh()
    }

    fun disconnectAll() {
        val ids = sessions.all().map { it.id }
        sessions.revokeAll()
        ids.forEach { server?.disconnect(it) }
        refresh()
    }

    /** The server's view of the device, backed by Android APIs. */
    val device = object : Device {
        override val canView: Boolean get() = capture != null
        override val canControl: Boolean get() = accessibility != null
        override val screenWidth: Int get() = this@RemoteHub.screenWidth
        override val screenHeight: Int get() = this@RemoteHub.screenHeight

        override fun viewersChanged(count: Int) {
            capture?.active = count > 0 && policy.viewEnabled
            main.post { refresh() }
        }

        override fun execute(command: Command): Boolean {
            when (command) {
                is Command.Key -> when (command.key) {
                    RemoteKey.PLAY_PAUSE -> return mediaKey(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
                    RemoteKey.NEXT -> return mediaKey(KeyEvent.KEYCODE_MEDIA_NEXT)
                    RemoteKey.PREVIOUS -> return mediaKey(KeyEvent.KEYCODE_MEDIA_PREVIOUS)
                    RemoteKey.VOLUME_UP -> return volume(AudioManager.ADJUST_RAISE)
                    RemoteKey.VOLUME_DOWN -> return volume(AudioManager.ADJUST_LOWER)
                    RemoteKey.MUTE -> return volume(AudioManager.ADJUST_TOGGLE_MUTE)
                    else -> Unit
                }
                else -> Unit
            }
            val service = accessibility ?: return false
            main.post { service.perform(command) }
            return true
        }

        override fun apps(): List<Pair<String, String>> {
            val pm = app.packageManager
            val seen = LinkedHashMap<String, String>()
            for (category in listOf(Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER)) {
                val intent = Intent(Intent.ACTION_MAIN).addCategory(category)
                for (info in pm.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0))) {
                    val pkg = info.activityInfo.packageName
                    if (pkg == app.packageName || pkg in seen) continue
                    seen[pkg] = info.loadLabel(pm).toString()
                }
            }
            return seen.entries.map { it.key to it.value }.sortedBy { it.second.lowercase() }
        }

        override fun appIcon(packageName: String): ByteArray? = runCatching {
            val drawable = app.packageManager.getApplicationIcon(packageName)
            val bmp = Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888)
            drawable.setBounds(0, 0, 96, 96)
            drawable.draw(Canvas(bmp))
            ByteArrayOutputStream().also { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
        }.getOrNull()

        override fun askControl(session: Session) {
            main.post {
                refresh()
                accessibility?.showApproval(session)
            }
        }

        override fun pairingRequested(address: String) {
            main.post {
                val service = accessibility
                if (service != null) {
                    service.showPin(sessions.pin, address)
                } else {
                    Toast.makeText(app, app.getString(R.string.toast_pin, sessions.pin), Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun mediaKey(code: Int): Boolean {
        val audio = app.getSystemService(AudioManager::class.java)
        audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code))
        audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code))
        return true
    }

    private fun volume(direction: Int): Boolean {
        app.getSystemService(AudioManager::class.java)
            .adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, AudioManager.FLAG_SHOW_UI)
        return true
    }

    private val QUALITY = stringPreferencesKey("quality")
    private val VIEW = booleanPreferencesKey("view_enabled")
    private val CONTROL = booleanPreferencesKey("control_enabled")
}
