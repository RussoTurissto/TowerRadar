package com.russoturissto.towerradar

import org.json.JSONObject
import java.util.Locale
import kotlin.math.*

data class TowerSite(
    val id: String, val latitude: Double, val longitude: Double,
    val name: String?, val operator: String?, val mobile: Boolean,
    val technology: String?, val height: String?, val url: String,
    val source: String = "OpenStreetMap",
    val samples: Int? = null,
    val estimatedRangeMeters: Int? = null,
    val averageSignalDbm: Int? = null,
    val extraInfo: String? = null
)

/** Calculations independent of the Android UI. OSM positions do not imply a specific serving cell. */
object TowerData {
    val radiiKm = listOf(1, 3, 5, 10, 20, 50)

    fun distanceKm(a: Double, b: Double, c: Double, d: Double): Double {
        val p = Math.toRadians(c - a)
        val q = Math.toRadians(d - b)
        val x = sin(p / 2).pow(2) + cos(Math.toRadians(a)) * cos(Math.toRadians(c)) * sin(q / 2).pow(2)
        return 6371.0088 * 2 * atan2(sqrt(x.coerceIn(0.0, 1.0)), sqrt((1 - x).coerceIn(0.0, 1.0)))
    }

    fun operator(value: String?): String? {
        val v = value?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val lower = v.lowercase(Locale.ROOT)
        return when {
            lower.contains("tele2") || lower == "t2" || lower.contains("т2") -> "T2 / Tele2"
            lower.contains("мтс") || lower == "mts" -> "МТС"
            lower.contains("мегафон") || lower.contains("megafon") -> "МегаФон"
            lower.contains("beeline") || lower.contains("билайн") -> "Билайн"
            else -> v.take(70)
        }
    }

    fun query(latitude: Double, longitude: Double, radiusKm: Int): String {
        require(latitude in -90.0..90.0 && longitude in -180.0..180.0)
        require(radiusKm in radiiKm)
        val point = String.format(Locale.US, "around:%d,%.6f,%.6f", radiusKm * 1000, latitude, longitude)
        return "[out:json][timeout:25];(" +
            "nwr($point)[\"tower:type\"~\"^(communication|telecommunication|mobile_phone|cellular)$\"];" +
            "nwr($point)[\"mast:type\"~\"^(communication|telecommunication)$\"];" +
            "nwr($point)[\"communication:mobile_phone\"];" +
            "nwr($point)[\"technology:mobile_phone\"];" +
            "nwr($point)[\"man_made\"=\"communications_tower\"];" +
            "nwr($point)[\"telecom\"~\"^(antenna|mobile_phone|radio)$\"];" +
            "nwr($point)[\"communication\"~\"mobile_phone\"];" +
            "nwr($point)[\"man_made\"~\"^(mast|tower|antenna)$\"][\"operator\"];" +
            "nwr($point)[\"man_made\"~\"^(mast|tower)$\"][\"network\"];" +
            ");out center tags;"
    }

    fun parse(json: String, latitude: Double, longitude: Double, radiusKm: Int): List<TowerSite> {
        val elements = JSONObject(json).getJSONArray("elements")
        val result = ArrayList<TowerSite>()
        for (i in 0 until elements.length()) {
            val item = elements.optJSONObject(i) ?: continue
            val type = item.optString("type")
            if (type !in setOf("node", "way", "relation")) continue
            val center = item.optJSONObject("center") ?: item
            val lat = center.optDouble("lat", Double.NaN)
            val lon = center.optDouble("lon", Double.NaN)
            if (!lat.isFinite() || !lon.isFinite() || lat !in -90.0..90.0 || lon !in -180.0..180.0) continue
            if (distanceKm(latitude, longitude, lat, lon) > radiusKm + 0.1) continue
            val tags = item.optJSONObject("tags") ?: JSONObject()
            val number = item.optLong("id", -1L)
            if (number < 0) continue
            val mobileValue = tags.optString("communication:mobile_phone")
            val mobile = (mobileValue.isNotBlank() && mobileValue.lowercase(Locale.ROOT) !in listOf("no", "false")) ||
                tags.has("technology:mobile_phone") ||
                tags.optString("communication").contains("mobile_phone") ||
                tags.optString("telecom") == "mobile_phone" ||
                tags.optString("technology:mobile_phone").isNotBlank()
            result += TowerSite(
                id = "$type/$number",
                latitude = lat,
                longitude = lon,
                name = tags.optString("name").takeIf { it.isNotBlank() },
                operator = operator(tags.optString("operator").takeIf { it.isNotBlank() }
                    ?: tags.optString("operator:brand").takeIf { it.isNotBlank() }),
                mobile = mobile,
                technology = tags.optString("technology:mobile_phone").takeIf { it.isNotBlank() },
                height = tags.optString("height").takeIf { it.isNotBlank() },
                url = "https://www.openstreetmap.org/$type/$number",
                extraInfo = listOfNotNull(
                    tags.optString("ref").takeIf { it.isNotBlank() }?.let { "Обозначение: " + it },
                    tags.optString("frequency").takeIf { it.isNotBlank() }?.let { "Частота: " + it },
                    tags.optString("antenna:type").takeIf { it.isNotBlank() }?.let { "Тип антенны: " + it },
                    tags.optString("tower:type").takeIf { it.isNotBlank() }?.let { "Тип конструкции: " + it },
                    tags.optString("frequency:downlink").takeIf { it.isNotBlank() }?.let {
                        "Частота приёма: " + it
                    },
                    tags.optString("network").takeIf { it.isNotBlank() }?.let {
                        "Сеть: " + it.take(90)
                    },
                    tags.optString("support").takeIf { it.isNotBlank() }?.let {
                        "Опора: " + it.take(90)
                    },
                    tags.optString("tower:construction").takeIf { it.isNotBlank() }?.let {
                        "Конструкция: " + it.take(90)
                    },
                    tags.optString("manufacturer").takeIf { it.isNotBlank() }?.let {
                        "Изготовитель: " + it.take(90)
                    },
                    tags.optString("start_date").takeIf { it.isNotBlank() }?.let {
                        "Год установки: " + it.take(30)
                    },
                    tags.optString("communication:mobile_phone").takeIf { it.isNotBlank() }?.let {
                        "Мобильная связь: " + it.take(90)
                    },
                    tags.optString("lte:cellid").takeIf { it.isNotBlank() }?.let {
                        "LTE Cell ID (OSM): " + it.take(70)
                    },
                    tags.optString("gsm:cellid").takeIf { it.isNotBlank() }?.let {
                        "GSM Cell ID (OSM): " + it.take(70)
                    }
                ).joinToString(" · ").takeIf { it.isNotBlank() }
            )
        }
        return result.distinctBy { it.id }
            .sortedBy { distanceKm(latitude, longitude, it.latitude, it.longitude) }
            .take(2000)
    }
}
