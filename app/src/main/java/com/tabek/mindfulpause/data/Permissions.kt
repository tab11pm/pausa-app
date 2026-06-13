package com.tabek.mindfulpause.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import android.text.TextUtils
import com.tabek.mindfulpause.service.InterceptAccessibilityService

/** True if the user has granted "draw over other apps". */
fun canDrawOverlays(context: Context): Boolean =
    Settings.canDrawOverlays(context)

/**
 * True if the app is exempt from battery optimization. When it is NOT, Samsung
 * and other aggressive OEMs may kill the accessibility service and silently
 * revoke its permission — which is why the pause "stops working".
 */
fun isIgnoringBatteryOptimizations(context: Context): Boolean {
    val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    return pm.isIgnoringBatteryOptimizations(context.packageName)
}

/** Intent that asks the user to exempt us from battery optimization. */
fun requestIgnoreBatteryOptimizationsIntent(context: Context): Intent =
    Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
        data = Uri.parse("package:${context.packageName}")
    }

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
