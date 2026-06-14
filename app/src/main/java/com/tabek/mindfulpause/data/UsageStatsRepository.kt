package com.tabek.mindfulpause.data

import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Build
import android.os.Process
import java.util.Calendar

data class AppUsageTime(
    val packageName: String,
    val usageTimeMs: Long,
)

/** Check if the user has granted PACKAGE_USAGE_STATS permission. */
fun hasUsageStatsPermission(context: Context): Boolean {
    val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
    // unsafeCheckOpNoThrow exists only on API 29+; on API 26-28 the equivalent
    // is the now-deprecated checkOpNoThrow. minSdk is 26, so guard the call.
    @Suppress("DEPRECATION")
    val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        appOps.unsafeCheckOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName,
        )
    } else {
        appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName,
        )
    }
    return mode == AppOpsManager.MODE_ALLOWED
}

/** How many days of usage history to aggregate for sorting. */
private const val USAGE_WINDOW_DAYS = 7

/**
 * Returns total foreground usage time per app over the last [days] days
 * (default [USAGE_WINDOW_DAYS]), so an app that wasn't used today but was
 * used earlier in the week still counts.
 *
 * Requires PACKAGE_USAGE_STATS permission granted in system settings.
 */
fun getUsageStats(context: Context, days: Int = USAGE_WINDOW_DAYS): Map<String, Long> {
    val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager

    val calendar = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
        add(Calendar.DAY_OF_YEAR, -(days - 1))
    }
    val startTime = calendar.timeInMillis
    val endTime = System.currentTimeMillis()

    // INTERVAL_DAILY returns one record per app per day, so the same package
    // appears multiple times across the window — sum them up per package.
    val stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, startTime, endTime)

    return stats
        .filter { it.totalTimeInForeground > 0 }
        .groupingBy { it.packageName }
        .fold(0L) { acc, usage -> acc + usage.totalTimeInForeground }
        .filterValues { it > 0 }
}
