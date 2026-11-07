package com.russoturissto.towerradar

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
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
            color = palette.olive
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

    private fun applyBasemap(provider: MapProvider, key: String = "") {
        val current = map ?: return
        styled = false
        providerIndex = MapSources.providers.indexOf(provider).coerceAtLeast(0)
        if (::mapStatus.isInitialized) mapStatus.text = "Подложка: " + provider.name
        if (::mapAttribution.isInitialized) mapAttribution.text = provider.attribution
        if (::brandLink.isInitialized) {
            brandLink.visibility = if (provider.id == "yandex") View.VISIBLE else View.GONE
        }
        current.setStyle(MapSources.style(provider, key)) { style ->
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
            style.addSource(GeoJsonSource("cell-link", emptyFeatures()))
            style.addSource(GeoJsonSource("serving-cell", emptyFeatures()))

            style.addLayer(FillLayer("gps-accuracy-fill", "gps-accuracy").apply {
                setProperties(PropertyFactory.fillColor(palette.olive), PropertyFactory.fillOpacity(0.10f))
            })
            style.addLayer(FillLayer("heading-cone-fill", "heading-cone").apply {
                setProperties(PropertyFactory.fillColor(palette.olive), PropertyFactory.fillOpacity(0.24f))
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
            requestMapKey(provider)
            return
        }
        if (choosingMap) {
            mapStatus.text = "Сервер карты ещё проверяется."
            return
        }
        choosingMap = true
        mapStatus.text = "Проверяем " + provider.name + "…"
        val point = location?.let { LatLng(it.latitude, it.longitude) }
            ?: center ?: LatLng(49.0, 39.0)
        executor.execute {
            val reachable = MapSources.reachable(provider, point.latitude, point.longitude, key)
            runOnUiThread {
                choosingMap = false
                if (isFinishing || isDestroyed) return@runOnUiThread
                if (reachable) {
                    getPreferences(Context.MODE_PRIVATE).edit()
                        .putString("map_provider", provider.id).apply()
                    applyBasemap(provider, key)
                } else {
                    mapStatus.text = "Не удалось загрузить " + provider.name +
                        ". Проверьте ключ или выберите другой сервер."
                }
            }
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
        AlertDialog.Builder(this)
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
            "Тема оформления: " + when(getPreferences(Context.MODE_PRIVATE)
                .getString("app_theme", KhakiTheme.SYSTEM)) {
                    KhakiTheme.DAY -> "Дневная"
                    KhakiTheme.NIGHT -> "Ночная"
                    else -> "Системная"
                },
            "Геопозиция: " + if (manualPoint == null) "Автоматическая" else "Ручная",
            "SIM для отслеживания",
            "Ключи Яндекс / 2ГИС",
            "Открыть кабинет Яндекс ID",
            "Дополнительные соты OpenCellID",
            "Сектор направления: " + if (showCone) "Включён" else "Выключен"
        )
        AlertDialog.Builder(this).setTitle("Настройки · TowerRadar")
            .setItems(items) { _, selected ->
                when (selected) {
                    0 -> selectTheme()
                    1 -> showPositionOptions()
                    2 -> selectSim()
                    3 -> AlertDialog.Builder(this).setTitle("Картографические ключи")
                        .setItems(arrayOf("Яндекс Карты", "2ГИС")) { _, index ->
                            requestMapKey(MapSources.providers.first { it.id ==
                                if (index == 0) "yandex" else "2gis" })
                        }.show()
                    4 -> startActivity(Intent(Intent.ACTION_VIEW,
                        Uri.parse("https://developer.tech.yandex.ru/")))
                    5 -> editCellKey()
                    6 -> {
                        showCone = !showCone
                        getPreferences(Context.MODE_PRIVATE).edit()
                            .putBoolean("show_cone", showCone).apply()
                        updateOverlays()
                    }
                }
            }.setNegativeButton("Закрыть", null).show()
    }

    private fun selectTheme() {
        val prefs = getPreferences(Context.MODE_PRIVATE)
        val current = prefs.getString("app_theme", KhakiTheme.SYSTEM)
        AlertDialog.Builder(this).setTitle("Тема оформления")
            .setSingleChoiceItems(KhakiTheme.names,
                KhakiTheme.values.indexOf(current).coerceAtLeast(0)) { dialog, selected ->
                prefs.edit().putString("app_theme", KhakiTheme.values[selected]).apply()
                dialog.dismiss()
                recreate()
            }.setNegativeButton("Отмена", null).show()
    }

    private fun selectSim() {
        if (!cellMonitor.hasPermission()) {
            AlertDialog.Builder(this).setTitle("Доступ к SIM")
                .setMessage("Чтобы определить активную соту и уровень сигнала, нужен доступ к телефону и точному местоположению.")
                .setPositiveButton("Разрешить") { _, _ -> askLocation() }
                .setNegativeButton("Отмена", null).show()
            return
        }
        val options = cellMonitor.availableSubscriptions()
        val labels = arrayOf("Автоматически · SIM для интернета") +
            options.map { it.second + " (SIM ID " + it.first + ")" }
        AlertDialog.Builder(this).setTitle("Отслеживать сеть SIM")
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
        AlertDialog.Builder(this).setTitle("Моя геопозиция")
            .setItems(arrayOf("Установить в центре карты", "Ввести координаты",
                "Вернуться к автоматической GPS")) { _, selection ->
                when (selection) {
                    0 -> map?.cameraPosition?.target?.let(::setManualPosition)
                    1 -> enterManualCoordinates()
                    2 -> useAutomaticPosition()
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
        AlertDialog.Builder(this).setTitle("Геопозиция вручную")
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
        updateDot()
        moveCamera(point)
        lastFetch = null
        load(force = true)
    }

    private fun useAutomaticPosition() {
        manualPoint = null
        manualCenter = false
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
        AlertDialog.Builder(this).setTitle("Подключённая сеть")
            .setMessage(description).setPositiveButton("Закрыть", null).show()
    }

    private fun editCellKey() {
        val field = EditText(this).apply {
            hint = "Ключ OpenCellID"
            setSingleLine(true)
            setText(getPreferences(Context.MODE_PRIVATE).getString("opencellid_key", ""))
            setSelectAllOnFocus(true)
        }
        AlertDialog.Builder(this)
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
                selectTower(tapped)
                true
            }
        }
        askLocation()
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
        ArrayAdapter(this, android.R.layout.simple_spinner_item, labels).also {
            it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
    private fun row(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
    }

    private fun createScreen() {
        val root = FrameLayout(this)
        setContentView(root)
        mapView = MapView(this)
        root.addView(mapView, FrameLayout.LayoutParams(-1, -1))

        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(12))
            background = rounded(palette.surface)
            elevation = dp(8).toFloat()
        }
        val panelParams = FrameLayout.LayoutParams(-1, -2, Gravity.TOP).apply {
            setMargins(dp(12), dp(12), dp(12), 0)
        }
        root.addView(panel, panelParams)
        val header = row()
        panel.addView(header)
        val title = text("◉  TowerRadar", 20f, palette.text).apply {
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }
        header.addView(title, LinearLayout.LayoutParams(0, dp(43), 1f).apply {
            gravity = Gravity.CENTER_VERTICAL
        })
        gpsRefresh = styledButton("⌖", 22f)
        gpsRefresh.contentDescription = "Уточнить GPS"
        gpsRefresh.setOnClickListener {
            if (!manualCenter) {
                startLocationUpdates()
                locationFusion.refresh()
                status.text = "Уточняем местоположение через GPS и Wi-Fi…"
            } else status.text = "Активна ручная геопозиция. Для GPS нажмите кнопку ◎."
        }
        header.addView(gpsRefresh, LinearLayout.LayoutParams(dp(43), dp(42)).apply {
            marginEnd = dp(6)
        })
        settingsButton = styledButton("⚙", 23f)
        settingsButton.contentDescription = "Настройки TowerRadar"
        settingsButton.setOnClickListener { showSettings() }
        header.addView(settingsButton, LinearLayout.LayoutParams(dp(43), dp(42)))
        status = text("Определяем местоположение…", 12f, palette.secondary)
        panel.addView(status)
        gpsLabel = text("GPS / Wi-Fi: ожидаем координаты", 11f, palette.secondary)
        panel.addView(gpsLabel)
        mapStatus = text("Выбираем доступный сервер карты…", 11f, palette.secondary)
        panel.addView(mapStatus)

        val signalRow = row()
        signalRow.setPadding(0, dp(6), 0, 0)
        panel.addView(signalRow, LinearLayout.LayoutParams(-1, dp(47)))
        signalLabel = text("Сеть: ожидаем данные SIM", 13f, palette.text).apply {
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }
        signalRow.addView(signalLabel, LinearLayout.LayoutParams(0, -2, 1f))
        signalBars = LinearLayout(this).apply {
            gravity = Gravity.BOTTOM
            orientation = LinearLayout.HORIZONTAL
        }
        for (h in listOf(11, 15, 19, 23, 27)) {
            signalBars.addView(View(this).apply {
                background = rounded(palette.signalMuted, 3)
            }, LinearLayout.LayoutParams(dp(10), dp(h)).apply { marginStart = dp(4) })
        }
        signalRow.addView(signalBars, LinearLayout.LayoutParams(-2, dp(29)))
        signalDetails = text("Сигнал: нет измерения", 11f, palette.secondary)
        panel.addView(signalDetails)
        cellStatus = text("Обслуживающая сота: определяем…", 11f, palette.secondary)
        panel.addView(cellStatus)
        signalRow.setOnClickListener { showSignalDetails() }
        signalDetails.setOnClickListener { showSignalDetails() }
        cellStatus.setOnClickListener { showSignalDetails() }

        val filterRow = row()
        panel.addView(filterRow, LinearLayout.LayoutParams(-1, dp(52)).apply { topMargin = dp(8) })
        radius = Spinner(this)
        radius.adapter = choice(TowerData.radiiKm.map { "$it км" })
        radius.setSelection(TowerData.radiiKm.indexOf(radiusKm))
        filterRow.addView(radius, LinearLayout.LayoutParams(dp(102), dp(48)))
        operators = Spinner(this)
        filterRow.addView(operators, LinearLayout.LayoutParams(0, dp(48), 1f).apply { leftMargin = dp(8) })
        updateOperatorChoices()

        val actions = row()
        panel.addView(actions, LinearLayout.LayoutParams(-1, dp(49)))
        refresh = styledButton("Обновить вышки", 12f, true)
        searchHere = styledButton("Искать здесь", 12f)
        actions.addView(refresh, LinearLayout.LayoutParams(0, dp(48), 1f))
        actions.addView(searchHere, LinearLayout.LayoutParams(0, dp(48), 1f))
        val options = row()
        panel.addView(options, LinearLayout.LayoutParams(-1, dp(44)))
        headingView = text("➤ —°", 15f, palette.olive).apply {
            gravity = Gravity.CENTER_VERTICAL
        }
        options.addView(headingView, LinearLayout.LayoutParams(dp(103), dp(44)))
        sourceSpinner = Spinner(this)
        sourceSpinner.adapter = choice(listOf("Карта: авто") + MapSources.providers.map { it.name })
        val prefId = getPreferences(Context.MODE_PRIVATE)
            .getString("map_provider", "auto") ?: "auto"
        val initial = MapSources.providers.indexOfFirst { it.id == prefId }
        sourceSpinner.setSelection(initial + 1)
        keyButton = styledButton("Доп. соты", 11f)
        options.addView(sourceSpinner, LinearLayout.LayoutParams(0, dp(44), 1f))
        options.addView(keyButton, LinearLayout.LayoutParams(dp(96), dp(44)))
        sourceSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
            override fun onItemSelected(
                parent: AdapterView<*>?, view: View?, position: Int, id: Long
            ) {
                if (map == null) return
                if (position == 0) {
                    getPreferences(Context.MODE_PRIVATE).edit()
                        .putString("map_provider", "auto").apply()
                    chooseBasemap()
                } else {
                    useMapChoice(MapSources.providers[position - 1])
                }
            }
        }
        sourceSpinner.setOnLongClickListener {
            val item = MapSources.providers.getOrNull(sourceSpinner.selectedItemPosition - 1)
            if (item?.keyPref != null) {
                requestMapKey(item)
                true
            } else false
        }
        keyButton.setOnClickListener { editCellKey() }

        locate = styledButton("◎", 25f, true).apply {
            contentDescription = "Вернуться к GPS"
            elevation = dp(6).toFloat()
            setOnLongClickListener { showPositionOptions(); true }
        }
        root.addView(locate, FrameLayout.LayoutParams(dp(54), dp(54), Gravity.END or Gravity.BOTTOM).apply {
            setMargins(0, 0, dp(14), dp(64))
        })

        details = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(12))
            background = rounded(palette.surface)
            elevation = dp(9).toFloat()
            visibility = View.GONE
        }
        root.addView(details, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM).apply {
            setMargins(dp(12), 0, dp(12), dp(52))
        })
        val detailRow = row()
        details.addView(detailRow)
        detailTitle = text("", 17f, palette.text).apply {
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }
        detailRow.addView(detailTitle, LinearLayout.LayoutParams(0, -2, 1f))
        detailRow.addView(styledButton("✕", 18f).apply {
            setOnClickListener { details.visibility = View.GONE }
        }, LinearLayout.LayoutParams(dp(54), dp(44)))
        detailBody = text("", 13f, palette.secondary)
        details.addView(detailBody)

        mapAttribution = text("© OpenStreetMap contributors", 10f, palette.text).apply {
            setPadding(dp(6), dp(3), dp(6), dp(3))
            background = rounded(palette.surface, 3)
        }
        root.addView(mapAttribution, FrameLayout.LayoutParams(-2, -2, Gravity.START or Gravity.BOTTOM).apply {
            setMargins(dp(8), 0, 0, dp(9))
        })
        brandLink = text("Яндекс Карты", 12f, palette.olive).apply {
            setPadding(dp(8), dp(5), dp(8), dp(5))
            background = rounded(palette.surface, 4)
            visibility = View.GONE
            setOnClickListener {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://yandex.ru/maps/")))
            }
        }
        root.addView(brandLink, FrameLayout.LayoutParams(-2, -2, Gravity.END or Gravity.BOTTOM).apply {
            setMargins(0, 0, dp(8), dp(8))
        })

        radius.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val newValue = TowerData.radiiKm[position]
                if (radiusKm != newValue) {
                    radiusKm = newValue
                    load(force = true)
                }
            }
        }
        refresh.setOnClickListener { load(force = true) }
        searchHere.setOnClickListener {
            map?.cameraPosition?.target?.let(::setManualPosition)
        }
        locate.setOnClickListener { useAutomaticPosition() }
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
        if (busy) return
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
        val point = manualPoint ?: location?.let { LatLng(it.latitude, it.longitude) }
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
        val sector = if (showCone && headingSensor != null && point != null) {
            val headings = (-35..35 step 5).map { headingDegrees.toDouble() + it }
            val ring = listOf(Point.fromLngLat(point.longitude, point.latitude)) +
                headings.map { geoPoint(point.latitude, point.longitude, 600.0, it) } +
                Point.fromLngLat(point.longitude, point.latitude)
            FeatureCollection.fromFeatures(arrayOf(
                Feature.fromGeometry(Polygon.fromLngLats(listOf(ring)))
            ))
        } else emptyFeatures()
        (map?.style?.getSource("heading-cone") as? GeoJsonSource)?.setGeoJson(sector)
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
        val from = location?.let { LatLng(it.latitude, it.longitude) } ?: center
        val distance = from?.let {
            String.format(Locale.US, "%.2f км", TowerData.distanceKm(
                it.latitude, it.longitude, nearest.latitude, nearest.longitude))
        } ?: "неизвестно"
        detailBody.text = listOfNotNull(
            "Оператор: " + (nearest.operator ?: "не указан"),
            if (nearest.mobile) "Сотовая связь отмечена в OSM" else "Назначение вышки не подтверждено",
            nearest.technology?.let { "Технологии: " + it },
            nearest.height?.let { "Высота: " + it },
            "Расстояние: " + distance,
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
