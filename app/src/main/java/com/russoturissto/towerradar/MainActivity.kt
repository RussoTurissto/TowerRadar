package com.russoturissto.towerradar

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.animation.ValueAnimator
import android.content.ClipboardManager
import android.view.MotionEvent
import android.view.WindowInsets
import kotlin.math.pow
import android.widget.ScrollView
import android.widget.Toast
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.content.Context
import android.content.res.Configuration
import android.content.res.ColorStateList
import android.os.Build
import android.telephony.SubscriptionManager
import android.os.SystemClock
import android.text.InputType
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.drawable.GradientDrawable
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.hardware.GeomagneticField
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.SeekBar
import android.widget.TextView
import android.widget.AdapterView
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.RasterLayer
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point
import org.maplibre.geojson.Polygon
import org.maplibre.geojson.LineString
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.Locale
import org.maplibre.android.module.http.HttpRequestUtil
import okhttp3.OkHttpClient
import java.util.concurrent.Executors
import kotlin.math.hypot
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.sqrt
import kotlin.math.PI

/** No background tracking. A search shares the selected coordinates with public Overpass. */
class MainActivity : Activity(), SensorEventListener {
    private val executor = Executors.newSingleThreadExecutor()
    private lateinit var topHeader: LinearLayout
    private lateinit var crosshairReticle: CrosshairView
    private lateinit var crosshairControls: LinearLayout
    private lateinit var crosshairCoordinates: TextView
    private var isChoosingCrosshair = false
    private var crosshairStart: LatLng? = null
    private var crosshairStartZoom = 12.5
    private var systemTopInset = 0
    private var systemBottomInset = 0
    private var beamBrightness = 85
    private var sectorBrightness = 35
    private val apiChecks = mutableMapOf<String, ApiProbe>()
    private val checkingApi = mutableSetOf<String>()
    private val googleSessions = mutableMapOf<String, GoogleTileSession>()
    private var googleMapSession: GoogleTileSession? = null
    private var lastGoogleAttributionAt = 0L
    private var lastGoogleAttributionKey = ""
    private lateinit var rootView: FrameLayout
    private lateinit var sheetView: LinearLayout
    private lateinit var sheetExtra: ScrollView
    private lateinit var sheetHandle: TextView
    private lateinit var floatingRail: LinearLayout
    private lateinit var fullScreenButton: Button
    private var sheetExpanded = false
    private var mapFullscreen = false
    private lateinit var mapView: MapView
    private lateinit var locationFusion: LocationFusion
    private val cellExecutor = Executors.newSingleThreadExecutor()
    private lateinit var gpsLabel: TextView
    private lateinit var cellMonitor: CellMonitor
    private var servingCell: ServingCell? = null
    private var servingSite: TowerSite? = null
    private var requestedCellId: String? = null
    private var cellRequestAt = 0L
    private var manualPoint: LatLng? = null
    private lateinit var palette: KhakiPalette
    private lateinit var settingsButton: Button
    private lateinit var signalLabel: TextView
    private lateinit var signalDetails: TextView
    private lateinit var cellStatus: TextView
    private lateinit var signalBars: LinearLayout
    private lateinit var gpsRefresh: Button
    private var showCone = true

    private lateinit var locationManager: LocationManager
    private lateinit var status: TextView
    private lateinit var radius: Spinner
    private lateinit var operators: Spinner
    private lateinit var refresh: Button
    private lateinit var locate: Button
    private lateinit var searchHere: Button
    private lateinit var sourceSpinner: Spinner
    private lateinit var mapAttribution: TextView
    private lateinit var brandLink: TextView
    private lateinit var keyButton: Button
    private lateinit var headingView: TextView
    private lateinit var mapStatus: TextView
    private lateinit var sensorManager: SensorManager
    private var headingSensor: Sensor? = null
    private var headingDegrees = 0f
    private var providerIndex = 0
    private var choosingMap = false
    private lateinit var details: LinearLayout
    private lateinit var detailTitle: TextView
    private lateinit var detailBody: TextView

    private var map: MapLibreMap? = null
    private var styled = false
    private var location: Location? = null
    private var center: LatLng? = null
    private var manualCenter = false
    private var initiallyCentered = false
    private var listening = false
    private var busy = false
    private var pendingManualSearch = false
    private var selectedOperator: String? = null
    private var radiusKm = 5
    private var lastFetch: LatLng? = null
    private var lastFetchRadius = 0
    private var lastFetchTime = 0L
    private var sites = emptyList<TowerSite>()
    private var visible = emptyList<TowerSite>()
    private val unknown = "• Не указан"

    private val listener = object : LocationListener {
        override fun onLocationChanged(newLocation: Location) = showLocation(newLocation)
        override fun onProviderEnabled(provider: String) = Unit
        override fun onProviderDisabled(provider: String) = Unit
        @Deprecated("Deprecated by Android")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
    }


    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_ROTATION_VECTOR) return
        val rotation = FloatArray(9)
        val orientation = FloatArray(3)
        SensorManager.getRotationMatrixFromVector(rotation, event.values)
        SensorManager.getOrientation(rotation, orientation)
        val point = location
        val declination = if (point == null) 0f else GeomagneticField(
            point.latitude.toFloat(), point.longitude.toFloat(),
            point.altitude.toFloat(), System.currentTimeMillis()).declination
        val degrees = ((Math.toDegrees(orientation[0].toDouble()).toFloat() + declination) % 360f + 360f) % 360f
        val diff = kotlin.math.abs((degrees - headingDegrees + 540f) % 360f - 180f)
        if (diff < 4f && headingView.text.toString() != "➤ —°") return
        headingDegrees = degrees
        if (::headingView.isInitialized) {
            val directions = arrayOf("С", "СВ", "В", "ЮВ", "Ю", "ЮЗ", "З", "СЗ")
            val direction = directions[((degrees + 22.5f).toInt() / 45) % 8]
            headingView.text = "➤ " + degrees.toInt() + "° " + direction
        }
        updateOverlays()
        (map?.style?.getLayer("phone-heading") as? SymbolLayer)
            ?.setProperties(PropertyFactory.iconRotate(degrees))
    }

    private fun headingIcon(): Bitmap {
        val image = Bitmap.createBitmap(72, 72, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(image)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(0, 170, 199)
            style = Paint.Style.FILL
        }
        val triangle = Path().apply {
            moveTo(36f, 4f)
            lineTo(12f, 60f)
            lineTo(36f, 48f)
            lineTo(60f, 60f)
            close()
        }
        canvas.drawPath(triangle, paint)
        paint.color = Color.WHITE
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 3f
        canvas.drawPath(triangle, paint)
        return image
    }

    private fun applyBasemap(provider: MapProvider, key: String = "",
                             session: GoogleTileSession? = null) {
        val current = map ?: return
        if (provider.id.startsWith("google-") && (session == null || !session.valid())) {
            mapStatus.text = "Google · требуется действующий сеанс Map Tiles API"
            return
        }
        googleMapSession = if (provider.id.startsWith("google-")) session else null
        lastGoogleAttributionAt = 0L
        styled = false
        providerIndex = MapSources.providers.indexOf(provider).coerceAtLeast(0)
        if (::mapStatus.isInitialized) mapStatus.text = "Подложка: " + provider.name
        if (::mapAttribution.isInitialized) mapAttribution.text =
            if (provider.id.startsWith("google-"))
                "Google Maps · данные об авторских правах загружаются"
            else provider.attribution
        if (::brandLink.isInitialized) {
            brandLink.visibility = if (provider.id == "yandex" ||
                provider.id.startsWith("google-")) View.VISIBLE else View.GONE
            brandLink.text = if (provider.id.startsWith("google-"))
                "Google Maps" else "Яндекс Карты"
            brandLink.contentDescription = brandLink.text
            brandLink.setOnClickListener {
                val address = if (provider.id.startsWith("google-"))
                    "https://www.google.com/maps" else "https://yandex.ru/maps/"
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(address)))
            }
        }
        current.setStyle(MapSources.style(provider, key, session?.token.orEmpty())) { style ->
            if (palette.dark && provider.keyPref == null) {
                (style.getLayer("basemap-layer") as? RasterLayer)?.setProperties(
                    PropertyFactory.rasterBrightnessMax(0.66f),
                    PropertyFactory.rasterSaturation(-0.48f),
                    PropertyFactory.rasterContrast(0.1f)
                )
            }
            style.addSource(GeoJsonSource("towers", emptyFeatures()))
            style.addSource(GeoJsonSource("my-location", emptyFeatures()))
            style.addSource(GeoJsonSource("gps-accuracy", emptyFeatures()))
            style.addSource(GeoJsonSource("heading-cone", emptyFeatures()))
            style.addSource(GeoJsonSource("heading-border", emptyFeatures()))
            style.addSource(GeoJsonSource("heading-ray", emptyFeatures()))
            style.addSource(GeoJsonSource("cell-link", emptyFeatures()))
            style.addSource(GeoJsonSource("serving-cell", emptyFeatures()))

            style.addLayer(FillLayer("gps-accuracy-fill", "gps-accuracy").apply {
                setProperties(PropertyFactory.fillColor(palette.olive), PropertyFactory.fillOpacity(0.10f))
            })
            style.addLayer(FillLayer("heading-cone-fill", "heading-cone").apply {
                setProperties(
                    PropertyFactory.fillColor(Color.rgb(0, 179, 205)),
                    PropertyFactory.fillOpacity(sectorAlpha())
                )
            })
            style.addLayer(LineLayer("heading-border-halo", "heading-border").apply {
                setProperties(PropertyFactory.lineColor(Color.WHITE),
                    PropertyFactory.lineOpacity(0.92f),
                    PropertyFactory.lineWidth(6f))
            })
            style.addLayer(LineLayer("heading-border-color", "heading-border").apply {
                setProperties(PropertyFactory.lineColor(Color.rgb(0, 99, 148)),
                    PropertyFactory.lineWidth(2.8f))
            })
            style.addLayer(LineLayer("heading-center-halo", "heading-ray").apply {
                setProperties(PropertyFactory.lineColor(Color.WHITE),
                    PropertyFactory.lineWidth(7.0f))
            })
            style.addLayer(LineLayer("heading-center-color", "heading-ray").apply {
                setProperties(PropertyFactory.lineColor(Color.rgb(0, 130, 204)),
                    PropertyFactory.lineWidth(3.8f))
            })
            style.addLayer(LineLayer("serving-line", "cell-link").apply {
                setProperties(PropertyFactory.lineColor(Color.rgb(215, 141, 45)),
                    PropertyFactory.lineWidth(3.3f),
                    PropertyFactory.lineDasharray(arrayOf(2.4f, 2.0f)))
            })
            style.addLayer(CircleLayer("serving-cell-dot", "serving-cell").apply {
                setProperties(PropertyFactory.circleColor(Color.rgb(215, 141, 45)),
                    PropertyFactory.circleRadius(10f), PropertyFactory.circleStrokeColor(Color.WHITE),
                    PropertyFactory.circleStrokeWidth(3f))
            })

            style.addLayer(CircleLayer("potential-masts", "towers").apply {
                setFilter(Expression.eq(Expression.get("mobile"), Expression.literal(false)))
                setProperties(
                    PropertyFactory.circleColor(Color.rgb(245, 158, 11)),
                    PropertyFactory.circleRadius(6f),
                    PropertyFactory.circleStrokeColor(Color.WHITE),
                    PropertyFactory.circleStrokeWidth(2f)
                )
            })
            style.addLayer(CircleLayer("mobile-masts", "towers").apply {
                setFilter(Expression.eq(Expression.get("mobile"), Expression.literal(true)))
                setProperties(
                    PropertyFactory.circleColor(palette.olive),
                    PropertyFactory.circleRadius(7f),
                    PropertyFactory.circleStrokeColor(Color.WHITE),
                    PropertyFactory.circleStrokeWidth(2f)
                )
            })
            style.addLayer(CircleLayer("location-halo", "my-location").apply {
                setProperties(
                    PropertyFactory.circleColor(palette.olive),
                    PropertyFactory.circleRadius(16f),
                    PropertyFactory.circleOpacity(0.35f)
                )
            })
            style.addLayer(CircleLayer("location-dot", "my-location").apply {
                setProperties(
                    PropertyFactory.circleColor(palette.olive),
                    PropertyFactory.circleRadius(6f),
                    PropertyFactory.circleStrokeColor(Color.WHITE),
                    PropertyFactory.circleStrokeWidth(3f)
                )
            })

            style.addImage("phone-direction", headingIcon())
            style.addLayer(SymbolLayer("phone-heading", "my-location").apply {
                setProperties(
                    PropertyFactory.iconImage("phone-direction"),
                    PropertyFactory.iconRotate(headingDegrees),
                    PropertyFactory.iconSize(0.62f),
                    PropertyFactory.iconAllowOverlap(true),
                    PropertyFactory.iconIgnorePlacement(true),
                    PropertyFactory.iconRotationAlignment("map")
                )
            })
            styled = true
            updateDot()
            showPins()
            updateOverlays()
            if (!initiallyCentered) center?.let(::moveCamera)
            if (provider.id.startsWith("google-")) requestGoogleAttribution(force = true)
        }
    }


    /** Select a reachable raster tile host using a single low-zoom tile request. */
    private fun chooseBasemap() {
        if (choosingMap) return
        choosingMap = true
        val point = location?.let { LatLng(it.latitude, it.longitude) }
            ?: center ?: LatLng(49.0, 39.0)
        executor.execute {
            val selected = MapSources.unkeyed().indexOfFirst {
                MapSources.reachable(it, point.latitude, point.longitude)
            }
            runOnUiThread {
                choosingMap = false
                if (isFinishing || isDestroyed) return@runOnUiThread
                if (selected >= 0) {
                    if (selected != providerIndex) applyBasemap(MapSources.providers[selected])
                    mapStatus.text = "Подложка: " + MapSources.providers[selected].attribution
                } else mapStatus.text = "OSM недоступен. Выберите другой источник в списке."
            }
        }
    }


    private fun useMapChoice(provider: MapProvider) {
        val key = provider.keyPref?.let {
            getPreferences(Context.MODE_PRIVATE).getString(it, "").orEmpty()
        }.orEmpty()
        if (provider.keyPref != null && key.isBlank()) {
            if (provider.id == "yandex") showYandexSetup()
            else if (provider.id.startsWith("google-")) showGoogleSetup()
            else requestMapKey(provider)
            return
        }
        if (choosingMap) {
            mapStatus.text = "Сервер карты ещё проверяется."
            return
        }
        choosingMap = true
        mapStatus.text = "Проверяем " + provider.name + "…"
        val point = activeUserPoint() ?: center ?: LatLng(49.0, 39.0)
        executor.execute {
            val test = ApiConnections.checkMap(provider, point.latitude, point.longitude, key)
            runOnUiThread {
                choosingMap = false
                if (isFinishing || isDestroyed) return@runOnUiThread
                val pref = provider.keyPref
                if (pref != null &&
                    getPreferences(Context.MODE_PRIVATE).getString(pref, "") == key) {
                    apiChecks[pref] = test
                    checkingApi.remove(pref)
                }
                if (test.connected) {
                    getPreferences(Context.MODE_PRIVATE).edit()
                        .putString("map_provider", provider.id).apply()
                    test.session?.let { googleSessions[provider.id] = it }
                    applyBasemap(provider, key, test.session)
                    mapStatus.text = "✓ " + provider.name + " · подключено"
                } else {
                    mapStatus.text = test.message + " · переключаемся на OSM"
                    getPreferences(Context.MODE_PRIVATE).edit()
                        .putString("map_provider", "auto").apply()
                    chooseBasemap()
                }
            }
        }
    }

    /** Google source attributions are viewport-dependent and must stay visible. */
    private fun requestGoogleAttribution(force: Boolean = false) {
        val current = map ?: return
        if (!MapSources.providers.getOrNull(providerIndex)?.id.orEmpty()
                .startsWith("google-")) return
        val session = googleMapSession?.takeIf { it.valid() } ?: return
        if (!force && System.currentTimeMillis() - lastGoogleAttributionAt < 15000L) return
        val key = getPreferences(Context.MODE_PRIVATE)
            .getString("map_key_google", "").orEmpty()
        if (key.isBlank()) return
        lastGoogleAttributionAt = System.currentTimeMillis()
        val bounds = current.projection.visibleRegion.latLngBounds
        val north = bounds.northEast.latitude
        val south = bounds.southWest.latitude
        val east = bounds.northEast.longitude
        val west = bounds.southWest.longitude
        val zoom = current.cameraPosition.zoom.toInt()
        val providerId = MapSources.providers[providerIndex].id
        executor.execute {
            val copyright = ApiConnections.googleCopyright(key, session, zoom,
                north, south, east, west)
            runOnUiThread {
                if (!isDestroyed && !isFinishing &&
                    MapSources.providers.getOrNull(providerIndex)?.id == providerId &&
                    googleMapSession?.token == session.token) {
                    if (copyright != null) {
                        lastGoogleAttributionKey = copyright
                        mapAttribution.text = "Google Maps · " + copyright +
                            "\nВышки: © OpenStreetMap contributors"
                    } else if (lastGoogleAttributionKey.isNotBlank()) {
                        mapAttribution.text = "Google Maps · " + lastGoogleAttributionKey +
                            "\nВышки: © OpenStreetMap contributors"
                    } else {
                        mapAttribution.text = "Google Maps · данные об авторских правах недоступны"
                    }
                }
            }
        }
    }

    /**
     * Login is opened in the official browser. Yandex ID itself cannot issue a Tiles API key:
     * the user must activate Tiles API in the developer dashboard. We never collect passwords
     * or attempt to scrape Yandex's dashboard. Clipboard is read only on explicit tap.
     */
    private fun showYandexSetup() {
        KhakiDialogBuilder(this, palette)
            .setTitle("Яндекс Карты · подключение")
            .setMessage(
                "1. Откройте официальный кабинет Яндекса и войдите через Яндекс ID.\n" +
                "2. Подключите продукт «Tiles API» и выберите тариф. " +
                "После активации ключ появится в разделе «Ключи».\n" +
                "3. Скопируйте API-ключ, вернитесь в TowerRadar и нажмите «Вставить ключ».\n\n" +
                "Вход в Яндекс ID сам по себе не выдаёт ключ карт. " +
                "TowerRadar не получает пароль и не создаёт ключи в обход правил Яндекса."
            )
            .setPositiveButton("Войти через Яндекс ID") { _, _ ->
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://developer.tech.yandex.ru/"))
                try {
                    startActivity(intent)
                    getPreferences(Context.MODE_PRIVATE).edit()
                        .putBoolean("yandex_return_hint", true).apply()
                } catch (_: Exception) {
                    Toast.makeText(this, "Нет приложения для открытия браузера", Toast.LENGTH_LONG).show()
                }
            }
            .setNeutralButton("Вставить ключ") { _, _ -> importYandexKeyFromClipboard() }
            .setNegativeButton("Ввести вручную") { _, _ ->
                requestMapKey(MapSources.providers.first { it.id == "yandex" })
            }
            .show()
    }

    private fun importYandexKeyFromClipboard() {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val key = try {
            clipboard.primaryClip?.getItemAt(0)?.coerceToText(this)?.toString()?.trim().orEmpty()
        } catch (_: Exception) { "" }
        if (!key.matches(Regex("[a-zA-Z0-9._-]{8,256}"))) {
            KhakiDialogBuilder(this, palette).setTitle("Ключ не найден")
                .setMessage("Скопируйте ключ продукта Tiles API из кабинета Яндекса. " +
                    "Затем вернитесь в TowerRadar и нажмите «Вставить ключ».")
                .setPositiveButton("Повторить") { _, _ -> showYandexSetup() }
                .setNegativeButton("Отмена", null).show()
            return
        }
        val provider = MapSources.providers.first { it.id == "yandex" }
        getPreferences(Context.MODE_PRIVATE).edit()
            .putString(provider.keyPref, key).apply()
        Toast.makeText(this, "Ключ сохранён. Проверяем Яндекс Карты…", Toast.LENGTH_SHORT).show()
        val index = MapSources.providers.indexOf(provider) + 1
        if (sourceSpinner.selectedItemPosition != index) {
            sourceSpinner.setSelection(index)
        } else {
            useMapChoice(provider)
        }
    }

    private fun requestMapKey(provider: MapProvider) {
        val pref = provider.keyPref ?: return
        val input = EditText(this).apply {
            hint = "API-ключ " + provider.name
            setSingleLine(true)
            setText(getPreferences(Context.MODE_PRIVATE).getString(pref, ""))
        }
        val address = if (provider.id == "yandex")
            "developer.tech.yandex.ru (Tiles API)" else "platform.2gis.ru (Raster Tiles API)"
        KhakiDialogBuilder(this, palette)
            .setTitle(provider.name)
            .setMessage(
                "Для этой карты с русскими подписями нужен личный ключ. " +
                    "Получите его на " + address + ". Доступ и лимиты определяет поставщик. " +
                    "Ключ хранится только на телефоне, без отправки в GitHub."
            )
            .setView(input)
            .setPositiveButton("Сохранить") { _, _ ->
                val key = input.text.toString().trim()
                if (key.isNotEmpty()) {
                    getPreferences(Context.MODE_PRIVATE).edit()
                        .putString(pref, key).apply()
                    useMapChoice(provider)
                } else {
                    mapStatus.text = "Введите ключ, чтобы включить " + provider.name
                }
            }
            .setNeutralButton("Удалить ключ") { _, _ ->
                getPreferences(Context.MODE_PRIVATE).edit().remove(pref).apply()
                mapStatus.text = "Ключ " + provider.name + " удалён."
            }
            .setNegativeButton("Отмена", null)
            .show()
    }


    private fun showSettings() {
        val items = arrayOf(
            "Тема оформления: " + when (getPreferences(Context.MODE_PRIVATE)
                .getString("app_theme", KhakiTheme.SYSTEM)) {
                    KhakiTheme.DAY -> "Дневная"
                    KhakiTheme.NIGHT -> "Ночная"
                    else -> "Системная"
                },
            "Геопозиция: " + if (manualPoint == null) "Автоматическая GPS" else "Ручная точка",
            "SIM для отслеживания",
            "Подключения API: Яндекс · 2ГИС · Google · OpenCellID",
            "Индикатор направления: " + if (showCone) "Включён" else "Выключен",
            "Яркость луча и сектора: раздельная настройка"
        )
        KhakiDialogBuilder(this, palette).setTitle("Настройки · TowerRadar")
            .setItems(items) { _, selected ->
                when (selected) {
                    0 -> selectTheme()
                    1 -> showPositionOptions()
                    2 -> selectSim()
                    3 -> showConnections()
                    4 -> {
                        showCone = !showCone
                        getPreferences(Context.MODE_PRIVATE).edit()
                            .putBoolean("show_cone", showCone).apply()
                        updateOverlays()
                    }
                    5 -> showDirectionBrightness()
                }
            }.setNegativeButton("Закрыть", null).show()
    }

    private fun sectorAlpha(): Float = 0.50f * sectorBrightness / 100f
    private fun beamAlpha(): Float = beamBrightness / 100f

    private fun updateDirectionStyle() {
        (map?.style?.getLayer("heading-cone-fill") as? FillLayer)?.setProperties(
            PropertyFactory.fillOpacity(sectorAlpha())
        )
        (map?.style?.getLayer("heading-near-fill") as? FillLayer)?.setProperties(
            PropertyFactory.fillOpacity(sectorAlpha() * .38f)
        )
        for (name in listOf("heading-border-halo", "heading-border-color")) {
            (map?.style?.getLayer(name) as? LineLayer)?.setProperties(
                PropertyFactory.lineOpacity(sectorAlpha() * if (name.endsWith("halo")) .2f else .85f)
            )
        }
        (map?.style?.getLayer("heading-center-halo") as? LineLayer)?.setProperties(
            PropertyFactory.lineOpacity(beamAlpha() * .24f)
        )
        (map?.style?.getLayer("heading-center-color") as? LineLayer)?.setProperties(
            PropertyFactory.lineOpacity(beamAlpha())
        )
    }

    private fun showDirectionBrightness() {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(4), dp(4), dp(4), dp(4))
        }
        val help = text("Бирюзовый пунктир — азимут телефона. Мягкий сектор — зона направления. " +
            "Каждый элемент можно сделать невидимым, установив 0%. " +
            "Кольцо позиции останется.", 13f, palette.secondary).apply {
            setPadding(dp(5), dp(5), dp(5), dp(12))
        }
        layout.addView(help)
        fun addSlider(title: String, current: Int, onChanged: (Int) -> Unit) {
            val header = row().apply { gravity = Gravity.CENTER_VERTICAL }
            val name = text(title, 14f, palette.text).apply {
                setTypeface(typeface, android.graphics.Typeface.BOLD)
            }
            header.addView(name, LinearLayout.LayoutParams(0, -2, 1f))
            val valueLabel = text("$current%", 14f, palette.olive)
            header.addView(valueLabel)
            layout.addView(header)
            val slider = SeekBar(this).apply {
                max = 100
                progress = current
                progressTintList = ColorStateList.valueOf(palette.olive)
                thumbTintList = ColorStateList.valueOf(palette.olive)
            }
            slider.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(view: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (!fromUser) return
                    valueLabel.text = "$progress%"
                    onChanged(progress)
                    updateDirectionStyle()
                }
                override fun onStartTrackingTouch(view: SeekBar?) = Unit
                override fun onStopTrackingTouch(view: SeekBar?) = Unit
            })
            layout.addView(slider, LinearLayout.LayoutParams(-1, dp(58)))
        }
        addSlider("Яркость пунктирного луча", beamBrightness) {
            beamBrightness = it
            getPreferences(Context.MODE_PRIVATE).edit().putInt("beam_brightness", it).apply()
        }
        addSlider("Яркость сектора", sectorBrightness) {
            sectorBrightness = it
            getPreferences(Context.MODE_PRIVATE).edit().putInt("sector_brightness", it).apply()
        }
        KhakiDialogBuilder(this, palette).setTitle("Индикатор направления")
            .setView(layout)
            .setPositiveButton("Готово", null)
            .show()
    }

    private fun connectionStatus(pref: String): String {
        val stored = getPreferences(Context.MODE_PRIVATE).getString(pref, "").orEmpty()
        if (stored.isBlank()) return "Нет ключа"
        if (checkingApi.contains(pref)) return "⏳ Проверяем подключение…"
        val check = apiChecks[pref] ?: return "Ключ сохранён · не проверен"
        return if (check.connected) "✓ Подключено · сервер подтвердил"
            else "⚠ " + check.message
    }

    private val connectionStatusViews = mutableMapOf<String, TextView>()

    private fun redrawConnectionStatuses() {
        for ((pref, view) in connectionStatusViews) {
            val status = connectionStatus(pref)
            view.text = status
            view.setTextColor(when {
                status.startsWith("✓") -> Color.rgb(91, 184, 113)
                status.startsWith("⚠") -> Color.rgb(214, 158, 87)
                else -> palette.secondary
            })
        }
    }

    private fun showConnections() {
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(2), dp(2), dp(2), dp(2))
        }
        panel.addView(text("Галочка означает, что сервер ответил и проверка ключа прошла. " +
            "Один только ввод ключа не считается подключением.", 13f, palette.secondary).apply {
            setPadding(dp(6), dp(4), dp(6), dp(12))
        })
        val connections = listOf(
            Triple("map_key_yandex", "Яндекс Карты", "Tiles API"),
            Triple("map_key_2gis", "2ГИС", "Raster Tiles API"),
            Triple("map_key_google", "Google Maps", "Map Tiles API · 3 вида карты"),
            Triple("opencellid_key", "OpenCellID", "Данные о сотах")
        )
        connectionStatusViews.clear()
        connections.forEach { (pref, title, subtitle) ->
            val cell = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                background = rounded(palette.elevated, 12)
                setPadding(dp(12), dp(9), dp(12), dp(9))
                isClickable = true
                isFocusable = true
            }
            cell.addView(text("$title  ›", 15f, palette.text).apply {
                setTypeface(typeface, android.graphics.Typeface.BOLD)
            })
            cell.addView(text(subtitle, 11f, palette.secondary).apply {
                setPadding(0, dp(2), 0, dp(2))
            })
            val status = text("", 12f, palette.secondary)
            connectionStatusViews[pref] = status
            cell.addView(status)
            cell.setOnClickListener {
                when (pref) {
                    "map_key_yandex" -> showYandexSetup()
                    "map_key_2gis" -> requestMapKey(
                        MapSources.providers.first { it.id == "2gis" })
                    "map_key_google" -> showGoogleSetup()
                    else -> editCellKey()
                }
            }
            panel.addView(cell, LinearLayout.LayoutParams(-1, -2).apply {
                bottomMargin = dp(7)
            })
        }
        val retry = styledButton("↻  Проверить все подключения", 13f)
        panel.addView(retry, LinearLayout.LayoutParams(-1, dp(46)))
        retry.setOnClickListener { verifySavedConnections() }
        redrawConnectionStatuses()
        KhakiDialogBuilder(this, palette).setTitle("Подключения API")
            .setView(panel).setPositiveButton("Закрыть", null).show()
    }

    private fun showGoogleSetup() {
        KhakiDialogBuilder(this, palette)
            .setTitle("Google Карты · подключение")
            .setMessage("Для обычной, спутниковой и гибридной карты нужен ключ именно Google " +
                "Map Tiles API. В Google Cloud необходимо включить Map Tiles API и оплату " +
                "(Billing). Это не ключ Maps SDK for Android. Ключ хранится только на телефоне; " +
                "запросы выполняются напрямую к Google.")
            .setItems(arrayOf("Ввести или заменить API-ключ",
                "Проверить подключение", "Открыть Google Cloud")) { _, selected ->
                when (selected) {
                    0 -> requestMapKey(MapSources.providers.first { it.id == "google-road" })
                    1 -> verifyConnection("map_key_google")
                    2 -> try {
                        startActivity(Intent(Intent.ACTION_VIEW,
                            Uri.parse("https://console.cloud.google.com/google/maps-apis/credentials")))
                    } catch (_: Exception) {
                        Toast.makeText(this, "Не удалось открыть браузер", Toast.LENGTH_LONG).show()
                    }
                }
            }
            .setNegativeButton("Закрыть", null).show()
    }

    private fun verifySavedConnections() {
        for (pref in listOf("map_key_yandex", "map_key_2gis",
            "map_key_google", "opencellid_key")) {
            verifyConnection(pref)
        }
    }

    private fun verifyConnection(pref: String) {
        val key = getPreferences(Context.MODE_PRIVATE).getString(pref, "").orEmpty()
        if (key.isBlank()) {
            apiChecks.remove(pref)
            checkingApi.remove(pref)
            redrawConnectionStatuses()
            return
        }
        if (!checkingApi.add(pref)) return
        redrawConnectionStatuses()
        val where = activeUserPoint() ?: center ?: LatLng(55.75, 37.62)
        executor.execute {
            val result = if (pref == "opencellid_key")
                ApiConnections.checkOpenCell(key, where.latitude, where.longitude)
            else {
                val provider = MapSources.providers.first { it.keyPref == pref }
                ApiConnections.checkMap(provider, where.latitude, where.longitude, key)
            }
            runOnUiThread {
                if (isDestroyed || isFinishing) return@runOnUiThread
                if (getPreferences(Context.MODE_PRIVATE).getString(pref, "") == key) {
                    apiChecks[pref] = result
                    result.session?.let { googleSessions["google-road"] = it }
                }
                checkingApi.remove(pref)
                redrawConnectionStatuses()
            }
        }
    }

    private fun selectTheme() {
        val prefs = getPreferences(Context.MODE_PRIVATE)
        val current = prefs.getString("app_theme", KhakiTheme.SYSTEM)
        KhakiDialogBuilder(this, palette).setTitle("Тема оформления")
            .setSingleChoiceItems(KhakiTheme.names,
                KhakiTheme.values.indexOf(current).coerceAtLeast(0)) { dialog, selected ->
                prefs.edit().putString("app_theme", KhakiTheme.values[selected]).apply()
                dialog.dismiss()
                recreate()
            }.setNegativeButton("Отмена", null).show()
    }

    private fun selectSim() {
        if (!cellMonitor.hasPermission()) {
            KhakiDialogBuilder(this, palette).setTitle("Доступ к SIM")
                .setMessage("Чтобы определить активную соту и уровень сигнала, нужен доступ к телефону и точному местоположению.")
                .setPositiveButton("Разрешить") { _, _ -> askLocation() }
                .setNegativeButton("Отмена", null).show()
            return
        }
        val options = cellMonitor.availableSubscriptions()
        val labels = arrayOf("Автоматически · SIM для интернета") +
            options.map { it.second + " (SIM ID " + it.first + ")" }
        KhakiDialogBuilder(this, palette).setTitle("Отслеживать сеть SIM")
            .setItems(labels) { _, i ->
                val sub = if (i == 0) SubscriptionManager.INVALID_SUBSCRIPTION_ID
                    else options[i - 1].first
                getPreferences(Context.MODE_PRIVATE).edit().putInt("sim_id", sub).apply()
                servingCell = null
                servingSite = null
                requestedCellId = null
                updateOverlays()
                cellMonitor.start(sub)
            }.setNegativeButton("Отмена", null).show()
    }

    private fun showPositionOptions() {
        KhakiDialogBuilder(this, palette).setTitle("Моя геопозиция")
            .setItems(arrayOf("Установить точку перекрестием", "Установить в центре карты",
                "Ввести координаты", "Вернуться к автоматической GPS")) { _, selection ->
                when (selection) {
                    0 -> startCrosshairMode()
                    1 -> map?.cameraPosition?.target?.let(::setManualPosition)
                    2 -> enterManualCoordinates()
                    3 -> useAutomaticPosition()
                }
            }.setNegativeButton("Отмена", null).show()
    }

    private fun enterManualCoordinates() {
        val previous = manualPoint ?: center
        val input = EditText(this).apply {
            hint = "Широта, долгота"
            inputType = InputType.TYPE_CLASS_TEXT
            setSingleLine(true)
            if (previous != null) {
                setText(String.format(Locale.US, "%.6f, %.6f",
                    previous.latitude, previous.longitude))
                setSelectAllOnFocus(true)
            }
        }
        KhakiDialogBuilder(this, palette).setTitle("Геопозиция вручную")
            .setMessage("Введите широту и долготу через запятую. В ручном режиме GPS не перемещает выбранную точку.")
            .setView(input)
            .setPositiveButton("Сохранить") { _, _ ->
                val parts = input.text.toString().trim().split(Regex("[,;\\s]+"))
                val lat = parts.getOrNull(0)?.toDoubleOrNull()
                val lon = parts.getOrNull(1)?.toDoubleOrNull()
                if (lat != null && lon != null && lat in -90.0..90.0 &&
                    lon in -180.0..180.0) setManualPosition(LatLng(lat, lon))
                else status.text = "Некорректные координаты. Широта: −90…90, долгота: −180…180."
            }
            .setNegativeButton("Отмена", null).show()
    }

    private fun setManualPosition(point: LatLng) {
        manualPoint = point
        manualCenter = true
        center = point
        getPreferences(Context.MODE_PRIVATE).edit()
            .putString("manual_lat", point.latitude.toString())
            .putString("manual_lon", point.longitude.toString()).apply()
        status.text = "Геопозиция задана вручную · GPS не корректирует маркер"
        details.visibility = View.GONE
        updateDot()
        moveCamera(point)
        lastFetch = null
        load(force = true)
    }

    private fun useAutomaticPosition() {
        manualPoint = null
        manualCenter = false
        details.visibility = View.GONE
        getPreferences(Context.MODE_PRIVATE).edit()
            .remove("manual_lat").remove("manual_lon").apply()
        updateDot()
        val point = location?.let { LatLng(it.latitude, it.longitude) }
        if (point != null) {
            center = point
            moveCamera(point)
            load(force = true)
        } else {
            status.text = "Ожидаем координаты GPS / Wi-Fi…"
            askLocation()
        }
    }

    private fun showSignalDetails() {
        val reading = servingCell
        val description = if (reading == null) {
            "Данные обслуживающей соты недоступны. Разрешите точную геопозицию и доступ к телефону."
        } else {
            val metrics = listOfNotNull(
                "Оператор: " + (reading.operator.ifBlank { "не указан" }),
                "Сеть: " + reading.network,
                "Сигнал: " + (reading.dbm?.toString()?.plus(" dBm") ?: "нет данных"),
                reading.rsrp?.let { "RSRP: $it dBm" },
                reading.rsrq?.let { "RSRQ: $it dB" },
                reading.sinr?.let { "SINR: $it dB" },
                "MCC: " + reading.mcc + " / MNC: " + reading.mnc,
                "TAC/LAC: " + reading.area,
                "Cell ID: " + reading.cellId,
                "SIM ID: " + reading.subId,
                if (servingSite == null) "Координаты обслуживающей соты неизвестны"
                else "Маркер на карте: приблизительная позиция OpenCellID"
            )
            metrics.joinToString("\n")
        }
        KhakiDialogBuilder(this, palette).setTitle("Подключённая сеть")
            .setMessage(description).setPositiveButton("Закрыть", null).show()
    }

    private fun editCellKey() {
        val field = EditText(this).apply {
            hint = "Ключ OpenCellID"
            setSingleLine(true)
            setText(getPreferences(Context.MODE_PRIVATE).getString("opencellid_key", ""))
            setSelectAllOnFocus(true)
        }
        KhakiDialogBuilder(this, palette)
            .setTitle("Дополнительные соты OpenCellID")
            .setMessage(
                "Необязательный ключ для загрузки приблизительных позиций сот в радиусе около 1 км. " +
                    "Он хранится в приложении и передаётся только серверу OpenCellID. " +
                    "Получить ключ: opencellid.org"
            )
            .setView(field)
            .setPositiveButton("Сохранить") { _, _ ->
                getPreferences(Context.MODE_PRIVATE).edit()
                    .putString("opencellid_key", field.text.toString().trim()).apply()
                lastFetch = null
                requestedCellId = null
                servingCell?.let(::updateServingCell)
                load(force = true)
            }
            .setNeutralButton("Удалить") { _, _ ->
                getPreferences(Context.MODE_PRIVATE).edit().remove("opencellid_key").apply()
                lastFetch = null
                requestedCellId = null
                servingSite = null
                servingCell?.let(::updateServingCell)
                updateOverlays()
                load(force = true)
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    override fun onCreate(state: Bundle?) {
        val mode = getPreferences(Context.MODE_PRIVATE)
            .getString("app_theme", KhakiTheme.SYSTEM) ?: KhakiTheme.SYSTEM
        setTheme(if (KhakiTheme.dark(this, mode))
            android.R.style.Theme_Material_NoActionBar
        else android.R.style.Theme_Material_Light_NoActionBar)
        super.onCreate(state)
        palette = KhakiTheme.palette(this, mode)
        window.statusBarColor = palette.canvas
        window.navigationBarColor = palette.canvas
        if (Build.VERSION.SDK_INT >= 30) {
            window.setDecorFitsSystemWindows(false)
            window.statusBarColor = Color.TRANSPARENT
            window.navigationBarColor = Color.TRANSPARENT
        }
        if (Build.VERSION.SDK_INT >= 26) {
            window.decorView.systemUiVisibility =
                if (palette.dark) 0 else View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or
                    View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        }
        val prefs = getPreferences(Context.MODE_PRIVATE)
        val storedLat = prefs.getString("manual_lat", null)?.toDoubleOrNull()
        val storedLon = prefs.getString("manual_lon", null)?.toDoubleOrNull()
        if (storedLat != null && storedLon != null &&
            storedLat in -90.0..90.0 && storedLon in -180.0..180.0) {
            manualPoint = LatLng(storedLat, storedLon)
            center = manualPoint
            manualCenter = true
        }
        showCone = prefs.getBoolean("show_cone", true)
        beamBrightness = prefs.getInt("beam_brightness", 85).coerceIn(0, 100)
        sectorBrightness = prefs.getInt("sector_brightness", 35).coerceIn(0, 100)
        MapLibre.getInstance(this)
        HttpRequestUtil.setOkHttpClient(OkHttpClient.Builder().addInterceptor { chain ->
            chain.proceed(chain.request().newBuilder()
                .header("User-Agent", "TowerRadar/0.4.0 (+https://github.com/RussoTurissto/TowerRadar)").build())
        }.build())
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        headingSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        locationFusion = LocationFusion(this, ::showLocation)
        cellMonitor = CellMonitor(this) { updated -> updateServingCell(updated) }
        createScreen()
        mapView.onCreate(state)
        mapView.getMapAsync { ready ->
            map = ready
            ready.moveCamera(CameraUpdateFactory.newLatLngZoom(
                center ?: LatLng(20.0, 0.0), if (center == null) 2.5 else 12.5))
            applyBasemap(MapSources.providers[0])
            val savedId = getPreferences(Context.MODE_PRIVATE)
                .getString("map_provider", "auto") ?: "auto"
            val saved = MapSources.providers.firstOrNull { it.id == savedId }
            if (saved == null) chooseBasemap() else useMapChoice(saved)
            ready.addOnMapClickListener { tapped ->
                if (isChoosingCrosshair) {
                    ready.animateCamera(CameraUpdateFactory.newLatLng(tapped), 250)
                    true
                } else {
                    selectTower(tapped)
                    true
                }
            }
            ready.addOnCameraIdleListener {
                updateOverlays()
                if (isChoosingCrosshair) updateCrosshairCoordinates()
                requestGoogleAttribution()
            }
        }
        askLocation()
        verifySavedConnections()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density + 0.5f).toInt()
    private fun rounded(color: Int, corner: Int = 16) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(corner).toFloat()
    }
    private fun text(value: String, size: Float, color: Int = Color.DKGRAY) = TextView(this).apply {
        text = value
        textSize = size
        setTextColor(color)
    }
    private fun styledButton(label: String, size: Float = 12f, primary: Boolean = false): Button =
        Button(this).apply {
            text = label
            textSize = size
            isAllCaps = false
            setTextColor(if (primary) palette.oliveText else palette.text)
            background = rounded(if (primary) palette.olive else palette.accentSoft, 12)
            setPadding(dp(8), 0, dp(8), 0)
            minimumHeight = 0
            minHeight = 0
        }

    private fun choice(labels: List<String>): ArrayAdapter<String> =
        object : ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, labels) {
            private fun itemView(position: Int, dropDown: Boolean): TextView {
                return text(getItem(position).orEmpty(), 13f, palette.text).apply {
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(dp(10), dp(7), dp(8), dp(7))
                    maxLines = if (dropDown) 2 else 1
                    ellipsize = android.text.TextUtils.TruncateAt.END
                    minHeight = dp(if (dropDown) 47 else 43)
                    background = rounded(
                        if (dropDown) palette.elevated else palette.surface, 10)
                }
            }
            override fun getView(position: Int, convertView: View?,
                parent: android.view.ViewGroup): View = itemView(position, false)
            override fun getDropDownView(position: Int, convertView: View?,
                parent: android.view.ViewGroup): View = itemView(position, true)
        }
    private fun row(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
    }

    private fun sectionTitle(value: String): TextView = text(value, 12f, palette.olive).apply {
        setTypeface(typeface, android.graphics.Typeface.BOLD)
        setPadding(dp(1), dp(12), dp(1), dp(5))
    }

    /** A real full-screen map: only lightweight controls float above it. */
    private fun createScreen() {
        rootView = FrameLayout(this)
        setContentView(rootView)
        mapView = MapView(this)
        rootView.addView(mapView, FrameLayout.LayoutParams(-1, -1))

        // Small heading / GPS chip and always-reachable settings / full-screen controls.
        val header = row()
        topHeader = header
        rootView.addView(header, FrameLayout.LayoutParams(-1, -2, Gravity.TOP).apply {
            setMargins(dp(12), dp(10), dp(12), 0)
        })
        val headingChip = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(7), dp(12), dp(7))
            background = rounded(palette.surface, 17)
            elevation = dp(6).toFloat()
        }
        header.addView(headingChip, LinearLayout.LayoutParams(0, -2, 1f))
        headingView = text("➤ —°", 18f, palette.olive).apply {
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            gravity = Gravity.CENTER_VERTICAL
        }
        headingChip.addView(headingView)
        gpsLabel = text("GPS / Wi-Fi: ожидание", 11f, palette.secondary)
        headingChip.addView(gpsLabel)
        headingChip.setOnClickListener { showPositionOptions() }
        fullScreenButton = styledButton("⛶", 19f).apply {
            contentDescription = "Полноэкранная карта"
            setOnClickListener { setMapFullscreen(!mapFullscreen) }
        }
        header.addView(fullScreenButton, LinearLayout.LayoutParams(dp(43), dp(43)).apply {
            marginStart = dp(7)
        })
        settingsButton = styledButton("⚙", 22f).apply {
            contentDescription = "Настройки"
            setOnClickListener { showSettings() }
        }
        header.addView(settingsButton, LinearLayout.LayoutParams(dp(43), dp(43)).apply {
            marginStart = dp(7)
        })

        // Right-side quick actions. Hidden while the sheet is expanded.
        floatingRail = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            elevation = dp(7).toFloat()
        }
        rootView.addView(floatingRail, FrameLayout.LayoutParams(-2, -2,
            Gravity.END or Gravity.BOTTOM).apply {
            setMargins(0, 0, dp(12), dp(112))
        })
        fun railButton(button: Button, hint: String) {
            button.contentDescription = hint
            floatingRail.addView(button, LinearLayout.LayoutParams(dp(49), dp(49)).apply {
                bottomMargin = dp(8)
            })
        }
        gpsRefresh = styledButton("⌖", 22f)
        railButton(gpsRefresh, "Уточнить GPS")
        gpsRefresh.setOnClickListener {
            if (!manualCenter) {
                startLocationUpdates()
                locationFusion.refresh()
                status.text = "Уточняем местоположение через GPS и Wi-Fi…"
                Toast.makeText(this, "Уточняем GPS и Wi-Fi", Toast.LENGTH_SHORT).show()
            } else showPositionOptions()
        }
        searchHere = styledButton("⌕", 22f)
        railButton(searchHere, "Искать вышки в центре карты")
        searchHere.setOnClickListener {
            map?.cameraPosition?.target?.let {
                center = it
                manualCenter = true
                lastFetch = null
                load(force = true)
                Toast.makeText(this, "Ищем вышки в центре карты", Toast.LENGTH_SHORT).show()
            }
        }
        locate = styledButton("◎", 24f, true)
        railButton(locate, "Вернуться к автоматическому GPS")
        locate.setOnClickListener { useAutomaticPosition() }
        locate.setOnLongClickListener { showPositionOptions(); true }

        // Bottom sheet: 84dp closed, up to ~72% of screen on demand.
        sheetView = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(palette.surface, 22)
            elevation = dp(12).toFloat()
            setPadding(dp(14), dp(3), dp(14), dp(7))
        }
        rootView.addView(sheetView, FrameLayout.LayoutParams(-1, dp(84),
            Gravity.BOTTOM).apply { setMargins(dp(6), 0, dp(6), 0) })

        sheetHandle = text("⌃  Подробности", 11f, palette.secondary).apply {
            gravity = Gravity.CENTER
            setPadding(dp(8), 0, dp(8), 0)
            contentDescription = "Развернуть панель управления"
        }
        sheetView.addView(sheetHandle, LinearLayout.LayoutParams(-1, dp(20)))
        sheetHandle.setOnClickListener { expandSheet(!sheetExpanded) }
        var touchY = 0f
        sheetHandle.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    touchY = event.rawY
                    true
                }
                MotionEvent.ACTION_UP -> {
                    val diff = event.rawY - touchY
                    if (diff < -dp(24)) expandSheet(true)
                    else if (diff > dp(24)) expandSheet(false)
                    else sheetHandle.performClick()
                    true
                }
                MotionEvent.ACTION_CANCEL -> true
                else -> true
            }
        }

        val signalRow = row()
        sheetView.addView(signalRow, LinearLayout.LayoutParams(-1, dp(29)))
        signalLabel = text("Сеть: ожидаем SIM", 14f, palette.text).apply {
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            gravity = Gravity.CENTER_VERTICAL
            setSingleLine(true)
            ellipsize = android.text.TextUtils.TruncateAt.END
        }
        signalRow.addView(signalLabel, LinearLayout.LayoutParams(0, dp(29), 1f))
        signalBars = LinearLayout(this).apply {
            gravity = Gravity.BOTTOM
            orientation = LinearLayout.HORIZONTAL
        }
        for (h in listOf(9, 12, 15, 19, 23)) {
            signalBars.addView(View(this).apply {
                background = rounded(palette.signalMuted, 3)
            }, LinearLayout.LayoutParams(dp(9), dp(h)).apply { marginStart = dp(3) })
        }
        signalRow.addView(signalBars, LinearLayout.LayoutParams(-2, dp(25)))
        signalDetails = text("Сигнал: нет измерения", 11f, palette.secondary).apply {
            setSingleLine(true)
            ellipsize = android.text.TextUtils.TruncateAt.END
        }
        sheetView.addView(signalDetails)
        signalRow.setOnClickListener { showSignalDetails() }
        signalDetails.setOnClickListener { showSignalDetails() }

        sheetExtra = ScrollView(this).apply {
            isVerticalScrollBarEnabled = true
            visibility = View.GONE
        }
        sheetView.addView(sheetExtra, LinearLayout.LayoutParams(-1, 0, 1f).apply {
            topMargin = dp(10)
        })
        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(2), 0, dp(2), dp(22))
        }
        sheetExtra.addView(controls, FrameLayout.LayoutParams(-1, -2))

        controls.addView(sectionTitle("ПОИСК ВЫШЕК"))
        val filterRow = row()
        controls.addView(filterRow, LinearLayout.LayoutParams(-1, dp(48)))
        radius = Spinner(this)
        radius.adapter = choice(TowerData.radiiKm.map { "$it км" })
        radius.setSelection(TowerData.radiiKm.indexOf(radiusKm))
        filterRow.addView(radius, LinearLayout.LayoutParams(dp(96), dp(47)))
        operators = Spinner(this)
        filterRow.addView(operators, LinearLayout.LayoutParams(0, dp(47), 1f).apply {
            marginStart = dp(10)
        })
        updateOperatorChoices()

        val actions = row()
        controls.addView(actions, LinearLayout.LayoutParams(-1, dp(49)).apply {
            topMargin = dp(6)
        })
        refresh = styledButton("Обновить вышки", 12f, true)
        actions.addView(refresh, LinearLayout.LayoutParams(0, dp(47), 1f).apply {
            marginEnd = dp(5)
        })
        val searchControl = styledButton("Искать здесь", 12f)
        actions.addView(searchControl, LinearLayout.LayoutParams(0, dp(47), 1f))
        refresh.setOnClickListener { load(force = true) }
        searchControl.setOnClickListener {
            map?.cameraPosition?.target?.let { point ->
                center = point
                manualCenter = true
                lastFetch = null
                load(force = true)
                expandSheet(false)
            }
        }

        controls.addView(sectionTitle("КАРТОГРАФИЧЕСКАЯ ПОДЛОЖКА"))
        sourceSpinner = Spinner(this)
        sourceSpinner.adapter = choice(listOf("Автоматически · OSM") + MapSources.providers.map { it.name })
        val prefId = getPreferences(Context.MODE_PRIVATE).getString("map_provider", "auto") ?: "auto"
        sourceSpinner.setSelection(MapSources.providers.indexOfFirst { it.id == prefId } + 1)
        controls.addView(sourceSpinner, LinearLayout.LayoutParams(-1, dp(46)))
        val yandexButton = styledButton("Яндекс ID · подключить карты", 12f).apply {
            contentDescription = "Войти в кабинет Яндекса и получить ключ Tiles API"
            setOnClickListener { showYandexSetup() }
        }
        controls.addView(yandexButton, LinearLayout.LayoutParams(-1, dp(42)).apply {
            topMargin = dp(5)
        })
        mapStatus = text("Выбираем доступный сервер карты…", 11f, palette.secondary)
        controls.addView(mapStatus)
        sourceSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
            override fun onItemSelected(parent: AdapterView<*>?, view: View?,
                                        position: Int, id: Long) {
                if (map == null) return
                if (position == 0) {
                    getPreferences(Context.MODE_PRIVATE).edit()
                        .putString("map_provider", "auto").apply()
                    chooseBasemap()
                } else useMapChoice(MapSources.providers[position - 1])
            }
        }
        sourceSpinner.setOnLongClickListener {
            MapSources.providers.getOrNull(sourceSpinner.selectedItemPosition - 1)?.let {
                if (it.id == "yandex") showYandexSetup()
                else if (it.keyPref != null) requestMapKey(it)
                else return@setOnLongClickListener false
                true
            } ?: false
        }

        controls.addView(sectionTitle("ГЕОПОЗИЦИЯ"))
        val positionButton = styledButton("Изменить местоположение · GPS / вручную", 12f)
        controls.addView(positionButton, LinearLayout.LayoutParams(-1, dp(44)))
        positionButton.setOnClickListener { showPositionOptions() }
        status = text("Определяем местоположение…", 11f, palette.secondary)
        controls.addView(status)

        controls.addView(sectionTitle("СОТОВАЯ СЕТЬ"))
        cellStatus = text("Обслуживающая сота: определяем…", 12f, palette.secondary)
        controls.addView(cellStatus)
        cellStatus.setOnClickListener { showSignalDetails() }
        val cellActions = row()
        controls.addView(cellActions, LinearLayout.LayoutParams(-1, dp(44)).apply {
            topMargin = dp(8)
        })
        val simButton = styledButton("Выбор SIM", 12f)
        keyButton = styledButton("Доп. соты", 12f)
        cellActions.addView(simButton, LinearLayout.LayoutParams(0, dp(43), 1f).apply {
            marginEnd = dp(5)
        })
        cellActions.addView(keyButton, LinearLayout.LayoutParams(0, dp(43), 1f))
        simButton.setOnClickListener { selectSim() }
        keyButton.setOnClickListener { editCellKey() }

        controls.addView(sectionTitle("ДОПОЛНИТЕЛЬНО"))
        val settingsControl = styledButton("Открыть настройки · оформление / компас", 12f)
        controls.addView(settingsControl, LinearLayout.LayoutParams(-1, dp(44)))
        settingsControl.setOnClickListener { showSettings() }

        // Detail panel sits above the bottom sheet only when a pin is selected.
        details = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(13), dp(10), dp(13), dp(10))
            background = rounded(palette.surface, 15)
            elevation = dp(9).toFloat()
            visibility = View.GONE
        }
        rootView.addView(details, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM).apply {
            setMargins(dp(11), 0, dp(11), dp(95))
        })
        val detailRow = row()
        details.addView(detailRow)
        detailTitle = text("", 16f, palette.text).apply {
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }
        detailRow.addView(detailTitle, LinearLayout.LayoutParams(0, -2, 1f))
        detailRow.addView(styledButton("✕", 17f).apply {
            setOnClickListener { details.visibility = View.GONE }
        }, LinearLayout.LayoutParams(dp(43), dp(40)))
        detailBody = text("", 12f, palette.secondary).apply {
            maxLines = 9
            ellipsize = android.text.TextUtils.TruncateAt.END
        }
        details.addView(detailBody)

        mapAttribution = text("© OpenStreetMap contributors", 10f, palette.text).apply {
            setPadding(dp(6), dp(3), dp(6), dp(3))
            maxWidth = dp(310)
            background = rounded(palette.surface, 3)
        }
        rootView.addView(mapAttribution, FrameLayout.LayoutParams(-2, -2,
            Gravity.START or Gravity.BOTTOM).apply {
            setMargins(dp(8), 0, 0, dp(91))
        })
        brandLink = text("Яндекс Карты", 12f, palette.olive).apply {
            setPadding(dp(8), dp(5), dp(8), dp(5))
            background = rounded(palette.surface, 4)
            visibility = View.GONE
            setOnClickListener {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://yandex.ru/maps/")))
            }
        }
        rootView.addView(brandLink, FrameLayout.LayoutParams(-2, -2,
            Gravity.END or Gravity.BOTTOM).apply {
            setMargins(0, 0, dp(8), dp(91))
        })

        // Crosshair mode is a separate, focused location picker: drag the map
        // under the reticle, tap a desired point or zoom for precise placement.
        crosshairReticle = CrosshairView(this).apply {
            visibility = View.GONE
            isClickable = false
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        rootView.addView(crosshairReticle, FrameLayout.LayoutParams(
            dp(95), dp(95), Gravity.CENTER
        ))
        crosshairControls = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(palette.surface, 19)
            elevation = dp(12).toFloat()
            setPadding(dp(13), dp(9), dp(13), dp(12))
            visibility = View.GONE
        }
        rootView.addView(crosshairControls, FrameLayout.LayoutParams(
            -1, -2, Gravity.BOTTOM
        ).apply { setMargins(dp(10), 0, dp(10), dp(16)) })
        crosshairControls.addView(text("⌖  Наведите перекрестие на нужную точку", 13f,
            palette.text).apply {
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            gravity = Gravity.CENTER
        })
        crosshairCoordinates = text("Перемещайте карту или коснитесь места", 12f,
            palette.secondary).apply {
            gravity = Gravity.CENTER
            setPadding(0, dp(7), 0, dp(11))
        }
        crosshairControls.addView(crosshairCoordinates)
        val pickerActions = row()
        crosshairControls.addView(pickerActions, LinearLayout.LayoutParams(-1, dp(48)))
        val cancelPicker = styledButton("Отмена", 13f)
        val confirmPicker = styledButton("✓  Установить точку", 13f, true)
        pickerActions.addView(cancelPicker,
            LinearLayout.LayoutParams(0, dp(46), 1f).apply { marginEnd = dp(5) })
        pickerActions.addView(confirmPicker,
            LinearLayout.LayoutParams(0, dp(46), 1.7f))
        cancelPicker.setOnClickListener { finishCrosshairMode(false) }
        confirmPicker.setOnClickListener { finishCrosshairMode(true) }
        installSafeInsets()

        radius.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
            override fun onItemSelected(parent: AdapterView<*>?, view: View?,
                                        position: Int, id: Long) {
                val next = TowerData.radiiKm[position]
                if (radiusKm != next) {
                    radiusKm = next
                    load(force = true)
                }
            }
        }
    }


    /**
     * Android 15+ draws behind system bars; controls are inset individually,
     * leaving the map full-bleed and the status bar completely unobstructed.
     */
    private fun installSafeInsets() {
        rootView.setOnApplyWindowInsetsListener { _, insets ->
            val top = if (Build.VERSION.SDK_INT >= 30) {
                insets.getInsets(WindowInsets.Type.statusBars() or
                    WindowInsets.Type.displayCutout()).top
            } else insets.systemWindowInsetTop
            val bottom = if (Build.VERSION.SDK_INT >= 30) {
                insets.getInsets(WindowInsets.Type.navigationBars() or
                    WindowInsets.Type.displayCutout()).bottom
            } else insets.systemWindowInsetBottom
            if (systemTopInset != top || systemBottomInset != bottom) {
                systemTopInset = top
                systemBottomInset = bottom
                topHeader.layoutParams = (topHeader.layoutParams as FrameLayout.LayoutParams)
                    .apply { topMargin = dp(10) + top }
                sheetView.layoutParams = (sheetView.layoutParams as FrameLayout.LayoutParams)
                    .apply { bottomMargin = bottom }
                floatingRail.layoutParams = (floatingRail.layoutParams as FrameLayout.LayoutParams)
                    .apply { bottomMargin = bottom + dp(112) }
                crosshairControls.layoutParams =
                    (crosshairControls.layoutParams as FrameLayout.LayoutParams).apply {
                        bottomMargin = bottom + dp(16)
                    }
                updateMapChrome(if (mapFullscreen || isChoosingCrosshair)
                    0 else sheetView.layoutParams.height)
            }
            insets
        }
        rootView.post { rootView.requestApplyInsets() }
    }

    private fun startCrosshairMode() {
        val current = map ?: return
        if (isChoosingCrosshair) return
        isChoosingCrosshair = true
        crosshairStart = current.cameraPosition.target
        crosshairStartZoom = current.cameraPosition.zoom
        details.visibility = View.GONE
        sheetView.visibility = View.GONE
        floatingRail.visibility = View.GONE
        crosshairReticle.visibility = View.VISIBLE
        crosshairControls.visibility = View.VISIBLE
        updateCrosshairCoordinates()
        mapAttribution.layoutParams = (mapAttribution.layoutParams as FrameLayout.LayoutParams)
            .apply { bottomMargin = systemBottomInset + dp(171) }
        brandLink.layoutParams = (brandLink.layoutParams as FrameLayout.LayoutParams)
            .apply { bottomMargin = systemBottomInset + dp(171) }
        Toast.makeText(this, "Перемещайте карту или коснитесь нужной точки",
            Toast.LENGTH_SHORT).show()
    }

    private fun updateCrosshairCoordinates() {
        if (!isChoosingCrosshair || !::crosshairCoordinates.isInitialized) return
        val focus = map?.cameraPosition?.target ?: return
        crosshairCoordinates.text = String.format(Locale.US,
            "Широта %.6f  ·  Долгота %.6f", focus.latitude, focus.longitude)
    }

    private fun finishCrosshairMode(confirm: Boolean) {
        if (!isChoosingCrosshair) return
        val chosen = map?.cameraPosition?.target
        val zoom = map?.cameraPosition?.zoom ?: crosshairStartZoom
        isChoosingCrosshair = false
        crosshairReticle.visibility = View.GONE
        crosshairControls.visibility = View.GONE
        sheetView.visibility = if (mapFullscreen) View.GONE else View.VISIBLE
        floatingRail.visibility = if (mapFullscreen || sheetExpanded) View.GONE else View.VISIBLE
        updateMapChrome(if (mapFullscreen) 0 else sheetView.layoutParams.height)
        if (confirm && chosen != null) {
            setManualPosition(chosen)
            map?.animateCamera(CameraUpdateFactory.newLatLngZoom(chosen, zoom), 300)
        } else {
            crosshairStart?.let { saved ->
                map?.animateCamera(CameraUpdateFactory.newLatLngZoom(saved, crosshairStartZoom), 300)
            }
        }
        crosshairStart = null
    }

    @Deprecated("Deprecated by Android")
    override fun onBackPressed() {
        if (isChoosingCrosshair) finishCrosshairMode(false)
        else super.onBackPressed()
    }

    private fun setMapFullscreen(full: Boolean) {
        mapFullscreen = full
        sheetView.visibility = if (full) View.GONE else View.VISIBLE
        floatingRail.visibility =
            if (full || sheetExpanded || isChoosingCrosshair) View.GONE else View.VISIBLE
        fullScreenButton.text = if (full) "▣" else "⛶"
        fullScreenButton.contentDescription = if (full) "Вернуть панель" else "На весь экран"
        updateMapChrome(if (full) 0 else sheetView.layoutParams.height)
        if (full) details.visibility = View.GONE
    }

    private fun expandSheet(expanded: Boolean) {
        if (mapFullscreen) setMapFullscreen(false)
        sheetExpanded = expanded
        sheetHandle.text = if (expanded) "⌄  Свернуть" else "⌃  Подробности"
        sheetHandle.contentDescription = if (expanded)
            "Свернуть панель" else "Развернуть панель"
        sheetExtra.visibility = if (expanded) View.VISIBLE else View.GONE
        floatingRail.visibility = if (expanded) View.GONE else View.VISIBLE
        if (expanded) details.visibility = View.GONE
        val usable = rootView.height.takeIf { it > 0 } ?: resources.displayMetrics.heightPixels
        val maxHeight = (usable - dp(85)).coerceAtLeast(dp(230))
        val target = if (expanded) ((usable * 0.73f).toInt()).coerceIn(
            dp(230).coerceAtMost(maxHeight), maxHeight
        ) else dp(84)
        val startHeight = sheetView.layoutParams.height
        ValueAnimator.ofInt(startHeight, target).apply {
            duration = 220L
            addUpdateListener { animator ->
                val height = animator.animatedValue as Int
                sheetView.layoutParams = (sheetView.layoutParams as FrameLayout.LayoutParams).apply {
                    this.height = height
                }
                updateMapChrome(height)
            }
        }.start()
    }

    private fun updateMapChrome(sheetHeight: Int) {
        val footerMargin = sheetHeight + dp(7) + systemBottomInset
        if (::mapAttribution.isInitialized) {
            mapAttribution.layoutParams = (mapAttribution.layoutParams as FrameLayout.LayoutParams).apply {
                bottomMargin = footerMargin
            }
        }
        if (::brandLink.isInitialized) {
            brandLink.layoutParams = (brandLink.layoutParams as FrameLayout.LayoutParams).apply {
                bottomMargin = footerMargin + dp(36)
            }
        }
        if (::details.isInitialized) {
            details.layoutParams = (details.layoutParams as FrameLayout.LayoutParams).apply {
                bottomMargin = footerMargin + dp(8)
            }
        }
    }

    private fun updateOperatorChoices() {
        val names = listOf("Все операторы") +
            sites.map { it.operator ?: unknown }.distinct().sortedWith(String.CASE_INSENSITIVE_ORDER)
        val old = selectedOperator ?: "Все операторы"
        operators.onItemSelectedListener = null
        operators.adapter = choice(names)
        val position = names.indexOf(old).coerceAtLeast(0)
        operators.setSelection(position)
        if (position == 0) selectedOperator = null
        operators.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                selectedOperator = if (position == 0) null else names[position]
                showPins()
            }
        }
    }

    private fun hasLocation(): Boolean =
        checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    private fun askLocation() {
        val permissions = listOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.READ_PHONE_STATE
        ).filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
        if (permissions.isNotEmpty()) {
            // Android 12+ requires FINE and COARSE together, even if COARSE was previously granted.
            val requested = if (Manifest.permission.ACCESS_FINE_LOCATION in permissions)
                listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.READ_PHONE_STATE)
            else permissions
            requestPermissions(requested.distinct().toTypedArray(), 101)
            status.text = "Для точного GPS и информации о соте разрешите доступ к местоположению и телефону."
        } else {
            startLocationUpdates()
            cellMonitor.start(getPreferences(Context.MODE_PRIVATE).getInt("sim_id", -1))
        }
    }

    @SuppressLint("MissingPermission")
    private fun startLocationUpdates() {
        if (!hasLocation()) return
        if (!listening) {
            val enabled = locationManager.getProviders(true)
            for (provider in listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER)) {
                if (provider !in enabled) continue
                try {
                    locationManager.getLastKnownLocation(provider)?.let(::showLocation)
                    locationManager.requestLocationUpdates(provider, 2000L, 0f,
                        listener, Looper.getMainLooper())
                    listening = true
                } catch (_: SecurityException) {
                } catch (_: IllegalArgumentException) {
                }
            }
        }
        locationFusion.start()
        if (!listening) status.text = "Геолокация отключена. Можно установить точку вручную."
    }

    private fun showLocation(fix: Location) {
        if (!fix.latitude.isFinite() || !fix.longitude.isFinite() ||
            fix.latitude !in -90.0..90.0 || fix.longitude !in -180.0..180.0) return
        val ageMs = if (fix.elapsedRealtimeNanos > 0L)
            (SystemClock.elapsedRealtimeNanos() - fix.elapsedRealtimeNanos) / 1000000L
        else System.currentTimeMillis() - fix.time
        if (ageMs !in 0L..120000L) return // Never show a cached old fix as fresh.
        val old = location
        if (old != null && fix.elapsedRealtimeNanos < old.elapsedRealtimeNanos) return
        if (old != null && fix.hasAccuracy() && old.hasAccuracy() &&
            fix.accuracy > old.accuracy * 2f &&
            (fix.elapsedRealtimeNanos - old.elapsedRealtimeNanos) < 9000000000L) return
        location = fix
        gpsLabel.text = if (fix.hasAccuracy()) {
            val accuracy = fix.accuracy.toInt()
            "GPS / Wi-Fi: ±" + accuracy + " м · " + fix.provider +
                if (accuracy > 70) " · низкая точность" else " · уточняется"
        } else "GPS / Wi-Fi: точность не сообщена · " + fix.provider
        gpsLabel.setTextColor(
            if (fix.hasAccuracy() && fix.accuracy > 70f) Color.rgb(188, 98, 42) else palette.secondary
        )
        updateDot()
        if (manualCenter) return
        val point = LatLng(fix.latitude, fix.longitude)
        center = point
        if (!initiallyCentered) moveCamera(point)
        val recent = System.currentTimeMillis() - lastFetchTime < 90000L
        val previous = lastFetch
        if (previous == null || (!recent && TowerData.distanceKm(
                previous.latitude, previous.longitude, point.latitude, point.longitude
            ) >= (radiusKm / 2.0).coerceAtLeast(0.5))) load(force = false)
    }

    private fun moveCamera(position: LatLng) {
        if (map == null) return
        initiallyCentered = true
        map?.animateCamera(CameraUpdateFactory.newLatLngZoom(position, 12.5), 600)
    }

    private fun cache(latitude: Double, longitude: Double, range: Int): List<TowerSite>? {
        val prefs = getSharedPreferences("tower_results", Context.MODE_PRIVATE)
        val body = prefs.getString("body", null) ?: return null
        if (prefs.getInt("radius", -1) != range) return null
        if (System.currentTimeMillis() - prefs.getLong("savedAt", 0) > 24 * 3600000L) return null
        if (TowerData.distanceKm(latitude, longitude,
                prefs.getString("lat", null)?.toDoubleOrNull() ?: return null,
                prefs.getString("lon", null)?.toDoubleOrNull() ?: return null) > 0.5) return null
        return try { TowerData.parse(body, latitude, longitude, range) } catch (_: Exception) { null }
    }

    private fun fetch(latitude: Double, longitude: Double, range: Int): List<TowerSite> {
        // Prefer a geographically suitable endpoint first, fall back on failure.
        val endpoints = if (longitude in 25.0..180.0 && latitude in 35.0..75.0)
            listOf(
                "https://maps.mail.ru/osm/tools/overpass/api/interpreter",
                "https://overpass.private.coffee/api/interpreter",
                "https://overpass-api.de/api/interpreter"
            )
        else listOf(
            "https://overpass.private.coffee/api/interpreter",
            "https://overpass-api.de/api/interpreter",
            "https://maps.mail.ru/osm/tools/overpass/api/interpreter"
        )
        var lastError: Exception = IOException("Все серверы Overpass недоступны.")
        for (endpoint in endpoints) {
            try {
                return fetchFromOverpass(endpoint, latitude, longitude, range)
            } catch (e: Exception) {
                lastError = e
                if (Thread.currentThread().isInterrupted) throw e
            }
        }
        throw lastError
    }

    private fun fetchFromOverpass(
        endpoint: String, latitude: Double, longitude: Double, range: Int
    ): List<TowerSite> {
        val request = TowerData.query(latitude, longitude, range)
        val body = "data=" + URLEncoder.encode(request, StandardCharsets.UTF_8.name())
        val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = 12000
            readTimeout = 35000
            setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "TowerRadar/0.2.0 (+https://github.com/RussoTurissto/TowerRadar)")
        }
        try {
            connection.outputStream.use { it.write(body.toByteArray(StandardCharsets.UTF_8)) }
            val code = connection.responseCode
            if (code !in 200..299) throw IOException(when (code) {
                429 -> "Слишком много запросов к серверу. Повторите позже."
                504 -> "Сервер не успел ответить. Уменьшите радиус."
                else -> "Overpass: ошибка HTTP $code."
            })
            val bytes = connection.inputStream.use { input ->
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (true) {
                    val n = input.read(buffer)
                    if (n < 0) break
                    if (output.size() + n > 6_000_000) throw IOException("Слишком много объектов. Уменьшите радиус.")
                    output.write(buffer, 0, n)
                }
                output.toByteArray()
            }
            val json = String(bytes, StandardCharsets.UTF_8)
            val parsed = TowerData.parse(json, latitude, longitude, range)
            getSharedPreferences("tower_results", Context.MODE_PRIVATE).edit()
                .putString("body", json).putString("lat", latitude.toString())
                .putString("lon", longitude.toString()).putInt("radius", range)
                .putLong("savedAt", System.currentTimeMillis()).apply()
            return parsed
        } finally {
            connection.disconnect()
        }
    }

    private fun load(force: Boolean) {
        val point = center ?: map?.cameraPosition?.target
        if (point == null) {
            status.text = "Сначала выберите точку поиска на карте."
            return
        }
        if (busy) {
            if (force) pendingManualSearch = true
            return
        }
        val previous = lastFetch
        if (!force && previous != null && radiusKm == lastFetchRadius && sites.isNotEmpty() &&
            TowerData.distanceKm(point.latitude, point.longitude, previous.latitude, previous.longitude) < (radiusKm / 2.0).coerceAtLeast(0.5)) return

        val lat = point.latitude
        val lon = point.longitude
        val range = radiusKm
        busy = true
        refresh.isEnabled = false
        searchHere.isEnabled = false
        details.visibility = View.GONE
        status.text = "Загружаем объекты связи · $range км…"
        lastFetchTime = System.currentTimeMillis()
        executor.execute {
            try {
                val cached = if (force) null else cache(lat, lon, range)
                val osmSites = cached ?: fetch(lat, lon, range)
                val key = getPreferences(Context.MODE_PRIVATE)
                    .getString("opencellid_key", "").orEmpty()
                var cellError = false
                val cells = if (key.isBlank()) emptyList() else try {
                    CellSources.fetch(key, lat, lon)
                } catch (_: Exception) {
                    cellError = true
                    emptyList()
                }
                val result = (osmSites + cells).distinctBy { it.id }
                runOnUiThread {
                    if (isDestroyed || isFinishing) return@runOnUiThread
                    busy = false
                    refresh.isEnabled = true
                    searchHere.isEnabled = true
                    sites = result
                    lastFetch = LatLng(lat, lon)
                    lastFetchRadius = range
                    updateOperatorChoices()
                    showPins()
                    servingCell?.let(::updateServingCell)
                    status.text = "Найдено: " + sites.size + " · " + range + " км · " +
                        if (cellError) "OpenCellID недоступен" else if (cells.isNotEmpty())
                            "OSM + OpenCellID (" + cells.size + " сот)"
                        else if (cached != null) "кеш OSM" else "OpenStreetMap"
                    if (pendingManualSearch) {
                        pendingManualSearch = false
                        load(force = true)
                    }
                }
            } catch (e: Exception) {
                val cached = cache(lat, lon, range)
                runOnUiThread {
                    if (isDestroyed || isFinishing) return@runOnUiThread
                    busy = false
                    refresh.isEnabled = true
                    searchHere.isEnabled = true
                    if (cached != null) {
                        sites = cached
                        lastFetch = LatLng(lat, lon)
                        lastFetchRadius = range
                        updateOperatorChoices()
                        showPins()
                        status.text = "Нет соединения · показан сохранённый результат"
                    } else {
                        status.text = e.message?.take(140) ?: "Не удалось загрузить данные."
                    }
                    if (pendingManualSearch) {
                        pendingManualSearch = false
                        load(force = true)
                    }
                }
            }
        }
    }


    /** Only a precisely matched radio-cell identifier may have a linked map marker. */
    private fun updateServingCell(reading: ServingCell?) {
        if (!::signalLabel.isInitialized) return
        val previousId = servingCell?.identity
        servingCell = reading
        if (reading == null) {
            signalLabel.text = "Сеть: данные соты недоступны"
            signalDetails.text = "Нет измерения уровня сигнала"
            cellStatus.text = if (cellMonitor.hasPermission())
                "Нет зарегистрированной соты или модем ограничил доступ."
            else "Разрешите точную геопозицию и доступ к телефону."
            if (previousId != null) {
                servingSite = null
                updateOverlays()
            }
            showSignalBars(0)
            return
        }
        if (reading.identity != previousId) {
            servingSite = null
            updateOverlays()
        }
        signalLabel.text = (if (reading.operator.isBlank()) "Мобильная сеть" else reading.operator) +
            " · " + when (reading.network) {
                "LTE" -> "4G LTE"
                "NR" -> "5G NR"
                "UMTS" -> "3G"
                else -> reading.network
            }
        val values = listOfNotNull(
            reading.dbm?.let { "$it dBm" },
            reading.rsrp?.let { "RSRP $it dBm" },
            reading.rsrq?.let { "RSRQ $it dB" },
            reading.sinr?.let { "SINR " + String.format(Locale.US, "%.1f", it) + " dB" }
        )
        signalDetails.text = if (values.isEmpty()) "Модем не передал мощность сигнала"
        else values.joinToString(" · ")
        val quality = when (reading.level.coerceIn(0, 4)) {
            0 -> "Очень слабый"
            1 -> "Слабый"
            2 -> "Средний"
            3 -> "Хороший"
            else -> "Отличный"
        }
        signalLabel.contentDescription = quality + " уровень сигнала; " + signalDetails.text
        showSignalBars(reading.level + 1)
        if (!reading.validId) {
            cellStatus.text = "Сота зарегистрирована, но её идентификатор не раскрыт устройством."
            return
        }
        val matching = sites.firstOrNull { it.id == "opencellid/" + reading.identity }
        if (matching != null) {
            servingSite = matching
            cellStatus.text = "Сота " + reading.cellId + " · точка в OpenCellID (примерная)"
            updateOverlays()
            return
        }
        if (servingSite != null) {
            cellStatus.text = "Сота " + reading.cellId + " · оценка OpenCellID, не физическая вышка"
            return
        }
        val key = getPreferences(Context.MODE_PRIVATE)
            .getString("opencellid_key", "").orEmpty()
        if (key.isBlank()) {
            cellStatus.text = "Сота " + reading.cellId +
                " · для координат нужен ключ OpenCellID в настройках"
            return
        }
        val now = SystemClock.elapsedRealtime()
        if (requestedCellId == reading.identity && now - cellRequestAt < 600000L) {
            cellStatus.text = "Сота " + reading.cellId +
                " · местоположение неизвестно (нет совпадения в OpenCellID)"
            return
        }
        requestedCellId = reading.identity
        cellRequestAt = now
        cellStatus.text = "Сота " + reading.cellId + " · ищем координаты в OpenCellID…"
        cellExecutor.execute {
            val site = CellSources.lookup(key, reading)
            runOnUiThread {
                if (isDestroyed || isFinishing || servingCell?.identity != reading.identity) return@runOnUiThread
                servingSite = site
                cellStatus.text = if (site == null)
                    "Сота " + reading.cellId + " · координаты в OpenCellID не найдены"
                else "Сота " + reading.cellId + " · пунктир к примерной позиции OpenCellID"
                updateOverlays()
            }
        }
    }

    private fun showSignalBars(count: Int) {
        if (!::signalBars.isInitialized) return
        val color = when (count) {
            0, 1 -> Color.rgb(197, 77, 65)
            2 -> Color.rgb(204, 133, 55)
            3 -> Color.rgb(180, 169, 70)
            else -> palette.olive
        }
        for (i in 0 until signalBars.childCount) {
            signalBars.getChildAt(i).background =
                rounded(if (i < count) color else palette.signalMuted, 3)
        }
    }

    private fun emptyFeatures(): FeatureCollection = FeatureCollection.fromFeatures(emptyArray<Feature>())

    private fun showPins() {
        visible = sites.filter {
            selectedOperator == null || (selectedOperator == unknown && it.operator == null) || it.operator == selectedOperator
        }
        if (!styled) return
        val features = visible.map { site ->
            Feature.fromGeometry(Point.fromLngLat(site.longitude, site.latitude)).apply {
                addBooleanProperty("mobile", site.mobile)
            }
        }
        (map?.style?.getSource("towers") as? GeoJsonSource)
            ?.setGeoJson(FeatureCollection.fromFeatures(features.toTypedArray()))
    }

    private fun updateDot() {
        if (!styled) return
        val point = activeUserPoint()
        val features = if (point == null) emptyFeatures() else FeatureCollection.fromFeatures(
            arrayOf(Feature.fromGeometry(Point.fromLngLat(point.longitude, point.latitude)))
        )
        (map?.style?.getSource("my-location") as? GeoJsonSource)?.setGeoJson(features)
        updateOverlays()
    }

    private fun geoPoint(lat: Double, lon: Double, meters: Double, bearing: Double): Point {
        val latR = Math.toRadians(lat)
        val lonR = Math.toRadians(lon)
        val distance = meters / 6371008.8
        val direction = Math.toRadians(bearing)
        val lat2 = asin((sin(latR) * cos(distance) +
            cos(latR) * sin(distance) * cos(direction)).coerceIn(-1.0, 1.0))
        val lon2 = lonR + atan2(sin(direction) * sin(distance) * cos(latR),
            cos(distance) - sin(latR) * sin(lat2))
        return Point.fromLngLat(Math.toDegrees(lon2), Math.toDegrees(lat2))
    }

    private fun polygonAround(lat: Double, lon: Double, meters: Double,
                              bearings: List<Double>): FeatureCollection {
        if (bearings.size < 2) return emptyFeatures()
        val coordinates = bearings.map { geoPoint(lat, lon, meters, it) }
        val ring = coordinates + coordinates.first()
        return FeatureCollection.fromFeatures(arrayOf(
            Feature.fromGeometry(Polygon.fromLngLats(listOf(ring)))
        ))
    }

    private fun updateOverlays() {
        if (!styled) return
        val point = manualPoint ?: location?.let { LatLng(it.latitude, it.longitude) }
        val accurate = if (manualPoint == null) location?.takeIf {
            it.hasAccuracy() && it.accuracy in 1f..1500f &&
                System.currentTimeMillis() - it.time < 120000L
        } else null
        val accuracy = accurate?.let {
            polygonAround(it.latitude, it.longitude, it.accuracy.toDouble(),
                (0..36).map { index -> index * 10.0 })
        } ?: emptyFeatures()
        (map?.style?.getSource("gps-accuracy") as? GeoJsonSource)?.setGeoJson(accuracy)
        val visibleDirection = showCone && headingSensor != null && point != null
        val sector = if (visibleDirection && point != null) {
            // Convert the visible screen scale into metres. The previous fixed 600 m
            // radius disappeared when the user zoomed out.
            val resolution = (156543.03392 * cos(Math.toRadians(point.latitude))
                .coerceAtLeast(0.01)) / 2.0.pow((map?.cameraPosition?.zoom ?: 13.0))
            val screen = kotlin.math.min(mapView.width, mapView.height)
                .coerceAtLeast(dp(320)).toDouble()
            val length = (resolution * screen * 0.39).coerceIn(30.0, 800000.0)
            val headings = (-38..38 step 4).map { headingDegrees.toDouble() + it }
            val origin = Point.fromLngLat(point.longitude, point.latitude)
            val ring = listOf(origin) +
                headings.map { geoPoint(point.latitude, point.longitude, length, it) } +
                origin
            FeatureCollection.fromFeatures(arrayOf(
                Feature.fromGeometry(Polygon.fromLngLats(listOf(ring)))
            ))
        } else emptyFeatures()
        (map?.style?.getSource("heading-cone") as? GeoJsonSource)?.setGeoJson(sector)
        if (visibleDirection && point != null) {
            val resolution = 156543.03392 *
                cos(Math.toRadians(point.latitude)).coerceAtLeast(0.01) /
                2.0.pow(map?.cameraPosition?.zoom ?: 13.0)
            val px = kotlin.math.min(mapView.width, mapView.height)
                .coerceAtLeast(dp(320)).toDouble()
            val length = (resolution * px * 0.39).coerceIn(30.0, 800000.0)
            val origin = Point.fromLngLat(point.longitude, point.latitude)
            val outline = listOf(origin) +
                (-38..38 step 4).map {
                    geoPoint(point.latitude, point.longitude, length,
                        headingDegrees.toDouble() + it)
                } + origin
            val border = Feature.fromGeometry(LineString.fromLngLats(outline))
            val ray = Feature.fromGeometry(LineString.fromLngLats(listOf(origin,
                geoPoint(point.latitude, point.longitude, length * 1.07,
                    headingDegrees.toDouble()))))
            (map?.style?.getSource("heading-border") as? GeoJsonSource)?.setGeoJson(
                FeatureCollection.fromFeatures(arrayOf(border)))
            (map?.style?.getSource("heading-ray") as? GeoJsonSource)?.setGeoJson(
                FeatureCollection.fromFeatures(arrayOf(ray)))
        } else {
            (map?.style?.getSource("heading-border") as? GeoJsonSource)
                ?.setGeoJson(emptyFeatures())
            (map?.style?.getSource("heading-ray") as? GeoJsonSource)
                ?.setGeoJson(emptyFeatures())
        }
        val site = servingSite
        val line = if (point == null || site == null) emptyFeatures() else
            FeatureCollection.fromFeatures(arrayOf(Feature.fromGeometry(
                LineString.fromLngLats(listOf(
                    Point.fromLngLat(point.longitude, point.latitude),
                    Point.fromLngLat(site.longitude, site.latitude)
                ))
            )))
        (map?.style?.getSource("cell-link") as? GeoJsonSource)?.setGeoJson(line)
        val cell = if (site == null) emptyFeatures() else FeatureCollection.fromFeatures(
            arrayOf(Feature.fromGeometry(Point.fromLngLat(site.longitude, site.latitude)))
        )
        (map?.style?.getSource("serving-cell") as? GeoJsonSource)?.setGeoJson(cell)
    }

    /** The sole distance origin. Panning the map never changes this coordinate. */
    private fun activeUserPoint(): LatLng? =
        manualPoint ?: location?.let { LatLng(it.latitude, it.longitude) }

    private fun selectTower(tapped: LatLng) {
        val currentMap = map ?: return
        val touch = currentMap.projection.toScreenLocation(tapped)
        val nearest = visible.minByOrNull {
            val p = currentMap.projection.toScreenLocation(LatLng(it.latitude, it.longitude))
            hypot((p.x - touch.x).toDouble(), (p.y - touch.y).toDouble())
        } ?: run {
            details.visibility = View.GONE
            return
        }
        val p = currentMap.projection.toScreenLocation(LatLng(nearest.latitude, nearest.longitude))
        if (hypot((p.x - touch.x).toDouble(), (p.y - touch.y).toDouble()) > dp(30)) {
            details.visibility = View.GONE
            return
        }
        detailTitle.text = nearest.name ?: nearest.operator ?: "Объект связи"
        val from = activeUserPoint()
        val distance = from?.let {
            String.format(Locale.US, "%.2f км", TowerData.distanceKm(
                it.latitude, it.longitude, nearest.latitude, nearest.longitude))
        } ?: "неизвестно"
        detailBody.text = listOfNotNull(
            "Оператор: " + (nearest.operator ?: "не указан"),
            if (nearest.mobile) "Сотовая связь отмечена в OSM" else "Назначение вышки не подтверждено",
            nearest.technology?.let { "Технологии: " + it },
            nearest.height?.let { "Высота: " + it },
            "Расстояние от " +
                if (manualPoint != null) "ручной точки: " + distance
                else "GPS: " + distance,
            String.format(Locale.US, "Координаты: %.6f, %.6f", nearest.latitude, nearest.longitude),
            "База: " + nearest.source,
            "Источник: " + nearest.url
        ).joinToString("\n")
        details.visibility = View.VISIBLE
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 101) {
            if (hasLocation()) startLocationUpdates()
            else status.text = "Геолокация запрещена. Можно искать в центре карты."
            if (cellMonitor.hasPermission()) {
                cellMonitor.start(getPreferences(Context.MODE_PRIVATE).getInt("sim_id", -1))
            } else {
                cellStatus.text = "Для определения обслуживающей соты нужны точная геопозиция и доступ к телефону."
            }
        }
    }
    override fun onStart() { super.onStart(); mapView.onStart() }
    override fun onResume() {
        super.onResume()
        mapView.onResume()
        val prefs = getPreferences(Context.MODE_PRIVATE)
        if (prefs.getBoolean("yandex_return_hint", false)) {
            prefs.edit().remove("yandex_return_hint").apply()
            rootView.post {
                if (!isFinishing && !isDestroyed) {
                    KhakiDialogBuilder(this, palette)
                        .setTitle("Яндекс Карты · ключ Tiles API")
                        .setMessage("Если подключили Tiles API и скопировали ключ, нажмите «Вставить ключ». " +
                            "Если ещё нет — вернитесь в кабинет Яндекса через настройки.")
                        .setPositiveButton("Вставить ключ") { _, _ -> importYandexKeyFromClipboard() }
                        .setNegativeButton("Позже", null).show()
                }
            }
        }
        headingSensor?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }
        if (hasLocation()) startLocationUpdates()
        if (cellMonitor.hasPermission()) {
            cellMonitor.start(getPreferences(Context.MODE_PRIVATE).getInt("sim_id", -1))
        }
        if (headingSensor == null) {
            headingView.text = "Компас недоступен"
            headingView.contentDescription = "На устройстве отсутствует датчик ориентации"
        }
    }
    override fun onPause() {
        sensorManager.unregisterListener(this)
        cellMonitor.stop()
        locationFusion.stop()
        mapView.onPause()
        super.onPause()
    }
    override fun onStop() {
        if (listening) {
            locationManager.removeUpdates(listener)
            listening = false
        }
        locationFusion.stop()
        cellMonitor.stop()
        mapView.onStop()
        super.onStop()
    }
    override fun onLowMemory() { super.onLowMemory(); mapView.onLowMemory() }
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        mapView.onSaveInstanceState(outState)
    }
    override fun onDestroy() {
        executor.shutdownNow()
        cellExecutor.shutdownNow()
        mapView.onDestroy()
        super.onDestroy()
    }
}
