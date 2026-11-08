package com.russoturissto.towerradar

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale
import kotlin.math.*

data class TerrainSample(val latitude: Double, val longitude: Double, val elevation: Double?)
data class TerrainProfile(val samples: List<TerrainSample>, val distanceKm: Double, val source: String)

/** The elevations are ground elevations above sea level, not antenna heights. */
object TerrainProfileClient {
    private const val DAYS_30 = 30L * 24L * 60L * 60L * 1000L
    private fun fmt(v: Double) = String.format(Locale.US, "%.6f", v)

    fun coordinates(aLat: Double, aLon: Double, bLat: Double, bLon: Double,
                    count: Int): List<Pair<Double, Double>> {
        require(count in 2..100)
        val a = Math.toRadians(aLat); val b = Math.toRadians(bLat)
        val al = Math.toRadians(aLon); val bl = Math.toRadians(bLon)
        val dot = (sin(a) * sin(b) + cos(a) * cos(b) * cos(bl - al)).coerceIn(-1.0, 1.0)
        val omega = acos(dot)
        return (0 until count).map { i ->
            val f = i.toDouble() / (count - 1)
            if (omega < 1e-8) return@map aLat to aLon
            val wa = sin((1 - f) * omega) / sin(omega)
            val wb = sin(f * omega) / sin(omega)
            val x = wa * cos(a) * cos(al) + wb * cos(b) * cos(bl)
            val y = wa * cos(a) * sin(al) + wb * cos(b) * sin(bl)
            val z = wa * sin(a) + wb * sin(b)
            Math.toDegrees(atan2(z, hypot(x, y))) to Math.toDegrees(atan2(y, x))
        }
    }
    private fun get(url: String): String {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 7500
        connection.readTimeout = 11000
        connection.setRequestProperty("User-Agent", "TowerRadar/0.22.0")
        return try {
            if (connection.responseCode != 200)
                throw IllegalStateException("HTTP " + connection.responseCode)
            connection.inputStream.use { it.bufferedReader().readText().take(300000) }
        } finally { connection.disconnect() }
    }
    fun load(context: Context, aLat: Double, aLon: Double, bLat: Double,
             bLon: Double, mapTilerKey: String = ""): TerrainProfile {
        require(aLat in -90.0..90.0 && bLat in -90.0..90.0)
        require(aLon in -180.0..180.0 && bLon in -180.0..180.0)
        val distance = TowerData.distanceKm(aLat, aLon, bLat, bLon)
        val key = listOf(aLat, aLon, bLat, bLon).joinToString(",") {
            String.format(Locale.US, "%.5f", it)
        }
        val prefs = context.getSharedPreferences("terrain_profiles", Context.MODE_PRIVATE)
        try {
            val cached = prefs.getString(key, null)?.let(::JSONObject)
            if (cached != null && System.currentTimeMillis() -
                cached.optLong("time") in 0L..DAYS_30) {
                val array = cached.getJSONArray("samples")
                val list = (0 until array.length()).map { i ->
                    val obj = array.getJSONObject(i)
                    TerrainSample(obj.getDouble("lat"), obj.getDouble("lon"),
                        if (obj.isNull("ele")) null else obj.getDouble("ele"))
                }
                if (list.count { it.elevation != null } >= 10)
                    return TerrainProfile(list, distance, cached.optString("source") + " · кеш")
            }
        } catch (_: Exception) { }

        val locations = fmt(aLat) + "," + fmt(aLon) + "|" + fmt(bLat) + "," + fmt(bLon)
        var lastError: Exception? = null
        val topodata = try {
            val url = "https://api.opentopodata.org/v1/mapzen?locations=" +
                URLEncoder.encode(locations, "UTF-8") +
                "&samples=65&interpolation=bilinear"
            val json = JSONObject(get(url))
            if (json.optString("status") != "OK") throw IllegalStateException("Unavailable")
            val array = json.getJSONArray("results")
            if (array.length() != 65) throw IllegalStateException("Incomplete response")
            TerrainProfile((0 until array.length()).map { i ->
                val obj = array.getJSONObject(i)
                val p = obj.getJSONObject("location")
                TerrainSample(p.getDouble("lat"), p.getDouble("lng"),
                    if (obj.isNull("elevation")) null else obj.getDouble("elevation"))
            }, distance, "OpenTopoData · Mapzen")
        } catch (e: Exception) { lastError = e; null }

        // MapTiler free key is optional. Its free tier is non-commercial and quota-limited.
        val maptiler = if (topodata == null && mapTilerKey.isNotBlank()) try {
            val points = coordinates(aLat, aLon, bLat, bLon, 49)
            val packed = points.joinToString(";") { (lat, lon) -> fmt(lon) + "," + fmt(lat) }
            val json = JSONArray(get("https://api.maptiler.com/elevation/" + packed +
                ".json?key=" + URLEncoder.encode(mapTilerKey, "UTF-8")))
            if (json.length() != points.size) throw IllegalStateException("Incomplete response")
            TerrainProfile(points.mapIndexed { i, (lat, lon) ->
                val v = json.getJSONArray(i)
                TerrainSample(lat, lon, if (v.isNull(2)) null else v.getDouble(2))
            }, distance, "MapTiler Elevation")
        } catch (e: Exception) { lastError = e; null } else null
        val result = topodata ?: maptiler ?: throw IllegalStateException(
            "Сервис высот временно недоступен. Проверьте интернет.", lastError)
        if (result.samples.count { it.elevation?.isFinite() == true } < 10)
            throw IllegalStateException("В этой области недостаточно данных о рельефе.")
        try {
            val array = JSONArray()
            result.samples.forEach {
                array.put(JSONObject().put("lat", it.latitude).put("lon", it.longitude)
                    .put("ele", it.elevation))
            }
            prefs.edit().putString(key, JSONObject().put("time", System.currentTimeMillis())
                .put("source", result.source).put("samples", array).toString()).apply()
        } catch (_: Exception) { }
        return result
    }
}
