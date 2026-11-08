package com.russoturissto.towerradar

import org.junit.Assert.*
import org.junit.Test

class LastMapCameraModelTest {
    @Test fun savedPositionKeepsZoomAndRotation() {
        assertTrue(LastMapCameraModel.valid(50.396875, 43.360014, 15.75, 128.0, 26.0))
        assertTrue(LastMapCameraModel.valid(-33.84, 151.2, 2.5, -25.0, 0.0))
    }
    @Test fun invalidCameraWillNotBeRestored() {
        assertFalse(LastMapCameraModel.valid(Double.NaN, 30.0, 12.0, 0.0, 0.0))
        assertFalse(LastMapCameraModel.valid(90.0, 30.0, 12.0, 0.0, 0.0))
        assertFalse(LastMapCameraModel.valid(50.0, 200.0, 12.0, 0.0, 0.0))
        assertFalse(LastMapCameraModel.valid(50.0, 30.0, 45.0, 0.0, 0.0))
    }
}
