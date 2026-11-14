package com.russoturissto.towerradar

import org.junit.Assert.*
import org.junit.Test
import java.util.Locale

class CoordinateFormatterTest {
    @Test fun formatsGpsAndSelectedPointWithSixDecimals() {
        assertEquals("55.123456, 37.123456",
            CoordinateFormatter.format(55.1234563, 37.1234564))
        assertEquals("-89.500000, 179.999999",
            CoordinateFormatter.format(-89.5, 179.999999))
    }
    @Test fun neverUsesLocaleCommaAsDecimalSeparator() {
        val previous = Locale.getDefault()
        try {
            Locale.setDefault(Locale.GERMANY)
            assertEquals("51.123457, 6.000001",
                CoordinateFormatter.format(51.1234567, 6.0000006))
        } finally { Locale.setDefault(previous) }
    }
    @Test fun refusesInvalidOrUnknownCoordinates() {
        assertNull(CoordinateFormatter.format(Double.NaN, 13.0))
        assertNull(CoordinateFormatter.format(91.0, 13.0))
        assertNull(CoordinateFormatter.format(0.0, -181.0))
        assertEquals("0.000000, 0.000000", CoordinateFormatter.format(-0.0, -0.0))
    }
}
