package com.russoturissto.towerradar

import org.junit.Assert.*
import org.junit.Test

class MapCacheLimitPolicyTest {
    @Test fun allowedChoicesIncludeUnlimited() {
        assertArrayEquals(intArrayOf(100, 250, 500, -1), MapCacheLimitPolicy.optionsMb)
        assertTrue(MapCacheLimitPolicy.valid(-1))
        assertFalse(MapCacheLimitPolicy.valid(0))
    }

    @Test fun unlimitedUsesMaximumLongRatherThanHiddenFiveHundredMbCap() {
        assertEquals(Long.MAX_VALUE, MapCacheLimitPolicy.maxBytes(-1))
        assertEquals(500L * 1024L * 1024L, MapCacheLimitPolicy.maxBytes(500))
        assertEquals("Без ограничений", MapCacheLimitPolicy.label(-1))
    }
}
