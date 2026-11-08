package com.russoturissto.towerradar

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/** Great-circle direction to a selected mapped site, independent of the map camera. */
object TowerAimModel {
    const val ENTER_DEGREES = 5.0
    const val EXIT_DEGREES = 9.0

    fun bearingDegrees(fromLat: Double, fromLon: Double, toLat: Double, toLon: Double): Double? {
        if (!fromLat.isFinite() || !fromLon.isFinite() ||
            !toLat.isFinite() || !toLon.isFinite() ||
            fromLat !in -90.0..90.0 || toLat !in -90.0..90.0 ||
            fromLon !in -180.0..180.0 || toLon !in -180.0..180.0) return null
        val lat1 = Math.toRadians(fromLat)
        val lat2 = Math.toRadians(toLat)
        val delta = Math.toRadians(toLon - fromLon)
        val y = sin(delta) * cos(lat2)
        val x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(delta)
        // At the same point (or its antipode), a unique azimuth does not exist.
        if (abs(x) + abs(y) < 1e-12) return null
        return (Math.toDegrees(atan2(y, x)) + 360.0) % 360.0
    }

    fun angularError(heading: Double, target: Double): Double {
        if (!heading.isFinite() || !target.isFinite()) return Double.POSITIVE_INFINITY
        val a = ((heading % 360.0) + 360.0) % 360.0
        val b = ((target % 360.0) + 360.0) % 360.0
        return abs((a - b + 540.0) % 360.0 - 180.0)
    }

    /** Hysteresis avoids haptic chatter at the +/-5 degree boundary. */
    fun aligned(previouslyAligned: Boolean, heading: Double, target: Double): Boolean =
        angularError(heading, target) <= if (previouslyAligned) EXIT_DEGREES else ENTER_DEGREES
}
