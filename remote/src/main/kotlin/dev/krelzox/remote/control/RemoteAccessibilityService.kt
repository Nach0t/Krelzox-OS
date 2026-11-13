package dev.krelzox.remote.control

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Color
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import dev.krelzox.remote.R
import dev.krelzox.remote.RemoteHub
import dev.krelzox.remote.security.Session

/**
 * Performs remote input on the TV with public accessibility APIs: gestures at screen
 * coordinates, global actions (D-pad, Back, Home…), text into the focused field and
 * launching apps. The user enables it once in Settings › Accessibility; Android shows
 * that it is active. It also draws the remote cursor, the pairing PIN and the control
 * approval prompt as accessibility overlays (no extra permission needed).
 *
 * Overlay windows don't reliably get D-pad focus on TV, so while the approval prompt is
 * up the service filters key events itself (and only then): Left/Right choose, OK/Enter
 * confirms, Back denies, and every other key is swallowed so it can't reach the app
 * underneath. The filter is switched off as soon as the prompt goes away.
 */
class RemoteAccessibilityService : AccessibilityService() {

    private val main = Handler(Looper.getMainLooper())
    private val windows by lazy { getSystemService(WindowManager::class.java) }
    private var cursor: View? = null
    private var pinView: View? = null
    private var approval: View? = null
    private var approvalChoices: List<Pair<Button, () -> Unit>> = emptyList()
    private var approvalIndex = 0
    private val hideCursor = Runnable { cursor?.visibility = View.GONE }

    override fun onServiceConnected() {
        RemoteHub.accessibility = this
    }

    override fun onUnbind(intent: Intent?): Boolean {
        removeAll()
        if (RemoteHub.accessibility === this) RemoteHub.accessibility = null
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        removeAll()
        if (RemoteHub.accessibility === this) RemoteHub.accessibility = null
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (approval == null || approvalChoices.isEmpty()) return false
        if (event.action != KeyEvent.ACTION_UP) return true // act on release, swallow the rest
        when (event.keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT -> highlightChoice(approvalIndex - 1)
            KeyEvent.KEYCODE_DPAD_RIGHT -> highlightChoice(approvalIndex + 1)
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER ->
                approvalChoices[approvalIndex].second()
            KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_ESCAPE -> approvalChoices.first().second() // deny
        }
        return true
    }

    /** Turns key filtering on only while the approval prompt needs the D-pad. */
    private fun filterKeys(enabled: Boolean) {
        val info = serviceInfo ?: return
        val flags = if (enabled) {
            info.flags or AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS
        } else {
            info.flags and AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS.inv()
        }
        if (flags != info.flags) serviceInfo = info.apply { this.flags = flags }
    }

    private fun highlightChoice(index: Int) {
        approvalIndex = index.coerceIn(0, approvalChoices.lastIndex)
        approvalChoices.forEachIndexed { i, (button, _) -> paint(button, i == approvalIndex) }
    }

    private val width get() = RemoteHub.screenWidth.toFloat()
    private val height get() = RemoteHub.screenHeight.toFloat()

    fun perform(command: Command) {
        when (command) {
            is Command.Tap -> gesture(stroke(command.x, command.y, durationMs = 40))
            is Command.DoubleTap -> gesture(stroke(command.x, command.y, durationMs = 40), stroke(command.x, command.y, startMs = 140, durationMs = 40))
            is Command.LongPress -> gesture(stroke(command.x, command.y, durationMs = 800))
            is Command.Swipe -> gesture(swipe(command.x1, command.y1, command.x2, command.y2, command.durationMs))
            is Command.Scroll -> gesture(
                swipe(
                    command.x, command.y,
                    (command.x - command.dx).coerceIn(0f, 1f), (command.y - command.dy).coerceIn(0f, 1f),
                    300,
                ),
            )
            is Command.Key -> key(command.key)
            is Command.Text -> editFocused { current -> current + command.text }
            Command.Backspace -> editFocused { current -> current.dropLast(1) }
            Command.Enter -> focusedInput()?.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_IME_ENTER.id)
                ?: performGlobalAction(GLOBAL_ACTION_DPAD_CENTER)
            is Command.Launch -> launch(command.packageName)
            is Command.Cursor -> showCursor(command.x, command.y)
        }
    }

    private fun key(key: RemoteKey) {
        val action = when (key) {
            RemoteKey.UP -> GLOBAL_ACTION_DPAD_UP
            RemoteKey.DOWN -> GLOBAL_ACTION_DPAD_DOWN
            RemoteKey.LEFT -> GLOBAL_ACTION_DPAD_LEFT
            RemoteKey.RIGHT -> GLOBAL_ACTION_DPAD_RIGHT
            RemoteKey.CENTER -> GLOBAL_ACTION_DPAD_CENTER
            RemoteKey.BACK -> GLOBAL_ACTION_BACK
            RemoteKey.HOME -> GLOBAL_ACTION_HOME
            RemoteKey.RECENTS -> GLOBAL_ACTION_RECENTS
            RemoteKey.NOTIFICATIONS -> GLOBAL_ACTION_NOTIFICATIONS
            RemoteKey.POWER_MENU -> GLOBAL_ACTION_POWER_DIALOG
            else -> return // media and volume keys are handled without accessibility
        }
        performGlobalAction(action)
    }

    private fun stroke(x: Float, y: Float, startMs: Long = 0, durationMs: Long) =
        GestureDescription.StrokeDescription(
            Path().apply { moveTo(x * (width - 1), y * (height - 1)) },
            startMs,
            durationMs,
        )

    private fun swipe(x1: Float, y1: Float, x2: Float, y2: Float, durationMs: Long) =
        GestureDescription.StrokeDescription(
            Path().apply {
                moveTo(x1 * (width - 1), y1 * (height - 1))
                lineTo(x2 * (width - 1), y2 * (height - 1))
            },
            0,
            durationMs,
        )

    private fun gesture(vararg strokes: GestureDescription.StrokeDescription) {
        val builder = GestureDescription.Builder()
        strokes.forEach(builder::addStroke)
        dispatchGesture(builder.build(), null, null)
    }

    private fun focusedInput(): AccessibilityNodeInfo? =
        findFocus(AccessibilityNodeInfo.FOCUS_INPUT)?.takeIf { it.isEditable }

    /** Replaces the focused field's text with [change] applied to its current text. */
    private fun editFocused(change: (String) -> String) {
        val node = focusedInput() ?: return
        val current = if (node.isShowingHintText) "" else node.text?.toString().orEmpty()
        val updated = change(current)
        node.performAction(
            AccessibilityNodeInfo.ACTION_SET_TEXT,
            Bundle().apply { putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, updated) },
        )
        node.performAction(
            AccessibilityNodeInfo.ACTION_SET_SELECTION,
            Bundle().apply {
                putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, updated.length)
                putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, updated.length)
            },
        )
    }

    private fun launch(packageName: String) {
        val intent = packageManager.getLeanbackLaunchIntentForPackage(packageName)
            ?: packageManager.getLaunchIntentForPackage(packageName)
            ?: return
        startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    // --- Overlays -----------------------------------------------------------------

    /** Overlays never take focus; only the approval prompt is touchable (for a mouse). */
    private fun overlayParams(touchable: Boolean, gravity: Int) = WindowManager.LayoutParams(
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            (if (touchable) 0 else WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE) or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
        PixelFormat.TRANSLUCENT,
    ).apply { this.gravity = gravity }

    private fun showCursor(x: Float, y: Float) {
        val view = cursor ?: View(this).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.argb(200, 34, 211, 238))
                setStroke(dp(2), Color.WHITE)
            }
        }.also { v ->
            windows.addView(v, overlayParams(false, Gravity.TOP or Gravity.START).apply { width = dp(18); height = dp(18) })
            cursor = v
        }
        view.visibility = View.VISIBLE
        val params = view.layoutParams as WindowManager.LayoutParams
        params.x = (x * width).toInt() - dp(9)
        params.y = (y * height).toInt() - dp(9)
        windows.updateViewLayout(view, params)
        main.removeCallbacks(hideCursor)
        main.postDelayed(hideCursor, 4_000)
    }

    /** Shows the pairing PIN in a corner of the TV for a minute. */
    fun showPin(pin: String, address: String) {
        pinView?.let { runCatching { windows.removeView(it) } }
        val view = card().apply {
            addView(text(getString(R.string.overlay_pin_title), 15f, Color.argb(255, 154, 163, 178)))
            addView(text(pin.chunked(3).joinToString(" "), 40f, Color.WHITE, bold = true))
            addView(text(getString(R.string.overlay_pin_from, address), 13f, Color.argb(255, 154, 163, 178)))
        }
        windows.addView(view, overlayParams(false, Gravity.TOP or Gravity.END).apply { x = dp(32); y = dp(32) })
        pinView = view
        main.postDelayed({ if (pinView === view) removePin() }, 60_000)
    }

    fun removePin() {
        pinView?.let { runCatching { windows.removeView(it) } }
        pinView = null
    }

    /** Asks the person at the TV whether [session] may control it. Times out as "deny". */
    fun showApproval(session: Session) {
        dismissApproval()
        removePin()
        lateinit var view: LinearLayout
        val decide = { allow: Boolean ->
            RemoteHub.decideControl(session.id, allow)
            if (approval === view) dismissApproval()
        }
        view = card().apply {
            addView(text(getString(R.string.overlay_control_title), 20f, Color.WHITE, bold = true))
            addView(text(getString(R.string.overlay_control_body, session.label, session.address), 15f, Color.argb(255, 154, 163, 178)))
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, dp(12), 0, 0)
            }
            val deny = button(getString(R.string.overlay_deny)) { decide(false) }
            val allow = button(getString(R.string.overlay_allow)) { decide(true) }
            row.addView(deny)
            row.addView(allow, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { marginStart = dp(12) })
            addView(row)
            approvalChoices = listOf(deny to { decide(false) }, allow to { decide(true) })
        }
        // Keys come from the filter, not window focus, so the app underneath keeps its focus.
        windows.addView(view, overlayParams(true, Gravity.CENTER))
        approval = view
        // Deny is highlighted first: a stray OK never grants control.
        highlightChoice(0)
        filterKeys(true)
        main.postDelayed({ if (approval === view) decide(false) }, 60_000)
    }

    fun dismissApproval() {
        approval?.let { runCatching { windows.removeView(it) } }
        approval = null
        approvalChoices = emptyList()
        filterKeys(false)
    }

    private fun removeAll() {
        main.removeCallbacksAndMessages(null)
        listOfNotNull(cursor, pinView, approval).forEach { runCatching { windows.removeView(it) } }
        cursor = null
        pinView = null
        approval = null
        approvalChoices = emptyList()
    }

    private fun card() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(24), dp(20), dp(24), dp(20))
        background = GradientDrawable().apply {
            cornerRadius = dp(16).toFloat()
            setColor(Color.argb(240, 20, 24, 33))
            setStroke(dp(2), Color.argb(255, 47, 107, 255))
        }
    }

    private fun text(value: String, sp: Float, color: Int, bold: Boolean = false) = TextView(this).apply {
        text = value
        setTextColor(color)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)
        if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
    }

    private fun button(label: String, onClick: () -> Unit) = Button(this).apply {
        text = label
        isAllCaps = false
        setTextColor(Color.WHITE)
        background = GradientDrawable().apply {
            cornerRadius = dp(12).toFloat()
            setColor(Color.argb(255, 28, 33, 44))
        }
        setPadding(dp(20), dp(10), dp(20), dp(10))
        setOnClickListener { onClick() }
    }

    private fun paint(button: Button, highlighted: Boolean) {
        (button.background as GradientDrawable).setColor(if (highlighted) Color.argb(255, 47, 107, 255) else Color.argb(255, 28, 33, 44))
        button.scaleX = if (highlighted) 1.06f else 1f
        button.scaleY = button.scaleX
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}
