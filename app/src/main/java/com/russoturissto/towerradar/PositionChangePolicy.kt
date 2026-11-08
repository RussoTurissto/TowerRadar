package com.russoturissto.towerradar

/** Avoid repeated tower-selection alerts from minor fluctuations of GPS. */
object PositionChangePolicy {
    // A deliberately selected manual point is meaningful to a metre or two;
    // switching between manual and automatic mode is always a real change.
    fun manualUpdateChangesOrigin(
        wasManual: Boolean,
        distanceFromPreviousMeters: Double
    ): Boolean = !wasManual || distanceFromPreviousMeters > 2.0

    /** A selected target is invalidated only by significant physical movement. */
    fun gpsMovedSinceSelection(
        distanceMeters: Double,
        accuracyAtSelectionMeters: Float?,
        newAccuracyMeters: Float?
    ): Boolean {
        if (!distanceMeters.isFinite() || distanceMeters < 0) return false
        val accuracy = maxOf(
            accuracyAtSelectionMeters?.takeIf { it.isFinite() && it > 0 } ?: 0f,
            newAccuracyMeters?.takeIf { it.isFinite() && it > 0 } ?: 0f
        )
        // At least 100 m, or 2.5 times the GPS uncertainty.
        return distanceMeters > maxOf(100.0, accuracy.toDouble() * 2.5)
    }
}
