package com.tabek.mindfulpause.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.tabek.mindfulpause.data.EventLog
import com.tabek.mindfulpause.data.SettingsRepository
import com.tabek.mindfulpause.overlay.PauseOverlayController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

/**
 * Listens for window-state changes and, when a *tracked* app comes to the
 * foreground, shows the mindful pause overlay before the user can use it.
 *
 * Why AccessibilityService: it is the only API that fires the moment an app
 * is opened. UsageStats only reports usage after the fact, which is too late
 * to interrupt. This mirrors how OneSec / Mindful detect app launches.
 */
class InterceptAccessibilityService : AccessibilityService() {

    private lateinit var repo: SettingsRepository
    private lateinit var eventLog: EventLog
    private lateinit var overlay: PauseOverlayController
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    /**
     * Kept warm by collecting the trackedApps Flow. onAccessibilityEvent
     * cannot suspend, so it reads this synchronous snapshot instead.
     */
    @Volatile
    private var trackedApps: Set<String> = emptySet()

    /** Package we last raised the pause for, to avoid re-triggering on every
     *  sub-window change inside the same app session. */
    private var lastHandledPackage: String? = null

    /** When the user picks "Продолжить", we grant a short grace window so the
     *  app they just allowed doesn't immediately re-trigger the pause. */
    @Volatile
    private var grantedPackage: String? = null
    @Volatile
    private var grantExpiresAt: Long = 0L

    override fun onServiceConnected() {
        super.onServiceConnected()
        repo = SettingsRepository(applicationContext)
        eventLog = EventLog(applicationContext)
        overlay = PauseOverlayController(this, repo, eventLog, scope)

        repo.trackedApps
            .onEach { trackedApps = it }
            .launchIn(scope)

        Log.i(TAG, "Accessibility service connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return

        val pkg = event.packageName?.toString() ?: return

        // Ignore our own UI (settings + the overlay itself).
        if (pkg == packageName) return

        // Leaving a tracked app resets the de-dupe latch so re-entering pauses again.
        if (pkg != lastHandledPackage) {
            lastHandledPackage = null
        }

        if (pkg !in trackedApps) return

        // Respect a recent "Продолжить" grant for this package.
        if (pkg == grantedPackage && SystemClock.elapsedRealtime() < grantExpiresAt) {
            return
        }

        // Already showing / just showed for this package — don't stack overlays.
        if (pkg == lastHandledPackage || overlay.isShowing) return

        lastHandledPackage = pkg
        overlay.show(
            targetPackage = pkg,
            onContinue = { grantPackage(pkg) },
            onExit = { goHome() },
        )
    }

    private fun grantPackage(pkg: String) {
        grantedPackage = pkg
        grantExpiresAt = SystemClock.elapsedRealtime() + GRANT_WINDOW_MS
    }

    private fun goHome() {
        lastHandledPackage = null
        val home = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        startActivity(home)
    }

    override fun onInterrupt() { /* no-op */ }

    override fun onDestroy() {
        super.onDestroy()
        if (::overlay.isInitialized) overlay.dismiss()
        scope.cancel()
    }

    companion object {
        private const val TAG = "InterceptService"
        private const val GRANT_WINDOW_MS = 30_000L
    }
}
