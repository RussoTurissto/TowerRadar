package com.russoturissto.towerradar

import org.junit.Assert.*
import org.junit.Test

class OfflineAreaPolicyTest {
    @Test fun boundsAndTileCoverageAreGeographic() {
        val b = OfflineAreaPolicy.bounds(50.0, 6.0, 5)!!
        assertTrue(b.contains(50.0, 6.0))
        assertFalse(b.contains(52.0, 6.0))
        assertTrue(OfflineAreaPolicy.countTiles(b, 6, 14) > 0L)
        assertTrue(OfflineAreaPolicy.countTiles(b, 6, 19) >
            OfflineAreaPolicy.countTiles(b, 6, 14))
    }
    @Test fun unsafeOrGiganticDownloadsAreRejected() {
        assertNull(OfflineAreaPolicy.bounds(90.0, 6.0, 5))
        assertNull(OfflineAreaPolicy.bounds(50.0, 179.9, 20))
        val b = OfflineAreaPolicy.bounds(50.0, 6.0, 20)!!
        assertFalse(OfflineAreaPolicy.allowed(b, 19))
        assertTrue(OfflineAreaPolicy.allowed(b, 6))
    }
    @Test fun tileIndicesAreBoundedAndMonotone() {
        assertEquals(0, OfflineAreaPolicy.tileX(-180.0, 6))
        assertEquals(63, OfflineAreaPolicy.tileX(180.0, 6))
        assertTrue(OfflineAreaPolicy.tileY(60.0, 12) <
            OfflineAreaPolicy.tileY(40.0, 12))
    }
}
