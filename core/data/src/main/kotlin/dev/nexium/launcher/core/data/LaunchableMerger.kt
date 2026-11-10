package dev.nexium.launcher.core.data

import java.text.Collator
import java.util.Locale

/**
 * Pure merge logic, kept free of Android framework calls so it can be unit tested.
 *
 * - TV-native (LEANBACK_LAUNCHER) activities win over phone-style LAUNCHER ones.
 * - A package with a TV-native activity contributes no LAUNCHER fallback.
 * - Nexium itself is never listed.
 * - Result is sorted by label using locale-aware collation, then by key for stability.
 */
internal object LaunchableMerger {

    fun merge(
        tvNative: List<AppEntry>,
        launcher: List<AppEntry>,
        ownPackage: String,
        locale: Locale = Locale.getDefault(),
    ): List<AppEntry> {
        val tvPackages = tvNative.mapTo(HashSet()) { it.packageName }
        val merged = LinkedHashMap<String, AppEntry>()

        tvNative.forEach { merged.putIfAbsent(it.key, it) }
        launcher
            .filter { it.packageName !in tvPackages }
            .forEach { merged.putIfAbsent(it.key, it) }

        val collator = Collator.getInstance(locale).apply { strength = Collator.PRIMARY }
        return merged.values
            .filter { it.packageName != ownPackage }
            .sortedWith(compareBy(collator) { e: AppEntry -> e.label }.thenBy { it.key })
    }
}
