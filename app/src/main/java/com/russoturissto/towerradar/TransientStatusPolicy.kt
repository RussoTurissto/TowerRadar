package com.russoturissto.towerradar

/** Prevent long server waits and repeated progress events from obscuring the map. */
object TransientStatusPolicy {
    const val PROGRESS_MS = 3_000L
    const val RESULT_MS = 3_000L
    const val FADE_MS = 180L

    fun inWindow(startedAt: Long, now: Long): Boolean =
        startedAt > 0 && now >= startedAt && now - startedAt < PROGRESS_MS
}
