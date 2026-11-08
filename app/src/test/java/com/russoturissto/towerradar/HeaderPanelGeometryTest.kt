package com.russoturissto.towerradar
import org.junit.Assert.*
import org.junit.Test
class HeaderPanelGeometryTest {
    @Test fun centeredHoleLeavesSeparatedLabels() {
        val slots = HeaderPanelGeometry.allocate(400, listOf(180..220), 8, 92, 8)
        assertEquals(DisplayModePolicy.Segment(8,172), slots.leading)
        assertEquals(DisplayModePolicy.Segment(228,392), slots.trailing)
    }
    @Test fun noCameraSplitsWidthEqually() {
        val slots = HeaderPanelGeometry.allocate(400, emptyList(), 8, 92, 8)
        assertEquals(DisplayModePolicy.Segment(8,196), slots.leading)
        assertEquals(DisplayModePolicy.Segment(204,392), slots.trailing)
    }
    @Test fun leftCameraUsesRightSide() {
        val slots = HeaderPanelGeometry.allocate(400, listOf(0..64), 8, 92, 8)
        assertEquals(DisplayModePolicy.Segment(72,228), slots.leading)
        assertEquals(DisplayModePolicy.Segment(236,392), slots.trailing)
    }
    @Test fun wideNotchKeepsContentsOutOfCutout() {
        val slots = HeaderPanelGeometry.allocate(400, listOf(120..280), 8, 92, 8)
        assertTrue(slots.leading!!.endExclusive <= 112)
        assertTrue(slots.trailing!!.start >= 288)
    }
    @Test fun tooNarrowShowsPrimaryWithoutOverlap() {
        val slots = HeaderPanelGeometry.allocate(160, listOf(60..130), 8, 92, 8)
        assertNotNull(slots.leading)
        assertNull(slots.trailing)
    }
    @Test fun multipleHolesNeverLeadIntoCutouts() {
        val slots = HeaderPanelGeometry.allocate(450, listOf(50..80, 220..260), 8, 92, 8)
        assertTrue(slots.leading!!.start >= 88)
        assertTrue(slots.leading.endExclusive <= 212)
        assertTrue(slots.trailing!!.start >= 268)
    }
    @Test fun variedPhonesDoNotPlaceLabelsInsideCutouts() {
        val widths = listOf(240, 280, 320, 360, 393, 430, 600, 840)
        for (width in widths) {
            val variants = listOf(
                emptyList<IntRange>(),
                listOf((width / 2 - 24)..(width / 2 + 24)),
                listOf(0..55),
                listOf((width - 70)..width),
                listOf((width / 2 - 75)..(width / 2 + 75)),
                listOf(12..40, (width / 2 - 18)..(width / 2 + 18))
            )
            for (cutouts in variants) {
                val slots = HeaderPanelGeometry.allocate(width, cutouts, 12, 98, 8)
                for (segment in listOfNotNull(slots.leading, slots.trailing)) {
                    assertTrue(segment.start >= 0)
                    assertTrue(segment.endExclusive <= width)
                    assertTrue(segment.width > 0)
                    for (cutout in cutouts) {
                        // Insets include a 12px breathing room around holes.
                        assertTrue(segment.endExclusive <= cutout.first - 12 ||
                            segment.start >= cutout.last + 12)
                    }
                }
                if (slots.trailing != null && slots.leading != null)
                    assertTrue(slots.leading.endExclusive <= slots.trailing.start)
            }
        }
    }

}
