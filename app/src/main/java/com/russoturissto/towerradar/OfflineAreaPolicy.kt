package com.russoturissto.towerradar

import kotlin.math.*

/** A finite, explicitly bounded tile pyramid; never prefetch OSMF/third-party map tiles. */
data class OfflineBounds(val south: Double, val west: Double, val north: Double, val east: Double) {
    fun contains(lat: Double, lon: Double): Boolean =
        lat in south..north && lon in west..east
}

object OfflineAreaPolicy {
    const val MIN_ZOOM = 6
    const val MAX_TILES = 1800 // Conservative margin below OpenFreeMap's 3,000-tile limit.
    const val MAX_MAP_BYTES = 40L * 1024L * 1024L // Conservative margin below 50 MB.
    const val OFFLINE_STYLE = "https://tiles.openfreemap.org/styles/liberty"

    fun bounds(lat: Double, lon: Double, radiusKm: Int): OfflineBounds? {
        if (!lat.isFinite() || !lon.isFinite() || lat !in -80.0..80.0 ||
            lon !in -180.0..180.0 || radiusKm !in listOf(2, 5, 10, 20)) return null
        val dLat = radiusKm * 1.08 / 111.1
        val dLon = dLat / cos(Math.toRadians(lat)).coerceAtLeast(0.1)
        if (lat - dLat < -85.0 || lat + dLat > 85.0 ||
            lon - dLon < -180.0 || lon + dLon > 180.0) return null
        return OfflineBounds(lat - dLat, lon - dLon, lat + dLat, lon + dLon)
    }

    fun tileX(lon: Double, z: Int): Int {
        val n = 1 shl z
        return floor((lon + 180.0) / 360.0 * n).toInt().coerceIn(0, n - 1)
    }
    fun tileY(lat: Double, z: Int): Int {
        val n = 1 shl z
        val rad = Math.toRadians(lat.coerceIn(-85.05112878, 85.05112878))
        return floor((1.0 - ln(tan(rad) + 1.0 / cos(rad)) / PI) / 2.0 * n)
            .toInt().coerceIn(0, n - 1)
    }
    fun countTiles(bounds: OfflineBounds, minZoom: Int, maxZoom: Int): Long {
        if (minZoom < 0 || maxZoom !in minZoom..19 ||
            bounds.west > bounds.east || bounds.south > bounds.north) return Long.MAX_VALUE
        var count = 0L
        for (z in minZoom..maxZoom) {
            val width = (tileX(bounds.east, z) - tileX(bounds.west, z) + 1).toLong()
            val height = (tileY(bounds.south, z) - tileY(bounds.north, z) + 1).toLong()
            count += width * height
            if (count > 100000) return count
        }
        return count
    }
    fun allowed(bounds: OfflineBounds, maxZoom: Int): Boolean =
        maxZoom in MIN_ZOOM..19 && countTiles(bounds, MIN_ZOOM, maxZoom) <= MAX_TILES
}
