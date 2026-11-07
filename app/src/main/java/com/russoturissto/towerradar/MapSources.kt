package com.russoturissto.towerradar

import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.RasterLayer
import org.maplibre.android.style.sources.RasterSource
import org.maplibre.android.style.sources.TileSet
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.math.*

/** Raster backgrounds: free OSM-style sources, plus optional licensed Russian-labelled APIs. */
data class MapProvider(
    val id: String,
    val name: String,
    val tiles: String,
    val attribution: String,
    val keyPref: String? = null
)

object MapSources {
    val providers = listOf(
        MapProvider("osmfr", "OSM France · местные названия",
            "https://a.tile.openstreetmap.fr/osmfr/{z}/{x}/{y}.png",
            "© OpenStreetMap contributors · OpenStreetMap France"),
        MapProvider("osm", "OpenStreetMap · местные названия",
            "https://tile.openstreetmap.org/{z}/{x}/{y}.png",
            "© OpenStreetMap contributors"),
        MapProvider("osmde", "OSM Deutschland · местные названия",
            "https://tile.openstreetmap.de/{z}/{x}/{y}.png",
            "© OpenStreetMap contributors · OSM Deutschland"),
        MapProvider("osmhot", "OSM HOT · местные названия",
            "https://a.tile.openstreetmap.fr/hot/{z}/{x}/{y}.png",
            "© OpenStreetMap contributors · HOT"),
        MapProvider("yandex", "Яндекс Карты · русский",
            "https://tiles.api-maps.yandex.ru/v1/tiles/?x={x}&y={y}&z={z}&l=map&lang=ru_RU&projection=web_mercator&apikey={api_key}",
            "© Яндекс Карты", "map_key_yandex"),
        MapProvider("2gis", "2ГИС · русский",
            "https://tile0.maps.2gis.com/v2/tiles/online_sd/{z}/{x}/{y}.png?key={api_key}",
            "© 2ГИС", "map_key_2gis")
    )

    fun unkeyed(): List<MapProvider> = providers.filter { it.keyPref == null }

    fun tileTemplate(provider: MapProvider, key: String = ""): String {
        if (provider.keyPref != null) require(key.isNotBlank()) { "Требуется ключ " + provider.name }
        return provider.tiles.replace("{api_key}", URLEncoder.encode(key.trim(), "UTF-8"))
    }

    fun style(provider: MapProvider, key: String = ""): Style.Builder {
        val tileSet = TileSet("2.2.0", tileTemplate(provider, key))
        tileSet.setMinZoom(0f)
        tileSet.setMaxZoom(if (provider.id == "yandex") 20f else 19f)
        return Style.Builder()
            .withSource(RasterSource("basemap-source", tileSet))
            .withLayer(RasterLayer("basemap-layer", "basemap-source"))
    }

    fun sampleTileUrl(provider: MapProvider, lat: Double, lon: Double, key: String = ""): String {
        val z = 8
        val safeLat = lat.coerceIn(-85.05112878, 85.05112878)
        val safeLon = lon.coerceIn(-180.0, 180.0)
        val n = (1 shl z).toDouble()
        val x = floor((safeLon + 180.0) / 360.0 * n).toInt().coerceIn(0, 255)
        val rad = Math.toRadians(safeLat)
        val y = floor((1 - ln(tan(rad) + 1 / cos(rad)) / PI) / 2 * n)
            .toInt().coerceIn(0, 255)
        return tileTemplate(provider, key)
            .replace("{z}", z.toString())
            .replace("{x}", x.toString())
            .replace("{y}", y.toString())
    }

    fun reachable(provider: MapProvider, lat: Double, lon: Double, key: String = ""): Boolean {
        if (provider.keyPref != null && key.isBlank()) return false
        val connection = (URL(sampleTileUrl(provider, lat, lon, key)).openConnection() as HttpURLConnection)
        connection.connectTimeout = 4500
        connection.readTimeout = 4500
        connection.setRequestProperty("User-Agent", "TowerRadar/0.3.0 (+https://github.com/RussoTurissto/TowerRadar)")
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
