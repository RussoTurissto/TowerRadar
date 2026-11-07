package com.russoturissto.towerradar

import org.junit.Assert.assertEquals
import org.junit.Test

class NorthDirectionModelTest {
    @Test fun northUpMapShowsNorthUp() {
        assertEquals(0f, NorthDirectionModel.arrowRotation(0.0))
        assertEquals(0f, NorthDirectionModel.arrowRotation(360.0))
    }

    @Test fun bearingRotatesArrowOppositeMapRotation() {
        assertEquals(270f, NorthDirectionModel.arrowRotation(90.0))
        assertEquals(180f, NorthDirectionModel.arrowRotation(180.0))
        assertEquals(90f, NorthDirectionModel.arrowRotation(270.0))
        assertEquals(45f, NorthDirectionModel.arrowRotation(-45.0))
    }

    @Test fun invalidBearingDoesNotDrawMisleadingRotation() {
        assertEquals(0f, NorthDirectionModel.arrowRotation(Double.NaN))
    }
}
