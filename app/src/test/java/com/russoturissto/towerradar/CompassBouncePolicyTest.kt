package com.russoturissto.towerradar

import org.junit.Assert.*
import org.junit.Test

class CompassBouncePolicyTest {
    private val dp = 3f
    private val start = -450f

    @Test fun startsAtActualPreviousCompassPosition() {
        assertEquals(start, CompassBouncePolicy.offset(0f, start, dp), .001f)
    }
    @Test fun hitsBottomAtThreeDistinctTimes() {
        listOf(.43f, .68f, .85f, 1f).forEach {
            assertEquals(0f, CompassBouncePolicy.offset(it, start, dp), .05f)
        }
    }
    @Test fun reboundsGetSmaller() {
        val first = CompassBouncePolicy.offset(.555f, start, dp)
        val second = CompassBouncePolicy.offset(.765f, start, dp)
        val third = CompassBouncePolicy.offset(.925f, start, dp)
        assertTrue(first < second && second < third && third < 0f)
    }
    @Test fun movementIsClampedToEndpoints() {
        assertEquals(start, CompassBouncePolicy.offset(-2f, start, dp), .001f)
        assertEquals(0f, CompassBouncePolicy.offset(2f, start, dp), .001f)
    }
    @Test fun invalidOrUpwardsDropDoesNotAnimate() {
        assertEquals(0f, CompassBouncePolicy.offset(.25f, 0f, dp), .001f)
        assertEquals(0f, CompassBouncePolicy.offset(.25f, 50f, dp), .001f)
    }
}
