package dev.krelzox.launcher.core.designsystem

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.ButtonBorder
import androidx.tv.material3.ButtonColors
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ButtonShape

/** Shared Krelzox styling for generic buttons (dialogs, empty states, fallbacks). */
object KrelzoxButtonDefaults {

    @Composable
    fun colors(): ButtonColors = ButtonDefaults.colors(
        containerColor = KrelzoxColors.Surface,
        contentColor = KrelzoxColors.OnBackground,
        focusedContainerColor = KrelzoxColors.Electric,
        focusedContentColor = KrelzoxColors.OnBackground,
    )

    @Composable
    fun border(): ButtonBorder = ButtonDefaults.border(
        focusedBorder = Border(BorderStroke(1.5.dp, KrelzoxColors.Cyan)),
    )

    @Composable
    fun shape(): ButtonShape = ButtonDefaults.shape(RoundedCornerShape(KrelzoxDimens.ButtonCorner))
}
