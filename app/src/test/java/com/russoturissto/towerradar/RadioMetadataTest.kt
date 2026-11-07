package com.russoturissto.towerradar

import org.junit.Assert.*
import org.junit.Test

class RadioMetadataTest {
    @Test fun servingAndNeighborUseExactSameNetworkIdentity() {
        val cell = ServingCell("Operator", 1, "LTE", 250, 1, 1234, 987654L,
            -95, -95, -13, 11.0f, 3,
            pci = 201, channel = 1650, bands = listOf(3, 7),
            timingAdvance = 9, registered = false)
        assertEquals("250/1/1234/987654/LTE", cell.identity)
        assertTrue(cell.validId)
        assertFalse(cell.registered)
        assertEquals(listOf(3, 7), cell.bands)
        assertEquals(201, cell.pci)
    }

    @Test fun missingCellIdentityCannotBeMatchedToAMast() {
        val unidentified = ServingCell("Operator", -1, "LTE", -1, -1, -1, -1,
            -103, null, null, null, 1)
        assertFalse(unidentified.validId)
        assertTrue(unidentified.validDbm)
    }

    @Test fun opencellidPreservesEstimateAndSampleCounts() {
        val json = """
          {"cells":[
            {"lat":55.75,"lon":37.60,"mcc":250,"mnc":1,"lac":1234,
             "cellid":987654,"radio":"LTE","samples":44,
             "range":1050,"averageSignalStrength":-91}
          ]}
        """.trimIndent()
        val towers = CellSources.parse(json, 55.75, 37.60)
        assertEquals(1, towers.size)
        assertEquals("opencellid/250/1/1234/987654/LTE", towers.single().id)
        assertEquals(44, towers.single().samples)
        assertEquals(1050, towers.single().estimatedRangeMeters)
        assertEquals(-91, towers.single().averageSignalDbm)
        assertTrue(towers.single().source.contains("приблизительно"))
    }
}
