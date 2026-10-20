package com.tabek.mindfulpause.data

import com.tabek.mindfulpause.BuildConfig

data class ReminderConfig(
    val enabled: Boolean = false,
    val intervalMinutes: Int = 5,
) {
    // Temporarily shorten both timers in debug APKs, including saved settings.
    val intervalMs: Long get() = if (BuildConfig.DEBUG) 30_000L else intervalMinutes * 60_000L

    companion object {
        val INTERVALS = listOf(5, 10, 15, 30)
        val SNOOZE_MS = if (BuildConfig.DEBUG) 30_000L else 5 * 60_000L
        val SNOOZE_DURATION_TEXT = if (BuildConfig.DEBUG) "30 секунд" else "5 минут"
    }
}
