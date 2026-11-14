package com.russoturissto.towerradar

import org.json.JSONArray
import org.json.JSONObject

/** Local bookmarks for regions that have NOT been bulk-downloaded. */
object ViewedCacheAreaStore {
    data class Area(val providerId: String, val bounds: OfflineBounds, val markedAt: Long)

    fun parse(raw: String): List<Area> = try {
        val items = JSONArray(raw)
        (0 until items.length()).mapNotNull { i ->
            try {
                val obj = items.getJSONObject(i)
                val bounds = OfflineBounds(
                    obj.getDouble("south"), obj.getDouble("west"),
                    obj.getDouble("north"), obj.getDouble("east"))
                val id = obj.getString("provider")
                if (id.isBlank() || !bounds.south.isFinite() || !bounds.north.isFinite() ||
                    !bounds.west.isFinite() || !bounds.east.isFinite() ||
                    bounds.south !in -85.0..85.0 || bounds.north !in -85.0..85.0 ||
                    bounds.west !in -180.0..180.0 || bounds.east !in -180.0..180.0 ||
                    bounds.south >= bounds.north || bounds.west >= bounds.east) null
                else Area(id, bounds, obj.optLong("markedAt", 0L))
            } catch (_: Exception) { null }
        }
    } catch (_: Exception) { emptyList() }

    private fun encode(areas: List<Area>): String {
        val arr = JSONArray()
        for (area in areas) {
            arr.put(JSONObject().put("provider", area.providerId)
                .put("south", area.bounds.south).put("west", area.bounds.west)
                .put("north", area.bounds.north).put("east", area.bounds.east)
                .put("markedAt", area.markedAt))
        }
        return arr.toString()
    }

    fun append(raw: String, area: Area): String =
        encode((parse(raw) + area).takeLast(20))

    fun remove(raw: String, index: Int): String =
        encode(parse(raw).filterIndexed { i, _ -> i != index })
}
