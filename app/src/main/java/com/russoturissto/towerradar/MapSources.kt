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
    val allProviders = listOf(
        MapProvider("openfreemap-offline", "OpenFreeMap · офлайн", 
            OfflineAreaPolicy.OFFLINE_STYLE,
            "© OpenFreeMap · © OpenStreetMap contributors"),
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
            "https://tiles.api-maps.yandex.ru/v1/tiles/?x={x}&y={y}&z={z}&l=map&lang=ru_RU&projection=web_mercator&scale=2&apikey={api_key}",
            "© Яндекс Карты", "map_key_yandex"),
        // Separate JS API key: Tiles API does NOT support satellite/hybrid.
        MapProvider("yandex-js-road", "Яндекс JS · Схема",
            "", "© Яндекс Карты", "map_key_yandex_js"),
        MapProvider("yandex-js-satellite", "Яндекс JS · Спутник",
            "", "© Яндекс Карты", "map_key_yandex_js"),
        MapProvider("yandex-js-hybrid", "Яндекс JS · Гибрид",
            "", "© Яндекс Карты", "map_key_yandex_js"),
        // Personal/noncommercial free API plan, subject to MapTiler limits.
        MapProvider("maptiler-streets", "MapTiler · Карта",
            "https://api.maptiler.com/maps/streets-v4/style.json?key={api_key}",
            "© MapTiler · © OpenStreetMap contributors", "map_key_maptiler"),
        MapProvider("maptiler-satellite", "MapTiler · Спутник",
            "https://api.maptiler.com/tiles/satellite-v2/{z}/{x}/{y}.jpg?key={api_key}",
            "© MapTiler · спутниковые данные", "map_key_maptiler"),
        MapProvider("maptiler-hybrid", "MapTiler · Гибрид",
            "https://api.maptiler.com/maps/hybrid/style.json?key={api_key}",
            "© MapTiler · © OpenStreetMap contributors", "map_key_maptiler"),
        MapProvider("2gis", "2ГИС · русский",
            "https://tile0.maps.2gis.com/v2/tiles/online_sd/{z}/{x}/{y}.png?key={api_key}",
            "© 2ГИС", "map_key_2gis"),
        MapProvider("google-road", "Google Карты · обычная",
            "https://tile.googleapis.com/v1/2dtiles/{z}/{x}/{y}?session={session}&key={api_key}",
            "Google Maps", "map_key_google"),
        MapProvider("google-satellite", "Google · Спутник",
            "https://tile.googleapis.com/v1/2dtiles/{z}/{x}/{y}?session={session}&key={api_key}",
            "Google Maps", "map_key_google"),
        MapProvider("google-hybrid", "Google · Гибрид",
            "https://tile.googleapis.com/v1/2dtiles/{z}/{x}/{y}?session={session}&key={api_key}",
            "Google Maps", "map_key_google")
    )

    // Paid/billing-required providers are hidden but their existing keys are not erased.
    val providers = allProviders.filter { it.id != "2gis" && !it.id.startsWith("google-") }
    fun yandexWeb(provider: MapProvider) = provider.id.startsWith("yandex-js-")
    fun mapTiler(provider: MapProvider) = provider.id.startsWith("maptiler-")
    fun unkeyed(): List<MapProvider> = providers.filter { it.keyPref == null }

    fun tileTemplate(provider: MapProvider, key: String = "", session: String = ""): String {
        if (provider.keyPref != null) require(key.isNotBlank()) { "Требуется ключ " + provider.name }
        require(!yandexWeb(provider)) { "Yandex JS API renders through WebView, not tiles" }
        if (provider.id.startsWith("google-")) require(session.isNotBlank()) {
            "Необходимо открыть сеанс Google Map Tiles API"
        }
        return provider.tiles
            .replace("{api_key}", URLEncoder.encode(key.trim(), "UTF-8"))
            .replace("{session}", URLEncoder.encode(session, "UTF-8"))
    }

    fun style(provider: MapProvider, key: String = "", session: String = ""): Style.Builder {
        // MapTiler satellite imagery is a licensed raster tileset; other
        // MapTiler modes are published, standard MapLibre vector styles.
        if (provider.id == "openfreemap-offline")
            return Style.Builder().fromUri(OfflineAreaPolicy.OFFLINE_STYLE)
        if (mapTiler(provider) && provider.id != "maptiler-satellite")
            return Style.Builder().fromUri(tileTemplate(provider, key))
        val tileSet = TileSet("2.2.0", tileTemplate(provider, key, session))
        tileSet.setMinZoom(0f)
        tileSet.setMaxZoom(when {
            provider.id.startsWith("google-") || provider.id == "maptiler-satellite" -> 22f
            provider.id == "yandex" -> 20f
            else -> 19f
        })
        return Style.Builder()
            .withSource(RasterSource("basemap-source", tileSet, 256).apply {
                // Google and 2GIS remain transient: do not persist downloaded
                // tiles without the corresponding provider's explicit permission.
                isVolatile = !MapTileCache.diskCacheAllowed(provider)
            })
            .withLayer(RasterLayer("basemap-layer", "basemap-source"))
    }

    fun sampleTileUrl(provider: MapProvider, lat: Double, lon: Double, key: String = "",
        session: String = ""): String {
        val z = 8
        val safeLat = lat.coerceIn(-85.05112878, 85.05112878)
        val safeLon = lon.coerceIn(-180.0, 180.0)
        val n = (1 shl z).toDouble()
        val x = floor((safeLon + 180.0) / 360.0 * n).toInt().coerceIn(0, 255)
        val rad = Math.toRadians(safeLat)
        val y = floor((1 - ln(tan(rad) + 1 / cos(rad)) / PI) / 2 * n)
            .toInt().coerceIn(0, 255)
        return tileTemplate(provider, key, session)
            .replace("{z}", z.toString())
            .replace("{x}", x.toString())
            .replace("{y}", y.toString())
    }

    fun reachable(provider: MapProvider, lat: Double, lon: Double, key: String = ""): Boolean {
        if (provider.keyPref != null && key.isBlank()) return false
        if (provider.id == "openfreemap-offline" ||
            provider.id.startsWith("google-") || yandexWeb(provider) || mapTiler(provider))
            return false // session, WebView or vector style, not directly testable as PNG
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
