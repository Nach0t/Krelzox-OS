package dev.krelzox.remote.ui

import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.media.projection.MediaProjectionConfig
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import dev.krelzox.launcher.core.designsystem.KrelzoxColors
import dev.krelzox.launcher.core.designsystem.KrelzoxDimens
import dev.krelzox.launcher.core.designsystem.KrelzoxIcons
import dev.krelzox.launcher.core.designsystem.KrelzoxSpacing
import dev.krelzox.launcher.core.designsystem.KrelzoxTheme
import dev.krelzox.remote.Client
import dev.krelzox.remote.R
import dev.krelzox.remote.RemoteHub
import dev.krelzox.remote.RemoteService
import dev.krelzox.remote.RemoteStatus
import dev.krelzox.remote.capture.Quality
import dev.krelzox.remote.security.ControlState

/** Krelzox Remote panel on the TV: switch, address + QR + PIN, permissions, quality, devices. */
class RemoteActivity : ComponentActivity() {

    private val consent = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK && result.data != null) {
            startForegroundService(
                Intent(this, RemoteService::class.java)
                    .setAction(RemoteService.ACTION_START)
                    .putExtra(RemoteService.EXTRA_RESULT_CODE, result.resultCode)
                    .putExtra(RemoteService.EXTRA_DATA, result.data),
            )
        } else {
            RemoteHub.setRunning(url = null, error = "capture_denied")
        }
    }

    /** Android 16+ blocks LAN traffic for apps without this runtime permission. */
    private val localNetwork = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) requestCapture() else RemoteHub.setRunning(url = null, error = "lan_denied")
    }

    private fun switchOn() {
        if (checkSelfPermission(LOCAL_NETWORK) == PackageManager.PERMISSION_GRANTED) {
            requestCapture()
        } else {
            localNetwork.launch(LOCAL_NETWORK)
        }
    }

    /** Android asks the person at the TV to allow capturing the whole screen (not a single app). */
    private fun requestCapture() {
        val manager = getSystemService(MediaProjectionManager::class.java)
        val request = if (Build.VERSION.SDK_INT >= 34) {
            manager.createScreenCaptureIntent(MediaProjectionConfig.createConfigForDefaultDisplay())
        } else {
            manager.createScreenCaptureIntent()
        }
        consent.launch(request)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            KrelzoxTheme {
                val status by RemoteHub.status.collectAsStateWithLifecycle()
                RemotePanel(
                    status = status,
                    onToggle = {
                        if (status.running) {
                            RemoteService.stop(this)
                        } else {
                            switchOn()
                        }
                    },
                    onOpenAccessibility = { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) },
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        RemoteHub.refresh()
    }

    private companion object {
        const val LOCAL_NETWORK = "android.permission.ACCESS_LOCAL_NETWORK"
    }
}

@Composable
private fun RemotePanel(status: RemoteStatus, onToggle: () -> Unit, onOpenAccessibility: () -> Unit) {
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
    Row(
        Modifier
            .fillMaxSize()
            .background(KrelzoxColors.Background)
            .padding(horizontal = KrelzoxSpacing.SafeHorizontal, vertical = KrelzoxSpacing.SafeVertical),
        horizontalArrangement = Arrangement.spacedBy(KrelzoxSpacing.XLarge),
    ) {
        LazyColumn(
            Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(KrelzoxSpacing.Small),
            contentPadding = PaddingValues(12.dp),
        ) {
            item { Header() }
            item {
                Row_(
                    title = stringResource(R.string.remote_name),
                    value = stringResource(if (status.running) R.string.remote_state_on else R.string.remote_state_off),
                    detail = errorText(status.error) ?: stringResource(R.string.remote_switch_hint),
                    highlight = status.running,
                    modifier = Modifier.focusRequester(first),
                    onClick = onToggle,
                )
            }
            item {
                Row_(
                    title = stringResource(R.string.setting_view),
                    value = stringResource(if (status.viewEnabled) R.string.state_allowed else R.string.state_blocked),
                    detail = stringResource(R.string.setting_view_hint),
                    onClick = { RemoteHub.setViewEnabled(!status.viewEnabled) },
                )
            }
            item {
                Row_(
                    title = stringResource(R.string.setting_control),
                    value = stringResource(if (status.controlEnabled) R.string.state_allowed else R.string.state_blocked),
                    detail = stringResource(R.string.setting_control_hint),
                    onClick = { RemoteHub.setControlEnabled(!status.controlEnabled) },
                )
            }
            item {
                Row_(
                    title = stringResource(R.string.setting_accessibility),
                    value = stringResource(if (status.accessibilityEnabled) R.string.state_active else R.string.state_inactive),
                    detail = stringResource(R.string.setting_accessibility_hint),
                    onClick = onOpenAccessibility,
                )
            }
            item {
                Row_(
                    title = stringResource(R.string.setting_quality),
                    value = stringResource(qualityLabel(status.quality)),
                    detail = stringResource(R.string.setting_quality_hint),
                    onClick = {
                        val next = Quality.entries[(status.quality.ordinal + 1) % Quality.entries.size]
                        RemoteHub.setQuality(next)
                    },
                )
            }
            item {
                Text(
                    stringResource(R.string.devices_title),
                    style = MaterialTheme.typography.titleLarge,
                    color = KrelzoxColors.OnBackground,
                    modifier = Modifier.padding(top = KrelzoxSpacing.Medium),
                )
            }
            if (status.clients.isEmpty()) {
                item { Text(stringResource(R.string.devices_none), style = MaterialTheme.typography.bodyLarge, color = KrelzoxColors.OnSurfaceMuted) }
            }
            items(status.clients, key = { it.id }) { client -> ClientRow(client) }
            if (status.clients.isNotEmpty()) {
                item {
                    Row_(
                        title = stringResource(R.string.devices_stop_all),
                        value = "",
                        detail = stringResource(R.string.devices_stop_all_hint),
                        onClick = { RemoteHub.disconnectAll() },
                    )
                }
            }
        }
        AddressCard(status, Modifier.width(380.dp))
    }
}

@Composable
private fun Header() {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = KrelzoxSpacing.Medium)) {
        Image(rememberVectorPainter(KrelzoxIcons.LogoMark), contentDescription = null, modifier = Modifier.size(28.dp, 31.dp))
        Column(Modifier.padding(start = KrelzoxSpacing.Medium)) {
            Text(stringResource(R.string.remote_name), style = MaterialTheme.typography.displaySmall, color = KrelzoxColors.OnBackground)
            Text(stringResource(R.string.remote_subtitle), style = MaterialTheme.typography.bodyLarge, color = KrelzoxColors.OnSurfaceMuted)
        }
    }
}

@Composable
private fun AddressCard(status: RemoteStatus, modifier: Modifier) {
    Column(
        modifier
            .background(KrelzoxColors.Surface, RoundedCornerShape(KrelzoxDimens.CardCorner))
            .padding(KrelzoxSpacing.Large),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(KrelzoxSpacing.Medium),
    ) {
        val url = status.url
        if (url == null) {
            Text(stringResource(R.string.address_off), style = MaterialTheme.typography.titleMedium, color = KrelzoxColors.OnSurfaceMuted)
            return@Column
        }
        Text(stringResource(R.string.address_open), style = MaterialTheme.typography.labelSmall, color = KrelzoxColors.Cyan)
        Text(url, style = MaterialTheme.typography.titleLarge, color = KrelzoxColors.OnBackground)
        val qr = remember(url) { qrBitmap(url, 520) }
        Box(Modifier.background(Color.White, RoundedCornerShape(12.dp)).padding(12.dp)) {
            Image(qr.asImageBitmap(), contentDescription = url, modifier = Modifier.size(220.dp))
        }
        Text(stringResource(R.string.address_pin), style = MaterialTheme.typography.labelSmall, color = KrelzoxColors.Cyan)
        Text(status.pin.chunked(3).joinToString(" "), style = MaterialTheme.typography.displaySmall, color = KrelzoxColors.OnBackground)
        Text(
            stringResource(R.string.address_lan_only),
            style = MaterialTheme.typography.bodyMedium,
            color = KrelzoxColors.OnSurfaceMuted,
        )
    }
}

@Composable
private fun ClientRow(client: Client) {
    val control = when (client.control) {
        ControlState.GRANTED -> R.string.client_control_granted
        ControlState.REQUESTED -> R.string.client_control_requested
        ControlState.DENIED -> R.string.client_control_denied
        ControlState.NONE -> R.string.client_view_only
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row_(
            title = "${client.label} · ${client.address}",
            value = stringResource(R.string.client_disconnect),
            detail = stringResource(if (client.connected) R.string.client_connected else R.string.client_idle) + " · " + stringResource(control),
            onClick = { RemoteHub.disconnect(client.id) },
        )
        if (client.control == ControlState.REQUESTED) {
            Row(horizontalArrangement = Arrangement.spacedBy(KrelzoxSpacing.Small)) {
                Row_(stringResource(R.string.overlay_deny), "", "", onClick = { RemoteHub.decideControl(client.id, false) }, modifier = Modifier.width(220.dp))
                Row_(stringResource(R.string.overlay_allow), "", "", onClick = { RemoteHub.decideControl(client.id, true) }, modifier = Modifier.width(220.dp))
            }
        }
    }
}

/** A focusable settings row: title + detail on the left, value on the right. */
@Composable
private fun Row_(
    title: String,
    value: String,
    detail: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    highlight: Boolean = false,
) {
    val shape = RoundedCornerShape(KrelzoxDimens.CardCorner)
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = ClickableSurfaceDefaults.shape(shape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (highlight) KrelzoxColors.SurfaceRaised else KrelzoxColors.Surface,
            focusedContainerColor = KrelzoxColors.SurfaceRaised,
        ),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = Border(BorderStroke(KrelzoxDimens.FocusBorder, KrelzoxColors.IonBrush), shape = shape),
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.01f),
    ) {
        Row(Modifier.padding(KrelzoxSpacing.Medium), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = KrelzoxColors.OnBackground)
                if (detail.isNotBlank()) Text(detail, style = MaterialTheme.typography.bodyMedium, color = KrelzoxColors.OnSurfaceMuted)
            }
            if (value.isNotBlank()) Text(value, style = MaterialTheme.typography.labelLarge, color = KrelzoxColors.Cyan)
        }
    }
}

@Composable
private fun errorText(error: String?): String? = when (error) {
    "no_lan" -> stringResource(R.string.error_no_lan)
    "port" -> stringResource(R.string.error_port)
    "capture_denied" -> stringResource(R.string.error_capture_denied)
    "capture_ended" -> stringResource(R.string.error_capture_ended)
    "lan_denied" -> stringResource(R.string.error_lan_denied)
    else -> null
}

private fun qualityLabel(q: Quality) = when (q) {
    Quality.LOW -> R.string.quality_low
    Quality.MEDIUM -> R.string.quality_medium
    Quality.HIGH -> R.string.quality_high
}

private fun qrBitmap(text: String, size: Int): Bitmap {
    val matrix = QRCodeWriter().encode(
        text, BarcodeFormat.QR_CODE, size, size,
        mapOf(EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M, EncodeHintType.MARGIN to 1),
    )
    val pixels = IntArray(size * size) { i -> if (matrix[i % size, i / size]) 0xFF0B0D12.toInt() else 0xFFFFFFFF.toInt() }
    return Bitmap.createBitmap(pixels, size, size, Bitmap.Config.ARGB_8888)
}
