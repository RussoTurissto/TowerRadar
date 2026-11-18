package com.russoturissto.towerradar

import android.content.Context
import android.net.Uri
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.RasterLayer
import org.maplibre.android.style.sources.RasterSource
import org.maplibre.android.style.sources.TileSet
import org.mapsforge.core.model.Tile
import org.mapsforge.map.android.graphics.AndroidGraphicFactory
import org.mapsforge.map.layer.cache.InMemoryTileCache
import org.mapsforge.map.layer.labels.CachedMapDataStoreLabelStore
import org.mapsforge.map.layer.renderer.DatabaseRenderer
import org.mapsforge.map.layer.renderer.RendererJob
import org.mapsforge.map.model.DisplayModel
import org.mapsforge.map.reader.MapFile
import org.mapsforge.map.rendertheme.internal.MapsforgeThemes
import org.mapsforge.map.rendertheme.rule.RenderThemeFuture
import java.io.BufferedReader
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.FileInputStream
import java.io.InputStreamReader
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Mapsforge V5 rendered into loopback raster tiles for MapLibre.
 * The map stays in Android's document provider: it is never copied.
 */
internal class LocalMapsforgeTiles private constructor(
    val uri: Uri,
    private val descriptor: android.os.ParcelFileDescriptor,
    private val input: FileInputStream,
    private val mapFile: MapFile
) : Closeable {
    companion object {
        private const val TILE_SIZE = 256
        private val tilePath = Regex("^/(\\d{1,2})/(\\d{1,8})/(\\d{1,8})\\.png(?:\\?.*)?$")

        fun open(context: Context, uri: Uri): LocalMapsforgeTiles {
            AndroidGraphicFactory.createInstance(context.applicationContext)
            val descriptor = context.contentResolver.openFileDescriptor(uri, "r")
                ?: error("Файловый менеджер не открыл карту")
            val input = FileInputStream(descriptor.fileDescriptor)
            var mapFile: MapFile? = null
            try {
                mapFile = MapFile(input, "ru")
                return LocalMapsforgeTiles(uri, descriptor, input, mapFile)
            } catch (e: Exception) {
                runCatching { mapFile?.close() }
                runCatching { input.close() }
                runCatching { descriptor.close() }
                throw e
            }
        }
    }

    private val closed = AtomicBoolean(false)
    private val renderLock = Any()
    // MapLibre serves fixed 256-pixel raster tiles. Mapsforge's default Android
    // display density would magnify fonts and symbols several times over.
    private val display = DisplayModel().apply {
        setFixedTileSize(TILE_SIZE)
        setUserScaleFactor(MapsforgeRenderPolicy.userScaleForDevice(
            DisplayModel.getDeviceScaleFactor()))
    }
    private val tileCache = InMemoryTileCache(128)
    private val theme = RenderThemeFuture(
        AndroidGraphicFactory.INSTANCE, MapsforgeThemes.DEFAULT, display
    ).apply { run() }
    // Read labels in a 3x3 tile neighborhood and resolve collisions
    // deterministically. This renders both halves of labels crossing tile edges.
    // The old null label store made every tile choose and clip labels independently.
    private val labelStore = CachedMapDataStoreLabelStore(
        mapFile, theme, 1f, display, AndroidGraphicFactory.INSTANCE
    )
    private val renderer = DatabaseRenderer(
        mapFile, AndroidGraphicFactory.INSTANCE, tileCache, labelStore, true, false, null
    )
    private val server = ServerSocket(0, 16, InetAddress.getByName("127.0.0.1"))
    private val workers = Executors.newFixedThreadPool(2)
    private val listener = Thread({
        while (!closed.get()) {
            try {
                val client = server.accept()
                workers.execute { serve(client) }
            } catch (_: SocketException) {
                if (closed.get()) break
            } catch (_: Exception) {
                if (closed.get()) break
            }
        }
    }, "TowerRadar-LocalMapsforge").apply { isDaemon = true; start() }

    fun style(): Style.Builder {
        val tiles = TileSet("2.2.0",
            "http://127.0.0.1:" + server.localPort + "/{z}/{x}/{y}.png").apply {
            setMinZoom(0f)
            setMaxZoom(20f)
        }
        return Style.Builder()
            .withSource(RasterSource("basemap-source", tiles, TILE_SIZE))
            .withLayer(RasterLayer("basemap-layer", "basemap-source"))
    }

    private fun serve(client: Socket) {
        client.use { connection ->
            try {
                connection.soTimeout = 15_000
                val line = BufferedReader(InputStreamReader(connection.getInputStream())).readLine()
                val path = line?.takeIf { it.startsWith("GET ") }?.split(' ')?.getOrNull(1)
                val match = path?.let { tilePath.matchEntire(it) }
                if (match == null) {
                    respond(connection, 400, null)
                    return
                }
                val zoom = match.groupValues[1].toIntOrNull()
                val x = match.groupValues[2].toIntOrNull()
                val y = match.groupValues[3].toIntOrNull()
                val limit = if (zoom != null && zoom in 0..20) 1 shl zoom else 0
                if (x == null || y == null || x !in 0 until limit || y !in 0 until limit) {
                    respond(connection, 404, null)
                    return
                }
                val tile = Tile(x, y, zoom!!.toByte(), TILE_SIZE)
                if (!mapFile.supportsTile(tile)) {
                    respond(connection, 204, null)
                    return
                }
                val bytes = synchronized(renderLock) {
                    if (closed.get()) null else {
                        val job = RendererJob(tile, mapFile, theme, display, 1f, false, false)
                        val bitmap = renderer.executeJob(job)
                        if (bitmap == null) null else try {
                            ByteArrayOutputStream(32_768).use {
                                bitmap.compress(it)
                                it.toByteArray()
                            }
                        } finally {
                            bitmap.decrementRefCount()
                        }
                    }
                }
                respond(connection, if (bytes == null) 204 else 200, bytes)
            } catch (_: Exception) {
                runCatching { respond(connection, 500, null) }
            }
        }
    }

    private fun respond(connection: Socket, code: Int, bytes: ByteArray?) {
        val reason = when (code) {
            200 -> "OK"
            204 -> "No Content"
            400 -> "Bad Request"
            404 -> "Not Found"
            else -> "Internal Server Error"
        }
        val output = connection.getOutputStream()
        val header = "HTTP/1.1 " + code + " " + reason + "\r\n" +
            "Content-Length: " + (bytes?.size ?: 0) + "\r\n" +
            (if (bytes != null) "Content-Type: image/png\r\n" else "") +
            "Cache-Control: no-store\r\nConnection: close\r\n\r\n"
        output.write(header.toByteArray(Charsets.US_ASCII))
        if (bytes != null) output.write(bytes)
        output.flush()
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        runCatching { server.close() }
        workers.shutdownNow()
        synchronized(renderLock) {
            runCatching { labelStore.clear() }
            runCatching { tileCache.destroy() }
            runCatching { mapFile.close() }
            runCatching { input.close() }
            runCatching { descriptor.close() }
        }
    }
}
