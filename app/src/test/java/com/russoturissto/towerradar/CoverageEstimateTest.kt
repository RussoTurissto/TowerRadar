package com.russoturissto.towerradar

import org.junit.Assert.*
import org.junit.Test

class CoverageEstimateTest {
    @Test fun ringIsClosed() {
        val ring = CoverageEstimate.ring(50.0, 30.0, 1000, 32)
        assertEquals(33, ring.size)
        assertEquals(ring.first().first, ring.last().first, 1e-7)
        assertEquals(ring.first().second, ring.last().second, 1e-7)
    }
    @Test fun radiusIsApproximatelyOneKilometre() {
        val ring = CoverageEstimate.ring(50.0, 30.0, 1000, 32)
        for (p in ring)
            assertEquals(1.0, TowerData.distanceKm(50.0, 30.0, p.first, p.second), 0.005)
    }
    @Test(expected = IllegalArgumentException::class)
    fun invalidRadiusIsRejected() {
        CoverageEstimate.ring(50.0, 30.0, -1)
    }
}
