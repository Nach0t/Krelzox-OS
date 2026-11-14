package dev.krelzox.launcher.core.designsystem

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.pointer.pointerInput

/**
 * Makes a Compose for TV clickable (Surface, Card, Button, ListItem…) respond to touch
 * and mouse clicks as well. TV Material components only react to the D-pad, so a tap
 * (from a touchscreen, a mouse or Krelzox Remote's Touch/Trackpad modes) did nothing.
 *
 * A tap moves focus to the component, so the D-pad continues from there, and runs
 * [onClick]; a long press runs [onLongClick] when there is one, like holding OK.
 * Place it first in the component's modifier chain. Drags are not consumed, so lists
 * still scroll by touch.
 */
fun Modifier.touchClick(onClick: () -> Unit, onLongClick: (() -> Unit)? = null): Modifier = composed {
    val focus = remember { FocusRequester() }
    val click by rememberUpdatedState(onClick)
    val longClick by rememberUpdatedState(onLongClick)
    val hasLongClick = onLongClick != null
    focusRequester(focus).pointerInput(hasLongClick) {
        detectTapGestures(
            onTap = {
                runCatching { focus.requestFocus() }
                click()
            },
            onLongPress = if (hasLongClick) {
                {
                    runCatching { focus.requestFocus() }
                    longClick?.invoke()
                }
            } else {
                null
            },
        )
    }
}
