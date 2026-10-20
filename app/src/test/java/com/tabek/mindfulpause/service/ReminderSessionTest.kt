package com.tabek.mindfulpause.service

import junit.framework.TestCase
import com.tabek.mindfulpause.BuildConfig

class ReminderSessionTest : TestCase() {
    private val session = ReminderSession()

    fun testReminderAppearsAtDeadlineOnlyOnce() {
        session.start("app", 1_000, 300_000)
        assertNull(session.takeDue(300_999, "app", true))
        assertEquals("app", session.takeDue(301_000, "app", true))
        assertNull(session.takeDue(601_000, "app", true))
    }

    fun testSnoozeUsesDebugIntervalEvenWithLongInitialInterval() {
        session.start("app", 0, 1_800_000)
        assertEquals("app", session.takeDue(1_800_000, "app", true))
        assertTrue(session.snooze("app", 1_800_100))
        val deadline = if (BuildConfig.DEBUG) 1_830_100L else 2_100_100L
        assertNull(session.takeDue(deadline - 1, "app", true))
        assertEquals("app", session.takeDue(deadline, "app", true))
    }

    fun testLeavingAppInvalidatesExpiredReminder() {
        session.start("app", 0, 300_000)
        assertNull(session.takeDue(300_000, "other", true))
        assertNull(session.takeDue(300_001, "app", true))
    }

    fun testScreenOffInvalidatesReminder() {
        session.start("app", 0, 300_000)
        assertNull(session.takeDue(300_000, "app", false))
        assertNull(session.takeDue(300_001, "app", true))
    }

    fun testCancelPreventsBothDisplayAndStaleSnooze() {
        session.start("app", 0, 300_000)
        session.takeDue(300_000, "app", true)
        session.cancel()
        assertFalse(session.snooze("app", 300_001))
        assertNull(session.takeDue(600_001, "app", true))
    }

    fun testNewSessionReplacesOldDeadline() {
        session.start("old", 0, 300_000)
        session.start("new", 200_000, 600_000)
        assertNull(session.takeDue(300_000, "new", true))
        assertEquals("new", session.takeDue(800_000, "new", true))
    }

    fun testSnoozeCannotStartWithoutAnAnsweredReminder() {
        assertFalse(session.snooze("app", 0))
        session.start("app", 0, 300_000)
        assertFalse(session.snooze("app", 100))
        assertEquals("app", session.takeDue(300_000, "app", true))
        assertFalse(session.snooze("other", 300_001))
        assertTrue(session.snooze("app", 300_001))
    }
}
