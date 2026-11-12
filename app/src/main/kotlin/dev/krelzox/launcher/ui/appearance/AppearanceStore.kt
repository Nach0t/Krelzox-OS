package dev.krelzox.launcher.ui.appearance

import android.content.Context
import androidx.core.content.edit
import dev.krelzox.launcher.core.designsystem.KrelzoxAccent
import dev.krelzox.launcher.core.designsystem.KrelzoxColors

/**
 * The chosen theme. SharedPreferences (not DataStore) on purpose: it is read
 * synchronously in Application.onCreate, so the first frame of the launcher and of the
 * screensaver is already drawn in the right theme, with no flash. See ADR-005.
 */
class AppearanceStore(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    /** Applies the saved theme to [KrelzoxColors]. Unknown or missing values fall back to Ion. */
    fun restore() {
        KrelzoxColors.accent = KrelzoxAccent.fromKey(prefs.getString(KEY_ACCENT, null))
    }

    /** Applies [accent] everywhere at once and saves it. */
    fun select(accent: KrelzoxAccent) {
        KrelzoxColors.accent = accent
        prefs.edit { putString(KEY_ACCENT, accent.key) }
    }

    private companion object {
        const val FILE = "krelzox_appearance"
        const val KEY_ACCENT = "accent"
    }
}
