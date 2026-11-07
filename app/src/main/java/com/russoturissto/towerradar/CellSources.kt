package com.russoturissto.towerradar

import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale
import kotlin.math.cos

/**
 * Optional OpenCellID dataset. Results are logical radio cells with estimated positions,
 * not verified physical masts. Searches an <= 3 km² box centered on the GPS position
 * to comply with OpenCellID's 4 km² per-request BBOX limit and a small credit budget.
 */
object CellSources {
    fun bbox(latitude: Double, longitude: Double): String {
        val halfWidthKm = 0.85
        val dLat = halfWidthKm / 111.32
        val dLon = halfWidthKm / (111.32 * cos(Math.toRadians(latitude)).coerceAtLeast(0.02))
        return String.format(Locale.US, "%.6f,%.6f,%.6f,%.6f",
            (latitude - dLat).coerceAtLeast(-90.0), (longitude - dLon).coerceAtLeast(-180.0),
            (latitude + dLat).coerceAtMost(90.0), (longitude + dLon).coerceAtMost(180.0))
    }

    fun parse(body: String, latitude: Double, longitude: Double): List<TowerSite> {
        val objectRoot = JSONObject(body)
        if (objectRoot.optString("error").isNotEmpty() || objectRoot.optString("stat") == "fail") {
            throw IOException("OpenCellID вернул ошибку доступа или лимита.")
        }
        val cells = objectRoot.optJSONArray("cells") ?: return emptyList()
        val results = ArrayList<TowerSite>()
        for (i in 0 until cells.length()) {
            val cell = cells.optJSONObject(i) ?: continue
            val lat = cell.optDouble("lat", Double.NaN)
            val lon = cell.optDouble("lon", Double.NaN)
            if (!lat.isFinite() || !lon.isFinite() || lat !in -90.0..90.0 || lon !in -180.0..180.0) continue
            val mcc = cell.optInt("mcc", -1)
            val mnc = cell.optInt("mnc", -1)
            val lac = cell.optInt("lac", -1)
            val cid = cell.optLong("cellid", -1L)
            if (cid < 0) continue
            val radio = cell.optString("radio", "неизвестно")
            val operator = if (mcc >= 0 && mnc >= 0) "MCC $mcc · MNC $mnc" else null
            results.add(TowerSite(
                id = "opencellid/$mcc/$mnc/$lac/$cid/$radio",
                latitude = lat, longitude = lon,
                name = "Сота $radio · ID $cid",
                operator = operator,
                mobile = true,
                technology = radio,
                height = null,
                url = "https://opencellid.org/",
                source = "OpenCellID · приблизительно"
            ))
        }
        return results.distinctBy { it.id }.sortedBy {
            TowerData.distanceKm(latitude, longitude, it.latitude, it.longitude)
        }
    }

    fun fetch(key: String, latitude: Double, longitude: Double): List<TowerSite> {
        if (key.isBlank()) return emptyList()
        val box = URLEncoder.encode(bbox(latitude, longitude), "UTF-8")
        val cleanKey = URLEncoder.encode(key.trim(), "UTF-8")
        val result = ArrayList<TowerSite>()
        for (offset in listOf(0, 50, 100)) {
            val url = URL("https://opencellid.org/cell/getInArea?key=$cleanKey&BBOX=$box&limit=50&offset=$offset&format=json")
            val connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 8000
            connection.readTimeout = 12000
            connection.setRequestProperty("User-Agent", "TowerRadar/0.2.0 (+https://github.com/RussoTurissto/TowerRadar)")
            try {
                val code = connection.responseCode
                if (code !in 200..299) throw IOException("OpenCellID HTTP $code")
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                val page = parse(response, latitude, longitude)
                result.addAll(page)
                if (page.size < 50) break
            } finally {
                connection.disconnect()
            }
        }
        return result.distinctBy { it.id }
    }
}
