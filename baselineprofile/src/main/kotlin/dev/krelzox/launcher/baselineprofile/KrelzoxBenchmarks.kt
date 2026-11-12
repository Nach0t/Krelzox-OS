package dev.krelzox.launcher.baselineprofile

import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Cold startup in three compilation states: none (JIT only, what a fresh install gets
 * without profiles), the Baseline Profile (what profileinstaller gives after install)
 * and full AOT (upper bound: shows how much startup depends on compilation at all).
 *
 * Navigation smoothness is NOT measured here: on this ROM FrameTimingMetric captures no
 * app frames (0 results), so it is measured with `dumpsys gfxinfo` instead
 * (docs/rendimiento.md).
 *
 *     ./gradlew :baselineprofile:connectedBenchmarkReleaseAndroidTest \
 *         -Pandroid.testInstrumentationRunnerArguments.androidx.benchmark.enabledRules=Macrobenchmark
 *
 * Same device requirements as [BaselineProfileGenerator].
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class KrelzoxBenchmarks {

    @get:Rule
    val rule = MacrobenchmarkRule()

    @Test
    fun startupNoCompilation() = startup(CompilationMode.None())

    @Test
    fun startupBaselineProfile() = startup(CompilationMode.Partial(BaselineProfileMode.Require))

    @Test
    fun startupFullAot() = startup(CompilationMode.Full())

    private fun startup(mode: CompilationMode) = rule.measureRepeated(
        packageName = KRELZOX_PACKAGE,
        metrics = listOf(StartupTimingMetric()),
        compilationMode = mode,
        startupMode = StartupMode.COLD,
        iterations = 15,
        setupBlock = { pressHome() },
    ) {
        startKrelzox()
    }
}
