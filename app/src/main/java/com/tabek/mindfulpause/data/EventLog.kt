package com.tabek.mindfulpause.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.Calendar

/** Separate DataStore for the raw pause-event log (kept out of settings). */
private val Context.eventStore by preferencesDataStore(name = "events")

/** The choice the user made on the pause screen. */
enum class Choice { REFLECTED, CONTINUED }

/**
 * One pause event: when it happened, which app triggered it, and what the
 * user chose. This raw log is the source for every statistic we show.
 */
data class PauseEvent(
    val timestamp: Long,
    val packageName: String,
    val choice: Choice,
)

/**
 * Append-only log of pause events. Stored as a string set where each entry is
 * "timestamp|package|choice" — simple, dependency-free, and good enough for a
 * personal app's volume. Old events past RETENTION_DAYS are pruned on write.
 */
class EventLog(private val context: Context) {

    private val key = stringSetPreferencesKey("pause_events")

    val events: Flow<List<PauseEvent>> =
        context.eventStore.data.map { prefs ->
            (prefs[key] ?: emptySet()).mapNotNull(::decode).sortedBy { it.timestamp }
        }

    /** Record a pause outcome. Caller passes the timestamp (System.currentTimeMillis). */
    suspend fun record(now: Long, packageName: String, choice: Choice) {
        context.eventStore.edit { prefs ->
            val cutoff = now - RETENTION_DAYS * DAY_MS
            val current = prefs[key] ?: emptySet()
            // Prune anything older than the retention window, then append.
            val kept = current.filter { entry ->
                decode(entry)?.let { it.timestamp >= cutoff } ?: false
            }.toMutableSet()
            kept.add(encode(PauseEvent(now, packageName, choice)))
            prefs[key] = kept
        }
    }

    private fun encode(e: PauseEvent): String =
        "${e.timestamp}|${e.packageName}|${e.choice.name}"

    private fun decode(s: String): PauseEvent? {
        val parts = s.split("|")
        if (parts.size != 3) return null
        val ts = parts[0].toLongOrNull() ?: return null
        val choice = runCatching { Choice.valueOf(parts[2]) }.getOrNull() ?: return null
        return PauseEvent(ts, parts[1], choice)
    }

    companion object {
        const val RETENTION_DAYS = 90
        const val DAY_MS = 24L * 60 * 60 * 1000
    }
}

// ---------------------------------------------------------------------------
// Aggregation — pure functions over a list of events. No Android deps so they
// are trivial to reason about and (later) unit-test.
// ---------------------------------------------------------------------------

enum class Period { TODAY, WEEK, ALL }

data class DayBucket(
    val dayStartMs: Long,
    val reflected: Int,
    val continued: Int,
) {
    val total: Int get() = reflected + continued
}

data class AppBreakdown(
    val packageName: String,
    val reflected: Int,
    val continued: Int,
) {
    val total: Int get() = reflected + continued
}

data class Stats(
    val reflected: Int,
    val continued: Int,
    val perDay: List<DayBucket>,      // chronological, oldest -> newest
    val perApp: List<AppBreakdown>,   // sorted by total desc
    val streakDays: Int,              // consecutive days (up to today) with >=1 reflect
) {
    val total: Int get() = reflected + continued
    /** Share of pauses where the user reconsidered, 0..100. */
    val reflectPercent: Int get() = if (total == 0) 0 else (reflected * 100) / total
}

/** Midnight (local) of the day containing [ms]. */
private fun dayStart(ms: Long): Long {
    val c = Calendar.getInstance()
    c.timeInMillis = ms
    c.set(Calendar.HOUR_OF_DAY, 0)
    c.set(Calendar.MINUTE, 0)
    c.set(Calendar.SECOND, 0)
    c.set(Calendar.MILLISECOND, 0)
    return c.timeInMillis
}

/**
 * Build all statistics for a given period. [now] is the current time, passed
 * in so this stays a pure function. [days] controls how many day-buckets the
 * chart spans (e.g. 7 or 30) — only used for the per-day series.
 */
fun computeStats(events: List<PauseEvent>, now: Long, period: Period, chartDays: Int): Stats {
    val todayStart = dayStart(now)
    val from = when (period) {
        Period.TODAY -> todayStart
        Period.WEEK -> todayStart - 6 * EventLog.DAY_MS  // today + 6 previous = 7 days
        Period.ALL -> Long.MIN_VALUE
    }
    val scoped = events.filter { it.timestamp >= from }

    val reflected = scoped.count { it.choice == Choice.REFLECTED }
    val continued = scoped.count { it.choice == Choice.CONTINUED }

    // Per-app breakdown (within the period).
    val perApp = scoped.groupBy { it.packageName }
        .map { (pkg, evs) ->
            AppBreakdown(
                packageName = pkg,
                reflected = evs.count { it.choice == Choice.REFLECTED },
                continued = evs.count { it.choice == Choice.CONTINUED },
            )
        }
        .sortedByDescending { it.total }

    // Per-day chart series — always the last [chartDays] days regardless of period
    // so the bar chart has a stable x-axis.
    val perDay = (chartDays - 1 downTo 0).map { back ->
        val start = todayStart - back * EventLog.DAY_MS
        val end = start + EventLog.DAY_MS
        val inDay = events.filter { it.timestamp in start until end }
        DayBucket(
            dayStartMs = start,
            reflected = inDay.count { it.choice == Choice.REFLECTED },
            continued = inDay.count { it.choice == Choice.CONTINUED },
        )
    }

    // Streak: consecutive days ending today where the user reflected at least once.
    var streak = 0
    var cursor = todayStart
    val reflectByDay = events
        .filter { it.choice == Choice.REFLECTED }
        .map { dayStart(it.timestamp) }
        .toHashSet()
    while (reflectByDay.contains(cursor)) {
        streak++
        cursor -= EventLog.DAY_MS
    }

    return Stats(reflected, continued, perDay, perApp, streak)
}
