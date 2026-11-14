package com.russoturissto.towerradar

import android.app.Activity
import org.json.JSONObject
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.offline.OfflineManager
import org.maplibre.android.offline.OfflineRegion
import org.maplibre.android.offline.OfflineRegionError
import org.maplibre.android.offline.OfflineRegionStatus
import org.maplibre.android.offline.OfflineTilePyramidRegionDefinition

/**
 * One user-facing offline area: vector map/style/glyphs via MapLibre, DEM via
 * OfflineTerrainStore, and towers already held by TowerDatabase.
 * Never targets tile.openstreetmap.org, Yandex or Google for prefetching.
 */
class OfflineAreaManager(private val activity: Activity) {
    data class Area(val region: OfflineRegion, val name: String,
                    val bounds: OfflineBounds, val radiusKm: Int, val maxZoom: Int)
    data class State(val message: String, val complete: Boolean)
    private val mapManager get() = OfflineManager.getInstance(activity)
    private val terrain = OfflineTerrainStore(activity.applicationContext)

    fun list(done: (List<Area>, String?) -> Unit) {
        mapManager.listOfflineRegions(object : OfflineManager.ListOfflineRegionsCallback {
            override fun onList(offlineRegions: Array<OfflineRegion>?) {
                val parsed = offlineRegions.orEmpty().mapNotNull { region ->
                    try {
                        val obj = JSONObject(String(region.metadata, Charsets.UTF_8))
                        if (obj.optString("kind") != "towerradar-offline-1") return@mapNotNull null
                        Area(region, obj.getString("name"),
                            OfflineBounds(obj.getDouble("south"), obj.getDouble("west"),
                                obj.getDouble("north"), obj.getDouble("east")),
                            obj.getInt("radius"), obj.getInt("zoom"))
                    } catch (_: Exception) { null }
                }
                done(parsed, null)
            }
            override fun onError(error: String) { done(emptyList(), error) }
        })
    }

    fun create(name: String, bounds: OfflineBounds, radiusKm: Int, maxZoom: Int,
               update: (State) -> Unit) {
        if (!OfflineAreaPolicy.allowed(bounds, maxZoom)) {
            update(State("Превышен лимит тайлов. Уменьшите область или масштаб.", false))
            return
        }
        val meta = JSONObject().put("kind", "towerradar-offline-1")
            .put("name", name).put("south", bounds.south).put("north", bounds.north)
            .put("west", bounds.west).put("east", bounds.east)
            .put("radius", radiusKm).put("zoom", maxZoom)
            .toString().toByteArray(Charsets.UTF_8)
        val boundsMap = LatLngBounds.Builder()
            .include(LatLng(bounds.south, bounds.west))
            .include(LatLng(bounds.north, bounds.east)).build()
        val definition = OfflineTilePyramidRegionDefinition(
            OfflineAreaPolicy.OFFLINE_STYLE, boundsMap,
            OfflineAreaPolicy.MIN_ZOOM.toDouble(), maxZoom.toDouble(), 1.0f)
        mapManager.createOfflineRegion(definition, meta,
            object : OfflineManager.CreateOfflineRegionCallback {
                override fun onCreate(offlineRegion: OfflineRegion) {
                    val area = Area(offlineRegion, name, bounds, radiusKm, maxZoom)
                    update(State("Область создана. Загружаем карту и высоты…", false))
                    resume(area, update)
                }
                override fun onError(error: String) {
                    update(State("Не удалось создать область: $error", false))
                }
            })
    }

    fun terrainReady(area: Area): Boolean = terrain.isComplete(area.region.id)

    fun status(area: Area, done: (String) -> Unit) {
        area.region.getStatus(object : OfflineRegion.OfflineRegionStatusCallback {
            override fun onStatus(status: OfflineRegionStatus?) {
                if (status == null) { done("Нет сведений о карте"); return }
                val result = if (status.isComplete) "Карта готова" else
                    "Карта: ${status.completedResourceCount}/${status.requiredResourceCount} ресурсов"
                done(result + " · " + if (terrainReady(area)) "рельеф готов" else "рельеф не завершён")
            }
            override fun onError(error: String?) { done(error ?: "Нет сведений о карте") }
        })
    }

    fun resume(area: Area, update: (State) -> Unit) {
        val region = area.region
        terrain.download(region.id, area.bounds, area.radiusKm) { progress ->
            activity.runOnUiThread {
                if (!activity.isDestroyed && !activity.isFinishing) {
                    if (progress.complete) update(State("Рельеф сохранён: ${progress.total} тайлов", false))
                    else if (progress.error != null) update(State("Рельеф: ${progress.error}", false))
                }
            }
        }
        region.setDeliverInactiveMessages(true)
        region.setObserver(object : OfflineRegion.OfflineRegionObserver {
            override fun onStatusChanged(status: OfflineRegionStatus) {
                if (status.completedResourceSize >= OfflineAreaPolicy.MAX_MAP_BYTES ||
                    status.completedTileCount >= OfflineAreaPolicy.MAX_TILES.toLong()) {
                    region.setDownloadState(OfflineRegion.STATE_INACTIVE)
                    update(State("Лимит загрузки карты достигнут. Область неполная.", false))
                    return
                }
                if (status.isComplete) {
                    region.setDownloadState(OfflineRegion.STATE_INACTIVE)
                    update(State("Карта загружена полностью на масштабах 6–${area.maxZoom}." +
                        if (terrainReady(area)) " Рельеф готов." else " Рельеф загружается.", true))
                } else if (status.isRequiredResourceCountPrecise &&
                    status.requiredResourceCount > 0L) {
                    val pct = status.completedResourceCount * 100 / status.requiredResourceCount
                    update(State("Карта: $pct% · ${status.completedTileCount} тайлов", false))
                }
            }
            override fun onError(error: OfflineRegionError) {
                update(State("Ошибка карты: ${error.message.take(120)}", false))
            }
            override fun mapboxTileCountLimitExceeded(limit: Long) {
                region.setDownloadState(OfflineRegion.STATE_INACTIVE)
                update(State("Лимит MapLibre: $limit тайлов", false))
            }
        })
        region.setDownloadState(OfflineRegion.STATE_ACTIVE)
    }

    fun pause(area: Area) {
        terrain.stop(area.region.id)
        area.region.setDownloadState(OfflineRegion.STATE_INACTIVE)
    }

    fun delete(area: Area, done: (Boolean, String) -> Unit) {
        pause(area)
        area.region.delete(object : OfflineRegion.OfflineRegionDeleteCallback {
            override fun onDelete() {
                terrain.remove(area.region.id)
                done(true, "Область удалена")
            }
            override fun onError(error: String) { done(false, error) }
        })
    }
}
