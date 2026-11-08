package com.russoturissto.towerradar

import org.junit.Assert.*
import org.junit.Test

class FloatingOverlayPolicyTest {
    private fun layout(sheet: Int = 50, card: Int = 0) =
        FloatingOverlayPolicy.positions(20, sheet, card, 18, 8, 9)

    @Test fun openingAndClosingDrawerMovesBothWays() {
        val compact = layout(sheet = 70)
        val expanded = layout(sheet = 750)
        assertTrue(expanded.progressBottom > compact.progressBottom)
        assertEquals(compact, layout(sheet = 70)) // NO sticky previous maximum
    }

    @Test fun selectedTowerIsAlwaysAboveBothFooterAndCard() {
        val card = layout(sheet = 70, card = 400)
        assertEquals(408, card.creditsBottom)
        assertEquals(435, card.progressBottom)
        assertEquals(layout(sheet = 70), layout(sheet = 70, card = 0))
    }

    @Test fun creditsAndProgressRespectGestureNavigation() {
        val x = FloatingOverlayPolicy.positions(42, 0, 0, 20, 8, 9)
        assertEquals(50, x.creditsBottom)
        assertEquals(79, x.progressBottom)
    }

    @Test fun higherObstacleWinsWithoutLosingReturnPosition() {
        val a = layout(sheet = 700, card = 300)
        val b = layout(sheet = 70, card = 300)
        val c = layout(sheet = 70, card = 0)
        assertEquals(708, a.creditsBottom)
        assertEquals(308, b.creditsBottom)
        assertEquals(78, c.creditsBottom)
    }

    @Test(expected = IllegalArgumentException::class)
    fun negativeDimensionsRejected() {
        FloatingOverlayPolicy.positions(0, -1, 0, 10, 8, 9)
    }
}
