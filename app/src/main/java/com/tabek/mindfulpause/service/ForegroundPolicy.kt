package com.tabek.mindfulpause.service

/** Decides whether a window event ends the current foreground app session. */
internal object ForegroundPolicy {
    fun isAppSwitch(
        currentPackage: String?,
        nextPackage: String,
        imePackage: String?,
        ownPackage: String? = null,
        windowClass: String? = null,
    ): Boolean =
        nextPackage != ownPackage && nextPackage != currentPackage && nextPackage != imePackage &&
            nextPackage !in transientPackages &&
            windowClass?.endsWith(".ChooserActivity") != true &&
            windowClass?.endsWith(".ResolverActivity") != true

    private val transientPackages = setOf(
        "android",
        "com.android.systemui",
        "com.android.intentresolver",
        "com.google.android.intentresolver",
        "com.android.internal.app.ResolverActivity",
        "com.android.permissioncontroller",
        "com.google.android.permissioncontroller",
        "com.android.systemui.recents",
        // Samsung's screenshot toolbar is a separate package, not an app exit.
        "com.samsung.android.app.smartcapture",
    )
}
