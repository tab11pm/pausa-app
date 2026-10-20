package com.tabek.mindfulpause.data

data class ReminderConfig(
    val enabled: Boolean = false,
    val intervalMinutes: Int = 5,
) {
    val intervalMs: Long get() = intervalMinutes * 60_000L

    companion object {
        val INTERVALS = listOf(5, 10, 15, 30)
        const val SNOOZE_MS = 5 * 60_000L
        const val SNOOZE_DURATION_TEXT = "5 минут"
    }
}
