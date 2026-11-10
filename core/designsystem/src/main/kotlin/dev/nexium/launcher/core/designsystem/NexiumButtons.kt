package dev.nexium.launcher.core.designsystem

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.ButtonBorder
import androidx.tv.material3.ButtonColors
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ButtonShape

/** Shared Nexium styling for generic buttons (dialogs, empty states, fallbacks). */
object NexiumButtonDefaults {

    @Composable
    fun colors(): ButtonColors = ButtonDefaults.colors(
        containerColor = NexiumColors.Surface,
        contentColor = NexiumColors.OnBackground,
        focusedContainerColor = NexiumColors.Electric,
        focusedContentColor = NexiumColors.OnBackground,
    )

    @Composable
    fun border(): ButtonBorder = ButtonDefaults.border(
        focusedBorder = Border(BorderStroke(1.5.dp, NexiumColors.Cyan)),
    )

    @Composable
    fun shape(): ButtonShape = ButtonDefaults.shape(RoundedCornerShape(NexiumDimens.ButtonCorner))
}
