package com.tabek.mindfulpause.data

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable

/** A user-launchable app, for the picker list. */
data class AppInfo(
    val packageName: String,
    val label: String,
    val icon: Drawable?,
)

/**
 * Lists apps that have a launcher entry (i.e. ones the user actually opens),
 * excluding ourselves. Sorted by display name. This is the candidate set for
 * the "which apps to intercept" picker.
 */
fun loadLaunchableApps(context: Context): List<AppInfo> {
    val pm = context.packageManager
    val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    val resolved = pm.queryIntentActivities(intent, 0)

    return resolved
        .asSequence()
        .map { it.activityInfo.packageName }
        .distinct()
        .filter { it != context.packageName }
        .mapNotNull { pkg ->
            runCatching {
                val ai = pm.getApplicationInfo(pkg, 0)
                AppInfo(
                    packageName = pkg,
                    label = pm.getApplicationLabel(ai).toString(),
                    icon = pm.getApplicationIcon(ai),
                )
            }.getOrNull()
        }
        .sortedBy { it.label.lowercase() }
        .toList()
}

/** Suggested social / short-video apps to surface at the top of the picker. */
val SUGGESTED_PACKAGES = setOf(
    "com.instagram.android",
    "com.zhiliaoapp.musically",     // TikTok
    "com.google.android.youtube",
    "com.facebook.katana",
    "com.twitter.android",          // X
    "com.reddit.frontpage",
    "org.telegram.messenger",
    "com.snapchat.android",
    "com.pinterest",
    "com.vkontakte.android",
)
