package com.tabek.mindfulpause.data

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.graphics.drawable.Drawable

/** A user-launchable app, for the picker list. */
data class AppInfo(
    val packageName: String,
    val label: String,
    val icon: Drawable?,
    val usageTimeMs: Long = 0L,
    val isSocial: Boolean = false,
)

/**
 * Lists apps that have a launcher entry (i.e. ones the user actually opens),
 * excluding ourselves. Sorted by display name. This is the candidate set for
 * the "which apps to intercept" picker.
 */
fun loadLaunchableApps(
    context: Context,
    socialOnly: Boolean = false,
    cachedApps: List<AppInfo> = emptyList(),
): List<AppInfo> {
    val pm = context.packageManager
    val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    val resolved = pm.queryIntentActivities(intent, 0)
    val cached = cachedApps.associateBy { it.packageName }

    return resolved
        .asSequence()
        .map { it.activityInfo.applicationInfo }
        .distinctBy { it.packageName }
        .filter { it.packageName != context.packageName }
        // Filter before loading labels and icons: those are expensive on large devices.
        .filter { !socialOnly || isSocialApp(it.packageName, it.category) }
        .mapNotNull { ai ->
            val pkg = ai.packageName
            cached[pkg]?.let { return@mapNotNull it }
            runCatching {
                AppInfo(
                    packageName = pkg,
                    label = pm.getApplicationLabel(ai).toString(),
                    icon = pm.getApplicationIcon(ai),
                    isSocial = isSocialApp(pkg, ai.category),
                )
            }.getOrNull()
        }
        .sortedBy { it.label.lowercase() }
        .toList()
}

internal fun isSocialApp(packageName: String, category: Int): Boolean =
    packageName in SUGGESTED_PACKAGES || category == ApplicationInfo.CATEGORY_SOCIAL

/** Suggested social / short-video apps to surface at the top of the picker. */
val SUGGESTED_PACKAGES = setOf(
    "com.instagram.android",
    "com.zhiliaoapp.musically",     // TikTok
    "com.ss.android.ugc.trill",    // TikTok (other regions)
    "com.google.android.youtube",
    "com.facebook.katana",
    "com.facebook.lite",
    "com.facebook.orca",          // Messenger
    "com.instagram.barcelona",    // Threads
    "com.twitter.android",          // X
    "com.reddit.frontpage",
    "org.telegram.messenger",
    "org.telegram.messenger.web",
    "org.thunderdog.challegram",  // Telegram X
    "com.whatsapp",
    "com.whatsapp.w4b",
    "org.thoughtcrime.securesms", // Signal
    "com.viber.voip",
    "com.discord",
    "com.snapchat.android",
    "com.pinterest",
    "com.vkontakte.android",
    "com.vk.im",                  // VK Messenger
    "com.vk.vkvideo",
    "ru.ok.android",
    "ru.mail.my",
    "ru.dzen.zen",
    "com.linkedin.android",
    "com.tumblr",
    "tv.twitch.android.app",
)
