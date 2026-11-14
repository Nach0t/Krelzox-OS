package dev.krelzox.launcher.ui.settings

import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.Button
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Glow
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import dev.krelzox.launcher.R
import dev.krelzox.launcher.core.designsystem.KrelzoxAccent
import dev.krelzox.launcher.core.designsystem.KrelzoxButtonDefaults
import dev.krelzox.launcher.core.designsystem.KrelzoxColors
import dev.krelzox.launcher.core.designsystem.KrelzoxDimens
import dev.krelzox.launcher.core.designsystem.KrelzoxIcons
import dev.krelzox.launcher.core.designsystem.KrelzoxSpacing
import dev.krelzox.launcher.ui.screensaver.KrelzoxScreensaver
import dev.krelzox.launcher.core.designsystem.touchClick

private val CardShape = RoundedCornerShape(KrelzoxDimens.CardCorner)
private val PreviewShape = RoundedCornerShape(12.dp)

@StringRes
internal fun KrelzoxAccent.label(): Int = when (this) {
    KrelzoxAccent.Ion -> R.string.theme_ion
    KrelzoxAccent.Aurora -> R.string.theme_aurora
    KrelzoxAccent.Amber -> R.string.theme_amber
    KrelzoxAccent.Jade -> R.string.theme_jade
}

@StringRes
private fun KrelzoxAccent.description(): Int = when (this) {
    KrelzoxAccent.Ion -> R.string.theme_ion_desc
    KrelzoxAccent.Aurora -> R.string.theme_aurora_desc
    KrelzoxAccent.Amber -> R.string.theme_amber_desc
    KrelzoxAccent.Jade -> R.string.theme_jade_desc
}

/**
 * Ajustes › Apariencia: theme (left) and screensaver (right), one page of the same
 * system. Opens with focus on the theme in use; OK on a theme applies it everywhere at
 * once (this page included). Read-only otherwise: nothing here touches system settings.
 */
@Composable
fun AppearancePage(
    current: KrelzoxAccent,
    onSelectAccent: (KrelzoxAccent) -> Unit,
    onPreviewScreensaver: () -> Unit,
    onOpenSystemSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentCard = remember { FocusRequester() }
    val previewButton = remember { FocusRequester() }
    Column(modifier.fillMaxSize().padding(top = KrelzoxSpacing.Small)) {
        Text(stringResource(R.string.appearance_title), style = MaterialTheme.typography.headlineMedium, color = KrelzoxColors.OnBackground)
        Text(stringResource(R.string.appearance_subtitle), style = MaterialTheme.typography.bodyMedium, color = KrelzoxColors.OnSurfaceMuted)
        Spacer(Modifier.height(KrelzoxSpacing.Medium))
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(KrelzoxSpacing.Large)) {
            Column(Modifier.weight(1.15f)) {
                SectionLabel(R.string.appearance_theme)
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    KrelzoxAccent.entries.chunked(2).forEach { pair ->
                        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            pair.forEachIndexed { column, accent ->
                                ThemeCard(
                                    accent = accent,
                                    selected = accent == current,
                                    onClick = { onSelectAccent(accent) },
                                    modifier = Modifier
                                        .then(if (accent == current) Modifier.focusRequester(currentCard) else Modifier)
                                        // → from the right column goes to the screensaver actions (lower
                                        // on screen), not up to the top bar's performance panel.
                                        .then(if (column == 1) Modifier.focusProperties { right = previewButton } else Modifier),
                                )
                            }
                        }
                    }
                }
            }
            Column(Modifier.weight(0.9f)) {
                SectionLabel(R.string.appearance_screensaver)
                ScreensaverCard(onPreviewScreensaver, onOpenSystemSettings, previewButton)
            }
        }
    }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        runCatching { currentCard.requestFocus() }
    }
}

@Composable
private fun SectionLabel(@StringRes text: Int) {
    Text(
        stringResource(text).uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = KrelzoxColors.Cyan,
        modifier = Modifier.padding(bottom = 10.dp),
    )
}

/**
 * A theme as a miniature of Krelzox: brand mark, accent bar and three cards with the middle
 * one "focused". Its own focus ring uses ITS accent, so focusing it previews the theme.
 */
@Composable
private fun RowScope.ThemeCard(accent: KrelzoxAccent, selected: Boolean, onClick: () -> Unit, modifier: Modifier) {
    Surface(
        onClick = onClick,
        modifier = modifier.touchClick(onClick).weight(1f).height(132.dp),
        shape = ClickableSurfaceDefaults.shape(CardShape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = KrelzoxColors.Surface,
            contentColor = KrelzoxColors.OnBackground,
            focusedContainerColor = KrelzoxColors.SurfaceRaised,
            focusedContentColor = KrelzoxColors.OnBackground,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.04f),
        border = ClickableSurfaceDefaults.border(
            border = if (selected) Border(BorderStroke(1.5.dp, accent.primary), shape = CardShape) else Border.None,
            focusedBorder = Border(BorderStroke(KrelzoxDimens.FocusBorder, accent.brush), shape = CardShape),
        ),
        glow = ClickableSurfaceDefaults.glow(focusedGlow = Glow(accent.primary.copy(alpha = 0.45f), KrelzoxDimens.FocusGlow)),
    ) {
        Column(Modifier.fillMaxSize().padding(14.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Column { Miniature(accent) }
                Spacer(Modifier.weight(1f))
                if (selected) InUseBadge(accent)
            }
            Spacer(Modifier.weight(1f))
            Text(stringResource(accent.label()), style = MaterialTheme.typography.labelLarge, maxLines = 1)
            Text(
                stringResource(accent.description()),
                style = MaterialTheme.typography.labelMedium,
                color = KrelzoxColors.OnSurfaceMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun Miniature(accent: KrelzoxAccent) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Image(rememberVectorPainter(KrelzoxIcons.logoMarkFor(accent)), contentDescription = null, modifier = Modifier.size(12.dp, 13.dp))
        Box(Modifier.width(34.dp).height(4.dp).background(accent.horizontal, CircleShape))
    }
    Spacer(Modifier.height(10.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(3) { i ->
            Box(
                Modifier
                    .size(width = 30.dp, height = 18.dp)
                    // Background, not a surface color: visible on the card at rest and focused.
                    .background(KrelzoxColors.Background, RoundedCornerShape(5.dp))
                    .then(if (i == 1) Modifier.border(1.5.dp, accent.brush, RoundedCornerShape(5.dp)) else Modifier),
            )
        }
    }
}

@Composable
private fun InUseBadge(accent: KrelzoxAccent) {
    Row(
        Modifier
            .background(accent.primary.copy(alpha = 0.22f), CircleShape)
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(KrelzoxIcons.Check, contentDescription = null, tint = accent.secondary, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(4.dp))
        Text(
            stringResource(R.string.theme_in_use),
            style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp),
            color = KrelzoxColors.OnBackground,
        )
    }
}

/** Live miniature of the real screensaver (static here: no moves), how to enable it, actions. */
@Composable
private fun ScreensaverCard(onPreview: () -> Unit, onOpenSystemSettings: () -> Unit, previewButton: FocusRequester) {
    val description = stringResource(R.string.screensaver_desc)
    Column(
        Modifier
            .fillMaxWidth()
            .background(KrelzoxColors.Surface, CardShape)
            .padding(14.dp),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(PreviewShape)
                .border(1.dp, KrelzoxColors.Outline, PreviewShape),
        ) {
            KrelzoxScreensaver(Modifier.fillMaxSize(), animate = false)
        }
        Spacer(Modifier.height(10.dp))
        Text(
            stringResource(R.string.screensaver_how_to),
            style = MaterialTheme.typography.labelMedium,
            color = KrelzoxColors.OnSurfaceMuted,
            maxLines = 2,
        )
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
                onClick = onPreview,
                modifier = Modifier.touchClick(onPreview).focusRequester(previewButton),
                colors = KrelzoxButtonDefaults.colors(),
                border = KrelzoxButtonDefaults.border(),
                shape = KrelzoxButtonDefaults.shape(),
            ) {
                Icon(KrelzoxIcons.Moon, contentDescription = description, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.screensaver_preview))
            }
            Button(
                onClick = onOpenSystemSettings,
                modifier = Modifier.touchClick(onOpenSystemSettings),
                colors = KrelzoxButtonDefaults.colors(),
                border = KrelzoxButtonDefaults.border(),
                shape = KrelzoxButtonDefaults.shape(),
            ) { Text(stringResource(R.string.action_open_settings)) }
        }
    }
}
