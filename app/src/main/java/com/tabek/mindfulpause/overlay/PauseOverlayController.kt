package com.tabek.mindfulpause.overlay

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.tabek.mindfulpause.data.Choice
import com.tabek.mindfulpause.data.EventLog
import com.tabek.mindfulpause.data.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Owns the full-screen pause overlay shown over a tracked app.
 *
 * The overlay is a WindowManager view (TYPE_APPLICATION_OVERLAY) rather than
 * an Activity, so it can appear instantly on top of the social app without a
 * task switch. Compose needs lifecycle / saved-state / viewmodel owners that
 * an Activity would normally provide, so this class supplies minimal ones.
 */
class PauseOverlayController(
    private val context: Context,
    private val repo: SettingsRepository,
    private val eventLog: EventLog,
    private val scope: CoroutineScope,
) {
    private val windowManager =
        context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    private var rootView: View? = null

    val isShowing: Boolean get() = rootView != null

    fun show(
        targetPackage: String,
        onContinue: () -> Unit,
        onExit: () -> Unit,
    ) {
        if (isShowing) return

        scope.launch {
            val config = repo.pauseConfig.first()

            val lifecycleOwner = OverlayLifecycleOwner().apply { onCreate(); onResume() }

            val composeView = ComposeView(context).apply {
                setViewTreeLifecycleOwner(lifecycleOwner)
                setViewTreeViewModelStoreOwner(lifecycleOwner)
                setViewTreeSavedStateRegistryOwner(lifecycleOwner)
                // Intercept BACK so the user can't escape the pause without choosing.
                isFocusableInTouchMode = true
                setOnKeyListener { _, keyCode, ev ->
                    if (keyCode == KeyEvent.KEYCODE_BACK && ev.action == KeyEvent.ACTION_UP) {
                        true // swallow
                    } else false
                }
                setContent {
                    PauseScreen(
                        config = config,
                        onContinue = {
                            scope.launch {
                                repo.incrementContinued()
                                eventLog.record(System.currentTimeMillis(), targetPackage, Choice.CONTINUED)
                            }
                            onContinue()
                            dismiss(lifecycleOwner)
                        },
                        onExit = {
                            scope.launch {
                                repo.incrementReflected()
                                eventLog.record(System.currentTimeMillis(), targetPackage, Choice.REFLECTED)
                            }
                            onExit()
                            dismiss(lifecycleOwner)
                        },
                    )
                }
            }

            val params = buildLayoutParams()
            try {
                windowManager.addView(composeView, params)
                composeView.requestFocus()
                rootView = composeView
            } catch (t: Throwable) {
                // Overlay permission revoked mid-flight, etc. Fail open: let the
                // user into the app rather than getting stuck.
                lifecycleOwner.onDestroy()
                onContinue()
            }
        }
    }

    /**
     * Show the full-screen "blocked" lock for [targetPackage]. [untilMs] is when
     * the block lifts (null = indefinite). The countdown self-dismisses and
     * returns home when it reaches zero.
     */
    fun showBlocked(
        targetPackage: String,
        untilMs: Long?,
        onBack: () -> Unit,
    ) {
        if (isShowing) return

        val lifecycleOwner = OverlayLifecycleOwner().apply { onCreate(); onResume() }

        val composeView = ComposeView(context).apply {
            setViewTreeLifecycleOwner(lifecycleOwner)
            setViewTreeViewModelStoreOwner(lifecycleOwner)
            setViewTreeSavedStateRegistryOwner(lifecycleOwner)
            isFocusableInTouchMode = true
            setOnKeyListener { _, keyCode, ev ->
                if (keyCode == KeyEvent.KEYCODE_BACK && ev.action == KeyEvent.ACTION_UP) {
                    onBack()
                    dismiss(lifecycleOwner)
                    true
                } else false
            }
            setContent {
                BlockedScreen(
                    untilMs = untilMs,
                    onBack = {
                        onBack()
                        dismiss(lifecycleOwner)
                    },
                    onElapsed = {
                        // Block expired while on screen — let the user proceed.
                        onBack()
                        dismiss(lifecycleOwner)
                    },
                )
            }
        }

        try {
            windowManager.addView(composeView, buildLayoutParams())
            composeView.requestFocus()
            rootView = composeView
        } catch (t: Throwable) {
            lifecycleOwner.onDestroy()
            onBack()
        }
    }

    fun dismiss() = dismiss(null)

    private fun dismiss(owner: OverlayLifecycleOwner?) {
        rootView?.let { v ->
            try {
                windowManager.removeView(v)
            } catch (_: Throwable) { /* already detached */ }
        }
        rootView = null
        owner?.onDestroy()
    }

    private fun buildLayoutParams(): WindowManager.LayoutParams {
        val type =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE

        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            type,
            // FLAG_NOT_TOUCH_MODAL is intentionally omitted so we capture all
            // touches; FLAG_WATCH_OUTSIDE not needed. We DO need focus to grab BACK.
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
        }
    }
}

/**
 * Minimal lifecycle + saved-state + viewmodel owner so a ComposeView can live
 * inside a WindowManager view (which has no Activity behind it).
 */
private class OverlayLifecycleOwner :
    LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateController = SavedStateRegistryController.create(this)
    override val viewModelStore = ViewModelStore()

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateController.savedStateRegistry

    fun onCreate() {
        savedStateController.performRestore(null)
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
    }

    fun onResume() {
        lifecycleRegistry.currentState = Lifecycle.State.RESUMED
    }

    fun onDestroy() {
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        viewModelStore.clear()
    }
}
