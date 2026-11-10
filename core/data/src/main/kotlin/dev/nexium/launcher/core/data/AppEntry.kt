package dev.nexium.launcher.core.data

import android.content.ComponentName

/** A launchable activity shown by Nexium. Immutable and cheap to compare. */
data class AppEntry(
    val packageName: String,
    val activityName: String,
    val label: String,
    /** True if declared with LEANBACK_LAUNCHER (TV-native, usually has a banner). */
    val isTvNative: Boolean,
) {
    /** Stable key for lazy lists and caches. */
    val key: String get() = "$packageName/$activityName"

    val component: ComponentName get() = ComponentName(packageName, activityName)
}
