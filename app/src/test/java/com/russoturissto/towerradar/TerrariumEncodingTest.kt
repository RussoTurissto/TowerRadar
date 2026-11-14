package com.russoturissto.towerradar

import org.junit.Assert.*
import org.junit.Test

class TerrariumEncodingTest {
    @Test fun positiveNegativeAndFractionalElevations() {
        assertEquals(0.0, TerrariumEncoding.elevation(0xff800000.toInt())!!, 0.0001)
        assertEquals(170.0, TerrariumEncoding.elevation(0xff80aa00.toInt())!!, 0.0001)
        assertEquals(-1.0, TerrariumEncoding.elevation(0xff7fff00.toInt())!!, 0.0001)
        assertEquals(0.5, TerrariumEncoding.elevation(0xff800080.toInt())!!, 0.0001)
    }
    @Test fun transparentPixelIsNotMadeUp() {
        assertNull(TerrariumEncoding.elevation(0x00800000))
    }
}
