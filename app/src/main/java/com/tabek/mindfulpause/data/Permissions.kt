package com.tabek.mindfulpause.data

import android.content.Context
import android.provider.Settings
import android.text.TextUtils
import com.tabek.mindfulpause.service.InterceptAccessibilityService

/** True if the user has granted "draw over other apps". */
fun canDrawOverlays(context: Context): Boolean =
    Settings.canDrawOverlays(context)

/**
 * True if our accessibility service is enabled in system settings. Read from
 * the secure setting because there is no direct API to query a foreign-looking
 * component's enabled state.
 */
fun isAccessibilityServiceEnabled(context: Context): Boolean {
    val expected = "${context.packageName}/${InterceptAccessibilityService::class.java.name}"
    val enabled = Settings.Secure.getString(
        context.contentResolver,
        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
    ) ?: return false

    val splitter = TextUtils.SimpleStringSplitter(':')
    splitter.setString(enabled)
    while (splitter.hasNext()) {
        if (splitter.next().equals(expected, ignoreCase = true)) return true
    }
    return false
}
