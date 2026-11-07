package com.russoturissto.towerradar

import org.maplibre.android.style.layers.RasterLayer
import org.maplibre.android.style.sources.RasterSource
import org.maplibre.android.style.sources.TileSet
import org.maplibre.android.maps.Style
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.*

/**
 * Uses a bundled local style (no remote style.json, sprite, fonts or API token).
 * A single visible location tile is checked to choose a reachable host. Never bulk prefetch.
 */
data class MapProvider(val name: String, val tiles: String, val attribution: String)

object MapSources {
    val providers = listOf(
        MapProvider("OSM France", "https://a.tile.openstreetmap.fr/osmfr/{z}/{x}/{y}.png", "© OpenStreetMap contributors · OpenStreetMap France"),
        MapProvider("OpenStreetMap", "https://tile.openstreetmap.org/{z}/{x}/{y}.png", "© OpenStreetMap contributors"),
        MapProvider("OSM Deutschland", "https://tile.openstreetmap.de/{z}/{x}/{y}.png", "© OpenStreetMap contributors · OSM Deutschland"),
        MapProvider("OSM HOT", "https://a.tile.openstreetmap.fr/hot/{z}/{x}/{y}.png", "© OpenStreetMap contributors · HOT / OSM France")
    )
    fun style(provider: MapProvider): Style.Builder {
        val tiles = TileSet("2.2.0", provider.tiles)
        tiles.setMinZoom(0f)
        tiles.setMaxZoom(19f)
        return Style.Builder()
            .withSource(RasterSource("basemap-source", tiles))
            .withLayer(RasterLayer("basemap-layer", "basemap-source"))
    }

    fun sampleTileUrl(provider: MapProvider, lat: Double, lon: Double): String {
        val z = 8
        val safeLat = lat.coerceIn(-85.05112878, 85.05112878)
        val safeLon = lon.coerceIn(-180.0, 180.0)
        val n = (1 shl z).toDouble()
        val x = floor((safeLon + 180.0) / 360.0 * n).toInt().coerceIn(0, 255)
        val radians = Math.toRadians(safeLat)
        val y = floor((1 - ln(tan(radians) + 1 / cos(radians)) / PI) / 2 * n)
            .toInt().coerceIn(0, 255)
        return provider.tiles.replace("{z}", z.toString())
            .replace("{x}", x.toString()).replace("{y}", y.toString())
    }

    fun reachable(provider: MapProvider, lat: Double, lon: Double): Boolean {
        val connection = (URL(sampleTileUrl(provider, lat, lon)).openConnection() as HttpURLConnection)
        connection.connectTimeout = 3500
        connection.readTimeout = 3500
        connection.setRequestProperty("User-Agent", "TowerRadar/0.2.0 (+https://github.com/RussoTurissto/TowerRadar)")
        try {
            if (connection.responseCode != 200) return false
            if (!connection.contentType.orEmpty().startsWith("image/")) return false
            return connection.inputStream.use { it.read() != -1 }
        } catch (_: Exception) {
            return false
        } finally {
            connection.disconnect()
        }
    }
}
