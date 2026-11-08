package com.russoturissto.towerradar

import org.junit.Assert.*
import org.junit.Test

class TerrainProfileClientTest {
    @Test fun greatCircleIncludesEndpoints() {
        val p = TerrainProfileClient.coordinates(52.52, 13.40, 52.60, 13.55, 65)
        assertEquals(65, p.size)
        assertEquals(52.52, p.first().first, 1e-5)
        assertEquals(13.40, p.first().second, 1e-5)
        assertEquals(52.60, p.last().first, 1e-5)
        assertEquals(13.55, p.last().second, 1e-5)
    }
    @Test fun datelineIsCrossedAlongShortArc() {
        val p = TerrainProfileClient.coordinates(35.0,179.5,35.0,-179.5,9)
        assertTrue(kotlin.math.abs(p[4].second) > 179.0)
    }
    @Test(expected=IllegalArgumentException::class)
    fun rejectsInvalidSampleCount() {
        TerrainProfileClient.coordinates(1.0,2.0,3.0,4.0,1)
    }
}
