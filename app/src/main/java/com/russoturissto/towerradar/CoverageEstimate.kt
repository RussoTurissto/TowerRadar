package com.russoturissto.towerradar

import kotlin.math.*

/** A chosen radius is a visual approximation, NOT a predicted RF propagation footprint. */
object CoverageEstimate {
    val radiusChoicesMetres = intArrayOf(500, 1000, 3000, 5000, 10000)
    fun ring(lat: Double, lon: Double, radiusMetres: Int, segments: Int = 64):
        List<Pair<Double, Double>> {
        require(lat in -90.0..90.0 && lon in -180.0..180.0)
        require(radiusMetres in 100..50000 && segments in 16..256)
        val angular = radiusMetres / 6371008.8
        val phi = Math.toRadians(lat)
        val lambda = Math.toRadians(lon)
        return (0..segments).map { i ->
            val theta = 2 * PI * i / segments
            val lat2 = asin((sin(phi) * cos(angular) +
                cos(phi) * sin(angular) * cos(theta)).coerceIn(-1.0, 1.0))
            val lon2 = lambda + atan2(sin(theta) * sin(angular) * cos(phi),
                cos(angular) - sin(phi) * sin(lat2))
            Math.toDegrees(lat2) to ((Math.toDegrees(lon2) + 540.0) % 360.0 - 180.0)
        }
    }
}
