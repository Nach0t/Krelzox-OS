package dev.krelzox.remote

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.hardware.display.DisplayManager
import android.media.projection.MediaProjectionManager
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.view.Display
import dev.krelzox.remote.capture.ScreenCapture
import dev.krelzox.remote.net.LanGuard
import dev.krelzox.remote.net.RemoteServer
import dev.krelzox.remote.ui.RemoteActivity
import java.net.Inet4Address
import java.net.InetAddress

/**
 * Foreground service that runs Krelzox Remote while it is switched on: the local server
 * and the screen capture. It starts only from the TV panel, after the user has accepted
 * Android's screen-capture prompt, and stops when switched off, when the capture ends or
 * when the TV leaves the local network.
 */
class RemoteService : Service() {

    private var lan: Pair<InetAddress, Int>? = null
    private var watchingNetwork = false
    private val connectivity by lazy { getSystemService(ConnectivityManager::class.java) }
    private val main by lazy { Handler(Looper.getMainLooper()) }

    // Delivered on the main thread (see register), like every other server change, so a
    // network event can never race with starting the server.
    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onLinkPropertiesChanged(network: Network, linkProperties: LinkProperties) {
            if (findLan()?.first != lan?.first) restartServer()
        }

        override fun onLost(network: Network) {
            if (findLan()?.first != lan?.first) restartServer()
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                shutdown(error = null)
                return START_NOT_STICKY
            }
            ACTION_START -> start(intent)
        }
        return START_NOT_STICKY
    }

    private fun start(intent: Intent) {
        // Must be in the foreground with the media projection type before using the token.
        startForeground(NOTIFICATION_ID, notification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, 0)
        val data = intent.getParcelableExtra(EXTRA_DATA, Intent::class.java)
        val projection = data?.let { getSystemService(MediaProjectionManager::class.java).getMediaProjection(resultCode, it) }
        if (projection == null) {
            shutdown(error = "capture_denied")
            return
        }

        val display = getSystemService(DisplayManager::class.java).getDisplay(Display.DEFAULT_DISPLAY)
        val mode = display.mode
        RemoteHub.screenWidth = mode.physicalWidth
        RemoteHub.screenHeight = mode.physicalHeight
        RemoteHub.capture?.stop()
        RemoteHub.capture = ScreenCapture(
            projection = projection,
            screenWidth = mode.physicalWidth,
            screenHeight = mode.physicalHeight,
            densityDpi = resources.displayMetrics.densityDpi,
            hub = RemoteHub.frames,
            onStopped = { main.post { shutdown(error = "capture_ended") } },
        ).also { it.start(RemoteHub.status.value.quality) }

        startServer()
        if (!watchingNetwork) {
            connectivity.registerDefaultNetworkCallback(networkCallback, main)
            watchingNetwork = true
        }
    }

    /** Starts (or restarts) the server on the current LAN address. Main thread only. */
    private fun startServer() {
        RemoteHub.server?.stop()
        RemoteHub.server = null
        val found = findLan()
        lan = found
        if (found == null) {
            RemoteHub.setRunning(url = null, error = "no_lan")
            return
        }
        val (address, prefix) = found
        for (port in PORTS) {
            val server = RemoteServer(
                bindAddress = address,
                port = port,
                prefixLength = prefix,
                sessions = RemoteHub.sessions,
                hub = RemoteHub.frames,
                device = RemoteHub.device,
                policy = { RemoteHub.policy },
                asset = ::asset,
                onChange = { RemoteHub.refresh() },
                log = { Log.i(TAG, it) },
            )
            try {
                server.start()
            } catch (e: Exception) {
                Log.w(TAG, "Port $port unavailable", e)
                continue
            }
            RemoteHub.server = server
            RemoteHub.setRunning(url = "http://${address.hostAddress}:$port", error = null)
            return
        }
        RemoteHub.setRunning(url = null, error = "port")
    }

    private fun restartServer() {
        RemoteHub.disconnectAll()
        startServer()
    }

    private fun shutdown(error: String?) {
        if (watchingNetwork) {
            runCatching { connectivity.unregisterNetworkCallback(networkCallback) }
            watchingNetwork = false
        }
        RemoteHub.server?.stop()
        RemoteHub.server = null
        RemoteHub.capture?.stop()
        RemoteHub.capture = null
        RemoteHub.disconnectAll()
        RemoteHub.accessibility?.let {
            it.dismissApproval()
            it.removePin()
        }
        RemoteHub.setRunning(url = null, error = error)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        if (RemoteHub.server != null || RemoteHub.capture != null) shutdown(error = null)
        super.onDestroy()
    }

    /** The private IPv4 address (and prefix) of the active network; null if there is none. */
    private fun findLan(): Pair<InetAddress, Int>? {
        val network = connectivity.activeNetwork ?: return null
        val props = connectivity.getLinkProperties(network) ?: return null
        return props.linkAddresses
            .firstOrNull { it.address is Inet4Address && LanGuard.isPrivateIpv4(it.address.hostAddress ?: "") }
            ?.let { it.address to it.prefixLength }
    }

    private fun asset(name: String): ByteArray? = runCatching { assets.open("web/$name").use { it.readBytes() } }.getOrNull()

    private fun notification(): Notification {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, getString(R.string.notification_channel), NotificationManager.IMPORTANCE_LOW),
        )
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, RemoteActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
        )
        return Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_remote_notification)
            .setContentTitle(getString(R.string.remote_name))
            .setContentText(getString(R.string.notification_text))
            .setContentIntent(open)
            .setOngoing(true)
            .build()
    }

    companion object {
        private const val TAG = "KrelzoxRemote"
        private const val CHANNEL = "remote"
        private const val NOTIFICATION_ID = 1
        const val ACTION_START = "dev.krelzox.remote.START"
        const val ACTION_STOP = "dev.krelzox.remote.STOP"
        const val EXTRA_RESULT_CODE = "resultCode"
        const val EXTRA_DATA = "data"
        private val PORTS = 8080..8089

        fun stop(context: Context) {
            context.startService(Intent(context, RemoteService::class.java).setAction(ACTION_STOP))
        }
    }
}
