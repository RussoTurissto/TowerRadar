package com.russoturissto.towerradar

import org.junit.Assert.*
import org.junit.Test

class DisplayModePolicyTest {
    @Test fun invalidSavedPreferenceFallsBackToStandard() {
        assertEquals(DisplayModePolicy.STANDARD, DisplayModePolicy.normalize(null))
        assertEquals(DisplayModePolicy.STANDARD, DisplayModePolicy.normalize("unknown"))
        assertEquals(DisplayModePolicy.FULLSCREEN,
            DisplayModePolicy.normalize(DisplayModePolicy.FULLSCREEN))
    }

    @Test fun centerPunchHoleLeavesTwoIndependentSafeRegions() {
        assertEquals(listOf(DisplayModePolicy.Segment(8, 166),
            DisplayModePolicy.Segment(234, 392)),
            DisplayModePolicy.topSegments(400, listOf(174..226), 8))
    }

    @Test fun leftCornerCutoutKeepsRestOfTopUsable() {
        assertEquals(listOf(DisplayModePolicy.Segment(68, 392)),
            DisplayModePolicy.topSegments(400, listOf(0..60), 8))
    }

    @Test fun overlappingCutoutsMergeWithoutInvalidSegments() {
        assertEquals(listOf(DisplayModePolicy.Segment(8, 92),
            DisplayModePolicy.Segment(228, 392)),
            DisplayModePolicy.topSegments(400, listOf(100..170, 161..220), 8))
    }

    @Test fun noCutoutUsesFullSafeHorizontalRange() {
        assertEquals(listOf(DisplayModePolicy.Segment(8, 392)),
            DisplayModePolicy.topSegments(400, emptyList(), 8))
    }

    @Test fun tinyScreenDoesNotReturnNegativeWidths() {
        assertTrue(DisplayModePolicy.topSegments(0, emptyList(), 8).isEmpty())
        assertEquals(emptyList<DisplayModePolicy.Segment>(),
            DisplayModePolicy.topSegments(10, listOf(0..10), 8))
    }
}
