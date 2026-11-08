package com.russoturissto.towerradar

import org.junit.Assert.*
import org.junit.Test

class PositionChangePolicyTest {
    @Test fun switchingToManualAlwaysRequiresNewTower() {
        assertTrue(PositionChangePolicy.manualUpdateChangesOrigin(false, 0.0))
    }
    @Test fun choosingSameManualPointDoesNotWarnAgain() {
        assertFalse(PositionChangePolicy.manualUpdateChangesOrigin(true, 1.5))
        assertTrue(PositionChangePolicy.manualUpdateChangesOrigin(true, 5.0))
    }
    @Test fun gpsJitterDoesNotInvalidateTarget() {
        assertFalse(PositionChangePolicy.gpsMovedSinceSelection(80.0, 8f, 10f))
        assertFalse(PositionChangePolicy.gpsMovedSinceSelection(140.0, 60f, 100f))
        assertTrue(PositionChangePolicy.gpsMovedSinceSelection(160.0, 8f, 10f))
    }
    @Test fun poorAccuracyRaisesMovementThreshold() {
        assertFalse(PositionChangePolicy.gpsMovedSinceSelection(400.0, 300f, 300f))
        assertTrue(PositionChangePolicy.gpsMovedSinceSelection(900.0, 300f, 300f))
    }
    @Test fun badGpsDistanceCannotInvalidateSelection() {
        assertFalse(PositionChangePolicy.gpsMovedSinceSelection(Double.NaN, null, null))
    }
}
