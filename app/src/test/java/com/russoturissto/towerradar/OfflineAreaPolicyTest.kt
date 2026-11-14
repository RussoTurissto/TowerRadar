package com.russoturissto.towerradar

import org.junit.Assert.*
import org.junit.Test

class OfflineAreaPolicyTest {
    @Test fun boundsAndTileCoverageAreGeographic() {
        val b = OfflineAreaPolicy.bounds(50.0, 6.0, 10)!!
        assertTrue(b.contains(50.0, 6.0))
        assertFalse(b.contains(52.0, 6.0))
        assertTrue(OfflineAreaPolicy.countTiles(b, 6, 14) > 0L)
        assertTrue(OfflineAreaPolicy.countTiles(b, 6, 19) >
            OfflineAreaPolicy.countTiles(b, 6, 14))
    }
    @Test fun unsafeOrGiganticDownloadsAreRejected() {
        assertNull(OfflineAreaPolicy.bounds(90.0, 6.0, 10))
        assertNull(OfflineAreaPolicy.bounds(50.0, 179.9, 30))
        val b = OfflineAreaPolicy.bounds(50.0, 6.0, 30)!!
        assertFalse(OfflineAreaPolicy.allowed(b, 19))
        assertTrue(OfflineAreaPolicy.allowed(b, 6))
    }
    @Test fun manualSelectionUsesAllFourCorners() {
        val chosen = OfflineAreaPolicy.selectedBounds(listOf(
            50.0 to 6.0, 50.0 to 6.2, 49.8 to 6.1, 49.9 to 6.3))!!
        assertEquals(49.8, chosen.south, 0.00001)
        assertEquals(50.0, chosen.north, 0.00001)
        assertEquals(6.0, chosen.west, 0.00001)
        assertEquals(6.3, chosen.east, 0.00001)
        assertNull(OfflineAreaPolicy.selectedBounds(listOf(
            50.0 to -179.0, 50.0 to 179.0, 49.0 to -179.0, 49.0 to 179.0)))
        assertNotNull(OfflineAreaPolicy.bounds(49.0, 39.0, 30))
        assertNull(OfflineAreaPolicy.bounds(49.0, 39.0, 5))
    }

    @Test fun tileIndicesAreBoundedAndMonotone() {
        assertEquals(0, OfflineAreaPolicy.tileX(-180.0, 6))
        assertEquals(63, OfflineAreaPolicy.tileX(180.0, 6))
        assertTrue(OfflineAreaPolicy.tileY(60.0, 12) <
            OfflineAreaPolicy.tileY(40.0, 12))
    }
}
