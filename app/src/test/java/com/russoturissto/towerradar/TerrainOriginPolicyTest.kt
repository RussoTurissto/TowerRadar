package com.russoturissto.towerradar

import org.junit.Assert.*
import org.junit.Test

class TerrainOriginPolicyTest {
    @Test fun manualOriginWorksWithoutGps() {
        val result = TerrainOriginPolicy.select(49.53 to 39.13, null)
        assertNotNull(result)
        assertTrue(result!!.manual)
        assertEquals(49.53, result.latitude, 0.00001)
    }
    @Test fun manualOriginOverridesAvailableGps() {
        val result = TerrainOriginPolicy.select(49.53 to 39.13, 52.0 to 13.0)
        assertTrue(result!!.manual)
        assertEquals(39.13, result.longitude, 0.00001)
    }
    @Test fun gpsUsedWithoutManualPoint() {
        val result = TerrainOriginPolicy.select(null, 52.0 to 13.0)
        assertFalse(result!!.manual)
    }
    @Test fun noOriginIsNotAnImaginaryCoordinate() {
        assertNull(TerrainOriginPolicy.select(null, null))
    }
    @Test fun badCoordinatesAreRejected() {
        assertNull(TerrainOriginPolicy.select(null, 95.0 to 180.1))
        assertEquals(52.0, TerrainOriginPolicy.select(99.0 to 14.0, 52.0 to 13.0)!!.latitude, 0.01)
    }
}
