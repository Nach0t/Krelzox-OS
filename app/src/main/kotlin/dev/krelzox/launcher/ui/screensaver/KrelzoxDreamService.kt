package dev.krelzox.launcher.ui.screensaver

import android.service.dreams.DreamService
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import dev.krelzox.launcher.core.designsystem.KrelzoxTheme

/**
 * System screensaver ("Protector de pantalla" › Krelzox). Shows [KrelzoxScreensaver] in the
 * active theme, which KrelzoxApp restores before this service draws.
 *
 * Non-interactive: any key wakes the TV, as users expect. A DreamService is not a
 * LifecycleOwner, so it provides the lifecycle and saved state that Compose needs.
 * Selecting it needs no permission: the user picks it in system Settings.
 */
class KrelzoxDreamService : DreamService(), LifecycleOwner, SavedStateRegistryOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateController = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry get() = savedStateController.savedStateRegistry

    override fun onCreate() {
        super.onCreate()
        savedStateController.performRestore(null)
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        isInteractive = false
        isFullscreen = true
        window?.decorView?.let { decor ->
            decor.setViewTreeLifecycleOwner(this)
            decor.setViewTreeSavedStateRegistryOwner(this)
        }
        setContentView(
            ComposeView(this).apply {
                setViewTreeLifecycleOwner(this@KrelzoxDreamService)
                setViewTreeSavedStateRegistryOwner(this@KrelzoxDreamService)
                setContent {
                    KrelzoxTheme { KrelzoxScreensaver(Modifier.fillMaxSize()) }
                }
            },
        )
    }

    override fun onDreamingStarted() {
        super.onDreamingStarted()
        lifecycleRegistry.currentState = Lifecycle.State.RESUMED
    }

    override fun onDreamingStopped() {
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
        super.onDreamingStopped()
    }

    override fun onDetachedFromWindow() {
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        super.onDetachedFromWindow()
    }
}
