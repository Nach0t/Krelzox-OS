package dev.krelzox.launcher.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Generates app/src/main/generated/baselineProfiles/baseline-prof.txt:
 *
 *     ./gradlew :app:generateBaselineProfile
 *
 * Needs the Raspberry Pi connected by ADB with Krelzox NOT holding the HOME role
 * (the rule presses HOME between iterations). See docs/rendimiento.md.
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class BaselineProfileGenerator {

    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun generate() = rule.collect(
        packageName = KRELZOX_PACKAGE,
        // Startup code also goes to the startup profile (dex layout optimization).
        includeInStartupProfile = true,
    ) {
        startKrelzox()
        browseHome()
        browseTabsAndPerformance()
    }
}
