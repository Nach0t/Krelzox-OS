package dev.nexium.launcher.ui.shell

import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.annotation.StringRes
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.nexium.launcher.R
import dev.nexium.launcher.core.data.AppEntry
import dev.nexium.launcher.core.data.NetworkDetails
import dev.nexium.launcher.core.data.NetworkStatus
import dev.nexium.launcher.core.data.metrics.SystemMetrics
import dev.nexium.launcher.core.data.metrics.SystemSnapshot
import dev.nexium.launcher.core.designsystem.NexiumAccent
import dev.nexium.launcher.core.designsystem.NexiumColors
import dev.nexium.launcher.core.designsystem.NexiumTheme
import dev.nexium.launcher.ui.AppArtLoader
import dev.nexium.launcher.ui.home.buildReadyState
import dev.nexium.launcher.ui.performance.SystemSources
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * D-pad focus rules of the shell, driven with real key events (as from a remote).
 * Regression tests for the etapa 5 bugs: ↓ from the Ajustes tab landed on "Red", and
 * OK on "Sistema y rendimiento" jumped to Inicio instead of opening the center.
 *
 *     ./gradlew :app:connectedDebugAndroidTest
 *
 * Runs against the debug build (dev.nexium.launcher.debug), so the installed Nexium is
 * never replaced or uninstalled.
 */
@RunWith(AndroidJUnit4::class)
class NexiumShellFocusTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()

    @Before
    fun showShell() {
        NexiumColors.accent = NexiumAccent.Ion
        val apps = (1..6).map { i ->
            AppEntry(packageName = "test.app$i", activityName = "test.app$i.Main", label = "App $i", isTvNative = true)
        }
        val state = buildReadyState(apps, favoriteKeys = apps.take(2).map { it.key })
        rule.setContent {
            NexiumTheme {
                NexiumShell(
                    state = state,
                    network = NetworkStatus.Wifi,
                    artLoader = AppArtLoader(rule.activity),
                    homeResets = emptyFlow(),
                    callbacks = ShellCallbacks(
                        onLaunch = {}, onToggleFavorite = {}, onMoveFavorite = { _, _ -> }, onAppInfo = {},
                        onNetworkSettings = {}, onDisplaySoundSettings = {}, onHomeAppSettings = {}, onSystemSettings = {},
                        // Same effect as the real store, without persisting anything.
                        onSelectAccent = { NexiumColors.accent = it },
                        showRaspberrySettings = true,
                    ),
                    system = SystemSources(flowOf(FakeMetrics), flowOf(FakeNetwork)),
                )
            }
        }
        rule.waitForIdle()
    }

    @Test
    fun startsOnHero() {
        assertFocused(R.string.hero_open)
    }

    @Test
    fun downFromSettingsTabFocusesPerformanceTile() {
        goToSettingsTab()
        press(KeyEvent.KEYCODE_DPAD_DOWN)
        assertFocused(R.string.perf_tile_title)
    }

    @Test
    fun okOnPerformanceTileOpensCenterAndBackReturnsToTile() {
        goToSettingsTab()
        press(KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_CENTER)
        assertFocused(R.string.cat_processor)

        press(KeyEvent.KEYCODE_BACK)
        assertFocused(R.string.perf_tile_title)
    }

    @Test
    fun expandedPanelOpensCenter() {
        goToSettingsTab()
        // The performance panel sits right of the tabs; OK expands it, ↓ reaches its button.
        press(KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_DPAD_CENTER)
        press(KeyEvent.KEYCODE_DPAD_DOWN)
        assertFocused(R.string.perf_open_center)

        press(KeyEvent.KEYCODE_DPAD_CENTER)
        assertFocused(R.string.cat_processor)
    }

    @Test
    fun appearanceOpensOnThemeInUseAndBackReturnsToTile() {
        goToSettingsTab()
        press(KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_DOWN)
        assertFocused(R.string.settings_appearance)

        press(KeyEvent.KEYCODE_DPAD_CENTER)
        assertFocused(R.string.theme_ion)

        press(KeyEvent.KEYCODE_BACK)
        assertFocused(R.string.settings_appearance)
    }

    @Test
    fun okOnThemeAppliesIt() {
        goToSettingsTab()
        press(KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_CENTER)
        assertFocused(R.string.theme_ion)

        press(KeyEvent.KEYCODE_DPAD_RIGHT)
        assertFocused(R.string.theme_aurora)
        press(KeyEvent.KEYCODE_DPAD_CENTER)
        rule.runOnIdle { assertEquals(NexiumAccent.Aurora, NexiumColors.accent) }
        assertFocused(R.string.theme_aurora)
    }

    @Test
    fun raspberryTileExplainsWhereSettingsAre() {
        goToSettingsTab()
        // Row 2: Apariencia (two columns) → Raspberry Pi.
        press(KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT)
        assertFocused(R.string.settings_raspberry)

        press(KeyEvent.KEYCODE_DPAD_CENTER)
        assertFocused(R.string.action_open_settings)

        press(KeyEvent.KEYCODE_BACK)
        assertFocused(R.string.settings_raspberry)
    }

    @After
    fun restoreTheme() {
        NexiumColors.accent = NexiumAccent.Ion
    }

    /** Hero → Inicio tab → Biblioteca → Multimedia → Ajustes (tabs are selected on focus). */
    private fun goToSettingsTab() {
        press(KeyEvent.KEYCODE_DPAD_UP)
        assertFocused(R.string.tab_home)
        press(KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_DPAD_RIGHT)
        assertFocused(R.string.tab_settings)
    }

    /** Real key events through the window, like a remote (also reaches the Back dispatcher). */
    private fun press(vararg keyCodes: Int) {
        keyCodes.forEach { code ->
            instrumentation.sendKeyDownUpSync(code)
            rule.waitForIdle()
        }
    }

    private fun assertFocused(@StringRes text: Int) {
        val label = instrumentation.targetContext.getString(text)
        val matcher: SemanticsMatcher = isFocused() and (hasText(label) or hasAnyDescendant(hasText(label)))
        rule.waitUntil(timeoutMillis = 3_000) {
            rule.onAllNodes(matcher, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private companion object {
        val FakeSnapshot = SystemSnapshot(
            cpuPercent = 12f, load = listOf(0.5f, 0.4f, 0.3f), cpuCores = 4,
            cpuFreqMHz = 1500, cpuMaxFreqMHz = 2400, temperatureC = 48f, fanRpm = 0,
            ramTotalBytes = 16_000_000_000, ramAvailableBytes = 15_000_000_000, lowMemory = false,
            storageTotalBytes = 11_000_000_000, storageFreeBytes = 10_000_000_000, uptimeMs = 600_000,
            netRxBytesPerSec = 1_000, netTxBytesPerSec = 500, unavailable = emptySet(),
        )
        val FakeMetrics = SystemMetrics(
            snapshot = FakeSnapshot,
            cpuHistory = listOf(10f, 12f), ramHistory = listOf(6f, 6f), temperatureHistory = listOf(47f, 48f),
            netRxHistory = listOf(800f, 1_000f), netTxHistory = listOf(400f, 500f),
            sampleIntervalMs = 2_000, historyCapacity = 120,
        )
        val FakeNetwork = NetworkDetails(NetworkStatus.Wifi, validated = true, interfaceName = "wlan0", ipv4 = "10.0.0.2", ipv6 = null, wifiLinkMbps = 100)
    }
}
