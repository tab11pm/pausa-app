package com.tabek.mindfulpause.service

import com.tabek.mindfulpause.data.ReminderConfig

/** Monotonic deadlines for one uninterrupted foreground session. */
internal class ReminderSession {
    private var targetPackage: String? = null
    private var deadlineMs: Long? = null
    private var awaitingChoice = false

    fun start(packageName: String, nowMs: Long, intervalMs: Long) {
        targetPackage = packageName
        deadlineMs = nowMs + intervalMs
        awaitingChoice = false
    }

    fun cancel() {
        targetPackage = null
        deadlineMs = null
        awaitingChoice = false
    }

    fun takeDue(nowMs: Long, foregroundPackage: String?, screenActive: Boolean): String? {
        if (!screenActive || foregroundPackage != targetPackage) {
            cancel()
            return null
        }
        val deadline = deadlineMs ?: return null
        if (nowMs < deadline) return null
        deadlineMs = null
        awaitingChoice = true
        return targetPackage
    }

    fun snooze(packageName: String, nowMs: Long): Boolean {
        if (!awaitingChoice || targetPackage != packageName) return false
        start(packageName, nowMs, SNOOZE_MS)
        return true
    }

    companion object {
        val SNOOZE_MS = ReminderConfig.SNOOZE_MS
    }
}
