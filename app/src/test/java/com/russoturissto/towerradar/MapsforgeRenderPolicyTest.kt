package com.russoturissto.towerradar

import org.junit.Assert.assertEquals
import org.junit.Test

class MapsforgeRenderPolicyTest {
    @Test fun fixedSizeRasterTilesStayAtNaturalScaleAcrossPixelDensities() {
        for (density in listOf(0.75f, 1f, 1.5f, 2f, 3f, 4f)) {
            val user = MapsforgeRenderPolicy.userScaleForDevice(density)
            assertEquals(1f, density * user, 0.0001f)
        }
    }

    @Test fun invalidReportedDeviceScaleDoesNotBreakTheMap() {
        for (bad in listOf(0f, -2f, Float.NaN, Float.POSITIVE_INFINITY)) {
            assertEquals(1f, MapsforgeRenderPolicy.userScaleForDevice(bad), 0f)
        }
    }
}
