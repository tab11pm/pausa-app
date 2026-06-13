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

    /** The package currently in the foreground (last distinct one we saw).
     *  We only act when the foreground package *changes* into a target app —
     *  i.e. a real open — not on every sub-window event inside it. */
    private var currentForeground: String? = null

    /** Per-package timestamp (elapsedRealtime) of when we last let the user
     *  into it (after "Продолжить") or when they left it. If they return
     *  within RE_PAUSE_MS the pause is skipped; after that gap it pauses again. */
    private val lastGrantAt = HashMap<String, Long>()

    /** Package the user is currently inside after continuing, so leaving it
     *  can stamp the "left at" time. */
    @Volatile
    private var grantedPackage: String? = null

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

        // Ignore transient system UI — share sheets, permission dialogs, the
        // notification shade, keyboards, etc. These briefly come to the front
        // but are NOT a real app switch, so they must not be treated as the
        // user leaving the app (which would re-trigger the pause on return).
        if (isTransientSystemUi(pkg)) return

        // We only care when the FOREGROUND package actually changes. Repeated
        // window events inside the same app (dialogs, keyboards) keep the same
        // foreground and must not re-trigger.
        if (pkg == currentForeground) return
        val previous = currentForeground
        currentForeground = pkg
        val elapsedNow = SystemClock.elapsedRealtime()

        // When leaving a granted app, stamp the "left at" time so we can tell
        // how long the user has been away when they come back.
        if (previous != null && previous == grantedPackage) {
            lastGrantAt[previous] = elapsedNow
            grantedPackage = null
        }

        val now = System.currentTimeMillis()
        val block = timedBlocks[pkg]
        val limit = dailyLimits[pkg]
        val hasRule = (block != null && block.isActive(now)) || limit != null

        // Acts only on tracked apps (pause) or apps with a blocking rule.
        if (pkg !in trackedApps && !hasRule) return

        if (overlay.isShowing) return

        // 1) Active timed block → lock screen with countdown.
        if (block != null && block.isActive(now)) {
            overlay.showBlocked(
                targetPackage = pkg,
                untilMs = if (block.isIndefinite) null else block.untilMs,
                onBack = { goHome() },
            )
            return
        }

        // 2) Daily open-limit reached → lock until midnight.
        if (limit != null && (openCounts[pkg] ?: 0) >= limit) {
            overlay.showBlocked(
                targetPackage = pkg,
                untilMs = nextMidnight(now),
                onBack = { goHome() },
            )
            return
        }

        // Recently continued into this app and came back quickly? Skip the
        // pause. After RE_PAUSE_MS away, it pauses again.
        val grantedAt = lastGrantAt[pkg]
        if (grantedAt != null && elapsedNow - grantedAt < RE_PAUSE_MS) {
            grantedPackage = pkg               // we're inside it again
            lastGrantAt[pkg] = elapsedNow      // keep the grant fresh while here
            return
        }

        // 3) Not blocked, and the grace window has expired. Tracked → mindful
        //    pause (count the open toward any daily limit on continue).
        if (pkg in trackedApps) {
            overlay.show(
                targetPackage = pkg,
                onContinue = {
                    grantedPackage = pkg
                    lastGrantAt[pkg] = SystemClock.elapsedRealtime()
                    if (limit != null) {
                        scope.launch { blocks.registerOpen(pkg, System.currentTimeMillis()) }
                    }
                },
                onExit = { goHome() },
            )
        } else if (limit != null) {
            // Limit-only app (no pause): count this open and let it through.
            grantedPackage = pkg
            lastGrantAt[pkg] = elapsedNow
            scope.launch { blocks.registerOpen(pkg, System.currentTimeMillis()) }
        }
    }

    private fun nextMidnight(now: Long): Long =
        BlockRepository.dayStart(now) + 24L * 60 * 60 * 1000

    /**
     * Packages that briefly take the foreground but aren't a real app switch:
     * the share sheet, permission dialogs, the notification shade, recents,
     * and the active input method. We skip these so opening a share sheet
     * inside YouTube doesn't look like leaving YouTube.
     */
    private fun isTransientSystemUi(pkg: String): Boolean {
        if (pkg in TRANSIENT_PACKAGES) return true
        // The current keyboard (IME) — resolved once, cached.
        if (pkg == imePackage) return true
        return false
    }

    private val imePackage: String? by lazy {
        runCatching {
            android.provider.Settings.Secure.getString(
                contentResolver,
                android.provider.Settings.Secure.DEFAULT_INPUT_METHOD,
            )?.substringBefore("/")
        }.getOrNull()
    }

    private fun goHome() {
        // Leaving to home stamps the away-time and clears the foreground latch.
        grantedPackage?.let { lastGrantAt[it] = SystemClock.elapsedRealtime() }
        grantedPackage = null
        currentForeground = null
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
        /** Re-show the pause if the user has been away from the app this long. */
        private const val RE_PAUSE_MS = 60_000L

        /** System UI packages that are not a real app switch. */
        private val TRANSIENT_PACKAGES = setOf(
            "android",                                  // share sheet, chooser (older)
            "com.android.systemui",                     // shade, recents, volume
            "com.android.intentresolver",               // share sheet (Android 14+)
            "com.google.android.intentresolver",
            "com.android.internal.app.ResolverActivity",
            "com.android.permissioncontroller",         // permission dialogs
            "com.google.android.permissioncontroller",
            "com.android.systemui.recents",
        )
    }
}
