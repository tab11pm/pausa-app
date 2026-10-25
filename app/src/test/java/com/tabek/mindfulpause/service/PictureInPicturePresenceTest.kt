package com.tabek.mindfulpause.service

import junit.framework.TestCase

class PictureInPicturePresenceTest : TestCase() {
    fun testVideoStillVisibleAfterTenMinutesKeepsItsSessionRecent() {
        val presence = PictureInPicturePresence()
        presence.observe(setOf("video.app"), 1_000L)
        presence.observe(setOf("video.app"), 601_000L)
        presence.observe(emptySet(), 602_000L)

        assertEquals(602_000L, presence.lastVisibleAt("video.app"))
    }

    fun testClosedVideoStopsRefreshingItsSession() {
        val presence = PictureInPicturePresence()
        presence.observe(setOf("video.app"), 1_000L)
        presence.observe(emptySet(), 120_000L)
        presence.observe(emptySet(), 240_000L)

        assertEquals(120_000L, presence.lastVisibleAt("video.app"))
    }

    fun testUnrelatedAppGetsNoPictureInPictureGrace() {
        val presence = PictureInPicturePresence()
        presence.observe(setOf("video.app"), 1_000L)

        assertNull(presence.lastVisibleAt("chat.app"))
    }
}
