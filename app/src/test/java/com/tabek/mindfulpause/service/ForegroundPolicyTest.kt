package com.tabek.mindfulpause.service

import junit.framework.TestCase

class ForegroundPolicyTest : TestCase() {
    fun testLateOverlayEventDoesNotCancelFiveMinuteSnooze() {
        val session = ReminderSession()
        session.start("tracked.app", 0, 300_000)
        assertEquals("tracked.app", session.takeDue(300_000, "tracked.app", true))
        assertTrue(session.snooze("tracked.app", 300_100))

        // Removing our overlay can deliver an own-package event after dismiss().
        if (ForegroundPolicy.isAppSwitch(
                "tracked.app", "our.app", null, ownPackage = "our.app",
            )) {
            session.cancel()
        }
        val deadline = 600_100L
        assertNull(session.takeDue(deadline - 1, "tracked.app", true))
        assertEquals("tracked.app", session.takeDue(deadline, "tracked.app", true))
    }

    fun testSamsungScreenshotDoesNotEndForegroundSession() {
        assertFalse(ForegroundPolicy.isAppSwitch(
            "tracked.app", "com.samsung.android.app.smartcapture", null,
        ))
    }

    fun testActualAppSwitchStillEndsForegroundSession() {
        assertTrue(ForegroundPolicy.isAppSwitch("tracked.app", "other.app", null))
    }

    fun testKeyboardDoesNotEndForegroundSession() {
        assertFalse(ForegroundPolicy.isAppSwitch("tracked.app", "keyboard", "keyboard"))
    }

    fun testSystemShadeDoesNotEndForegroundSession() {
        assertFalse(ForegroundPolicy.isAppSwitch("tracked.app", "com.android.systemui", null))
    }

    fun testRepeatedWindowInSameAppDoesNotRestartSession() {
        assertFalse(ForegroundPolicy.isAppSwitch("tracked.app", "tracked.app", null))
    }
}
