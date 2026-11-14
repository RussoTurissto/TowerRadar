package com.russoturissto.towerradar

import org.junit.Assert.*
import org.junit.Test

class TowerHeightPolicyTest {
    @Test fun knownHeightTakesPriority() {
        assertEquals(TowerHeightPolicy.Height(42.5, true),
            TowerHeightPolicy.resolve("42,5 м"))
    }
    @Test fun unknownHeightIsClearlyAnAssumption() {
        assertEquals(TowerHeightPolicy.Height(30.0, false),
            TowerHeightPolicy.resolve(null))
        assertEquals(TowerHeightPolicy.Height(25.0, false),
            TowerHeightPolicy.resolve("unknown", 25))
    }
    @Test fun unreasonableMetadataIsNotTrusted() {
        assertFalse(TowerHeightPolicy.resolve("-100").measured)
    }
}
