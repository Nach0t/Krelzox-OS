package dev.nexium.launcher.core.data

import android.content.ComponentName
import android.content.pm.ApplicationInfo

/** A launchable activity shown by Nexium. Immutable and cheap to compare. */
data class AppEntry(
    val packageName: String,
    val activityName: String,
    val label: String,
    /** True if declared with LEANBACK_LAUNCHER (TV-native, usually has a banner). */
    val isTvNative: Boolean,
    val category: AppCategory = AppCategory.Other,
    /** Optional app-provided description (android:description). */
    val description: String? = null,
    val isSystem: Boolean = false,
) {
    /** Stable key for lazy lists and caches. */
    val key: String get() = "$packageName/$activityName"

    val component: ComponentName get() = ComponentName(packageName, activityName)
}

/** Simplified app category, from android:appCategory. */
enum class AppCategory {
    Video, Audio, Image, Game, News, Maps, Productivity, Social, Accessibility, Other;

    val isMedia: Boolean get() = this == Video || this == Audio || this == Image

    companion object {
        /** Maps [ApplicationInfo.category] constants. Pure, unit tested. */
        fun fromPlatform(category: Int, isGameFlag: Boolean = false): AppCategory = when (category) {
            ApplicationInfo.CATEGORY_VIDEO -> Video
            ApplicationInfo.CATEGORY_AUDIO -> Audio
            ApplicationInfo.CATEGORY_IMAGE -> Image
            ApplicationInfo.CATEGORY_GAME -> Game
            ApplicationInfo.CATEGORY_NEWS -> News
            ApplicationInfo.CATEGORY_MAPS -> Maps
            ApplicationInfo.CATEGORY_PRODUCTIVITY -> Productivity
            ApplicationInfo.CATEGORY_SOCIAL -> Social
            ApplicationInfo.CATEGORY_ACCESSIBILITY -> Accessibility
            else -> if (isGameFlag) Game else Other
        }
    }
}
