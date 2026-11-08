package com.russoturissto.towerradar

import kotlin.math.PI
import kotlin.math.sin

/**
 * Predictable gravity-like compass drop followed by three progressively
 * smaller rebounds. Values are vertical translations in screen pixels.
 * The horizontal anchor never changes.
 */
object CompassBouncePolicy {
    const val DURATION_MS = 900L

    fun offset(fraction: Float, startY: Float, dp: Float): Float {
        val f = fraction.coerceIn(0f, 1f)
        if (startY >= 0f || dp <= 0f) return 0f
        if (f <= .43f) {
            val t = f / .43f
            return startY * (1f - t * t)
        }
        val bounce = when {
            f < .68f -> Triple(.43f, .68f, minOf(43f * dp, -startY * .26f))
            f < .85f -> Triple(.68f, .85f, minOf(19f * dp, -startY * .13f))
            else -> Triple(.85f, 1f, minOf(7f * dp, -startY * .06f))
        }
        val phase = ((f - bounce.first) / (bounce.second - bounce.first)).coerceIn(0f, 1f)
        return -(bounce.third * sin((PI * phase).toFloat()))
    }
}
