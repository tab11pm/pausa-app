package com.tabek.mindfulpause.data

import android.content.pm.ApplicationInfo
import junit.framework.TestCase

class SocialAppsTest : TestCase() {
    fun testCommonMessengersAndRussianSocialAppsAreSuggested() {
        val packages = setOf(
            "com.whatsapp", "com.whatsapp.w4b", "ru.ok.android",
            "com.vk.vkvideo", "org.telegram.messenger.web", "com.ss.android.ugc.trill",
        )
        assertTrue("Missing social apps: ${packages - SUGGESTED_PACKAGES}",
            SUGGESTED_PACKAGES.containsAll(packages))
    }

    fun testUtilitiesAreNotSuggested() {
        assertFalse("com.android.settings" in SUGGESTED_PACKAGES)
        assertFalse("com.android.calculator2" in SUGGESTED_PACKAGES)
    }

    fun testAndroidSocialCategoryIncludesAppsOutsideTheCatalogue() {
        assertTrue(isSocialApp("example.new.social", ApplicationInfo.CATEGORY_SOCIAL))
        assertFalse(isSocialApp("example.utility", ApplicationInfo.CATEGORY_PRODUCTIVITY))
    }

    fun testKnownSocialAppsWithMissingOrVideoCategoryAreIncluded() {
        assertTrue(isSocialApp("org.telegram.messenger", ApplicationInfo.CATEGORY_UNDEFINED))
        assertTrue(isSocialApp("com.google.android.youtube", ApplicationInfo.CATEGORY_VIDEO))
    }
}
