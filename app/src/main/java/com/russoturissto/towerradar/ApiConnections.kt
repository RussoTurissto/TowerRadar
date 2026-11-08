package com.russoturissto.towerradar

import org.json.JSONObject
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.io.IOException
import kotlin.math.cos
import java.util.Locale

/** API secrets are never written to logs, error messages or the repository. */
data class ApiProbe(
    val connected: Boolean,
    val message: String,
    val http: Int = 0,
    val session: GoogleTileSession? = null
)

data class GoogleTileSession(val token: String, val expirySeconds: Long) {
    fun valid(): Boolean = token.isNotBlank() &&
        System.currentTimeMillis() / 1000 + 90 < expirySeconds
}

object ApiConnections {
    private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")

    private fun failure(code: Int, provider: String): ApiProbe {
        val reason = when (code) {
            400 -> "Некорректный запрос или параметры Tile API"
            401, 403 -> "Ключ отклонён или нет доступа к этому продукту API / ограничениям ключа"
            404 -> "Сервис не поддерживает запрошенную карту"
            429 -> "Превышен лимит запросов или тарифа"
            in 500..599 -> "Сервер временно недоступен"
            else -> "Ошибка ответа сервера"
        }
        return ApiProbe(false, "$provider · HTTP $code: $reason", code)
    }

    private fun tileCheck(url: String, name: String): ApiProbe {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 6500
        connection.readTimeout = 6500
        connection.instanceFollowRedirects = true
        connection.setRequestProperty("User-Agent", "TowerRadar/0.7.0")
        return try {
            val code = connection.responseCode
            if (code !in 200..299) failure(code, name)
            else {
                val type = connection.contentType.orEmpty().lowercase(Locale.US)
                if (!type.startsWith("image/")) ApiProbe(false,
                    "$name · HTTP $code: ответ сервера не является изображением", code)
                else {
                    val valid = connection.inputStream.use { it.read() >= 0 }
                    if (valid) ApiProbe(true, "✓ Подключено · тайл получен", code)
                    else ApiProbe(false, "$name · пустой ответ от сервера", code)
                }
            }
        } finally { connection.disconnect() }
    }

    /** The API produces session-scoped URLs, not unauthorised Google tile host patterns. */
    fun googleSession(key: String, provider: MapProvider): ApiProbe {
        val mapType = if (provider.id == "google-road") "roadmap" else "satellite"
        val body = JSONObject().put("mapType", mapType)
            .put("language", "ru-RU")
            .put("region", Locale.getDefault().country
                .takeIf { it.length == 2 }?.uppercase(Locale.US) ?: "US")
        if (provider.id == "google-hybrid") {
            body.put("layerTypes", JSONArray().put("layerRoadmap"))
            body.put("overlay", false)
        }
        val connection = URL("https://tile.googleapis.com/v1/createSession?key=" +
            encode(key)).openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.connectTimeout = 8000
        connection.readTimeout = 8000
        connection.doOutput = true
        connection.setRequestProperty("Content-Type", "application/json")
        connection.setRequestProperty("User-Agent", "TowerRadar/0.7.0")
        return try {
            connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val code = connection.responseCode
            if (code !in 200..299) failure(code, "Google Map Tiles API")
            else {
                val reply = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
                val session = GoogleTileSession(reply.optString("session"),
                    reply.optString("expiry").toLongOrNull() ?: 0L)
                if (!session.valid()) ApiProbe(false, "Google · сеанс не выдан или уже истёк", code)
                else {
                    val tile = tileCheck(MapSources.sampleTileUrl(provider, 55.75, 37.62,
                        key, session.token), provider.name)
                    tile.copy(session = if (tile.connected) session else null)
                }
            }
        } finally { connection.disconnect() }
    }

    /** MapTiler vector style endpoint returns JSON, unlike PNG tile APIs. */
    private fun styleCheck(url: String, name: String): ApiProbe {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 6500
        c.readTimeout = 6500
        return try {
            val code = c.responseCode
            if (code !in 200..299) failure(code, name)
            else {
                val response = c.inputStream.bufferedReader().use { it.readText().take(7000) }
                if (response.contains("\"version\"") &&
                    response.contains("\"sources\"")) {
                    ApiProbe(true, "✓ Подключено · стиль карты получен", code)
                } else ApiProbe(false, name + " · ответ не содержит стиль карты", code)
            }
        } finally { c.disconnect() }
    }

    fun checkMap(provider: MapProvider, lat: Double, lon: Double, key: String): ApiProbe {
        if (provider.keyPref != null && key.isBlank()) return ApiProbe(false, "Ключ не введён")
        return try {
            when {
                provider.id.startsWith("google-") -> googleSession(key, provider)
                provider.id == "maptiler-satellite" ->
                    tileCheck(MapSources.sampleTileUrl(provider, lat, lon, key), provider.name)
                MapSources.mapTiler(provider) ->
                    styleCheck(MapSources.tileTemplate(provider, key), provider.name)
                MapSources.yandexWeb(provider) ->
                    ApiProbe(false, "Ключ JavaScript API проверяется при открытии WebView")
                else -> tileCheck(MapSources.sampleTileUrl(provider, lat, lon, key), provider.name)
            }
        } catch (_: java.net.SocketTimeoutException) {
            ApiProbe(false, provider.name + " · сервер не ответил вовремя")
        } catch (_: java.net.UnknownHostException) {
            ApiProbe(false, provider.name + " · нет связи с сервером (DNS / интернет)")
        } catch (_: javax.net.ssl.SSLException) {
            ApiProbe(false, provider.name + " · ошибка защищённого соединения")
        } catch (_: Exception) {
            ApiProbe(false, provider.name + " · сбой сети или недопустимый ответ")
        }
    }

    fun checkOpenCell(key: String, lat: Double, lon: Double): ApiProbe {
        if (key.isBlank()) return ApiProbe(false, "Ключ не введён")
        return try {
            // One small, bounded request. No bulk download or background polling.
            val box = encode(CellSources.bbox(lat.coerceIn(-80.0, 80.0), lon))
            val connection = URL("https://opencellid.org/cell/getInAreaSize?key=" +
                encode(key) + "&BBOX=" + box + "&format=json")
                .openConnection() as HttpURLConnection
            connection.connectTimeout = 8000
            connection.readTimeout = 8000
            try {
                val code = connection.responseCode
                if (code !in 200..299) failure(code, "OpenCellID")
                else {
                    val obj = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
                    if (obj.has("error") || obj.optString("stat") == "fail")
                        ApiProbe(false, "OpenCellID · сервер отклонил ключ или запрос", code)
                    else if (obj.has("count"))
                        ApiProbe(true, "✓ Подключено · API ответил", code)
                    else ApiProbe(false, "OpenCellID · неожиданный формат ответа", code)
                }
            } finally { connection.disconnect() }
        } catch (_: Exception) {
            ApiProbe(false, "OpenCellID · проверьте интернет и доступность API")
        }
    }

    /** Copyright is specific to the user's visible Google map viewport. */
    fun googleCopyright(key: String, session: GoogleTileSession,
        zoom: Int, north: Double, south: Double, east: Double, west: Double): String? {
        if (!session.valid()) return null
        return try {
            val p = String.format(Locale.US,
                "zoom=%d&north=%.6f&south=%.6f&east=%.6f&west=%.6f",
                zoom.coerceIn(0, 22), north.coerceIn(-85.0, 85.0),
                south.coerceIn(-85.0, 85.0), east.coerceIn(-180.0,180.0),
                west.coerceIn(-180.0,180.0))
            val conn = URL("https://tile.googleapis.com/tile/v1/viewport?session=" +
                encode(session.token) + "&key=" + encode(key) + "&" + p)
                .openConnection() as HttpURLConnection
            conn.connectTimeout = 6000
            conn.readTimeout = 6000
            try {
                if (conn.responseCode !in 200..299) null
                else JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
                    .optString("copyright").takeIf { it.isNotBlank() }
            } finally { conn.disconnect() }
        } catch (_: Exception) { null }
    }
}
