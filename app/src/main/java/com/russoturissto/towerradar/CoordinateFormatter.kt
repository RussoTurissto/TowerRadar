package com.russoturissto.towerradar

import java.util.Locale

/** WGS84 latitude then longitude; a single copy-ready, locale-neutral format. */
object CoordinateFormatter {
    fun format(latitude: Double, longitude: Double): String? {
        if (!latitude.isFinite() || !longitude.isFinite() ||
            latitude !in -90.0..90.0 || longitude !in -180.0..180.0) return null
        val lat = if (latitude == 0.0) 0.0 else latitude
        val lon = if (longitude == 0.0) 0.0 else longitude
        return String.format(Locale.US, "%.6f, %.6f", lat, lon)
    }
}
