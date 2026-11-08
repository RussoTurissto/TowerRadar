package com.russoturissto.towerradar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TowerAimModelTest {
    @Test fun cardinalDirectionsAreCalculatedFromGeographicNorth() {
        assertEquals(0.0, TowerAimModel.bearingDegrees(0.0, 0.0, 1.0, 0.0)!!, 0.0001)
        assertEquals(90.0, TowerAimModel.bearingDegrees(0.0, 0.0, 0.0, 1.0)!!, 0.0001)
        assertEquals(180.0, TowerAimModel.bearingDegrees(0.0, 0.0, -1.0, 0.0)!!, 0.0001)
        assertEquals(270.0, TowerAimModel.bearingDegrees(0.0, 0.0, 0.0, -1.0)!!, 0.0001)
    }

    @Test fun invalidOrCoincidentPointsCannotGiveFalseLock() {
        assertNull(TowerAimModel.bearingDegrees(50.0, 30.0, 50.0, 30.0))
        assertNull(TowerAimModel.bearingDegrees(Double.NaN, 30.0, 49.0, 30.0))
        assertNull(TowerAimModel.bearingDegrees(95.0, 30.0, 49.0, 30.0))
        assertFalse(TowerAimModel.aligned(false, Double.NaN, 50.0))
    }

    @Test fun angularErrorUsesShortestRotationAcrossNorth() {
        assertEquals(4.0, TowerAimModel.angularError(358.0, 2.0), 0.0001)
        assertEquals(4.0, TowerAimModel.angularError(2.0, 358.0), 0.0001)
        assertEquals(180.0, TowerAimModel.angularError(0.0, 180.0), 0.0001)
    }

    @Test fun alignmentEntersAtFiveDegreesAndExitsBeyondNine() {
        assertTrue(TowerAimModel.aligned(false, 5.0, 0.0))
        assertFalse(TowerAimModel.aligned(false, 5.1, 0.0))
        assertTrue(TowerAimModel.aligned(true, 8.0, 0.0))
        assertFalse(TowerAimModel.aligned(true, 9.1, 0.0))
    }
}
