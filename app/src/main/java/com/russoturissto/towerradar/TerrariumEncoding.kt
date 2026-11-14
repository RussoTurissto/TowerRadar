package com.russoturissto.towerradar

/** Original Mapzen Terrarium encoding; transparent no-data pixels stay unknown. */
object TerrariumEncoding {
    fun elevation(argb: Int): Double? {
        if ((argb ushr 24) == 0) return null
        return ((argb shr 16) and 255) * 256.0 +
            ((argb shr 8) and 255) + (argb and 255) / 256.0 - 32768.0
    }
}
