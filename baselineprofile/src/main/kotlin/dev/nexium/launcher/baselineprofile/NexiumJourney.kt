package dev.nexium.launcher.baselineprofile

import android.content.ComponentName
import android.content.Intent
import androidx.benchmark.macro.MacrobenchmarkScope

/** Package of the app under test (release / benchmark builds have no suffix). */
const val NEXIUM_PACKAGE = "dev.nexium.launcher"

/**
 * Explicit launch intent: Nexium declares LEANBACK_LAUNCHER and HOME but not LAUNCHER,
 * so the default lookup used by Macrobenchmark would not find it.
 */
fun MacrobenchmarkScope.startNexium() {
    startActivityAndWait(
        Intent(Intent.ACTION_MAIN).apply {
            component = ComponentName(NEXIUM_PACKAGE, "$NEXIUM_PACKAGE.MainActivity")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        },
    )
    device.waitForIdle()
}

/** D-pad presses with a short settle, as a user with a remote would do. */
private fun MacrobenchmarkScope.press(vararg keys: Key, settleMs: Long = 250) {
    keys.forEach { key ->
        when (key) {
            Key.Up -> device.pressDPadUp()
            Key.Down -> device.pressDPadDown()
            Key.Left -> device.pressDPadLeft()
            Key.Right -> device.pressDPadRight()
            Key.Ok -> device.pressDPadCenter()
            Key.Back -> device.pressBack()
        }
        Thread.sleep(settleMs)
    }
    device.waitForIdle()
}

enum class Key { Up, Down, Left, Right, Ok, Back }

/**
 * Inicio: hero ↔ Favoritos ↔ Aplicaciones, moving along each row. Starts and ends on
 * the hero's *Abrir* button. Opens nothing.
 */
fun MacrobenchmarkScope.browseHome() {
    press(Key.Down, Key.Right, Key.Right, Key.Right, Key.Left, Key.Left, Key.Left)
    press(Key.Down, Key.Right, Key.Right, Key.Left, Key.Left)
    press(Key.Up, Key.Up)
}

/**
 * The rest of the UI: every tab, the performance center (all categories), Apariencia
 * (themes and screensaver, browsed only) and the expanded performance panel. Starts on
 * the hero; ends back on it.
 */
fun MacrobenchmarkScope.browseTabsAndPerformance() {
    // Hero → Inicio tab → Biblioteca → Multimedia → Ajustes (tabs are selected on focus).
    press(Key.Up, Key.Right, Key.Right, Key.Right, settleMs = 400)
    // Ajustes grid → "Sistema y rendimiento" → its five categories → back to the grid.
    press(Key.Down, Key.Ok, settleMs = 600)
    press(Key.Down, Key.Down, Key.Down, Key.Down, settleMs = 400)
    // Back → its tile; ↓ → Apariencia: browse the themes and the screensaver actions
    // WITHOUT pressing OK (that would change the user's theme).
    press(Key.Back, settleMs = 600)
    press(Key.Down, Key.Ok, settleMs = 700)
    press(Key.Right, Key.Down, Key.Left, Key.Right, Key.Right, settleMs = 400)
    // Back is hierarchical: page → its tile → Ajustes tab (never ↑, which could land on,
    // and select, another tab).
    press(Key.Back, Key.Back, settleMs = 600)
    // Performance panel (right of the tabs) → expanded view → its metrics → close.
    press(Key.Right, Key.Ok, settleMs = 800)
    press(Key.Right, Key.Right, Key.Back, settleMs = 400)
    // From the top bar, Back goes to Inicio and focuses the hero.
    press(Key.Back, settleMs = 600)
}
