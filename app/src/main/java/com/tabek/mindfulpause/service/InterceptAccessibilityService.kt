package com.tabek.mindfulpause.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.tabek.mindfulpause.data.BlockRepository
import com.tabek.mindfulpause.data.DailyLimit
import com.tabek.mindfulpause.data.EventLog
import com.tabek.mindfulpause.data.SettingsRepository
import com.tabek.mindfulpause.data.TimedBlock
import com.tabek.mindfulpause.overlay.PauseOverlayController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

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
    private lateinit var blocks: BlockRepository
    private lateinit var overlay: PauseOverlayController
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    /**
     * Kept warm by collecting the trackedApps Flow. onAccessibilityEvent
     * cannot suspend, so it reads this synchronous snapshot instead.
     */
    @Volatile
    private var trackedApps: Set<String> = emptySet()

    // Synchronous snapshots of blocking state, kept warm the same way.
    @Volatile private var timedBlocks: Map<String, TimedBlock> = emptyMap()
    @Volatile private var dailyLimits: Map<String, Int> = emptyMap()
    @Volatile private var openCounts: Map<String, Int> = emptyMap()

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
        blocks = BlockRepository(applicationContext)
        overlay = PauseOverlayController(this, repo, eventLog, scope)

        repo.trackedApps
            .onEach { trackedApps = it }
            .launchIn(scope)
        blocks.timedBlocks
            .onEach { list -> timedBlocks = list.associateBy { it.packageName } }
            .launchIn(scope)
        blocks.dailyLimits
            .onEach { list -> dailyLimits = list.associate { it.packageName to it.maxOpens } }
            .launchIn(scope)
        blocks.openCounts
            .onEach { openCounts = it }
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

        val now = System.currentTimeMillis()

        // 1) Active timed block? Show the lock screen with a countdown.
        val block = timedBlocks[pkg]
        if (block != null && block.isActive(now)) {
            lastHandledPackage = pkg
            overlay.showBlocked(
                targetPackage = pkg,
                untilMs = if (block.isIndefinite) null else block.untilMs,
                onBack = { goHome() },
            )
            return
        }

        // 2) Daily open-limit reached? Lock until midnight.
        val limit = dailyLimits[pkg]
        if (limit != null && (openCounts[pkg] ?: 0) >= limit) {
            lastHandledPackage = pkg
            overlay.showBlocked(
                targetPackage = pkg,
                untilMs = nextMidnight(now),
                onBack = { goHome() },
            )
            return
        }

        // 3) Otherwise the normal mindful pause. Count the open on "Продолжить".
        lastHandledPackage = pkg
        overlay.show(
            targetPackage = pkg,
            onContinue = {
                grantPackage(pkg)
                if (dailyLimits.containsKey(pkg)) {
                    scope.launch { blocks.registerOpen(pkg, System.currentTimeMillis()) }
                }
            },
            onExit = { goHome() },
        )
    }

    private fun nextMidnight(now: Long): Long =
        BlockRepository.dayStart(now) + 24L * 60 * 60 * 1000

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
