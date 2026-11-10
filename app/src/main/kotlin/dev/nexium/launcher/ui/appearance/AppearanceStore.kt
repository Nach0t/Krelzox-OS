package dev.nexium.launcher.ui.appearance

import android.content.Context
import androidx.core.content.edit
import dev.nexium.launcher.core.designsystem.NexiumAccent
import dev.nexium.launcher.core.designsystem.NexiumColors

/**
 * The chosen theme. SharedPreferences (not DataStore) on purpose: it is read
 * synchronously in Application.onCreate, so the first frame of the launcher and of the
 * screensaver is already drawn in the right theme, with no flash. See ADR-005.
 */
class AppearanceStore(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    /** Applies the saved theme to [NexiumColors]. Unknown or missing values fall back to Ion. */
    fun restore() {
        NexiumColors.accent = NexiumAccent.fromKey(prefs.getString(KEY_ACCENT, null))
    }

    /** Applies [accent] everywhere at once and saves it. */
    fun select(accent: NexiumAccent) {
        NexiumColors.accent = accent
        prefs.edit { putString(KEY_ACCENT, accent.key) }
    }

    private companion object {
        const val FILE = "nexium_appearance"
        const val KEY_ACCENT = "accent"
    }
}
