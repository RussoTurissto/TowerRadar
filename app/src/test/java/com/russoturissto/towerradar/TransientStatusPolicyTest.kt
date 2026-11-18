package com.russoturissto.towerradar

import org.junit.Assert.*
import org.junit.Test

class TransientStatusPolicyTest {
    @Test fun progressIsNeverVisibleLongerThanThreeSeconds() {
        assertFalse(TransientStatusPolicy.inWindow(0L, 50L))
        assertTrue(TransientStatusPolicy.inWindow(1_000L, 1_000L))
        assertTrue(TransientStatusPolicy.inWindow(1_000L, 3_999L))
        assertFalse(TransientStatusPolicy.inWindow(1_000L, 4_000L))
        assertFalse(TransientStatusPolicy.inWindow(1_000L, 999L))
    }
    @Test fun resultBannerFadesAwayWithinThreeSeconds() {
        assertEquals(3_000L, TransientStatusPolicy.RESULT_MS)
        assertTrue(TransientStatusPolicy.FADE_MS < TransientStatusPolicy.RESULT_MS)
        assertEquals(2_820L, TransientStatusPolicy.RESULT_MS - TransientStatusPolicy.FADE_MS)
    }
}
