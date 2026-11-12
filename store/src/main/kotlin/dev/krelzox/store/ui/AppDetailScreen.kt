package dev.krelzox.store.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import dev.krelzox.launcher.core.designsystem.KrelzoxColors
import dev.krelzox.launcher.core.designsystem.KrelzoxGrid
import dev.krelzox.launcher.core.designsystem.KrelzoxIcons
import dev.krelzox.launcher.core.designsystem.KrelzoxSpacing
import dev.krelzox.store.R
import dev.krelzox.store.domain.AppStatus
import dev.krelzox.store.domain.AppStatusResolver
import dev.krelzox.store.domain.Compatibility
import dev.krelzox.store.domain.Incompatibility
import dev.krelzox.store.install.FailureReason
import dev.krelzox.store.install.Task
import dev.krelzox.store.model.CatalogApp
import java.text.DateFormat
import java.util.Date

@Composable
fun AppDetailScreen(viewModel: StoreViewModel, packageName: String, actions: StoreActions) {
    val state by viewModel.catalog.collectAsStateWithLifecycle()
    val installed by viewModel.installed.collectAsStateWithLifecycle()
    val tasks by viewModel.tasks.collectAsStateWithLifecycle()
    val favorites by viewModel.favorites.collectAsStateWithLifecycle()
    val repos by viewModel.repos.collectAsStateWithLifecycle()
    val app = state.catalog.byPackage[packageName] ?: return
    val status = remember(app, installed) { AppStatusResolver.resolve(app, viewModel.device, installed[packageName]) }
    val task = tasks[packageName]
    val focus = remember { FocusRequester() }
    // Back to the primary action when the page opens and whenever an operation starts or
    // ends: the button that had focus may have disappeared.
    LaunchedEffect(packageName, status::class, task?.let { it::class }) { runCatching { focus.requestFocus() } }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = KrelzoxGrid.FocusInset, end = KrelzoxGrid.FocusInset, bottom = KrelzoxSpacing.XLarge),
        verticalArrangement = Arrangement.spacedBy(KrelzoxSpacing.Large),
    ) {
        item { Header(viewModel, app) }
        item {
            ActionRow(
                app = app,
                status = status,
                task = task,
                isFavorite = packageName in favorites,
                focus = focus,
                viewModel = viewModel,
                actions = actions,
            )
        }
        item { StatusLine(status, task, viewModel, actions) }
        item { Facts(app, status) }
        if (app.antiFeatures.isNotEmpty()) item { Notices(app.antiFeatures) }
        if (!app.hasTvAssets) item { Note(StoreIcons.Warning, stringResource(R.string.detail_not_tv)) }
        if (app.screenshotUrls.isNotEmpty()) item { Screenshots(viewModel, app) }
        if (app.description.isNotBlank()) item { TextSection(stringResource(R.string.detail_about), app.description) }
        if (app.whatsNew.isNotBlank()) item { TextSection(stringResource(R.string.detail_whats_new), app.whatsNew) }
        item { Permissions(status) }
        item { Versions(viewModel, app, installed[packageName]?.versionCode) }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                val repo = repos.firstOrNull { it.id == app.repoId }
                repo?.let { Muted(stringResource(R.string.detail_repository, it.name)) }
                app.sourceCode?.let { Muted(stringResource(R.string.detail_source, it)) }
                Muted(stringResource(R.string.detail_ratings))
            }
        }
    }
}

@Composable
private fun Header(viewModel: StoreViewModel, app: CatalogApp) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        RemoteImage(app.iconUrl, viewModel.images, 128.dp, Modifier.size(112.dp).clip(RoundedCornerShape(24.dp)))
        Column(Modifier.padding(start = KrelzoxSpacing.Large)) {
            Text(app.name, style = MaterialTheme.typography.displaySmall, color = KrelzoxColors.OnBackground)
            app.author?.let { Text(it, style = MaterialTheme.typography.titleMedium, color = KrelzoxColors.Cyan) }
            if (app.summary.isNotBlank()) {
                Text(app.summary, style = MaterialTheme.typography.bodyLarge, color = KrelzoxColors.OnSurfaceMuted, modifier = Modifier.width(900.dp))
            }
        }
    }
}

@Composable
private fun ActionRow(
    app: CatalogApp,
    status: AppStatus,
    task: Task?,
    isFavorite: Boolean,
    focus: FocusRequester,
    viewModel: StoreViewModel,
    actions: StoreActions,
) {
    val busy = task != null && task !is Task.Failed
    val canOpen = viewModel.launchIntent(app.packageName) != null
    // Buttons in order; the first one is the primary action and always holds the focus
    // requester, so focus has a safe home whenever the available actions change.
    val buttons = buildList<@Composable (Modifier) -> Unit> {
        when {
            busy && task !is Task.WaitingForSystem -> add { m ->
                StoreButton(stringResource(R.string.action_cancel), { viewModel.cancel(app.packageName) }, m)
            }
            busy -> Unit
            status is AppStatus.Installable -> add { m ->
                StoreButton(stringResource(R.string.action_install), { viewModel.install(app) }, m, StoreIcons.Update, primary = true)
            }
            status is AppStatus.Updatable -> add { m ->
                StoreButton(stringResource(R.string.action_update), { viewModel.install(app) }, m, StoreIcons.Update, primary = true)
            }
            else -> Unit
        }
        val installed = status is AppStatus.UpToDate || status is AppStatus.Updatable || status is AppStatus.SignatureMismatch
        if (!busy && installed && canOpen) add { m ->
            StoreButton(stringResource(R.string.action_open), { actions.openApp(app.packageName) }, m, KrelzoxIcons.Play, primary = status !is AppStatus.Updatable)
        }
        add { m ->
            StoreButton(
                stringResource(if (isFavorite) R.string.action_unfavorite else R.string.action_favorite),
                { viewModel.toggleFavorite(app.packageName) },
                m,
                if (isFavorite) KrelzoxIcons.StarFilled else KrelzoxIcons.Star,
            )
        }
        // Destructive action last, away from where focus lands after an operation.
        if (!busy && installed) add { m ->
            StoreButton(stringResource(R.string.action_uninstall), { viewModel.uninstall(app) }, m, StoreIcons.Trash)
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(KrelzoxSpacing.Medium)) {
        buttons.forEachIndexed { index, button ->
            button(if (index == 0) Modifier.focusRequester(focus) else Modifier)
        }
    }
}

/** What is happening now, or why the app cannot be installed. */
@Composable
private fun StatusLine(status: AppStatus, task: Task?, viewModel: StoreViewModel, actions: StoreActions) {
    val canInstall by viewModel.canInstall.collectAsStateWithLifecycle()
    when {
        task is Task.Failed -> {
            val message = stringResource(
                when (task.reason) {
                    FailureReason.PERMISSION -> R.string.fail_permission
                    FailureReason.INCOMPATIBLE -> R.string.fail_incompatible
                    FailureReason.STORAGE -> R.string.fail_storage
                    FailureReason.NETWORK -> R.string.fail_network
                    FailureReason.VERIFICATION -> R.string.fail_verification
                    FailureReason.SYSTEM -> R.string.fail_system
                },
            )
            Column(verticalArrangement = Arrangement.spacedBy(KrelzoxSpacing.Small)) {
                Note(StoreIcons.Warning, message + (task.detail?.let { " ($it)" }.orEmpty()), error = true)
                if (task.reason == FailureReason.PERMISSION) {
                    StoreButton(stringResource(R.string.action_allow), actions.allowInstalls, icon = StoreIcons.Shield)
                }
            }
        }
        task != null -> TaskLine(task)
        status is AppStatus.UpToDate -> Muted(stringResource(R.string.status_installed, status.installed.versionName))
        status is AppStatus.Updatable -> Muted(stringResource(R.string.status_update, status.installed.versionName, status.version.versionName))
        status is AppStatus.SignatureMismatch -> Note(StoreIcons.Warning, stringResource(R.string.status_signature))
        status is AppStatus.Incompatible -> Note(
            StoreIcons.Warning,
            stringResource(R.string.status_incompatible) + reasons(status.reasons).let { if (it.isEmpty()) "" else ": $it" },
            error = true,
        )
        status is AppStatus.Installable && !canInstall -> Column(verticalArrangement = Arrangement.spacedBy(KrelzoxSpacing.Small)) {
            Muted(stringResource(R.string.fail_permission))
            StoreButton(stringResource(R.string.action_allow), actions.allowInstalls, icon = StoreIcons.Shield)
        }
    }
}

@Composable
fun TaskLine(task: Task, modifier: Modifier = Modifier) {
    Column(modifier.width(560.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        val text = when (task) {
            Task.Queued -> stringResource(R.string.task_queued)
            is Task.Downloading -> stringResource(
                R.string.task_downloading,
                (task.fraction * 100).toInt(),
                formatSize(task.read),
                formatSize(task.total),
            )
            Task.Verifying -> stringResource(R.string.task_verifying)
            Task.WaitingForSystem -> stringResource(R.string.task_waiting)
            is Task.Failed -> ""
        }
        Text(text, style = MaterialTheme.typography.bodyLarge, color = KrelzoxColors.OnBackground)
        ProgressBar((task as? Task.Downloading)?.fraction, Modifier.fillMaxWidth())
    }
}

@Composable
private fun reasons(list: List<Incompatibility>): String = list.map { reason ->
    when (reason) {
        is Incompatibility.SdkTooLow -> stringResource(R.string.incompat_sdk_low, reason.required)
        is Incompatibility.SdkTooHigh -> stringResource(R.string.incompat_sdk_high, reason.max)
        is Incompatibility.Abi -> stringResource(R.string.incompat_abi, reason.available.joinToString())
        is Incompatibility.Features -> stringResource(R.string.incompat_features, reason.missing.joinToString())
    }
}.joinToString("; ")

@Composable
private fun Facts(app: CatalogApp, status: AppStatus) {
    val version = when (status) {
        is AppStatus.Installable -> status.version
        is AppStatus.Updatable -> status.version
        else -> app.versions.first()
    }
    val dates = DateFormat.getDateInstance(DateFormat.MEDIUM)
    Row(horizontalArrangement = Arrangement.spacedBy(KrelzoxSpacing.XLarge)) {
        Fact(stringResource(R.string.detail_version), version.versionName)
        Fact(stringResource(R.string.detail_size), formatSize(version.size))
        Fact(stringResource(R.string.detail_updated), dates.format(Date(app.lastUpdated)))
        Fact(stringResource(R.string.detail_added), dates.format(Date(app.added)))
        Fact(stringResource(R.string.detail_license), app.license.ifBlank { "—" })
    }
    Text(
        listOfNotNull(
            app.categories.joinToString().takeIf { it.isNotBlank() },
            stringResource(R.string.detail_requires, version.minSdk),
        ).joinToString(" · "),
        style = MaterialTheme.typography.bodyMedium,
        color = KrelzoxColors.OnSurfaceMuted,
        modifier = Modifier.padding(top = KrelzoxSpacing.Small),
    )
}

@Composable
private fun Notices(antiFeatures: List<String>) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        SectionTitle(stringResource(R.string.detail_notices))
        for (key in antiFeatures) Note(StoreIcons.Warning, antiFeatureText(key))
    }
}

@Composable
private fun antiFeatureText(key: String): String = when (key) {
    "Ads" -> stringResource(R.string.af_Ads)
    "Tracking" -> stringResource(R.string.af_Tracking)
    "NonFreeNet" -> stringResource(R.string.af_NonFreeNet)
    "NonFreeAdd" -> stringResource(R.string.af_NonFreeAdd)
    "NonFreeDep" -> stringResource(R.string.af_NonFreeDep)
    "NonFreeAssets" -> stringResource(R.string.af_NonFreeAssets)
    "TetheredNet" -> stringResource(R.string.af_TetheredNet)
    "NoSourceSince" -> stringResource(R.string.af_NoSourceSince)
    "KnownVuln" -> stringResource(R.string.af_KnownVuln)
    "NSFW" -> stringResource(R.string.af_NSFW)
    "UpstreamNonFree" -> stringResource(R.string.af_UpstreamNonFree)
    else -> key
}

@Composable
private fun Screenshots(viewModel: StoreViewModel, app: CatalogApp) {
    Column {
        SectionTitle(stringResource(R.string.detail_screenshots))
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(KrelzoxGrid.Gap),
            contentPadding = PaddingValues(vertical = KrelzoxGrid.FocusInset),
        ) {
            items(app.screenshotUrls.take(12)) { url ->
                // Focusable so the remote can scroll through them.
                Card(
                    onClick = {},
                    modifier = Modifier.height(300.dp),
                    shape = CardDefaults.shape(TileShape),
                    colors = CardDefaults.colors(containerColor = KrelzoxColors.Surface),
                    border = tileBorder(),
                    glow = tileGlow(),
                ) {
                    RemoteImage(url, viewModel.images, 600.dp, Modifier.height(300.dp), ContentScale.FillHeight)
                }
            }
        }
    }
}

@Composable
private fun TextSection(title: String, text: String) {
    // A focusable block, so long descriptions can be scrolled with the D-pad.
    Surface(
        onClick = {},
        modifier = Modifier.width(1100.dp),
        shape = ClickableSurfaceDefaults.shape(TileShape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = KrelzoxColors.Surface,
            focusedContainerColor = KrelzoxColors.SurfaceRaised,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
    ) {
        Column(Modifier.padding(KrelzoxSpacing.Large)) {
            SectionTitle(title)
            Text(text, style = MaterialTheme.typography.bodyLarge, color = KrelzoxColors.OnSurfaceMuted)
        }
    }
}

@Composable
private fun Permissions(status: AppStatus) {
    val version = when (status) {
        is AppStatus.Installable -> status.version
        is AppStatus.Updatable -> status.version
        else -> null
    } ?: return
    val names = version.permissions.map { it.substringAfterLast('.') }.sorted()
    if (names.isEmpty()) return
    TextSection(stringResource(R.string.detail_permissions, names.size), names.joinToString(" · "))
}

@Composable
private fun Versions(viewModel: StoreViewModel, app: CatalogApp, installedCode: Long?) {
    val dates = DateFormat.getDateInstance(DateFormat.MEDIUM)
    val lines = app.versions.distinctBy { it.versionCode }.take(8).map { version ->
        val tags = buildList {
            if (version.versionCode == installedCode) add(stringResource(R.string.detail_version_installed))
            if (!Compatibility.isCompatible(version, viewModel.device)) add(stringResource(R.string.detail_version_incompatible))
        }
        stringResource(R.string.detail_version_line, version.versionName, dates.format(Date(version.added)), version.minSdk) +
            (if (tags.isEmpty()) "" else " · " + tags.joinToString())
    }
    TextSection(stringResource(R.string.detail_versions), lines.joinToString("\n"))
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleLarge, color = KrelzoxColors.OnBackground, modifier = Modifier.padding(bottom = KrelzoxSpacing.Small))
}

@Composable
private fun Muted(text: String) {
    Text(text, style = MaterialTheme.typography.bodyLarge, color = KrelzoxColors.OnSurfaceMuted)
}

@Composable
private fun Note(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, error: Boolean = false) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Icon(icon, contentDescription = null, tint = if (error) KrelzoxColors.Error else KrelzoxColors.Cyan, modifier = Modifier.size(20.dp))
        Text(
            text,
            style = MaterialTheme.typography.bodyLarge,
            color = if (error) KrelzoxColors.Error else KrelzoxColors.OnSurfaceMuted,
            modifier = Modifier.padding(start = KrelzoxSpacing.Small),
        )
    }
}
