package com.russoturissto.towerradar

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
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
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.Locale
import java.util.concurrent.Executors
import kotlin.math.hypot

/** No background tracking. A search shares the selected coordinates with public Overpass. */
class MainActivity : Activity() {
    private val executor = Executors.newSingleThreadExecutor()
    private lateinit var mapView: MapView
    private lateinit var locationManager: LocationManager
    private lateinit var status: TextView
    private lateinit var radius: Spinner
    private lateinit var operators: Spinner
    private lateinit var refresh: Button
    private lateinit var locate: Button
    private lateinit var searchHere: Button
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

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        MapLibre.getInstance(this)
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        createScreen()
        mapView.onCreate(state)
        mapView.getMapAsync { ready ->
            map = ready
            ready.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(20.0, 0.0), 2.5))
            ready.setStyle("https://tiles.openfreemap.org/styles/liberty") { style ->
                style.addSource(GeoJsonSource("towers", emptyFeatures()))
                style.addSource(GeoJsonSource("my-location", emptyFeatures()))

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
                        PropertyFactory.circleColor(Color.rgb(37, 99, 235)),
                        PropertyFactory.circleRadius(7f),
                        PropertyFactory.circleStrokeColor(Color.WHITE),
                        PropertyFactory.circleStrokeWidth(2f)
                    )
                })
                style.addLayer(CircleLayer("location-halo", "my-location").apply {
                    setProperties(
                        PropertyFactory.circleColor(Color.rgb(147, 197, 253)),
                        PropertyFactory.circleRadius(16f),
                        PropertyFactory.circleOpacity(0.35f)
                    )
                })
                style.addLayer(CircleLayer("location-dot", "my-location").apply {
                    setProperties(
                        PropertyFactory.circleColor(Color.rgb(29, 78, 216)),
                        PropertyFactory.circleRadius(6f),
                        PropertyFactory.circleStrokeColor(Color.WHITE),
                        PropertyFactory.circleStrokeWidth(3f)
                    )
                })
                styled = true
                updateDot()
                showPins()
                if (!initiallyCentered) center?.let(::moveCamera)
            }
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
            background = rounded(Color.rgb(249, 251, 255))
            elevation = dp(8).toFloat()
        }
        val panelParams = FrameLayout.LayoutParams(-1, -2, Gravity.TOP).apply {
            setMargins(dp(12), dp(12), dp(12), 0)
        }
        root.addView(panel, panelParams)
        panel.addView(text("◉  TowerRadar", 20f, Color.rgb(23, 37, 84)).apply {
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        })
        status = text("Определяем местоположение…", 12f, Color.rgb(71, 85, 105))
        panel.addView(status)

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
        refresh = Button(this).apply { text = "Обновить вышки"; isAllCaps = false; textSize = 12f }
        searchHere = Button(this).apply { text = "Искать здесь"; isAllCaps = false; textSize = 12f }
        actions.addView(refresh, LinearLayout.LayoutParams(0, dp(48), 1f))
        actions.addView(searchHere, LinearLayout.LayoutParams(0, dp(48), 1f))

        locate = Button(this).apply {
            text = "◎"
            textSize = 25f
            isAllCaps = false
            contentDescription = "Показать моё местоположение"
            background = rounded(Color.WHITE)
            elevation = dp(6).toFloat()
        }
        root.addView(locate, FrameLayout.LayoutParams(dp(54), dp(54), Gravity.END or Gravity.BOTTOM).apply {
            setMargins(0, 0, dp(14), dp(64))
        })

        details = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(12))
            background = rounded(Color.WHITE)
            elevation = dp(9).toFloat()
            visibility = View.GONE
        }
        root.addView(details, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM).apply {
            setMargins(dp(12), 0, dp(12), dp(52))
        })
        val detailRow = row()
        details.addView(detailRow)
        detailTitle = text("", 17f, Color.rgb(15, 23, 42)).apply {
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }
        detailRow.addView(detailTitle, LinearLayout.LayoutParams(0, -2, 1f))
        detailRow.addView(Button(this).apply {
            text = "✕"
            isAllCaps = false
            setOnClickListener { details.visibility = View.GONE }
        }, LinearLayout.LayoutParams(dp(54), dp(44)))
        detailBody = text("", 13f, Color.rgb(71, 85, 105))
        details.addView(detailBody)

        root.addView(text("© OpenStreetMap contributors · OpenFreeMap", 10f, Color.rgb(51, 65, 85)).apply {
            setPadding(dp(6), dp(3), dp(6), dp(3))
            background = rounded(Color.WHITE, 3)
        }, FrameLayout.LayoutParams(-2, -2, Gravity.START or Gravity.BOTTOM).apply {
            setMargins(dp(8), 0, 0, dp(9))
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
            center = map?.cameraPosition?.target
            manualCenter = true
            load(force = true)
        }
        locate.setOnClickListener {
            manualCenter = false
            val current = location
            if (current == null) {
                askLocation()
                status.text = "GPS недоступен. Переместите карту и нажмите «Искать здесь»."
            } else {
                val newCenter = LatLng(current.latitude, current.longitude)
                center = newCenter
                moveCamera(newCenter)
                load(force = false)
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
        if (hasLocation()) startLocationUpdates()
        else {
            requestPermissions(arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION
            ), 101)
            status.text = "Разрешите геолокацию или воспользуйтесь кнопкой «Искать здесь»."
        }
    }

    @SuppressLint("MissingPermission")
    private fun startLocationUpdates() {
        if (!hasLocation() || listening) return
        val enabled = locationManager.getProviders(true)
        for (provider in listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER)) {
            if (provider !in enabled) continue
            try {
                locationManager.getLastKnownLocation(provider)?.let(::showLocation)
                locationManager.requestLocationUpdates(provider, 10000L, 50f, listener, Looper.getMainLooper())
                listening = true
            } catch (_: SecurityException) {
            } catch (_: IllegalArgumentException) {
            }
        }
        if (!listening) status.text = "Геолокация выключена. Выберите центр карты вручную."
    }

    private fun showLocation(newLocation: Location) {
        val old = location
        if (old != null && newLocation.time + 1000L < old.time) return
        location = newLocation
        updateDot()
        if (manualCenter) return
        val point = LatLng(newLocation.latitude, newLocation.longitude)
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
        val request = TowerData.query(latitude, longitude, range)
        val body = "data=" + URLEncoder.encode(request, StandardCharsets.UTF_8.name())
        val connection = (URL("https://overpass-api.de/api/interpreter").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = 12000
            readTimeout = 35000
            setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "TowerRadar/0.1.0 (https://github.com/RussoTurissto/TowerRadar)")
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
                val result = cached ?: fetch(lat, lon, range)
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
                    status.text = "Найдено: " + sites.size + " · $range км · " +
                        if (cached != null) "кеш" else "OpenStreetMap"
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
        val point = location ?: return
        if (!styled) return
        val marker = Feature.fromGeometry(Point.fromLngLat(point.longitude, point.latitude))
        (map?.style?.getSource("my-location") as? GeoJsonSource)
            ?.setGeoJson(FeatureCollection.fromFeatures(arrayOf(marker)))
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
            "Источник: " + nearest.url
        ).joinToString("\n")
        details.visibility = View.VISIBLE
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 101) {
            if (hasLocation()) startLocationUpdates()
            else status.text = "Геолокация запрещена. Можно искать в центре карты."
        }
    }
    override fun onStart() { super.onStart(); mapView.onStart() }
    override fun onResume() { super.onResume(); mapView.onResume(); if (hasLocation()) startLocationUpdates() }
    override fun onPause() { mapView.onPause(); super.onPause() }
    override fun onStop() {
        if (listening) {
            locationManager.removeUpdates(listener)
            listening = false
        }
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
        mapView.onDestroy()
        super.onDestroy()
    }
}
