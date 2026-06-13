package com.tabek.mindfulpause.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.Calendar

/** Separate DataStore for blocking rules, independent of settings/events. */
private val Context.blockStore by preferencesDataStore(name = "blocks")

/**
 * A timed block on one app. [untilMs] is an absolute time; while now < untilMs
 * the app is fully blocked. Long.MAX_VALUE means "no limit" (blocked until the
 * user lifts it manually).
 */
data class TimedBlock(
    val packageName: String,
    val untilMs: Long,
) {
    val isIndefinite: Boolean get() = untilMs == Long.MAX_VALUE
    fun isActive(now: Long): Boolean = isIndefinite || now < untilMs
    /** Milliseconds left, or null if indefinite. */
    fun remaining(now: Long): Long? = if (isIndefinite) null else (untilMs - now).coerceAtLeast(0)
}

/**
 * A daily open-limit on one app: at most [maxOpens] launches per calendar day.
 * The service counts opens against this via the event-independent counter map.
 */
data class DailyLimit(
    val packageName: String,
    val maxOpens: Int,
)

/**
 * Owns blocking rules and the per-day open counters used to enforce daily
 * limits. Kept separate from SettingsRepository so the concerns stay clean.
 *
 * Storage formats (string sets):
 *  - timed blocks:  "package|untilMs"
 *  - daily limits:  "package|maxOpens"
 *  - open counters: "package|dayStartMs|count"
 */
class BlockRepository(private val context: Context) {

    private val timedKey = stringSetPreferencesKey("timed_blocks")
    private val limitKey = stringSetPreferencesKey("daily_limits")
    private val counterKey = stringSetPreferencesKey("open_counters")

    // ---- Timed blocks --------------------------------------------------

    val timedBlocks: Flow<List<TimedBlock>> =
        context.blockStore.data.map { prefs ->
            (prefs[timedKey] ?: emptySet()).mapNotNull { entry ->
                val p = entry.split("|")
                val ts = p.getOrNull(1)?.toLongOrNull() ?: return@mapNotNull null
                TimedBlock(p[0], ts)
            }
        }

    /** Block [packageName] for [durationMs] from [now]; pass null for indefinite. */
    suspend fun setTimedBlock(packageName: String, now: Long, durationMs: Long?) {
        val until = if (durationMs == null) Long.MAX_VALUE else now + durationMs
        context.blockStore.edit { prefs ->
            val kept = (prefs[timedKey] ?: emptySet())
                .filterNot { it.startsWith("$packageName|") }
                .toMutableSet()
            kept.add("$packageName|$until")
            prefs[timedKey] = kept
        }
    }

    suspend fun clearTimedBlock(packageName: String) {
        context.blockStore.edit { prefs ->
            prefs[timedKey] = (prefs[timedKey] ?: emptySet())
                .filterNot { it.startsWith("$packageName|") }
                .toSet()
        }
    }

    // ---- Daily limits --------------------------------------------------

    val dailyLimits: Flow<List<DailyLimit>> =
        context.blockStore.data.map { prefs ->
            (prefs[limitKey] ?: emptySet()).mapNotNull { entry ->
                val p = entry.split("|")
                val n = p.getOrNull(1)?.toIntOrNull() ?: return@mapNotNull null
                DailyLimit(p[0], n)
            }
        }

    /** Set a daily open limit; pass null/0 to remove it. */
    suspend fun setDailyLimit(packageName: String, maxOpens: Int?) {
        context.blockStore.edit { prefs ->
            val kept = (prefs[limitKey] ?: emptySet())
                .filterNot { it.startsWith("$packageName|") }
                .toMutableSet()
            if (maxOpens != null && maxOpens > 0) kept.add("$packageName|$maxOpens")
            prefs[limitKey] = kept
        }
    }

    // ---- Open counters (for daily limits) ------------------------------

    /**
     * Register one open of [packageName] today and return the new count.
     * Counters auto-reset when the calendar day changes.
     */
    suspend fun registerOpen(packageName: String, now: Long): Int {
        val today = dayStart(now)
        var newCount = 1
        context.blockStore.edit { prefs ->
            val current = prefs[counterKey] ?: emptySet()
            // Drop stale (previous-day) counters; find today's for this package.
            val kept = current.mapNotNull { entry ->
                val p = entry.split("|")
                val day = p.getOrNull(1)?.toLongOrNull() ?: return@mapNotNull null
                if (day != today) null else entry  // keep only today's
            }.toMutableSet()

            val existing = kept.firstOrNull { it.startsWith("$packageName|") }
            val prevCount = existing?.split("|")?.getOrNull(2)?.toIntOrNull() ?: 0
            newCount = prevCount + 1
            if (existing != null) kept.remove(existing)
            kept.add("$packageName|$today|$newCount")
            prefs[counterKey] = kept
        }
        return newCount
    }

    /** Current open count for [packageName] today (without incrementing). */
    val openCounts: Flow<Map<String, Int>> =
        context.blockStore.data.map { prefs ->
            (prefs[counterKey] ?: emptySet()).mapNotNull { entry ->
                val p = entry.split("|")
                val day = p.getOrNull(1)?.toLongOrNull() ?: return@mapNotNull null
                val cnt = p.getOrNull(2)?.toIntOrNull() ?: return@mapNotNull null
                Triple(p[0], day, cnt)
            }.associate { it.first to it.third }
        }

    companion object {
        /** Block-duration presets shown in the UI, in minutes. null = indefinite. */
        val PRESETS_MIN: List<Int?> = listOf(15, 30, 60, 180, 300, null)

        fun dayStart(ms: Long): Long {
            val c = Calendar.getInstance()
            c.timeInMillis = ms
            c.set(Calendar.HOUR_OF_DAY, 0); c.set(Calendar.MINUTE, 0)
            c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0)
            return c.timeInMillis
        }
    }
}
