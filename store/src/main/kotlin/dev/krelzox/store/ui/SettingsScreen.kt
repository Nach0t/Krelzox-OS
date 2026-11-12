package dev.krelzox.store.ui

import android.text.format.DateUtils
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import androidx.tv.material3.Text
import dev.krelzox.launcher.core.designsystem.KrelzoxColors
import dev.krelzox.launcher.core.designsystem.KrelzoxGrid
import dev.krelzox.launcher.core.designsystem.KrelzoxIcons
import dev.krelzox.launcher.core.designsystem.KrelzoxSpacing
import dev.krelzox.store.R

@Composable
fun SettingsScreen(viewModel: StoreViewModel, actions: StoreActions) {
    val state by viewModel.catalog.collectAsStateWithLifecycle()
    val repos by viewModel.repos.collectAsStateWithLifecycle()
    val canInstall by viewModel.canInstall.collectAsStateWithLifecycle()
    val device = viewModel.device

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = KrelzoxGrid.FocusInset, end = KrelzoxGrid.FocusInset, bottom = KrelzoxSpacing.XLarge),
        verticalArrangement = Arrangement.spacedBy(KrelzoxSpacing.Medium),
    ) {
        item { PageHeader(stringResource(R.string.nav_settings), modifier = Modifier.padding(start = 0.dp)) }

        item { Title(stringResource(R.string.settings_repos)) }
        items(repos, key = { it.id }) { repo ->
            val status = state.repos.firstOrNull { it.repo.id == repo.id }
            val checked = status?.checkedAt?.takeIf { it > 0 }?.let { DateUtils.getRelativeTimeSpanString(it).toString() }
                ?: stringResource(R.string.repo_never)
            val error = when (status?.error) {
                "network" -> stringResource(R.string.repo_error_network)
                "verification" -> stringResource(R.string.repo_error_verification)
                "format" -> stringResource(R.string.repo_error_format)
                else -> null
            }
            SettingRow(
                title = repo.name,
                lines = listOfNotNull(
                    repo.address,
                    stringResource(R.string.repo_line, status?.appCount ?: 0, checked),
                    error,
                ),
                value = stringResource(if (repo.enabled) R.string.repo_enabled else R.string.repo_disabled),
                onClick = { viewModel.setRepoEnabled(repo.id, !repo.enabled) },
            )
        }
        item {
            StoreButton(
                stringResource(if (state.refreshing) R.string.settings_refreshing else R.string.settings_refresh),
                onClick = viewModel::refreshCatalog,
                icon = StoreIcons.Refresh,
            )
        }
        item { AddRepository(viewModel) }

        item { Title(stringResource(R.string.settings_permission)) }
        item {
            SettingRow(
                title = stringResource(R.string.settings_permission),
                lines = emptyList(),
                value = stringResource(if (canInstall) R.string.permission_granted else R.string.permission_denied),
                onClick = actions.allowInstalls,
            )
        }

        item { Title(stringResource(R.string.settings_device)) }
        item {
            Text(
                stringResource(R.string.settings_device_line, device.sdk, device.abis.joinToString()),
                style = MaterialTheme.typography.bodyLarge,
                color = KrelzoxColors.OnSurfaceMuted,
            )
        }

        item { Title(stringResource(R.string.settings_security)) }
        item {
            Row(verticalAlignment = Alignment.Top) {
                Icon(StoreIcons.Shield, contentDescription = null, tint = KrelzoxColors.Cyan, modifier = Modifier.size(22.dp))
                Text(
                    stringResource(R.string.settings_security_text),
                    style = MaterialTheme.typography.bodyLarge,
                    color = KrelzoxColors.OnSurfaceMuted,
                    modifier = Modifier.padding(start = KrelzoxSpacing.Medium).width(1000.dp),
                )
            }
        }
    }
}

@Composable
private fun Title(text: String) {
    Text(text, style = MaterialTheme.typography.titleLarge, color = KrelzoxColors.OnBackground, modifier = Modifier.padding(top = KrelzoxSpacing.Medium))
}

@Composable
private fun SettingRow(title: String, lines: List<String>, value: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.width(1000.dp),
        shape = ClickableSurfaceDefaults.shape(TileShape),
        colors = ClickableSurfaceDefaults.colors(containerColor = KrelzoxColors.Surface, focusedContainerColor = KrelzoxColors.SurfaceRaised),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = androidx.tv.material3.Border(androidx.compose.foundation.BorderStroke(2.dp, KrelzoxColors.IonBrush), shape = TileShape),
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.01f),
    ) {
        Row(Modifier.padding(KrelzoxSpacing.Medium), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = KrelzoxColors.OnBackground)
                for (line in lines) Text(line, style = MaterialTheme.typography.bodyMedium, color = KrelzoxColors.OnSurfaceMuted)
            }
            Text(value, style = MaterialTheme.typography.labelLarge, color = KrelzoxColors.Cyan)
        }
    }
}

/** Form to add an F-Droid-compatible repository: HTTPS address and pinned key fingerprint. */
@Composable
private fun AddRepository(viewModel: StoreViewModel) {
    var open by rememberSaveable { mutableStateOf(false) }
    var name by rememberSaveable { mutableStateOf("") }
    var address by rememberSaveable { mutableStateOf("https://") }
    var fingerprint by rememberSaveable { mutableStateOf("") }
    var invalid by rememberSaveable { mutableStateOf(false) }

    if (!open) {
        StoreButton(stringResource(R.string.repo_add), onClick = { open = true }, icon = KrelzoxIcons.Plus)
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(KrelzoxSpacing.Small)) {
        Field(stringResource(R.string.repo_name), name) { name = it }
        Field(stringResource(R.string.repo_address), address) { address = it }
        Field(stringResource(R.string.repo_fingerprint), fingerprint) { fingerprint = it }
        if (invalid) Text(stringResource(R.string.repo_invalid), style = MaterialTheme.typography.bodyMedium, color = KrelzoxColors.Error)
        Row(horizontalArrangement = Arrangement.spacedBy(KrelzoxSpacing.Medium)) {
            StoreButton(stringResource(R.string.repo_save), onClick = {
                if (viewModel.addRepo(name, address, fingerprint)) {
                    open = false
                    invalid = false
                    name = ""
                    address = "https://"
                    fingerprint = ""
                } else {
                    invalid = true
                }
            }, primary = true)
            StoreButton(stringResource(R.string.action_cancel), onClick = { open = false })
        }
    }
}

@Composable
private fun Field(label: String, value: String, onChange: (String) -> Unit) {
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium, color = KrelzoxColors.OnSurfaceMuted)
        Surface(
            modifier = Modifier.width(800.dp).padding(top = 4.dp),
            shape = RoundedCornerShape(12.dp),
            colors = SurfaceDefaults.colors(containerColor = KrelzoxColors.SurfaceRaised),
        ) {
            BasicTextField(
                value = value,
                onValueChange = { onChange(it.take(200)) },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = KrelzoxColors.OnBackground),
                cursorBrush = SolidColor(KrelzoxColors.Cyan),
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .onPreviewKeyEvent { event -> tvTextFieldKeys(event, keyboard, focusManager) },
            )
        }
    }
}
