package com.russoturissto.towerradar

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.*

/**
 * Actual Mapzen Terrarium elevation pixels on disk, independent of the map tile cache.
 * Missing pixels are never invented. A partial pack is resumable, not called complete.
 */
class OfflineTerrainStore(private val context: Context) {
    private val root = File(context.filesDir, "offline-terrain")
    private val executor = Executors.newSingleThreadExecutor()
    private val cancelled = ConcurrentHashMap.newKeySet<Long>()

    data class Progress(val downloaded: Int, val total: Int, val complete: Boolean,
                        val error: String? = null)

    private fun directory(id: Long) = File(root, id.toString())
    private fun terrainZoom(radiusKm: Int) = if (radiusKm <= 5) 12 else 11

    fun isComplete(id: Long): Boolean {
        return try {
        val dir = directory(id)
        val obj = JSONObject(File(dir, "manifest.json").readText())
        if (!obj.optBoolean("complete")) return false
        val z = obj.getInt("z")
        val bounds = OfflineBounds(obj.getDouble("south"), obj.getDouble("west"),
            obj.getDouble("north"), obj.getDouble("east"))
        val x0 = OfflineAreaPolicy.tileX(bounds.west, z)
        val x1 = OfflineAreaPolicy.tileX(bounds.east, z)
        val y0 = OfflineAreaPolicy.tileY(bounds.north, z)
        val y1 = OfflineAreaPolicy.tileY(bounds.south, z)
        val count = (x1 - x0 + 1) * (y1 - y0 + 1)
        count in 1..200 && count == obj.optInt("total") &&
            (x0..x1).all { x -> (y0..y1).all { y ->
                File(dir, "$z/$x/$y.png").let { it.isFile && it.length() >= 100 }
            } }
        } catch (_: Exception) { false }
    }

    fun bytes(id: Long): Long = directory(id).takeIf { it.isDirectory }
        ?.walkTopDown()?.filter(File::isFile)?.sumOf { it.length() } ?: 0L

    fun stop(id: Long) { cancelled.add(id) }
    fun remove(id: Long) {
        stop(id)
        executor.execute { directory(id).deleteRecursively() }
    }

    fun download(id: Long, bounds: OfflineBounds, radiusKm: Int,
                 report: (Progress) -> Unit) {
        cancelled.remove(id)
        executor.execute {
            val z = terrainZoom(radiusKm)
            val x0 = OfflineAreaPolicy.tileX(bounds.west, z)
            val x1 = OfflineAreaPolicy.tileX(bounds.east, z)
            val y0 = OfflineAreaPolicy.tileY(bounds.north, z)
            val y1 = OfflineAreaPolicy.tileY(bounds.south, z)
            val total = (x1 - x0 + 1) * (y1 - y0 + 1)
            if (total !in 1..200) {
                report(Progress(0, total, false, "Район слишком велик для рельефа"))
                return@execute
            }
            val region = directory(id)
            region.mkdirs()
            val manifest = File(region, "manifest.json")
            fun writeManifest(done: Boolean) {
                val obj = JSONObject().put("z", z)
                    .put("south", bounds.south).put("west", bounds.west)
                    .put("north", bounds.north).put("east", bounds.east)
                    .put("total", total).put("complete", done)
                val temp = File(region, "manifest.part")
                temp.writeText(obj.toString())
                if (!temp.renameTo(manifest)) { manifest.writeText(obj.toString()); temp.delete() }
            }
            writeManifest(false)
            var finished = 0
            try {
                for (x in x0..x1) for (y in y0..y1) {
                    if (cancelled.contains(id)) {
                        report(Progress(finished, total, false, "Загрузка приостановлена"))
                        return@execute
                    }
                    val target = File(region, "$z/$x/$y.png")
                    if (!target.isFile || target.length() < 100) {
                        target.parentFile?.mkdirs()
                        val url = URL("https://s3.amazonaws.com/elevation-tiles-prod/terrarium/$z/$x/$y.png")
                        val conn = url.openConnection() as HttpURLConnection
                        conn.connectTimeout = 9000; conn.readTimeout = 20000
                        conn.setRequestProperty("User-Agent", "TowerRadar/0.26.0 (+https://github.com/RussoTurissto/TowerRadar)")
                        try {
                            if (conn.responseCode != 200) error("Рельеф: HTTP ${conn.responseCode}")
                            if (!conn.contentType.orEmpty().contains("image"))
                                error("Сервис рельефа вернул не изображение")
                            val output = ByteArrayOutputStream()
                            conn.inputStream.use { input ->
                                val buf = ByteArray(8192)
                                while (true) {
                                    val n = input.read(buf)
                                    if (n < 0) break
                                    if (output.size() + n > 3_000_000) error("Тайл рельефа слишком велик")
                                    output.write(buf, 0, n)
                                }
                            }
                            val bytes = output.toByteArray()
                            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                                ?: error("Повреждённый тайл рельефа")
                            if (bitmap.width != 256 || bitmap.height != 256) error("Неверный размер тайла")
                            bitmap.recycle()
                            val temp = File(target.path + ".part")
                            temp.writeBytes(bytes)
                            if (!temp.renameTo(target)) error("Не удалось сохранить тайл")
                        } finally { conn.disconnect() }
                    }
                    finished++
                    report(Progress(finished, total, false))
                }
                writeManifest(true)
                report(Progress(total, total, true))
            } catch (e: Exception) {
                report(Progress(finished, total, false,
                    e.localizedMessage?.take(130) ?: "Не удалось загрузить рельеф"))
            }
        }
    }

    /** Returns a local profile only when every interpolation point has a real DEM pixel. */
    fun profile(aLat: Double, aLon: Double, bLat: Double, bLon: Double): TerrainProfile? {
        val points = TerrainProfileClient.coordinates(aLat, aLon, bLat, bLon, 65)
        val dirs = root.listFiles()?.filter(File::isDirectory).orEmpty()
        for (region in dirs) {
            val manifest = try { JSONObject(File(region, "manifest.json").readText()) }
                catch (_: Exception) { continue }
            if (!manifest.optBoolean("complete")) continue
            val bounds = OfflineBounds(manifest.optDouble("south"), manifest.optDouble("west"),
                manifest.optDouble("north"), manifest.optDouble("east"))
            if (points.any { !bounds.contains(it.first, it.second) }) continue
            val z = manifest.optInt("z")
            if (z !in 0..15) continue
            val bitmaps = HashMap<String, Bitmap>()
            try {
                val samples = points.map { (lat, lon) ->
                    val n = (1 shl z).toDouble()
                    val gx = (lon + 180.0) / 360.0 * n
                    val r = Math.toRadians(lat.coerceIn(-85.05112878, 85.05112878))
                    val gy = (1 - ln(tan(r) + 1 / cos(r)) / PI) / 2 * n
                    val x = floor(gx).toInt(); val y = floor(gy).toInt()
                    val key = "$z/$x/$y.png"
                    val bitmap = bitmaps[key] ?: BitmapFactory.decodeFile(File(region, key).path)
                        ?.also { bitmaps[key] = it } ?: error("Нет тайла $key")
                    val px = floor((gx - x) * bitmap.width).toInt().coerceIn(0, bitmap.width - 1)
                    val py = floor((gy - y) * bitmap.height).toInt().coerceIn(0, bitmap.height - 1)
                    val color = bitmap.getPixel(px, py)
                    val elevation = TerrariumEncoding.elevation(color)
                        ?: error("Данные высот отсутствуют в тайле $key")
                    TerrainSample(lat, lon, elevation)
                }
                return TerrainProfile(samples, TowerData.distanceKm(aLat, aLon, bLat, bLon),
                    "Mapzen Terrarium · офлайн")
            } catch (_: Exception) {
                // Not enough tiles in this region, try another complete saved area.
            } finally {
                bitmaps.values.forEach { it.recycle() }
            }
        }
        return null
    }
}
