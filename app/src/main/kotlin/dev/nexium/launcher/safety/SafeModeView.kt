package dev.nexium.launcher.safety

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import dev.nexium.launcher.R

/**
 * Minimal recovery screen built with plain Views (no Compose), so it still works
 * if the crash comes from the regular UI stack.
 */
object SafeModeView {

    private val BACKGROUND = 0xFF07080C.toInt()
    private val SURFACE = 0xFF171B24.toInt()
    private val ION = 0xFF5B8CFF.toInt()
    private val TEXT = 0xFFF2F4F8.toInt()
    private val MUTED = 0xFF9AA3B2.toInt()

    fun create(
        context: Context,
        onChooseLauncher: () -> Unit,
        onOpenSettings: () -> Unit,
        onRetry: () -> Unit,
    ): View {
        fun dp(value: Int) = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), context.resources.displayMetrics,
        ).toInt()

        fun text(resId: Int, sizeSp: Float, color: Int) = TextView(context).apply {
            setText(resId)
            setTextColor(color)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
            gravity = Gravity.CENTER
        }

        fun button(resId: Int, onClick: () -> Unit) = Button(context).apply {
            setText(resId)
            isAllCaps = false
            setTextColor(TEXT)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            isFocusable = true
            background = GradientDrawable().apply { cornerRadius = dp(10).toFloat(); setColor(SURFACE) }
            setOnFocusChangeListener { v, hasFocus ->
                (v.background as GradientDrawable).setColor(if (hasFocus) ION else SURFACE)
            }
            setOnClickListener { onClick() }
            layoutParams = LinearLayout.LayoutParams(dp(420), LinearLayout.LayoutParams.WRAP_CONTENT)
                .apply { topMargin = dp(12) }
        }

        val first = button(R.string.safe_mode_choose_launcher, onChooseLauncher)
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(BACKGROUND)
            setPadding(dp(48), dp(27), dp(48), dp(27))
            addView(text(R.string.safe_mode_title, 32f, TEXT))
            addView(
                text(R.string.safe_mode_message, 18f, MUTED).apply {
                    setPadding(0, dp(12), 0, dp(24))
                },
            )
            addView(first)
            addView(button(R.string.open_settings, onOpenSettings))
            addView(button(R.string.safe_mode_retry, onRetry))
            post { first.requestFocus() }
            // Returning from another window (system chooser, settings) can leave nothing
            // focused; on a TV that means the first D-pad press is lost. Restore it.
            viewTreeObserver.addOnWindowFocusChangeListener { hasWindowFocus ->
                if (hasWindowFocus && findFocus() == null) first.requestFocus()
            }
        }
    }
}
