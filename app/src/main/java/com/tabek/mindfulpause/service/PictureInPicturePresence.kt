package com.tabek.mindfulpause.service

/** Keeps the last time an app was actually visible in picture-in-picture. */
internal class PictureInPicturePresence {
    private val lastVisibleAt = HashMap<String, Long>()
    private var visiblePackages = emptySet<String>()

    fun observe(packages: Set<String>, elapsedNow: Long) {
        // A window disappearing can be the same transition that expands it.
        // Stamp that transition too, so the return does not count as a new open.
        (visiblePackages + packages).forEach { lastVisibleAt[it] = elapsedNow }
        visiblePackages = packages
    }

    fun lastVisibleAt(packageName: String): Long? = lastVisibleAt[packageName]
}
