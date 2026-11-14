package com.russoturissto.towerradar

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.animation.ValueAnimator
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.view.ViewTreeObserver
import android.view.animation.LinearInterpolator
import android.content.ClipboardManager
import android.view.MotionEvent
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.text.TextUtils
import android.view.Surface
import android.view.ViewGroup
import org.maplibre.android.camera.CameraPosition
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
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.text.InputType
import android.content.pm.PackageManager
import android.graphics.Rect
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
import android.widget.ProgressBar
import android.os.Handler
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
    private val apiExecutor = Executors.newFixedThreadPool(2)
    private lateinit var topHeader: LinearLayout
    private lateinit var topStatusPanel: TowerStatusHeader
    private lateinit var mapCoordinatesLabel: TextView
    private var mapCenterCoordinateValue: String? = null
    private lateinit var statusBarProtection: View
    private var topPanelHeight = 0
    private lateinit var crosshairReticle: CrosshairView
    private lateinit var northIndicator: NorthIndicatorView
    private var directionPanel: LinearLayout? = null
    private lateinit var crosshairControls: LinearLayout
    private lateinit var crosshairCoordinates: TextView
    private var isChoosingCrosshair = false
    private var crosshairStart: LatLng? = null
    private var crosshairStartZoom = 12.5
    private var systemTopInset = 0
    private var systemBottomInset = 0
    private var beamBrightness = 85
    private var sectorBrightness = 35
    private var sectorLengthPercent = 30
    private var lastCameraSaveAt = 0L
    private var restoredLastCamera = false
    private var systemLeftInset = 0
    private var systemRightInset = 0
    private var sheetAnimator: ValueAnimator? = null
    private val landscape: Boolean get() = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    private var pendingEncryptedBackup: ByteArray? = null
    private val apiChecks = mutableMapOf<String, ApiProbe>()
    private val checkingApi = mutableSetOf<String>()
    private val googleSessions = mutableMapOf<String, GoogleTileSession>()
    private var googleMapSession: GoogleTileSession? = null
    private var lastGoogleAttributionAt = 0L
    private var lastGoogleAttributionKey = ""
    private lateinit var rootView: FrameLayout
    private lateinit var sheetView: LinearLayout
    private lateinit var yandexConnectButton: Button
    private var consumingOutsidePanelGesture = false
    private lateinit var sheetExtra: ScrollView
    private lateinit var sheetHandle: TextView
    private lateinit var compactSignalBars: LinearLayout
    private lateinit var expandedSignalRow: LinearLayout
    private lateinit var compactHandleRow: FrameLayout
    private lateinit var floatingRail: LinearLayout
    private lateinit var actionPopup: LinearLayout
    private lateinit var actionHeightRow: LinearLayout
    private lateinit var actionTowerRow: LinearLayout
    private lateinit var actionExpandButton: Button
    private var actionMenuExpanded = false
    private var gpsFocusPending = false
    private lateinit var fullScreenButton: Button
    private var sheetExpanded = false
    private var mapFullscreen = false
    private var compassBounceAnimator: ValueAnimator? = null
    private var compassBounceGeneration = 0
    private var servingPulseAnimator: ValueAnimator? = null
    private var servingPulseFrameAt = 0L

    // Independent of the map-only ⛶ button: system chrome and camera-safe top information.
    private var displayMode = DisplayModePolicy.STANDARD
    // Status and radio diagnostics are combined in topStatusPanel.
    private lateinit var mapTileCache: MapTileCache
    private lateinit var offlineAreas: OfflineAreaManager
    private var offlineSelectionFrame: OfflineSelectionFrame? = null
    private var offlineSelectionControls: LinearLayout? = null
    private var offlineSelectionInfo: TextView? = null
    private var offlineSelectionEnabled = false
    private lateinit var mapView: MapView
    private var yandexWebMap: YandexWebMap? = null
    private lateinit var profileButton: Button
    private lateinit var locationFusion: LocationFusion
    private val cellExecutor = Executors.newSingleThreadExecutor()
    private lateinit var gpsLabel: TextView
    private lateinit var cellMonitor: CellMonitor
    private var servingCell: ServingCell? = null
    private var radioSignal: RadioSignal? = null
    private var nearbyCells: List<ServingCell> = emptyList()
    private lateinit var neighborsButton: Button
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
    private lateinit var keyButton: Button
    private lateinit var headingView: TextView
    private lateinit var mapStatus: TextView
    private lateinit var sensorManager: SensorManager
    private var headingSensor: Sensor? = null
    private var headingDegrees = 0f
    private var lastHeadingOverlay = Float.NaN
    private var lastHeadingOverlayAt = 0L
    private var lastSensorAccuracy = -1
    private var providerIndex = 0
    private var choosingMap = false
    private lateinit var details: LinearLayout
    private lateinit var detailTitle: TextView
    private lateinit var detailBody: TextView
    private lateinit var detailCoordinates: TextView
    private var autoUpdateAttempted = false
    private var pendingOtaInstall: TowerRelease? = null

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
    private lateinit var towerDatabase: TowerDatabase
    private lateinit var userTowerStore: UserTowerStore
    private var userTowerEditor: UserTowerEditor? = null
    private lateinit var coverageButton: Button
    private lateinit var manualTowerActions: LinearLayout
    private lateinit var photoButton: Button
    private lateinit var removeTowerButton: Button
    private var coverageTowerId: String? = null
    private var coverageRadiusMetres: Int = 0
    private lateinit var progressStrip: LinearLayout
    private lateinit var progressLabel: TextView
    private lateinit var progressBar: ProgressBar
    private val progressHandler = Handler(Looper.getMainLooper())
    private var progressSequence = 0
    private var progressRequested = false
    private var lastTowerCardVisible = false
    private var selectedOriginPoint: LatLng? = null
    private var selectedOriginAccuracy: Float? = null
    private var terrainRevision = 0
    private var terrainDialog: android.app.Dialog? = null
    private var technologyFilter: String? = null
    private var sourceFilter: String? = null
    private var visible = emptyList<TowerSite>()
    private var selectedAimSite: TowerSite? = null
    private var aimAligned = false
    private var aimAlignmentInitialized = false
    private var lastAimPulseAt = -10000L
    private var hasHeadingReading = false
    private var aimForeground = false
    private val unknown = "• Не указан"

    private val listener = object : LocationListener {
        override fun onLocationChanged(newLocation: Location) = showLocation(newLocation)
        override fun onProviderEnabled(provider: String) = Unit
        override fun onProviderDisabled(provider: String) = Unit
        @Deprecated("Deprecated by Android")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
    }


    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        if (sensor?.type == headingSensor?.type) lastSensorAccuracy = accuracy
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_ROTATION_VECTOR) return
        val rotation = FloatArray(9)
        val adjusted = FloatArray(9)
        val orientation = FloatArray(3)
        SensorManager.getRotationMatrixFromVector(rotation, event.values)
        val axes = when (windowManager.defaultDisplay.rotation) {
            Surface.ROTATION_90 -> SensorManager.AXIS_Y to SensorManager.AXIS_MINUS_X
            Surface.ROTATION_180 -> SensorManager.AXIS_MINUS_X to SensorManager.AXIS_MINUS_Y
            Surface.ROTATION_270 -> SensorManager.AXIS_MINUS_Y to SensorManager.AXIS_X
            else -> SensorManager.AXIS_X to SensorManager.AXIS_Y
        }
        if (SensorManager.remapCoordinateSystem(rotation, axes.first, axes.second, adjusted)) {
            SensorManager.getOrientation(adjusted, orientation)
        } else SensorManager.getOrientation(rotation, orientation)
        val point = location
        val declination = if (point == null) 0f else GeomagneticField(
            point.latitude.toFloat(), point.longitude.toFloat(),
            point.altitude.toFloat(), System.currentTimeMillis()).declination
        val degrees = ((Math.toDegrees(orientation[0].toDouble()).toFloat() + declination) % 360f + 360f) % 360f
        val diff = kotlin.math.abs((degrees - headingDegrees + 540f) % 360f - 180f)
        if (diff < 0.7f && headingView.text.toString() != "➤ —°") return
        // Shortest-arc smoothing reduces sensor jitter without lagging a real turn.
        headingDegrees = if (headingView.text.toString() == "➤ —°") degrees
            else (headingDegrees + ((degrees - headingDegrees + 540f) % 360f - 180f) * 0.45f + 360f) % 360f
        hasHeadingReading = true
        updateAimAlignment(vibrateOnEnter = true)
        if (::northIndicator.isInitialized) northIndicator.setCompassHeading(headingDegrees)
        if (::headingView.isInitialized) {
            val directions = arrayOf("С", "СВ", "В", "ЮВ", "Ю", "ЮЗ", "З", "СЗ")
            val direction = directions[((headingDegrees + 22.5f).toInt() / 45) % 8]
            headingView.text = "➤ " + headingDegrees.toInt() + "° " + direction
        }
        // Rendering several GeoJSON layers on every sensor event causes jitter.
        val now = SystemClock.elapsedRealtime()
        val delta = if (lastHeadingOverlay.isNaN()) 360f
            else kotlin.math.abs((headingDegrees - lastHeadingOverlay + 540f) % 360f - 180f)
        if (delta >= 1.5f && now - lastHeadingOverlayAt >= 85L) {
            lastHeadingOverlayAt = now
            lastHeadingOverlay = headingDegrees
            updateOverlays()
        }
    }

    private fun applyBasemap(provider: MapProvider, key: String = "",
                             session: GoogleTileSession? = null) {
        val current = map ?: return
        if (MapSources.yandexWeb(provider)) {
            showYandexWeb(provider, key)
            return
        }
        yandexWebMap?.hide()
        mapView.visibility = View.VISIBLE
        if (provider.id.startsWith("google-") && (session == null || !session.valid())) {
            mapStatus.text = "Google · требуется действующий сеанс Map Tiles API"
            return
        }
        googleMapSession = if (provider.id.startsWith("google-")) session else null
        lastGoogleAttributionAt = 0L
        stopServingPulse()
        styled = false
        providerIndex = MapSources.providers.indexOf(provider).coerceAtLeast(0)
        getPreferences(Context.MODE_PRIVATE).edit()
            .putString("map_last_resolved", provider.id).apply()
        if (::mapStatus.isInitialized) mapStatus.text = "Подложка: " + provider.name
        if (::mapAttribution.isInitialized) mapAttribution.text =
            if (provider.id.startsWith("google-"))
                "Google Maps · данные об авторских правах загружаются"
            else provider.attribution
        // Keep required provider credits in the existing bottom-left attribution.
        // Do not place another floating link over the map controls.
        if (::mapAttribution.isInitialized) {
            val brandUrl = AttributionUiPolicy.creditLink(provider.id)
            mapAttribution.isClickable = brandUrl != null
            mapAttribution.isFocusable = brandUrl != null
            mapAttribution.contentDescription =
                provider.attribution + if (brandUrl == null) "" else " · сведения о карте"
            mapAttribution.setOnClickListener(if (brandUrl == null) null else
                View.OnClickListener {
                    try {
                        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(brandUrl)))
                    } catch (_: Exception) {
                        Toast.makeText(this, "Не удалось открыть браузер", Toast.LENGTH_SHORT).show()
                    }
                })
        }
        current.setStyle(MapSources.style(provider, key, session?.token.orEmpty())) { style ->
            if (palette.dark && (provider.keyPref == null || provider.id == "yandex")) {
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
            style.addSource(GeoJsonSource("heading-near", emptyFeatures()))
            style.addSource(GeoJsonSource("heading-border", emptyFeatures()))
            style.addSource(GeoJsonSource("heading-ray", emptyFeatures()))
            style.addSource(GeoJsonSource("cell-link", emptyFeatures()))
            style.addSource(GeoJsonSource("serving-cell", emptyFeatures()))
            style.addSource(GeoJsonSource("selected-aim", emptyFeatures()))
            style.addSource(GeoJsonSource("coverage-preview", emptyFeatures()))

            // Approximate manually chosen radius, clearly different from RF prediction.
            style.addLayer(FillLayer("coverage-approx-fill", "coverage-preview").apply {
                setProperties(PropertyFactory.fillColor(palette.olive),
                    PropertyFactory.fillOpacity(0.14f))
            })
            style.addLayer(LineLayer("coverage-approx-border", "coverage-preview").apply {
                setProperties(PropertyFactory.lineColor(palette.olive),
                    PropertyFactory.lineWidth(1.6f),
                    PropertyFactory.lineOpacity(0.66f),
                    PropertyFactory.lineDasharray(arrayOf(2.5f, 2f)))
            })
            style.addLayer(FillLayer("gps-accuracy-fill", "gps-accuracy").apply {
                setProperties(PropertyFactory.fillColor(palette.olive), PropertyFactory.fillOpacity(0.10f))
            })
            // Compact radar: soft cyan fill, lightly stroked arc, dashed central
            // bearing. Beam and sector have separate 0–100% intensity controls.
            style.addLayer(FillLayer("heading-cone-fill", "heading-cone").apply {
                setProperties(PropertyFactory.fillColor(Color.rgb(20, 189, 211)),
                    PropertyFactory.fillOpacity(sectorAlpha()))
            })
            style.addLayer(FillLayer("heading-near-fill", "heading-near").apply {
                setProperties(PropertyFactory.fillColor(Color.rgb(68, 230, 229)),
                    PropertyFactory.fillOpacity(sectorAlpha() * .38f))
            })
            style.addLayer(LineLayer("heading-border-halo", "heading-border").apply {
                setProperties(PropertyFactory.lineColor(Color.WHITE),
                    PropertyFactory.lineWidth(3f),
                    PropertyFactory.lineOpacity(sectorAlpha() * .20f))
            })
            style.addLayer(LineLayer("heading-border-color", "heading-border").apply {
                setProperties(PropertyFactory.lineColor(Color.rgb(5, 144, 177)),
                    PropertyFactory.lineWidth(1.5f),
                    PropertyFactory.lineOpacity(sectorAlpha() * .85f))
            })
            // Draw the selected-site guide above the fan but below the live heading beam.
            style.addLayer(LineLayer("selected-aim-guide", "selected-aim").apply {
                setProperties(
                    PropertyFactory.lineColor(Color.rgb(184, 119, 48)),
                    PropertyFactory.lineWidth(2.5f),
                    PropertyFactory.lineOpacity(0.74f),
                    PropertyFactory.lineDasharray(arrayOf(2.1f, 2.3f))
                )
            })
            style.addLayer(LineLayer("heading-center-halo", "heading-ray").apply {
                setProperties(PropertyFactory.lineColor(Color.WHITE),
                    PropertyFactory.lineWidth(5f),
                    PropertyFactory.lineOpacity(beamAlpha() * .24f),
                    PropertyFactory.lineDasharray(arrayOf(1.4f, 2.1f)))
            })
            style.addLayer(LineLayer("heading-center-color", "heading-ray").apply {
                setProperties(PropertyFactory.lineColor(Color.rgb(0, 195, 220)),
                    PropertyFactory.lineWidth(3.1f),
                    PropertyFactory.lineOpacity(beamAlpha()),
                    PropertyFactory.lineDasharray(arrayOf(1.4f, 2.1f)))
            })
            style.addLayer(LineLayer("serving-line", "cell-link").apply {
                setProperties(PropertyFactory.lineColor(Color.rgb(215, 141, 45)),
                    PropertyFactory.lineWidth(3.3f),
                    PropertyFactory.lineDasharray(arrayOf(2.4f, 2.0f)))
            })
            // A restrained, slowly expanding ring only around an identified
            // serving radio cell; estimated cell positions are never real masts.
            style.addLayer(CircleLayer("serving-cell-pulse", "serving-cell").apply {
                setProperties(
                    PropertyFactory.circleColor(Color.TRANSPARENT),
                    PropertyFactory.circleRadius(11f),
                    PropertyFactory.circleStrokeColor(Color.rgb(47, 191, 166)),
                    PropertyFactory.circleStrokeWidth(2.0f),
                    PropertyFactory.circleStrokeOpacity(0f)
                )
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
                setFilter(Expression.all(
                    Expression.eq(Expression.get("mobile"), Expression.literal(true)),
                    Expression.eq(Expression.get("estimated"), Expression.literal(false)),
                    Expression.eq(Expression.get("manual"), Expression.literal(false))
                ))
                setProperties(
                    PropertyFactory.circleColor(palette.olive),
                    PropertyFactory.circleRadius(7f),
                    PropertyFactory.circleStrokeColor(Color.WHITE),
                    PropertyFactory.circleStrokeWidth(2f)
                )
            })
            style.addLayer(CircleLayer("manual-tower-positions", "towers").apply {
                setFilter(Expression.eq(Expression.get("manual"), Expression.literal(true)))
                setProperties(
                    PropertyFactory.circleColor(palette.olive),
                    PropertyFactory.circleRadius(7.5f),
                    PropertyFactory.circleStrokeColor(Color.rgb(231, 207, 126)),
                    PropertyFactory.circleStrokeWidth(2.8f)
                )
            })
            style.addLayer(CircleLayer("estimated-cell-positions", "towers").apply {
                setFilter(Expression.eq(Expression.get("estimated"), Expression.literal(true)))
                setProperties(
                    PropertyFactory.circleColor(Color.rgb(35, 171, 192)),
                    PropertyFactory.circleOpacity(0.65f),
                    PropertyFactory.circleRadius(7f),
                    PropertyFactory.circleStrokeColor(Color.rgb(2, 83, 99)),
                    PropertyFactory.circleStrokeWidth(2.5f)
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

            style.addLayer(CircleLayer("location-compass-ring", "my-location").apply {
                setProperties(
                    PropertyFactory.circleColor(Color.TRANSPARENT),
                    PropertyFactory.circleRadius(19f),
                    PropertyFactory.circleStrokeColor(Color.rgb(66, 165, 161)),
                    PropertyFactory.circleStrokeOpacity(0.80f),
                    PropertyFactory.circleStrokeWidth(2.0f)
                )
            })
            styled = true
            updateDot()
            showPins()
            updateOverlays()
            updateCoverageOverlay()
            updateAimColors()
            updateServingPulse()
            if (!initiallyCentered) center?.let(::moveCamera)
            if (provider.id.startsWith("google-")) requestGoogleAttribution(force = true)
        }
    }



    private fun showYandexWeb(provider: MapProvider, key: String) {
        val current = map ?: return
        if (key.isBlank()) { requestMapKey(provider); return }
        if (yandexWebMap == null) {
            val bridge = YandexWebMap(this,
                onCamera = { lat, lon, zoom ->
                    if (yandexWebMap?.showing == true) {
                        current.moveCamera(CameraUpdateFactory.newCameraPosition(
                            CameraPosition.Builder().target(LatLng(lat, lon))
                                .zoom(zoom).bearing(0.0).tilt(0.0).build()))
                        persistLastCamera()
                    }
                },
                onSelect = { lat, lon ->
                    val point = LatLng(lat, lon)
                    if (isChoosingCrosshair) {
                        center = point
                        current.moveCamera(CameraUpdateFactory.newLatLng(point))
                        updateCrosshairCoordinates()
                    } else selectTower(point)
                },
                onLongTap = { _, _ -> Unit },
                onError = { message ->
                    if (::mapStatus.isInitialized) mapStatus.text = message
                    Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
                })
            yandexWebMap = bridge
            // Keep all native diagnostic overlays, compass, bottom sheet and
            // top-right actions ABOVE the alternate map engine.
            rootView.addView(bridge.view, 1, FrameLayout.LayoutParams(-1, -1))
        }
        val mode = when (provider.id) {
            "yandex-js-satellite" -> "yandex#satellite"
            "yandex-js-hybrid" -> "yandex#hybrid"
            else -> "yandex#map"
        }
        providerIndex = MapSources.providers.indexOf(provider).coerceAtLeast(0)
        getPreferences(Context.MODE_PRIVATE).edit()
            .putString("map_last_resolved", provider.id).apply()
        mapView.visibility = View.INVISIBLE
        val camera = current.cameraPosition
        val p = camera.target ?: LatLng(55.75, 37.62)
        yandexWebMap?.show(key, mode, p.latitude, p.longitude, camera.zoom)
        mapAttribution.text = "© Яндекс Карты"
        mapAttribution.isClickable = true
        mapAttribution.setOnClickListener {
            try {
                startActivity(Intent(Intent.ACTION_VIEW,
                    Uri.parse("https://yandex.ru/legal/maps_api/")))
            } catch (_: Exception) {}
        }
        mapStatus.text = provider.name + " · JavaScript API"
        syncYandexObjects()
    }

    private fun syncYandexObjects() {
        val bridge = yandexWebMap ?: return
        if (!bridge.showing) return
        val p = activeUserPoint()
        bridge.setObjects(visible, p?.let { it.latitude to it.longitude },
            servingSite, selectedAimSite,
            if (coverageTowerId == selectedAimSite?.id) coverageRadiusMetres else 0)
    }

    /** Select a reachable raster tile host using a single low-zoom tile request. */
    private fun chooseBasemap() {
        // A late background check must never replace a manual Yandex/MapTiler choice.
        if (choosingMap || getPreferences(Context.MODE_PRIVATE)
                .getString("map_provider", "auto") != "auto") return
        choosingMap = true
        val point = location?.let { LatLng(it.latitude, it.longitude) }
            ?: center ?: LatLng(49.0, 39.0)
        executor.execute {
            val selected = MapSources.unkeyed().indexOfFirst {
                MapSources.reachable(it, point.latitude, point.longitude)
            }
            runOnUiThread {
                choosingMap = false
                if (isFinishing || isDestroyed ||
                    getPreferences(Context.MODE_PRIVATE)
                        .getString("map_provider", "auto") != "auto") return@runOnUiThread
                if (selected >= 0) {
                    // selected is an index into unkeyed(), NOT providers().
                    val resolved = MapSources.unkeyed()[selected]
                    if (resolved.id != selectedMapProvider().id) applyBasemap(resolved)
                    mapStatus.text = "Подложка: " + resolved.attribution
                } else {
                    // No network: keep the previous source and any valid cached tiles.
                    // Do NOT reset a remembered OSM provider or erase its selection.
                    mapStatus.text = "Нет сети · сохранили предыдущую подложку и кеш"
                }
            }
        }
    }


    private fun useMapChoice(provider: MapProvider) {
        val key = provider.keyPref?.let {
            getPreferences(Context.MODE_PRIVATE).getString(it, "").orEmpty()
        }.orEmpty()
        if (MapSources.yandexWeb(provider)) {
            if (key.isBlank()) { requestMapKey(provider); return }
            getPreferences(Context.MODE_PRIVATE).edit()
                .putString("map_provider", provider.id).apply()
            applyBasemap(provider, key)
            return
        }
        // Public OSM sources may be reopened without internet: MapLibre can
        // draw already valid ambient cached tiles with no preflight HTTP call.
        if (provider.keyPref == null) {
            getPreferences(Context.MODE_PRIVATE).edit()
                .putString("map_provider", provider.id).apply()
            applyBasemap(provider)
            return
        }
        if (provider.keyPref != null && key.isBlank()) {
            if (provider.id == "yandex") showYandexSetup()
            else if (provider.id.startsWith("google-")) showGoogleSetup()
            else requestMapKey(provider)
            return
        }
        // For a manually selected Yandex Tiles or MapTiler layer, draw its own
        // style directly, including valid cached resources when connectivity fails.
        // Connection probing is informational and cannot substitute another source.
        if (provider.id == "yandex" || MapSources.mapTiler(provider)) {
            getPreferences(Context.MODE_PRIVATE).edit()
                .putString("map_provider", provider.id).apply()
            applyBasemap(provider, key)
            provider.keyPref?.let(::verifyConnection)
            return
        }
        if (choosingMap) {
            mapStatus.text = "Сервер карты ещё проверяется."
            return
        }
        choosingMap = true
        getPreferences(Context.MODE_PRIVATE).edit()
            .putString("map_provider", provider.id).apply()
        provider.keyPref?.let { checkingApi.add(it) }
        refreshYandexConnectButton()
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
                redrawConnectionStatuses()
                if (test.connected && getPreferences(Context.MODE_PRIVATE)
                        .getString("map_provider", "auto") == provider.id) {
                    test.session?.let { googleSessions[provider.id] = it }
                    applyBasemap(provider, key, test.session)
                    mapStatus.text = "✓ " + provider.name + " · подключено"
                } else {
                    // Failed verification must not delete the user's saved source.
                    // Cached tiles might still be useful without internet access.
                    mapStatus.text = test.message + " · выбор карты сохранён"
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
        apiChecks.remove("map_key_yandex")
        checkingApi.add("map_key_yandex")
        redrawConnectionStatuses()
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
        val address = when {
            provider.id == "yandex" -> "developer.tech.yandex.ru (Tiles API)"
            MapSources.yandexWeb(provider) ->
                "developer.tech.yandex.ru (JavaScript API · отдельный ключ)"
            MapSources.mapTiler(provider) -> "cloud.maptiler.com (Free · некоммерческое)"
            provider.id.startsWith("google-") -> "console.cloud.google.com (Map Tiles API)"
            else -> "platform.2gis.ru (Raster Tiles API)"
        }
        KhakiDialogBuilder(this, palette)
            .setTitle(provider.name)
            .setMessage(
                "Для этой карты нужен личный API-ключ. " +
                    "Получите его на " + address + ". Доступ и лимиты определяет поставщик. " +
                    (if (MapSources.yandexWeb(provider)) "Для JS API в WebView при ограничении ключа " +
                        "по Referer укажите com.russoturissto.towerradar. " else "") +
                    "Ключ хранится только на телефоне, без отправки в GitHub."
            )
            .setView(input)
            .setPositiveButton("Сохранить") { _, _ ->
                val key = input.text.toString().trim()
                if (key.isNotEmpty()) {
                    getPreferences(Context.MODE_PRIVATE).edit()
                        .putString(pref, key).apply()
                    apiChecks.remove(pref)
                    redrawConnectionStatuses()
                    useMapChoice(provider)
                } else {
                    mapStatus.text = "Введите ключ, чтобы включить " + provider.name
                }
            }
            .setNeutralButton("Удалить ключ") { _, _ ->
                getPreferences(Context.MODE_PRIVATE).edit().remove(pref).apply()
                apiChecks.remove(pref)
                checkingApi.remove(pref)
                redrawConnectionStatuses()
                mapStatus.text = "Ключ " + provider.name + " удалён."
                if (MapSources.providers.getOrNull(providerIndex)?.keyPref == pref) {
                    chooseBasemap()
                }
            }
            .setNegativeButton("Отмена", null)
            .show()
    }


    /** Five compact sections instead of a long flat technical settings list. */
    private fun showSettings() {
        val items = arrayOf(
            "Оформление и экран",
            "Карты и офлайн-данные",
            "Вышки и рельеф",
            "Геопозиция и сеть",
            "Компас и наведение",
            "Обновления TowerRadar"
        )
        KhakiDialogBuilder(this, palette).setTitle("Настройки · TowerRadar")
            .setItems(items) { _, index ->
                when (index) {
                    0 -> showAppearanceMenu()
                    1 -> showMapsMenu()
                    2 -> showTowersMenu()
                    3 -> showLocationMenu()
                    4 -> showCompassMenu()
                    5 -> showUpdateMenu()
                }
            }.setNegativeButton("Закрыть", null).show()
    }

    private fun showAppearanceMenu() {
        KhakiDialogBuilder(this, palette).setTitle("Оформление и экран")
            .setItems(arrayOf("Тема · системная / дневная / ночная",
                "Режим экрана · стандартный / полноэкранный")) { _, n ->
                when (n) { 0 -> selectTheme(); 1 -> selectDisplayMode() }
            }.setNegativeButton("Назад") { _, _ -> showSettings() }.show()
    }

    private fun showMapsMenu() {
        KhakiDialogBuilder(this, palette).setTitle("Карты и офлайн")
            .setItems(arrayOf("Картографические подложки · в панели Подробнее",
                "Подключения API · Яндекс / MapTiler / OpenCellID",
                "Офлайн-области и кеш · карты, рельеф, вышки",
                "Кеш просмотренных карт · лимит и очистка")) { _, n ->
                when (n) {
                    0 -> {
                        if (mapFullscreen) setMapFullscreen(false)
                        expandSheet(true)
                    }
                    1 -> showConnections()
                    2 -> showOfflineAreaInfo()
                    3 -> showCacheSettings()
                }
            }.setNegativeButton("Назад") { _, _ -> showSettings() }.show()
    }

    private fun showTowersMenu() {
        KhakiDialogBuilder(this, palette).setTitle("Вышки и рельеф")
            .setItems(arrayOf("Добавить вышку · координаты / оборудование / фото",
                "Пользовательские вышки · управление",
                "Высота антенны · расчётный сценарий",
                "Хранилище · данные и кеш",
                "Резервная копия · экспорт / восстановление",
                "Уведомления о состоянии базы вышек")) { _, n ->
                when (n) {
                    0 -> showAddUserTower()
                    1 -> showUserTowers()
                    2 -> showHeightScenario()
                    3 -> showTowerStorageSettings()
                    4 -> showBackupMenu()
                    5 -> enableDatabaseNotifications()
                }
            }.setNegativeButton("Назад") { _, _ -> showSettings() }.show()
    }

    private fun showLocationMenu() {
        KhakiDialogBuilder(this, palette).setTitle("Геопозиция и сеть")
            .setItems(arrayOf("Выбрать геопозицию · GPS / ручная точка",
                "Выбрать SIM",
                "Радиосигнал · диагностика",
                "Дополнительные соты · источники")) { _, n ->
                when (n) {
                    0 -> showPositionOptions()
                    1 -> selectSim()
                    2 -> showSignalDetails()
                    3 -> editCellKey()
                }
            }.setNegativeButton("Назад") { _, _ -> showSettings() }.show()
    }

    private fun showCompassMenu() {
        KhakiDialogBuilder(this, palette).setTitle("Компас и наведение")
            .setItems(arrayOf(
                "Калибровка компаса · проверка датчика",
                "Сектор направления: " + if (showCone) "включён" else "выключен",
                "Яркость луча и сектора"
            )) { _, n ->
                when (n) {
                    0 -> showCompassCalibration()
                    1 -> {
                        showCone = !showCone
                        getPreferences(Context.MODE_PRIVATE).edit()
                            .putBoolean("show_cone", showCone).apply()
                        updateOverlays()
                        showCompassMenu()
                    }
                    2 -> showDirectionBrightness()
                }
            }.setNegativeButton("Назад") { _, _ -> showSettings() }.show()
    }

    private fun showCompassCalibration() {
        val quality = when (lastSensorAccuracy) {
            SensorManager.SENSOR_STATUS_ACCURACY_HIGH -> "высокая"
            SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM -> "средняя"
            SensorManager.SENSOR_STATUS_ACCURACY_LOW -> "низкая"
            SensorManager.SENSOR_STATUS_UNRELIABLE -> "ненадёжная"
            else -> "не сообщается датчиком"
        }
        KhakiDialogBuilder(this, palette)
            .setTitle("Калибровка компаса")
            .setMessage("Точность датчика: " + quality + ".\n\n" +
                "Переместитесь подальше от магнитов, металла и электроники. " +
                "Несколько раз плавно повращайте телефон в воздухе восьмёркой, " +
                "наклоняя его по разным осям. Сверьте направление с известным ориентиром.\n\n" +
                "Калибровку выполняет Android и оборудование телефона; TowerRadar " +
                "не может принудительно перепрограммировать магнитометр.")
            .setPositiveButton("Готово", null)
            .setNeutralButton("Проверить ещё раз") { _, _ -> showCompassCalibration() }
            .show()
    }

    private fun showHeightScenario() {
        val heights = intArrayOf(20, 25, 30, 50)
        val current = getPreferences(Context.MODE_PRIVATE).getInt("height_scenario_m", 30)
        val labels = heights.map {
            it.toString() + " м" + if (it == 30) " · типовой сценарий ITU-R" else ""
        }.toTypedArray()
        KhakiDialogBuilder(this, palette).setTitle("Расчётная высота антенны")
            .setMessage("Используется ТОЛЬКО если фактическая высота не указана. " +
                "Это модельный сценарий, а не измерение вышки. " +
                "Изменение параметра не исправляет и не дополняет открытую базу.")
            .setSingleChoiceItems(labels, heights.indexOf(current).coerceAtLeast(0)) { _, n ->
                getPreferences(Context.MODE_PRIVATE).edit()
                    .putInt("height_scenario_m", heights[n]).apply()
            }.setNegativeButton("Назад") { _, _ -> showTowersMenu() }.show()
    }

    private fun selectedMapProvider(): MapProvider =
        MapSources.providers.getOrNull(providerIndex) ?: MapSources.providers.first()

    private fun showOfflineAreaInfo() {
        val provider = selectedMapProvider()
        val capability = OfflineMapPolicy.capability(provider.id)
        val explanation = when (capability) {
            OfflineMapCapability.REGIONAL_DOWNLOAD ->
                "Для этой карты доступна загрузка области на всех выбранных масштабах. " +
                    "Вышки и рельеф загружаются отдельно. До 1800 тайлов карты на пакет."
            OfflineMapCapability.VIEWED_TILE_CACHE ->
                "Для этой карты могут сохраняться только тайлы, фактически открытые " +
                    "при просмотре, с учётом ограничений поставщика. " +
                    (if (provider.id == "yandex") "Кеш Яндекс Tiles API временный, до 29 дней. "
                        else "") +
                    "Заранее скачать весь район нельзя. Размер кеша настраивается отдельно."
            OfflineMapCapability.NO_REGION_DOWNLOAD ->
                "Предварительное скачивание региона из этого сервиса недоступно. " +
                    "Режим браузерной карты и условия поставщика не дают TowerRadar " +
                    "возможности гарантировать работу без сети."
        }
        KhakiDialogBuilder(this, palette).setTitle("Офлайн-области")
            .setMessage("Выбрана: " + provider.name + ".\n\n" + explanation +
                "\n\nTowerRadar не переключает выбранную карту на другую подложку.")
            .setItems(arrayOf(
                "Выбрать область на текущей карте",
                "Мои загруженные офлайн-области",
                "Мои отмеченные области кеша",
                "Кеш просмотренных карт"))
            { _, selected -> when (selected) {
                0 -> selectOfflineRadius()
                1 -> listOfflineAreas()
                2 -> listMarkedCacheAreas()
                3 -> showCacheSettings()
            } }.setNegativeButton("Назад") { _, _ -> showMapsMenu() }.show()
    }

    private fun showSelectedMapOfflineLimit() {
        val provider = selectedMapProvider()
        val viewed = OfflineMapPolicy.capability(provider.id) ==
            OfflineMapCapability.VIEWED_TILE_CACHE
        val description = if (provider.id == "yandex")
            "Яндекс Tiles API разрешает временно кешировать полученные при просмотре " +
                "тайлы, но не сохранять целые районы заранее или обходить условия API. " +
                "Просматривайте нужные участки обычным способом: TowerRadar использует " +
                "временный кеш Яндекса и очищает его не позднее 29 дней. " +
                "Спутник и гибрид Яндекса работают через JavaScript API и не имеют " +
                "управляемого TowerRadar офлайн-кеша."
        else if (viewed)
            "Открытые при обычном просмотре тайлы могут сохраняться временно, " +
                "пока это допускается картографическим сервисом. " +
                "Загрузка всей области через этот API не поддерживается."
        else
            "Для этой подложки предварительное скачивание не поддерживается. " +
                "Временным кешем карт браузера или сервиса TowerRadar не управляет."
        KhakiDialogBuilder(this, palette)
            .setTitle("Кеш: " + provider.name)
            .setMessage(description + "\n\nВыбранная карта останется прежней.")
            .setPositiveButton(if (viewed) "Настройки кеша" else "Понятно") { _, _ ->
                if (viewed) showCacheSettings()
            }
            .setNegativeButton("Закрыть", null).show()
    }

    private fun selectOfflineRadius() {
        val target = map?.cameraPosition?.target
        if (target == null) {
            Toast.makeText(this, "Сначала откройте карту", Toast.LENGTH_LONG).show()
            return
        }
        val radii = intArrayOf(10, 30)
        KhakiDialogBuilder(this, palette).setTitle("Выбор области · " + selectedMapProvider().name)
            .setMessage("Радиус вокруг перекрестия или рамка по масштабу текущей карты. " +
                if (OfflineMapPolicy.canCreateRegion(selectedMapProvider().id))
                    "Для этой подложки доступна предварительная загрузка."
                else "Здесь можно отметить границы. Предварительная загрузка тайлов " +
                    "из этого сервиса не запускается.")
            .setItems(arrayOf("Радиус 10 км", "Радиус 30 км",
                "Выбрать область вручную")) { _, index ->
                if (index == radii.size) startOfflineSelection()
                else selectOfflineZoom(target, radii[index])
            }.setNegativeButton("Назад") { _, _ -> showOfflineAreaInfo() }.show()
    }

    private fun selectOfflineZoom(target: LatLng, radiusKm: Int) {
        val bounds = OfflineAreaPolicy.bounds(target.latitude, target.longitude, radiusKm)
        if (bounds == null) {
            KhakiDialogBuilder(this, palette).setTitle("Недоступный район")
                .setMessage("Выберите другой район или уменьшите масштаб карты.")
                .setPositiveButton("Понятно", null).show()
            return
        }
        if (OfflineMapPolicy.canCreateRegion(selectedMapProvider().id))
            selectOfflineZoomForBounds(target, bounds, radiusKm)
        else showSelectedCacheArea(target, bounds)
    }

    private fun selectOfflineZoomForBounds(target: LatLng, bounds: OfflineBounds,
                                           radiusKm: Int) {
        val zooms = intArrayOf(10, 12, 14, 16, 18, 19)
        val options = zooms.map { z ->
            val count = OfflineAreaPolicy.countTiles(bounds, OfflineAreaPolicy.MIN_ZOOM, z)
            "Масштабы 6–$z · $count тайлов" +
                if (OfflineAreaPolicy.allowed(bounds, z)) "" else " · слишком много"
        }.toTypedArray()
        KhakiDialogBuilder(this, palette).setTitle("Детализация офлайн-карты")
            .setMessage("Сохраняются все уровни выбранного диапазона. " +
                "Границы ручного выделения определены положением и масштабом карты.")
            .setItems(options) { _, which ->
                val zoom = zooms[which]
                if (!OfflineAreaPolicy.allowed(bounds, zoom)) {
                    KhakiDialogBuilder(this, palette).setTitle("Слишком большой пакет")
                        .setMessage("Лимит сервера превышен. Увеличьте масштаб карты, " +
                            "уменьшив выбранную область, или ограничьте детализацию.")
                        .setPositiveButton("Изменить область") { _, _ ->
                            if (radiusKm == 0) startOfflineSelection()
                            else selectOfflineRadius()
                        }.show()
                } else confirmOfflineArea(target, bounds, radiusKm, zoom)
            }.setNegativeButton("Назад") { _, _ ->
                if (radiusKm == 0) startOfflineSelection() else selectOfflineRadius()
            }.show()
    }

    private fun startOfflineSelection() {
        if (map == null || offlineSelectionEnabled) return
        // Never replace the map chosen by the user as a side effect of selecting an area.
        offlineSelectionEnabled = true
        details.visibility = View.GONE
        if (actionMenuExpanded) setActionMenuExpanded(false)
        val frame = OfflineSelectionFrame(this)
        offlineSelectionFrame = frame
        rootView.addView(frame, 2, FrameLayout.LayoutParams(-1, -1))
        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(palette.surface, 16)
            elevation = dp(12).toFloat()
            setPadding(dp(12), dp(10), dp(12), dp(10))
        }
        offlineSelectionControls = controls
        val info = text("Переместите или масштабируйте карту", 12f, palette.text).apply {
            gravity = Gravity.CENTER
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }
        offlineSelectionInfo = info
        controls.addView(info, LinearLayout.LayoutParams(-1, -2).apply {
            bottomMargin = dp(8)
        })
        val actions = row()
        controls.addView(actions)
        val cancel = styledButton("Отмена", 13f)
        val save = styledButton("Выбрать", 13f, true)
        actions.addView(cancel, LinearLayout.LayoutParams(0, dp(44), 1f).apply {
            rightMargin = dp(6)
        })
        actions.addView(save, LinearLayout.LayoutParams(0, dp(44), 1f))
        cancel.setOnClickListener { finishOfflineSelection(false) }
        save.setOnClickListener { finishOfflineSelection(true) }
        rootView.addView(controls, FrameLayout.LayoutParams(-1, -2,
            Gravity.BOTTOM).apply {
            leftMargin = dp(15) + systemLeftInset
            rightMargin = dp(15) + systemRightInset
            bottomMargin = systemBottomInset + dp(20)
        })
        applyResponsiveLayout(false)
        rootView.post { updateOfflineSelectionInfo() }
    }

    private fun currentOfflineSelection(): OfflineBounds? {
        val current = map ?: return null
        val frame = offlineSelectionFrame ?: return null
        if (frame.width < 20 || frame.height < 20) return null
        val area = frame.selectionRect()
        val corners = listOf(
            android.graphics.PointF(area.left, area.top),
            android.graphics.PointF(area.right, area.top),
            android.graphics.PointF(area.right, area.bottom),
            android.graphics.PointF(area.left, area.bottom)
        ).map { p ->
            current.projection.fromScreenLocation(p).let { it.latitude to it.longitude }
        }
        return OfflineAreaPolicy.selectedBounds(corners)
    }

    private fun updateOfflineSelectionInfo() {
        if (!offlineSelectionEnabled) return
        val info = offlineSelectionInfo ?: return
        val area = currentOfflineSelection()
        if (area == null) {
            info.text = "Область недоступна в этом месте. Переместите карту."
            return
        }
        val width = TowerData.distanceKm((area.north + area.south) / 2,
            area.west, (area.north + area.south) / 2, area.east)
        val height = TowerData.distanceKm(area.south,
            (area.west + area.east) / 2, area.north, (area.west + area.east) / 2)
        val currentZoom = map?.cameraPosition?.zoom?.toInt()?.coerceIn(6, 19) ?: 12
        val count = OfflineAreaPolicy.countTiles(area, 6, currentZoom)
        info.text = String.format(Locale.US,
            "Область: %.1f × %.1f км · масштаб %d · до %d тайлов",
            width, height, currentZoom, count)
    }

    private fun finishOfflineSelection(save: Boolean) {
        val bounds = if (save) currentOfflineSelection() else null
        val centerPoint = map?.cameraPosition?.target
        offlineSelectionEnabled = false
        offlineSelectionControls?.let(rootView::removeView)
        offlineSelectionFrame?.let(rootView::removeView)
        offlineSelectionControls = null
        offlineSelectionFrame = null
        offlineSelectionInfo = null
        applyResponsiveLayout(false)
        if (!save) return
        if (bounds == null || centerPoint == null) {
            Toast.makeText(this, "Не удалось определить границы", Toast.LENGTH_LONG).show()
            return
        }
        if (OfflineMapPolicy.canCreateRegion(selectedMapProvider().id))
            selectOfflineZoomForBounds(centerPoint, bounds, 0)
        else showSelectedCacheArea(centerPoint, bounds)
    }

    /**
     * Marking a region is not downloading it. Restricted providers only use
     * normal viewport requests and whatever ambient cache their terms permit.
     */
    private fun showSelectedCacheArea(target: LatLng, bounds: OfflineBounds) {
        val provider = selectedMapProvider()
        val capability = OfflineMapPolicy.capability(provider.id)
        val dimensionX = TowerData.distanceKm(
            (bounds.north + bounds.south) / 2, bounds.west,
            (bounds.north + bounds.south) / 2, bounds.east)
        val dimensionY = TowerData.distanceKm(
            bounds.south, (bounds.west + bounds.east) / 2,
            bounds.north, (bounds.west + bounds.east) / 2)
        val advice = when (capability) {
            OfflineMapCapability.VIEWED_TILE_CACHE ->
                "Тайлы карты, которые вы действительно открываете при обычном просмотре, " +
                "могут временно оставаться в кеше. Просто выделение области НЕ скачивает " +
                "все масштабы и не гарантирует работу без интернета. " +
                if (provider.id == "yandex")
                    "Кеш Яндекс Tiles API очищается не позднее 29 дней."
                else "Срок хранения зависит от поставщика."
            OfflineMapCapability.NO_REGION_DOWNLOAD ->
                "Для этой карты TowerRadar не может предварительно скачать тайлы " +
                "или гарантировать офлайн-доступ. Сохраняются только границы " +
                "выбранного района, не содержимое карты."
            OfflineMapCapability.REGIONAL_DOWNLOAD ->
                "Для OpenFreeMap используется отдельный менеджер офлайн-областей."
        }
        KhakiDialogBuilder(this, palette)
            .setTitle("Выбранная область · " + provider.name)
            .setMessage("Центр: " + CoordinateFormatter.format(
                target.latitude, target.longitude) +
                "\nРазмер: " + String.format(Locale.US, "%.1f × %.1f км", dimensionX, dimensionY) +
                "\n\n" + advice +
                "\n\nТекущая карта останется выбранной. " +
                "Можно сохранить границы и позднее вернуться к просмотру района.")
            .setPositiveButton("Сохранить границы") { _, _ ->
                val prefs = getPreferences(Context.MODE_PRIVATE)
                val saved = ViewedCacheAreaStore.append(
                    prefs.getString("viewed_cache_areas", "[]").orEmpty(),
                    ViewedCacheAreaStore.Area(
                        provider.id, bounds, System.currentTimeMillis()))
                prefs.edit().putString("viewed_cache_areas", saved).apply()
                Toast.makeText(this,
                    "Границы сохранены · загрузка тайлов не запускалась",
                    Toast.LENGTH_LONG).show()
            }
            .setNeutralButton("Настройки кеша") { _, _ -> showCacheSettings() }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun listMarkedCacheAreas() {
        val prefs = getPreferences(Context.MODE_PRIVATE)
        val areas = ViewedCacheAreaStore.parse(
            prefs.getString("viewed_cache_areas", "[]").orEmpty())
        if (areas.isEmpty()) {
            KhakiDialogBuilder(this, palette).setTitle("Отмеченные области")
                .setMessage("Пока нет сохранённых границ. Выберите область на текущей карте. " +
                    "Отметка сама по себе не загружает тайлы.")
                .setPositiveButton("Выбрать область") { _, _ -> selectOfflineRadius() }
                .setNegativeButton("Закрыть", null).show()
            return
        }
        KhakiDialogBuilder(this, palette)
            .setTitle("Отмеченные области · не скачанные карты")
            .setItems(areas.map { area ->
                val name = MapSources.providers.firstOrNull { it.id == area.providerId }
                    ?.name ?: area.providerId
                val midLat = (area.bounds.north + area.bounds.south) / 2
                val midLon = (area.bounds.west + area.bounds.east) / 2
                name + " · " + CoordinateFormatter.format(midLat, midLon)
            }.toTypedArray()) { _, index ->
                val chosen = areas[index]
                KhakiDialogBuilder(this, palette)
                    .setTitle("Отмеченная область")
                    .setMessage("Источник: " + chosen.providerId +
                        "\nЗдесь сохранены только географические границы. " +
                        "Наличие тайлов в кеше не подтверждено. " +
                        "Перейти к району, не переключая выбранную карту?")
                    .setPositiveButton("Показать на карте") { _, _ ->
                        val midLat = (chosen.bounds.north + chosen.bounds.south) / 2
                        val midLon = (chosen.bounds.west + chosen.bounds.east) / 2
                        val focus = LatLng(midLat, midLon)
                        map?.animateCamera(CameraUpdateFactory.newLatLng(focus), 400)
                        yandexWebMap?.takeIf { it.showing }?.let {
                            it.move(midLat, midLon, map?.cameraPosition?.zoom ?: 12.0)
                        }
                    }
                    .setNeutralButton("Удалить отметку") { _, _ ->
                        prefs.edit().putString("viewed_cache_areas",
                            ViewedCacheAreaStore.remove(
                                prefs.getString("viewed_cache_areas", "[]").orEmpty(),
                                index)).apply()
                        listMarkedCacheAreas()
                    }
                    .setNegativeButton("Назад") { _, _ -> listMarkedCacheAreas() }
                    .show()
            }
            .setNegativeButton("Назад") { _, _ -> showOfflineAreaInfo() }
            .show()
    }

    private fun confirmOfflineArea(target: LatLng, bounds: OfflineBounds,
                                   radiusKm: Int, zoom: Int) {
        if (!OfflineMapPolicy.canCreateRegion(selectedMapProvider().id)) {
            showSelectedMapOfflineLimit()
            return
        }
        val count = OfflineAreaPolicy.countTiles(bounds, OfflineAreaPolicy.MIN_ZOOM, zoom)
        KhakiDialogBuilder(this, palette).setTitle("Сохранить офлайн-область?")
            .setMessage("Центр: " + CoordinateFormatter.format(target.latitude, target.longitude) +
                "\n" + (if (radiusKm == 0) "Область по масштабу карты"
                    else "Радиус: $radiusKm км") + " · масштабы: 6–$zoom" +
                "\nКарта: до $count тайлов (OpenFreeMap)" +
                "\nРельеф: Mapzen Terrarium" +
                "\nВышки: существующая локальная база; при наличии сети — обновление района." +
                "\n\nПакет будет полностью пригоден только после окончания загрузки " +
                "карты и рельефа. Скачивание можно продолжить позднее.")
            .setPositiveButton("Сохранить") { _, _ ->
                val name = "Область " +
                    String.format(Locale.US, "%.3f, %.3f", target.latitude, target.longitude) +
                    (if (radiusKm == 0) " · вручную" else " · $radiusKm км")
                if (!OfflineMapPolicy.canCreateRegion(selectedMapProvider().id)) {
                    showSelectedMapOfflineLimit()
                    return@setPositiveButton
                }
                // OfflineAreaManager downloads OpenFreeMap resources without changing
                // the rendered style or the persisted map_provider preference.
                offlineAreas.create(name, bounds, radiusKm, zoom) { state ->
                    if (!isDestroyed && !isFinishing) mapStatus.text = state.message
                    if (!isDestroyed && !isFinishing && state.complete) {
                        Toast.makeText(this, state.message, Toast.LENGTH_LONG).show()
                    } else if (!isDestroyed && !isFinishing &&
                        (state.message.startsWith("Ошибка") ||
                            state.message.startsWith("Лимит") ||
                            state.message.startsWith("Не удалось"))) {
                        Toast.makeText(this, state.message, Toast.LENGTH_LONG).show()
                    }
                }
                // Towers use their existing global SQLite cache and user-created local store.
                apiExecutor.execute {
                    try {
                        val scanRadius = if (radiusKm == 0) {
                            val a = TowerData.distanceKm(target.latitude, target.longitude,
                                bounds.north, bounds.east)
                            val b = TowerData.distanceKm(target.latitude, target.longitude,
                                bounds.south, bounds.west)
                            kotlin.math.ceil(maxOf(a, b)).toInt().coerceIn(1, 60)
                        } else radiusKm
                        val towers = fetch(target.latitude, target.longitude, scanRadius)
                            .filter { bounds.contains(it.latitude, it.longitude) }
                        towerDatabase.merge(towers)
                        runOnUiThread {
                            if (!isDestroyed && !isFinishing) {
                                sites = towerDatabase.all()
                                updateOperatorChoices()
                                showPins()
                                Toast.makeText(this, "Вышки района сохранены", Toast.LENGTH_SHORT).show()
                            }
                        }
                    } catch (_: Exception) { /* Existing towers remain available offline. */ }
                }
                Toast.makeText(this, "Загрузка запущена. Статус — в «Мои области».",
                    Toast.LENGTH_LONG).show()
            }.setNegativeButton("Отмена", null).show()
    }

    private fun listOfflineAreas() {
        offlineAreas.list { areas, error ->
            if (isFinishing || isDestroyed) return@list
            if (error != null || areas.isEmpty()) {
                KhakiDialogBuilder(this, palette).setTitle("Мои офлайн-области")
                    .setMessage(error ?: "Сохранённых областей пока нет.")
                    .setPositiveButton("Создать") { _, _ -> selectOfflineRadius() }
                    .setNegativeButton("Назад", null).show()
            } else {
                KhakiDialogBuilder(this, palette).setTitle("Мои офлайн-области")
                    .setItems(areas.map {
                        it.name + " · до масштаба " + it.maxZoom
                    }.toTypedArray()) { _, index -> offlineAreaActions(areas[index]) }
                    .setNegativeButton("Назад") { _, _ -> showOfflineAreaInfo() }.show()
            }
        }
    }

    private fun offlineAreaActions(area: OfflineAreaManager.Area) {
        offlineAreas.status(area) { description ->
            if (isFinishing || isDestroyed) return@status
            KhakiDialogBuilder(this, palette).setTitle(area.name)
                .setMessage(description + "\nМасштабы 6–${area.maxZoom}. " +
                    "Пакет содержит OpenFreeMap; для просмотра без сети " +
                    "выберите OpenFreeMap вручную в списке карт. " +
                    "Текущую подложку TowerRadar не меняет.")
                .setItems(arrayOf("Показать область на карте",
                    "Продолжить загрузку карты и рельефа",
                    "Приостановить загрузку",
                    "Удалить область")) { _, selected ->
                    when (selected) {
                        0 -> {
                            if (selectedMapProvider().id != "openfreemap-offline") {
                                Toast.makeText(this,
                                    "Подложка сохранена. Для использования офлайн " +
                                        "выберите OpenFreeMap вручную.",
                                    Toast.LENGTH_LONG).show()
                            }
                            map?.animateCamera(CameraUpdateFactory.newLatLngZoom(
                                LatLng((area.bounds.north + area.bounds.south) / 2,
                                    (area.bounds.east + area.bounds.west) / 2),
                                (area.maxZoom - 2).coerceAtLeast(6).toDouble()), 350)
                        }
                        1 -> {
                            offlineAreas.resume(area) { state ->
                                if (!isFinishing && !isDestroyed) mapStatus.text = state.message
                                if (!isFinishing && !isDestroyed &&
                                    (state.complete || state.message.startsWith("Ошибка") ||
                                        state.message.startsWith("Лимит"))) {
                                    Toast.makeText(this, state.message, Toast.LENGTH_LONG).show()
                                }
                            }
                            Toast.makeText(this, "Продолжаем загрузку", Toast.LENGTH_SHORT).show()
                        }
                        2 -> {
                            offlineAreas.pause(area)
                            Toast.makeText(this, "Загрузка приостановлена", Toast.LENGTH_SHORT).show()
                        }
                        3 -> KhakiDialogBuilder(this, palette)
                            .setTitle("Удалить офлайн-область?")
                            .setMessage("Карта и данные высот этой области будут удалены. " +
                                "Общая база вышек и пользовательские записи сохранятся.")
                            .setPositiveButton("Удалить") { _, _ ->
                                offlineAreas.delete(area) { _, msg ->
                                    Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
                                }
                            }.setNegativeButton("Отмена", null).show()
                    }
                }.setNegativeButton("Назад") { _, _ -> listOfflineAreas() }.show()
        }
    }

    /** The first-install guide is distinct from the changelog on real OTA upgrades. */
    private fun showStartupInformation() {
        val preferences = getPreferences(Context.MODE_PRIVATE)
        val current = 30
        val previous = preferences.getInt("startup_seen_version", 0)
        if (previous == current) {
            checkReleaseOnLaunch()
            return
        }
        @Suppress("DEPRECATION")
        val info = try { packageManager.getPackageInfo(packageName, 0) }
            catch (_: Exception) { null }
        val isUpgrade = previous > 0 ||
            (info != null && info.lastUpdateTime - info.firstInstallTime > 30000L)
        if (!isUpgrade && !preferences.getBoolean("api_welcome_seen", false)) {
            showFirstRunApiGuide {
                preferences.edit().putBoolean("api_welcome_seen", true)
                    .putInt("startup_seen_version", current).apply()
                checkReleaseOnLaunch()
            }
        } else {
            preferences.edit().putInt("startup_seen_version", current).apply()
            if (isUpgrade) showWhatsNew { checkReleaseOnLaunch() }
            else checkReleaseOnLaunch()
        }
    }

    private fun showFirstRunApiGuide(done: () -> Unit) {
        val labels = arrayOf(
            "Яндекс Tiles API · обычная карта",
            "Яндекс JavaScript API · спутник / гибрид",
            "MapTiler API · карты, спутник и высоты",
            "OpenCellID · дополнительные сведения о сотах")
        val links = arrayOf(
            "https://developer.tech.yandex.ru/",
            "https://yandex.ru/dev/jsapi-v2-1/doc/ru/",
            "https://cloud.maptiler.com/",
            "https://opencellid.org/")
        KhakiDialogBuilder(this, palette)
            .setTitle("Добро пожаловать в TowerRadar")
            .setMessage("Для более комфортной работы можно получить личные API-ключи. " +
                "Они НЕ обязательны: открытые карты и основные функции доступны без них. " +
                "У каждого сервиса могут быть тарифы и ограничения.\n\n" +
                "Нажмите название, чтобы открыть официальный сайт получения ключа.")
            .setItems(labels) { _, n ->
                done()
                openExternal(links[n])
            }
            .setPositiveButton("Настроить ключи") { _, _ ->
                done()
                showConnections()
            }
            .setNegativeButton("Продолжить без ключей") { _, _ -> done() }
            .show()
    }

    private fun showWhatsNew(afterClose: (() -> Unit)? = null) {
        KhakiDialogBuilder(this, palette)
            .setTitle("Что нового в TowerRadar · 0.27.3")
            .setMessage(ChangeLog.forVersion("0.27.3"))
            .setPositiveButton("Понятно") { _, _ -> afterClose?.invoke() }
            .show()
    }

    private fun showUpdateMenu() {
        KhakiDialogBuilder(this, palette).setTitle("Обновления TowerRadar")
            .setMessage("Установлена версия " + currentVersionName() + ".\n\n" +
                "Стабильная версия проходит ручную публикацию после тестирования. " +
                "Тестовые APK доступны отдельно и могут содержать ошибки. " +
                "При запуске проверяем наличие новой тестовой версии; " +
                "в настройках можно проверить любой канал вручную. " +
                "APK скачивается и проверяется внутри приложения, " +
                "Android подтверждает установку.")
            .setItems(arrayOf("Проверить стабильную версию",
                "Проверить тестовую версию",
                "Сборки GitHub Actions · тесты до релиза",
                "История изменений")) { _, n ->
                when (n) {
                    0 -> checkAvailableRelease(false)
                    1 -> checkAvailableRelease(true)
                    2 -> openExternal("https://github.com/RussoTurissto/TowerRadar/actions")
                    3 -> showWhatsNew()
                }
            }.setNegativeButton("Назад") { _, _ -> showSettings() }.show()
    }

    private fun checkAvailableRelease(testChannel: Boolean) {
        val label = if (testChannel) "тестовом" else "стабильном"
        Toast.makeText(this, "Проверяем релизы GitHub…", Toast.LENGTH_SHORT).show()
        apiExecutor.execute {
            val outcome = try { ReleaseFeed.query(testChannel) to null }
            catch (e: Exception) { null to (e.message ?: "Нет связи с GitHub") }
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                val release = outcome.first
                if (release == null) {
                    KhakiDialogBuilder(this, palette)
                        .setTitle("Обновления · " + label + " канале")
                        .setMessage(outcome.second ?: "Опубликованных APK на этом канале пока нет.")
                        .setPositiveButton("Понятно", null).show()
                    return@runOnUiThread
                }
                val newer = ReleaseFeed.isNewer(release.version, currentVersionName())
                val size = String.format(Locale.US, "%.1f МБ",
                    release.apkBytes / 1048576.0)
                KhakiDialogBuilder(this, palette)
                    .setTitle(if (newer) "Доступна версия " + release.version
                        else "TowerRadar " + release.version)
                    .setMessage((if (newer) "Можно скачать обновление.\n"
                        else "Установлена не более старая версия.\n") +
                        "Канал: " + (if (testChannel) "тестовый" else "стабильный"))
                    .setPositiveButton(if (newer) "Скачать APK" else "Открыть релиз") { _, _ ->
                        if (newer) confirmReleaseDownload(release)
                        else openExternal(release.releasePage)
                    }
                    .setNegativeButton("Закрыть", null)
                    .show()
            }
        }
    }

    private fun checkReleaseOnLaunch() {
        apiExecutor.execute {
            val available = try {
                listOfNotNull(ReleaseFeed.query(true), ReleaseFeed.query(false))
                    .reduceOrNull { current, next ->
                        if (ReleaseFeed.isNewer(next.version, current.version)) next else current
                    }
            } catch (_: Exception) { null }
            runOnUiThread {
                if (isFinishing || isDestroyed || available == null ||
                    !ReleaseFeed.isNewer(available.version, currentVersionName())) return@runOnUiThread
                val prefs = getPreferences(Context.MODE_PRIVATE)
                if (prefs.getString("ota_offered_version", "") == available.version) return@runOnUiThread
                prefs.edit().putString("ota_offered_version", available.version).apply()
                KhakiDialogBuilder(this, palette)
                    .setTitle("Доступно обновление " + available.version)
                    .setMessage("Доступна новая версия TowerRadar (GitHub Releases). " +
                        "Скачать, проверить и установить её можно прямо в приложении. " +
                        "Текущие данные сохраняются при установке обновления тем же ключом.")
                    .setPositiveButton("Открыть обновление") { _, _ ->
                        confirmReleaseDownload(available)
                    }.setNegativeButton("Позже", null).show()
            }
        }
    }

    private fun confirmReleaseDownload(release: TowerRelease) {
        if (!release.prerelease) {
            downloadReleaseApk(release)
            return
        }
        KhakiDialogBuilder(this, palette)
            .setTitle("Внимание! Тестовая версия")
            .setMessage("Вы собираетесь установить тестовую версию TowerRadar " +
                release.version + ". Она может содержать ошибки, работать нестабильно " +
                "или нарушать работу отдельных функций.\n\n" +
                "Вы уверены, что хотите продолжить обновление?")
            .setPositiveButton("Да") { _, _ -> downloadReleaseApk(release) }
            .setNegativeButton("Нет", null)
            .show()
    }

    private fun downloadReleaseApk(release: TowerRelease) {
        if (release.digest == null) {
            KhakiDialogBuilder(this, palette).setTitle("Релиз не проверен")
                .setMessage("Нет контрольной суммы SHA-256. Установка через приложение заблокирована.")
                .setPositiveButton("Закрыть", null).show()
            return
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(8), dp(6), dp(8), dp(9))
        }
        val intro = text("TowerRadar " + release.version +
            " · " + String.format(Locale.US, "%.1f МБ", release.apkBytes / 1048576.0) +
            "\nЗагрузка с GitHub · проверка SHA-256 и сертификата перед установкой.",
            13f, palette.secondary)
        content.addView(intro)
        val bar = ProgressBar(this, null,
            android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            progress = 0
            progressTintList = ColorStateList.valueOf(palette.olive)
        }
        content.addView(bar, LinearLayout.LayoutParams(-1, dp(5)).apply {
            topMargin = dp(14)
            bottomMargin = dp(10)
        })
        val state = text("Пока не загружено", 13f, palette.text)
        content.addView(state)
        val action = styledButton("Скачать обновление", 14f, true)
        content.addView(action, LinearLayout.LayoutParams(-1, dp(48)).apply {
            topMargin = dp(12)
        })
        val dialog = KhakiDialogBuilder(this, palette)
            .setTitle("Обновление TowerRadar")
            .setView(content).setNegativeButton("Позже", null).show()
        val handler = Handler(Looper.getMainLooper())
        val watcher = object : Runnable {
            override fun run() {
                if (!dialog.isShowing || isFinishing || isDestroyed) return
                val progress = try { TowerApkInstaller.status(this@MainActivity, release) }
                    catch (_: Exception) { null }
                when (progress?.state) {
                    android.app.DownloadManager.STATUS_RUNNING,
                    android.app.DownloadManager.STATUS_PENDING,
                    android.app.DownloadManager.STATUS_PAUSED -> {
                        val pct = if (progress.total > 0)
                            (progress.downloaded * 100L / progress.total).toInt().coerceIn(0, 100)
                            else 0
                        bar.isIndeterminate = progress.total <= 0
                        bar.progress = pct
                        state.text = if (progress.total > 0) "Загрузка: $pct%"
                            else "Загрузка с GitHub…"
                        action.text = "Загрузка…"
                        action.isEnabled = false
                    }
                    android.app.DownloadManager.STATUS_SUCCESSFUL -> {
                        bar.isIndeterminate = false
                        bar.progress = 100
                        state.text = "APK загружен · требуется проверка"
                        action.text = "Проверить и установить"
                        action.isEnabled = true
                    }
                    android.app.DownloadManager.STATUS_FAILED -> {
                        bar.isIndeterminate = false
                        bar.progress = 0
                        state.text = "Ошибка загрузки (код ${progress.reason}). Можно повторить."
                        action.text = "Повторить"
                        action.isEnabled = true
                    }
                    else -> {
                        bar.isIndeterminate = false
                        action.text = "Скачать обновление"
                        action.isEnabled = true
                    }
                }
                handler.postDelayed(this, 850L)
            }
        }
        action.setOnClickListener {
            val current = try { TowerApkInstaller.status(this, release) }
                catch (_: Exception) { null }
            if (current?.state == android.app.DownloadManager.STATUS_SUCCESSFUL) {
                action.isEnabled = false
                state.text = "Проверяем SHA-256, пакет и сертификат…"
                installVerifiedRelease(release)
            } else {
                try {
                    TowerApkInstaller.download(this, release)
                    action.isEnabled = false
                    state.text = "Загрузка начата"
                } catch (e: Exception) {
                    state.text = e.localizedMessage ?: "Ошибка запуска загрузки"
                }
            }
        }
        handler.post(watcher)
    }

    private fun installVerifiedRelease(release: TowerRelease) {
        apiExecutor.execute {
            val file = try { TowerApkInstaller.verifiedFile(applicationContext, release) }
            catch (e: Exception) {
                runOnUiThread {
                    if (!isDestroyed) Toast.makeText(this, "Проверка APK: " +
                        (e.localizedMessage ?: "ошибка"), Toast.LENGTH_LONG).show()
                }
                return@execute
            }
            runOnUiThread {
                if (isDestroyed || isFinishing) return@runOnUiThread
                if (!TowerApkInstaller.canInstall(this)) {
                    pendingOtaInstall = release
                    KhakiDialogBuilder(this, palette).setTitle("Разрешение Android")
                        .setMessage("Разрешите TowerRadar устанавливать скачанные APK. " +
                            "После возврата проверка файла повторится, " +
                            "а установку потребуется подтвердить в Android.")
                        .setPositiveButton("Открыть настройки") { _, _ ->
                            try { startActivity(TowerApkInstaller.permissionIntent(this)) }
                            catch (_: Exception) {
                                pendingOtaInstall = null
                                Toast.makeText(this, "Настройки установки недоступны",
                                    Toast.LENGTH_LONG).show()
                            }
                        }.setNegativeButton("Позже", null).show()
                } else try {
                    startActivity(TowerApkInstaller.installationIntent(this, file))
                } catch (e: Exception) {
                    Toast.makeText(this, "Не удалось открыть установщик Android",
                        Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun copyCoordinates(value: String) {
        try {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Координаты", value))
            Toast.makeText(this, "Координаты скопированы", Toast.LENGTH_SHORT).show()
        } catch (_: Exception) {
            Toast.makeText(this, "Не удалось скопировать координаты", Toast.LENGTH_SHORT).show()
        }
    }

    private fun currentVersionName(): String = try {
        @Suppress("DEPRECATION")
        (packageManager.getPackageInfo(packageName, 0).versionName ?: "0.27.3")
    } catch (_: Exception) { "0.27.3" }

    private fun openExternal(url: String) {
        try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
        catch (_: Exception) {
            Toast.makeText(this, "Не удалось открыть браузер", Toast.LENGTH_SHORT).show()
        }
    }


    private fun showAddUserTower() {
        if (userTowerEditor?.showing == true) return
        val point = map?.cameraPosition?.target ?: activeUserPoint() ?: LatLng(55.75, 37.62)
        userTowerEditor = UserTowerEditor(this, palette, userTowerStore, apiExecutor,
            point.latitude, point.longitude, onSaved = { site ->
                if (!isFinishing && !isDestroyed) {
                    selectedOperator = null
                    technologyFilter = null
                    sourceFilter = null
                    updateOperatorChoices()
                    showPins()
                    if (sheetExpanded) expandSheet(false)
                    map?.animateCamera(CameraUpdateFactory.newLatLngZoom(
                        LatLng(site.latitude, site.longitude), 15.5), 420)
                    Toast.makeText(this, "Вышка сохранена только на устройстве",
                        Toast.LENGTH_LONG).show()
                }
            }, onClosed = { userTowerEditor = null })
        userTowerEditor?.show()
    }

    private fun showUserTowers() {
        val records = userTowerStore.all()
        if (records.isEmpty()) {
            KhakiDialogBuilder(this, palette).setTitle("Мои вышки")
                .setMessage("Пока нет добавленных вручную объектов. " +
                    "Нажмите «Добавить вышку» в панели Подробнее или настройках.")
                .setPositiveButton("Добавить") { _, _ -> showAddUserTower() }
                .setNegativeButton("Закрыть", null).show()
            return
        }
        KhakiDialogBuilder(this, palette).setTitle("Мои вышки · " + records.size)
            .setItems(records.map { it.name ?: "Вышка" }.toTypedArray()) { _, index ->
                showUserTowerActions(records[index])
            }.setNegativeButton("Закрыть", null).show()
    }

    private fun showUserTowerActions(site: TowerSite) {
        val photo = userTowerStore.photoFor(site.id)
        val choices = listOf("Показать на карте") +
            if (photo != null) listOf("Открыть фотографию", "Удалить запись")
            else listOf("Удалить запись")
        KhakiDialogBuilder(this, palette).setTitle(site.name ?: "Моя вышка")
            .setMessage("Локальная запись · " +
                String.format(Locale.US, "%.6f, %.6f", site.latitude, site.longitude))
            .setItems(choices.toTypedArray()) { _, index ->
                when (choices[index]) {
                    "Показать на карте" -> {
                        selectedOperator = null
                        technologyFilter = null
                        sourceFilter = null
                        updateOperatorChoices()
                        showPins()
                        if (sheetExpanded) expandSheet(false)
                        map?.animateCamera(CameraUpdateFactory.newLatLngZoom(
                            LatLng(site.latitude, site.longitude), 15.5), 450)
                        rootView.postDelayed({
                            if (!isDestroyed) selectTower(LatLng(site.latitude, site.longitude))
                        }, 530L)
                    }
                    "Открыть фотографию" -> showUserPhoto(site)
                    "Удалить запись" -> confirmRemoveUserTower(site)
                }
            }.setNegativeButton("Назад") { _, _ -> showUserTowers() }.show()
    }

    private fun showUserPhoto(site: TowerSite) {
        val photo = userTowerStore.photoFor(site.id) ?: return
        val image = android.widget.ImageView(this).apply {
            setPadding(dp(3), dp(3), dp(3), dp(3))
            adjustViewBounds = true
            scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
            val bitmap = android.graphics.BitmapFactory.decodeFile(photo.absolutePath)
            setImageBitmap(bitmap)
            contentDescription = "Фотография вышки " + (site.name ?: "")
        }
        KhakiDialogBuilder(this, palette)
            .setTitle("Фотография · " + (site.name ?: "вышка"))
            .setView(image)
            .setPositiveButton("Закрыть", null).show()
    }

    private fun confirmRemoveUserTower(site: TowerSite) {
        KhakiDialogBuilder(this, palette).setTitle("Удалить вышку?")
            .setMessage("Будет удалена только ваша локальная запись и её фотография. " +
                "Данные из OpenStreetMap не затрагиваются.")
            .setPositiveButton("Удалить") { _, _ ->
                if (userTowerStore.delete(site.id)) {
                    if (selectedAimSite?.id == site.id) {
                        details.visibility = View.GONE
                        clearAimSelection()
                    }
                    updateOperatorChoices()
                    showPins()
                    Toast.makeText(this, "Локальная вышка удалена", Toast.LENGTH_SHORT).show()
                }
            }.setNegativeButton("Отмена", null).show()
    }

    private fun showTowerStorageSettings() {
        val total = try { towerDatabase.count() } catch (_: Exception) { 0 }
        KhakiDialogBuilder(this, palette).setTitle("Хранилище и данные")
            .setMessage("В базе: " + total + " объектов. Кеш временных файлов и база " +
                "вышек очищаются отдельно. API-ключи и настройки не затрагиваются.")
            .setItems(arrayOf("Очистить кеш приложения", "Удалить базу вышек")) { _, action ->
                if (busy) {
                    Toast.makeText(this, "Дождитесь окончания обновления", Toast.LENGTH_LONG).show()
                } else if (action == 0) {
                    KhakiDialogBuilder(this, palette).setTitle("Очистить кеш?")
                        .setMessage("Временные результаты запросов и кеш карт будут удалены. " +
                            "Постоянная база вышек останется.")
                        .setPositiveButton("Очистить") { _, _ ->
                            getSharedPreferences("tower_results", Context.MODE_PRIVATE)
                                .edit().clear().apply()
                            mapTileCache.clear { _, message ->
                                Toast.makeText(this, message, Toast.LENGTH_LONG).show()
                            }
                        }.setNegativeButton("Отмена", null).show()
                } else {
                    KhakiDialogBuilder(this, palette).setTitle("Удалить " + total + " объектов?")
                        .setMessage("База вышек будет удалена. Кеш карт, API-ключи и " +
                            "настройки останутся. Новый поиск — вручную или после перезапуска.")
                        .setPositiveButton("Удалить базу") { _, _ ->
                            executor.execute {
                                try {
                                    towerDatabase.clear()
                                    getSharedPreferences("tower_results", Context.MODE_PRIVATE)
                                        .edit().clear().apply()
                                    runOnUiThread {
                                        if (!isDestroyed) {
                                            sites = emptyList()
                                            servingSite = null
                                            lastFetch = null
                                            updateOperatorChoices()
                                            showPins()
                                            updateOverlays()
                                            status.text = "База вышек очищена"
                                        }
                                    }
                                } catch (_: Exception) {
                                    runOnUiThread {
                                        Toast.makeText(this, "Ошибка очистки базы", Toast.LENGTH_LONG).show()
                                    }
                                }
                            }
                        }.setNegativeButton("Отмена", null).show()
                }
            }.setNegativeButton("Закрыть", null).show()
    }

    private fun showBackupMenu() {
        KhakiDialogBuilder(this, palette).setTitle("Резервная копия настроек")
            .setMessage("API-ключи, ручная точка и параметры приложения сохраняются " +
                "только в зашифрованном файле AES-256-GCM. " +
                "Придумайте отдельный пароль длиной не меньше 12 символов. " +
                "Ни пароль, ни ключи не передаются в GitHub или разработчику. " +
                "Подпись APK .jks здесь НЕ экспортируется.")
            .setItems(arrayOf("Экспортировать зашифрованную копию",
                "Восстановить из зашифрованной копии")) { _, i ->
                if (i == 0) prepareEncryptedBackup()
                else try {
                    val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                        addCategory(Intent.CATEGORY_OPENABLE)
                        type = "*/*"
                    }
                    startActivityForResult(intent, 6102)
                } catch (_: Exception) {
                    Toast.makeText(this, "Не удалось выбрать файл", Toast.LENGTH_LONG).show()
                }
            }
            .setNegativeButton("Закрыть", null).show()
    }

    private fun backupPasswordInput(hint: String): EditText = EditText(this).apply {
        this.hint = hint
        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        setSingleLine(true)
    }

    private fun prepareEncryptedBackup() {
        val field = backupPasswordInput("Пароль не менее 12 символов")
        KhakiDialogBuilder(this, palette).setTitle("Зашифровать настройки")
            .setMessage("Сохраните пароль отдельно. Без него резервную копию " +
                "восстановить невозможно. Не используйте пароль от GitHub.")
            .setView(field).setPositiveButton("Выбрать файл") { _, _ ->
                val password = field.text.toString().toCharArray()
                try {
                    pendingEncryptedBackup = SettingsVault.encrypt(
                        SettingsVault.capture(getPreferences(Context.MODE_PRIVATE)), password)
                    field.text.clear()
                    val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                        addCategory(Intent.CATEGORY_OPENABLE)
                        type = "application/octet-stream"
                        putExtra(Intent.EXTRA_TITLE, "TowerRadar-settings.trbk")
                    }
                    startActivityForResult(intent, 6101)
                } catch (e: Exception) {
                    pendingEncryptedBackup = null
                    Toast.makeText(this, e.message ?: "Ошибка экспорта",
                        Toast.LENGTH_LONG).show()
                } finally {
                    password.fill('\u0000')
                }
            }.setNegativeButton("Отмена", null).show()
    }

    private fun restoreEncryptedBackup(bytes: ByteArray) {
        val field = backupPasswordInput("Пароль резервной копии")
        KhakiDialogBuilder(this, palette).setTitle("Восстановить настройки")
            .setMessage("При восстановлении сохранённые настройки и API-ключи " +
                "заменят текущие значения. Другие данные останутся без изменений.")
            .setView(field).setPositiveButton("Восстановить") { _, _ ->
                val password = field.text.toString().toCharArray()
                try {
                    val json = SettingsVault.decrypt(bytes, password)
                    SettingsVault.restore(getPreferences(Context.MODE_PRIVATE), json)
                    field.text.clear()
                    Toast.makeText(this, "Настройки восстановлены", Toast.LENGTH_LONG).show()
                    recreate()
                } catch (_: Exception) {
                    Toast.makeText(this, "Ошибка пароля или повреждённый файл",
                        Toast.LENGTH_LONG).show()
                } finally {
                    password.fill('\u0000')
                }
            }.setNegativeButton("Отмена", null).show()
    }

    @Deprecated("Deprecated by Android")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 6201) {
            if (resultCode == Activity.RESULT_OK) userTowerEditor?.onImagePicked(data?.data)
            return
        }
        if (requestCode == 6101) {
            val bytes = pendingEncryptedBackup
            pendingEncryptedBackup = null
            if (resultCode != Activity.RESULT_OK || bytes == null) return
            try {
                val uri = data?.data ?: return
                contentResolver.openOutputStream(uri, "w")?.use { it.write(bytes) }
                    ?: error("Нет доступа к файлу")
                Toast.makeText(this, "Зашифрованная копия сохранена", Toast.LENGTH_LONG).show()
            } catch (_: Exception) {
                Toast.makeText(this, "Ошибка сохранения файла", Toast.LENGTH_LONG).show()
            } finally { bytes.fill(0) }
        } else if (requestCode == 6102 && resultCode == Activity.RESULT_OK) {
            try {
                val uri = data?.data ?: return
                val content = java.io.ByteArrayOutputStream()
                contentResolver.openInputStream(uri)?.use { input ->
                    val buffer = ByteArray(8192)
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        if (content.size() + n > 500000) error("Слишком большой файл")
                        content.write(buffer, 0, n)
                    }
                } ?: error("Нет доступа к файлу")
                restoreEncryptedBackup(content.toByteArray())
            } catch (_: Exception) {
                Toast.makeText(this, "Не удалось прочитать резервную копию",
                    Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun showCacheSettings() {
        KhakiDialogBuilder(this, palette)
            .setTitle("Кеш карт · TowerRadar")
            .setMessage(
                "Загруженные при просмотре участки OSM и Яндекс Карт " +
                "сохраняются автоматически, пока это разрешено правилами сервера. " +
                "Для Яндекса предусмотрена очистка не позднее 29 дней. " +
                "Google и 2ГИС не сохраняются на диск из-за ограничений поставщиков. " +
                "Вариант «Без ограничений» не ограничивает объём кеша приложением; " +
                "доступное место, срок хранения тайлов и условия сервиса действуют.\n\n" +
                "Это не предварительное скачивание районов: вне сети карта отображает " +
                "только ещё действительные сохранённые тайлы.\n\n" +
                mapTileCache.detailedDiagnostics(MapSources.providers.getOrNull(providerIndex))
            )
            .setItems(arrayOf(
                "Лимит: 500 МБ",
                "Без ограничений",
                "Очистить загруженные карты"
            )) { _, selected ->
                when (selected) {
                    0, 1 -> mapTileCache.changeLimit(
                        MapTileCache.limitsMb[selected]
                    ) { _, message ->
                        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
                    }
                    2 -> KhakiDialogBuilder(this, palette)
                        .setTitle("Очистить кеш карт?")
                        .setMessage("Ранее просмотренные участки могут потребовать " +
                            "новой загрузки через интернет. Сохранённые ключи, " +
                            "ручная геопозиция и другие настройки останутся.")
                        .setPositiveButton("Очистить") { _, _ ->
                            mapTileCache.clear { ok, message ->
                                Toast.makeText(this, message, Toast.LENGTH_LONG).show()
                            }
                        }
                        .setNegativeButton("Отмена", null).show()
                }
            }
            .setNegativeButton("Закрыть", null)
            .show()
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
        updateAimColors()
    }

    /**
     * Compact, non-modal overlay: the map remains fully visible and touchable.
     * The existing MapLibre line/fill properties are changed on every slider event.
     */
    private fun showDirectionBrightness() {
        if (directionPanel != null) {
            hideDirectionPanel()
            return
        }
        if (isChoosingCrosshair) finishCrosshairMode(false)
        if (mapFullscreen) setMapFullscreen(false)
        if (sheetExpanded) expandSheet(false)

        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(palette.surface, 17)
            elevation = dp(13).toFloat()
            setPadding(dp(12), dp(8), dp(12), dp(9))
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
        }
        directionPanel = panel
        // Leave room for the right-hand map rail rather than covering it.
        val width = (resources.displayMetrics.widthPixels - dp(88))
            .coerceAtMost(dp(390)).coerceAtLeast(dp(210))
        rootView.addView(panel, FrameLayout.LayoutParams(
            width, -2, Gravity.START or Gravity.BOTTOM
        ).apply { setMargins(dp(11), 0, 0, systemBottomInset + dp(96)) })

        val head = row()
        panel.addView(head, LinearLayout.LayoutParams(-1, dp(27)))
        head.addView(text("Направление", 14f, palette.text).apply {
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }, LinearLayout.LayoutParams(0, -2, 1f))
        head.addView(text("✕", 21f, palette.secondary).apply {
            contentDescription = "Закрыть настройку направления"
            gravity = Gravity.CENTER
            setPadding(dp(8), 0, dp(2), 0)
            setOnClickListener { hideDirectionPanel() }
        }, LinearLayout.LayoutParams(dp(38), -1))

        fun sliderRow(title: String, start: Int, onValue: (Int) -> Unit) {
            val label = row()
            panel.addView(label, LinearLayout.LayoutParams(-1, -2).apply {
                topMargin = dp(4)
            })
            label.addView(text(title, 12f, palette.text),
                LinearLayout.LayoutParams(0, -2, 1f))
            val currentValue = text("$start%", 12f, palette.olive)
            label.addView(currentValue)
            val seek = SeekBar(this).apply {
                max = 100
                progress = start
                progressTintList = ColorStateList.valueOf(palette.olive)
                thumbTintList = ColorStateList.valueOf(palette.olive)
                contentDescription = title
                setPadding(0, 0, 0, 0)
            }
            seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(bar: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (!fromUser) return
                    currentValue.text = "$progress%"
                    onValue(progress)
                    updateDirectionStyle()
                }
                override fun onStartTrackingTouch(bar: SeekBar?) = Unit
                override fun onStopTrackingTouch(bar: SeekBar?) = Unit
            })
            panel.addView(seek, LinearLayout.LayoutParams(-1,
                dp(if (landscape) 26 else 34)))
        }
        sliderRow("Пунктирный луч", beamBrightness) {
            beamBrightness = it
            getPreferences(Context.MODE_PRIVATE).edit().putInt("beam_brightness", it).apply()
        }
        sliderRow("Сектор", sectorBrightness) {
            sectorBrightness = it
            getPreferences(Context.MODE_PRIVATE).edit().putInt("sector_brightness", it).apply()
        }
        sliderRow("Длина сектора", sectorLengthPercent) {
            sectorLengthPercent = it.coerceIn(10, 70)
            getPreferences(Context.MODE_PRIVATE).edit()
                .putInt("sector_length", sectorLengthPercent).apply()
            updateOverlays()
        }
        val finish = row().apply { gravity = Gravity.END or Gravity.CENTER_VERTICAL }
        panel.addView(finish, LinearLayout.LayoutParams(-1, dp(35)).apply {
            topMargin = dp(2)
        })
        finish.addView(styledButton("Готово", 12f, true).apply {
            setOnClickListener { hideDirectionPanel() }
        }, LinearLayout.LayoutParams(dp(88), dp(34)))
        applyResponsiveLayout(false)
    }

    private fun hideDirectionPanel() {
        directionPanel?.let { rootView.removeView(it) }
        directionPanel = null
    }

    private fun connectionStatus(pref: String): String {
        val stored = getPreferences(Context.MODE_PRIVATE).getString(pref, "").orEmpty()
        if (stored.isBlank()) return "Нет ключа"
        if (checkingApi.contains(pref)) return "⏳ Проверяем подключение…"
        if (pref == "map_key_yandex_js") return "Ключ сохранён · проверка при открытии карты"
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
        refreshYandexConnectButton()
    }

    private fun refreshYandexConnectButton() {
        if (!::yandexConnectButton.isInitialized) return
        val key = getPreferences(Context.MODE_PRIVATE)
            .getString("map_key_yandex", "").orEmpty()
        val state = PanelUiPolicy.yandexAction(
            key.isNotBlank(),
            checkingApi.contains("map_key_yandex"),
            apiChecks["map_key_yandex"]?.connected
        )
        yandexConnectButton.visibility =
            if (state == PanelUiPolicy.YandexAction.HIDDEN) View.GONE else View.VISIBLE
        yandexConnectButton.isEnabled = state != PanelUiPolicy.YandexAction.WAIT
        yandexConnectButton.text = when (state) {
            PanelUiPolicy.YandexAction.CONNECT -> "Подключить Яндекс Карты"
            PanelUiPolicy.YandexAction.VERIFY -> "Проверить подключение Яндекс Карт"
            PanelUiPolicy.YandexAction.FIX -> "Исправить подключение Яндекс Карт"
            PanelUiPolicy.YandexAction.WAIT -> "Проверяем Яндекс Карты…"
            PanelUiPolicy.YandexAction.HIDDEN -> ""
        }
        yandexConnectButton.setOnClickListener {
            when (state) {
                PanelUiPolicy.YandexAction.CONNECT,
                PanelUiPolicy.YandexAction.FIX -> showYandexSetup()
                PanelUiPolicy.YandexAction.VERIFY -> verifyConnection("map_key_yandex")
                else -> Unit
            }
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
            Triple("map_key_yandex", "Яндекс Карты", "Tiles API · схема"),
            Triple("map_key_yandex_js", "Яндекс JS · спутник / гибрид", "JavaScript API 2.1"),
            Triple("map_key_maptiler", "MapTiler · карта / спутник / гибрид",
                "Бесплатный тариф для личного некоммерческого использования"),
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
                    "map_key_yandex_js" -> requestMapKey(
                        MapSources.providers.first { it.id == "yandex-js-hybrid" })
                    "map_key_maptiler" -> requestMapKey(
                        MapSources.providers.first { it.id == "maptiler-streets" })
                    else -> editCellKey()
                }
            }
            panel.addView(cell, LinearLayout.LayoutParams(-1, -2).apply {
                bottomMargin = dp(7)
            })
        }
        val legalButton = styledButton("Условия использования Яндекс Карт ↗", 12f)
        panel.addView(legalButton, LinearLayout.LayoutParams(-1, dp(40)).apply {
            bottomMargin = dp(7)
        })
        legalButton.setOnClickListener {
            try {
                startActivity(Intent(Intent.ACTION_VIEW,
                    Uri.parse("https://yandex.ru/legal/maps_api/")))
            } catch (_: Exception) {
                Toast.makeText(this, "Не удалось открыть ссылку", Toast.LENGTH_SHORT).show()
            }
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
                    0 -> requestMapKey(MapSources.allProviders.first { it.id == "google-road" })
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

    private fun verifySavedConnections(skipActive: Boolean = false) {
        // The selected map source is already verified by useMapChoice(); avoid
        // duplicate chargeable Google sessions or OpenCellID requests on startup.
        val selected = getPreferences(Context.MODE_PRIVATE)
            .getString("map_provider", "auto")
        val activePref = MapSources.providers.firstOrNull { it.id == selected }?.keyPref
        for (pref in listOf("map_key_yandex",
            "map_key_maptiler", "opencellid_key")) {
            if (!skipActive || pref != activePref) verifyConnection(pref)
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
        apiExecutor.execute {
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

    private fun selectDisplayMode() {
        val entries = arrayOf(
            "Стандартный · часы и системные уведомления видны",
            "Полноэкранный · больше места для карты и сведений у камеры"
        )
        KhakiDialogBuilder(this, palette).setTitle("Экран и отображение")
            .setSingleChoiceItems(entries,
                if (displayMode == DisplayModePolicy.FULLSCREEN) 1 else 0) { dialog, selected ->
                val next = if (selected == 1) DisplayModePolicy.FULLSCREEN
                    else DisplayModePolicy.STANDARD
                getPreferences(Context.MODE_PRIVATE).edit()
                    .putString("display_mode", next).apply()
                displayMode = next
                dialog.dismiss()
                applyDisplayMode()
            }
            .setNegativeButton("Отмена", null).show()
    }

    private fun selectSim() {
        if (!cellMonitor.hasSignalPermission()) {
            KhakiDialogBuilder(this, palette).setTitle("Доступ к SIM")
                .setMessage("Для выбора SIM и измерения сигнала нужен доступ к телефону. " +
                    "Точная геопозиция нужна только для Cell ID и соседних сот.")
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


    /** One invalidation path for explicit geolocation changes and meaningful GPS moves. */
    private fun onActivePositionChanged() {
        ++terrainRevision
        terrainDialog?.dismiss()
        terrainDialog = null
        if (::details.isInitialized) details.visibility = View.GONE
        clearAimSelection()
        if (!isFinishing && !isDestroyed) {
            KhakiDialogBuilder(this, palette)
                .setTitle("Геопозиция изменена")
                .setMessage("Для корректного профиля высот выберите требуемую вышку " +
                    "на карте повторно.")
                .setPositiveButton("Понятно", null)
                .show()
        }
    }

    private fun setManualPosition(point: LatLng) {
        val oldManual = manualPoint
        val previous = activeUserPoint()
        val delta = previous?.let {
            TowerData.distanceKm(it.latitude, it.longitude,
                point.latitude, point.longitude) * 1000.0
        } ?: Double.POSITIVE_INFINITY
        val changed = PositionChangePolicy.manualUpdateChangesOrigin(
            oldManual != null, delta)
        if (changed) onActivePositionChanged()
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
        if (manualPoint != null) onActivePositionChanged()
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

    private fun radioMetrics(
        dbm: Int?, rsrp: Int?, rsrq: Int?, sinr: Float?
    ): String = listOfNotNull(
        dbm?.let { "Мощность: " + it + " dBm" },
        rsrp?.let { "RSRP: " + it + " dBm" },
        rsrq?.let { "RSRQ: " + it + " dB" },
        sinr?.let { "SINR: " + String.format(Locale.US, "%.1f", it) + " dB" }
    ).joinToString("\n").ifBlank { "Точный уровень сигнала модем не сообщил" }

    private fun radioExtra(reading: ServingCell): String = listOfNotNull(
        reading.pci?.let { "PCI / код физической соты: " + it },
        reading.channel?.let {
            (if (reading.network == "NR") "NRARFCN"
                else if (reading.network == "LTE") "EARFCN"
                else "Номер радиоканала") + ": " + it
        },
        reading.bands.takeIf { it.isNotEmpty() }?.let {
            "Диапазоны: " + it.joinToString(", ") { band ->
                (if (reading.network == "NR") "n" else "B") + band
            }
        },
        reading.bandwidthKhz?.let { "Ширина канала: " + it / 1000.0 + " МГц" },
        reading.timingAdvance?.let { "Timing Advance: " + it + " (не расстояние до вышки)" }
    ).joinToString("\n").ifBlank { "Дополнительные радиопараметры недоступны" }

    private fun showSignalDetails() {
        val reading = servingCell
        val signal = radioSignal?.takeIf {
            SystemClock.elapsedRealtime() - it.measuredAt < 60000L
        }
        val intro = if (reading != null) {
            listOfNotNull(
                "Оператор: " + reading.operator.ifBlank { "не указан" },
                "Сеть: " + reading.network,
                radioMetrics(reading.dbm, reading.rsrp, reading.rsrq, reading.sinr),
                "MCC: " + reading.mcc + " · MNC: " + reading.mnc,
                "TAC / LAC: " + reading.area,
                "Cell ID: " + reading.cellId,
                "SIM ID: " + reading.subId,
                radioExtra(reading),
                if (servingSite == null) "Положение соты не найдено; это не ближайшая OSM-вышка"
                else "OpenCellID: примерная координата радиосоты",
                servingSite?.let { site ->
                    activeUserPoint()?.let { point ->
                        "Расстояние от " + (if (manualPoint != null) "ручной точки" else "GPS") +
                            ": " + String.format(Locale.US, "%.2f км",
                            TowerData.distanceKm(point.latitude, point.longitude,
                                site.latitude, site.longitude))
                    }
                }
            ).joinToString("\n")
        } else if (signal != null) {
            "Оператор: " + signal.operator.ifBlank { "не указан" } +
                "\nСеть: " + signal.network +
                "\n" + radioMetrics(signal.dbm, signal.rsrp, signal.rsrq, signal.sinr) +
                "\nCell ID / соседние соты: недоступны без геолокации или разрешения"
        } else {
            "Нет измерений модема. Проверьте доступ к телефону, SIM и режим полёта. " +
                "GPS для отображения мощности сети не требуется."
        }
        KhakiDialogBuilder(this, palette).setTitle("Подключённая сеть")
            .setMessage(intro)
            .setPositiveButton("Соседние соты") { _, _ -> showNeighborCells() }
            .setNegativeButton("Закрыть", null).show()
    }

    private fun updateRadioSignal(signal: RadioSignal) {
        radioSignal = signal
        refreshSignalDisplay()
    }

    private fun refreshSignalDisplay() {
        if (!::signalLabel.isInitialized) return
        val radio = radioSignal?.takeIf {
            SystemClock.elapsedRealtime() - it.measuredAt in 0..60000L
        }
        val cell = servingCell
        if (radio == null && cell == null) {
            signalLabel.text = if (cellMonitor.hasSignalPermission())
                "Сеть: ожидаем сигнал модема" else "Сеть: разрешите доступ к телефону"
            signalDetails.text = "Нет измерения"
            showSignalBars(0)
            refreshCameraStatus()
            return
        }
        val operator = cell?.operator?.takeIf { it.isNotBlank() }
            ?: radio?.operator?.takeIf { it.isNotBlank() } ?: "Мобильная сеть"
        val network = cell?.network?.let { when (it) {
            "NR" -> "5G NR"
            "LTE" -> "4G LTE"
            "UMTS" -> "3G"
            "GSM" -> "2G"
            else -> it
        } } ?: radio?.network.orEmpty()
        signalLabel.text = operator + if (network.isBlank()) "" else " · " + network
        val dbm = cell?.dbm ?: radio?.dbm
        val rsrp = cell?.rsrp ?: radio?.rsrp
        val rsrq = cell?.rsrq ?: radio?.rsrq
        val sinr = cell?.sinr ?: radio?.sinr
        signalDetails.text = listOfNotNull(
            dbm?.let { it.toString() + " dBm" },
            rsrp?.let { "RSRP " + it + " dBm" },
            rsrq?.let { "RSRQ " + it + " dB" },
            sinr?.let { "SINR " + String.format(Locale.US, "%.1f", it) + " dB" }
        ).joinToString(" · ").ifBlank { "Модем не сообщает мощность" }
        val level = (cell?.level ?: radio?.level ?: 0).coerceIn(0, 4)
        showSignalBars(level + 1)
        signalLabel.contentDescription = "Сеть " + network + ", уровень " +
            (level + 1) + " из 5; " + signalDetails.text
        refreshCameraStatus()
    }

    private fun updateNeighborCells(cells: List<ServingCell>) {
        nearbyCells = cells
        if (::neighborsButton.isInitialized) {
            neighborsButton.text = "Соседние соты · " + cells.size +
                (if (!cellMonitor.canReadCellDetails()) " · нужен GPS" else "")
        }
    }

    private fun showNeighborCells() {
        val cells = nearbyCells
        if (!cellMonitor.canReadCellDetails()) {
            KhakiDialogBuilder(this, palette).setTitle("Соседние соты")
                .setMessage("Android скрывает идентификаторы соседних сот, когда " +
                    "выключена системная геолокация или нет разрешения на точную позицию. " +
                    "Мощность основной сети при этом продолжаем показывать отдельно. " +
                    "Включите геолокацию для расширенного сканирования.")
                .setPositiveButton("Закрыть", null).show()
            return
        }
        if (cells.isEmpty()) {
            KhakiDialogBuilder(this, palette).setTitle("Соседние соты")
                .setMessage("Модем не выдал соседние соты. На некоторых телефонах " +
                    "и сетях Android возвращает только обслуживающую соту.")
                .setPositiveButton("Обновить") { _, _ -> cellMonitor.refresh() }
                .setNegativeButton("Закрыть", null).show()
            return
        }
        val options = cells.map { c ->
            (c.network + " · " + (c.dbm?.let { it.toString() + " dBm" } ?: "нет dBm")) +
                "\nPCI " + (c.pci?.toString() ?: "—") +
                " · " + (c.bands.firstOrNull()?.let { "B" + it } ?: "без диапазона")
        }.toTypedArray()
        KhakiDialogBuilder(this, palette).setTitle("Соседние соты · " + cells.size)
            .setMessage("Обнаружены модемом. Не каждая радиосота имеет известные координаты.")
            .setItems(options) { _, index -> showNeighborInfo(cells[index]) }
            .setNegativeButton("Закрыть", null).show()
    }

    private fun showNeighborInfo(cell: ServingCell) {
        val match = sites.firstOrNull {
            it.id == "opencellid/" + cell.identity
        }
        val message = listOf(
            (if (cell.registered) "Обслуживающая" else "Соседняя") + " · " + cell.network,
            "Оператор: " + cell.operator.ifBlank { "не указан" },
            "MCC/MNC: " + cell.mcc + "/" + cell.mnc,
            "TAC/LAC: " + cell.area,
            "Cell ID: " + (if (cell.validId) cell.cellId.toString() else "не предоставлен"),
            radioMetrics(cell.dbm, cell.rsrp, cell.rsrq, cell.sinr),
            radioExtra(cell),
            if (match != null) "Координаты: оценка OpenCellID" else
                "Координаты неизвестны — не подставляем ближайшую OSM-вышку"
        ).joinToString("\n")
        KhakiDialogBuilder(this, palette).setTitle("Радиосота · подробности")
            .setMessage(message)
            .setPositiveButton(
                if (match != null) "На карте" else "Искать в OpenCellID"
            ) { _, _ ->
                if (match != null) {
                    map?.animateCamera(CameraUpdateFactory.newLatLngZoom(
                        LatLng(match.latitude, match.longitude), 14.0), 400)
                    expandSheet(false)
                } else lookupNeighbor(cell)
            }
            .setNegativeButton("Закрыть", null).show()
    }

    private fun lookupNeighbor(cell: ServingCell) {
        val key = getPreferences(Context.MODE_PRIVATE)
            .getString("opencellid_key", "").orEmpty()
        if (key.isBlank() || !cell.validId) {
            KhakiDialogBuilder(this, palette).setTitle("Поиск координат")
                .setMessage(if (key.isBlank()) "Сначала добавьте ключ OpenCellID в настройках."
                    else "Модем не сообщил полный идентификатор соты. Точный поиск невозможен.")
                .setPositiveButton("Закрыть", null).show()
            return
        }
        Toast.makeText(this, "Поиск точного Cell ID в OpenCellID…", Toast.LENGTH_SHORT).show()
        cellExecutor.execute {
            val site = CellSources.lookup(key, cell)
            runOnUiThread {
                if (isDestroyed || isFinishing) return@runOnUiThread
                if (site == null) {
                    KhakiDialogBuilder(this, palette).setTitle("Сота не найдена")
                        .setMessage("В OpenCellID нет подходящей записи. " +
                            "Не выбираем ближайшую вышку без подтверждения.")
                        .setPositiveButton("Закрыть", null).show()
                } else {
                    sites = (sites + site).distinctBy { it.id }
                    showPins()
                    KhakiDialogBuilder(this, palette).setTitle("Найдена оценка OpenCellID")
                        .setMessage(
                            String.format(Locale.US, "Координаты: %.6f, %.6f\n",
                                site.latitude, site.longitude) +
                                "Это примерная позиция радиосоты, не точное положение мачты."
                        )
                        .setPositiveButton("На карте") { _, _ ->
                            map?.animateCamera(CameraUpdateFactory.newLatLngZoom(
                                LatLng(site.latitude, site.longitude), 14.0), 400)
                            expandSheet(false)
                        }.setNegativeButton("Закрыть", null).show()
                }
            }
        }
    }

    private fun showAntennaRegistries() {
        val names = arrayOf(
            "Нидерланды · Antenneregister",
            "Франция · Cartoradio (ANFR)",
            "Германия · Bundesnetzagentur EMF"
        )
        val addresses = arrayOf(
            "https://antenneregister.nl/",
            "https://www.cartoradio.fr/",
            "https://www.bundesnetzagentur.de/DE/Fachthemen/Telekommunikation/Technik/EMF/EMF-Datenportal/emf_datenportal_node.html"
        )
        KhakiDialogBuilder(this, palette).setTitle("Официальные реестры")
            .setMessage("Открываются только официальные сайты в браузере. " +
                "Доступ к выгрузкам и лицензиям различается. " +
                "Запись в реестре подтверждает площадку, но не то, " +
                "что телефон подключён именно к ней.")
            .setItems(names) { _, i ->
                try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(addresses[i]))) }
                catch (_: Exception) {
                    Toast.makeText(this, "Не удалось открыть браузер", Toast.LENGTH_LONG).show()
                }
            }
            .setNegativeButton("Закрыть", null).show()
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
                apiChecks.remove("opencellid_key")
                verifyConnection("opencellid_key")
                lastFetch = null
                requestedCellId = null
                servingCell?.let(::updateServingCell)
                load(force = true)
            }
            .setNeutralButton("Удалить") { _, _ ->
                getPreferences(Context.MODE_PRIVATE).edit().remove("opencellid_key").apply()
                apiChecks.remove("opencellid_key")
                checkingApi.remove("opencellid_key")
                redrawConnectionStatuses()
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
        displayMode = DisplayModePolicy.normalize(
            prefs.getString("display_mode", DisplayModePolicy.STANDARD))
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
        sectorLengthPercent = prefs.getInt("sector_length", 30).coerceIn(10, 70)
        MapLibre.getInstance(this)
        mapTileCache = MapTileCache(this)
        mapTileCache.initialize()
        offlineAreas = OfflineAreaManager(this)
        towerDatabase = TowerDatabase(this)
        userTowerStore = UserTowerStore(this)
        // Strictly cap OpenFreeMap offline tile download concurrency to four.
        val mapDispatcher = okhttp3.Dispatcher().apply {
            maxRequests = 4
            maxRequestsPerHost = 4
        }
        HttpRequestUtil.setOkHttpClient(OkHttpClient.Builder()
            .dispatcher(mapDispatcher).addInterceptor { chain ->
            val request = chain.request()
            val url = request.url.toString()
            if (url.contains("tile") || url.contains("tiles.api-maps.yandex.ru") ||
                url.contains("maps.2gis.com")) mapTileCache.recordTileNetworkRequest()
            chain.proceed(request.newBuilder()
                .header("User-Agent", "TowerRadar/0.13.0 (+https://github.com/RussoTurissto/TowerRadar)").build())
        }.build())
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        headingSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        locationFusion = LocationFusion(this, ::showLocation)
        cellMonitor = CellMonitor(this,
            listener = ::updateServingCell,
            signalListener = ::updateRadioSignal,
            neighborsListener = ::updateNeighborCells
        )
        createScreen()
        applyDisplayMode()
        mapView.onCreate(state)
        // Restore all observations from the SQLite store immediately at startup.
        executor.execute {
            try {
                val legacy = getSharedPreferences("tower_results", Context.MODE_PRIVATE)
                if (!legacy.getBoolean("migrated_to_database", false)) {
                    val body = legacy.getString("body", null)
                    val lat = legacy.getString("lat", null)?.toDoubleOrNull()
                    val lon = legacy.getString("lon", null)?.toDoubleOrNull()
                    val range = legacy.getInt("radius", -1)
                    if (body != null && lat != null && lon != null &&
                        lat in -90.0..90.0 && lon in -180.0..180.0 &&
                        range in TowerData.radiiKm) {
                        try { towerDatabase.merge(TowerData.parse(body, lat, lon, range)) }
                        catch (_: Exception) {}
                    }
                    legacy.edit().putBoolean("migrated_to_database", true).apply()
                }
                val saved = towerDatabase.all()
                runOnUiThread {
                    if (!isFinishing && !isDestroyed) {
                        sites = (sites + saved).distinctBy { it.id }
                        updateOperatorChoices()
                        showPins()
                        if (saved.isNotEmpty()) status.text =
                            "Сохранено " + saved.size + " · проверка обновлений"
                    }
                }
            } catch (_: Exception) {}
        }
        mapView.getMapAsync { ready ->
            map = ready
            // Restore camera BEFORE loading any style to avoid OSM/world-map flashes.
            ready.uiSettings.isCompassEnabled = false
            ready.uiSettings.isLogoEnabled = false
            ready.uiSettings.isAttributionEnabled = false // Own visible provider credits remain.
            val storedCamera = readLastCamera()
            if (storedCamera != null) {
                ready.moveCamera(CameraUpdateFactory.newCameraPosition(storedCamera))
                if (center == null) {
                    center = storedCamera.target
                    rootView.postDelayed({
                        if (!isDestroyed && !busy && lastFetch == null) load(false)
                    }, 1200L)
                }
                initiallyCentered = true // GPS updates must not steal a saved view.
                restoredLastCamera = true
            } else {
                ready.moveCamera(CameraUpdateFactory.newLatLngZoom(
                    center ?: LatLng(20.0, 0.0), if (center == null) 2.5 else 12.5))
            }
            northIndicator.setMapBearing(ready.cameraPosition.bearing)
            updateMapCenterCoordinates()
            // Never temporarily replace an explicitly selected map with OSM.
            val savedId = prefs.getString("map_provider", "auto") ?: "auto"
            val preferredId = if (savedId == "auto")
                prefs.getString("map_last_resolved", null) ?: "osmfr" else savedId
            val saved = MapSources.providers.firstOrNull { it.id == preferredId }
            when {
                saved == null -> { applyBasemap(MapSources.providers[0]); chooseBasemap() }
                saved.keyPref == null -> applyBasemap(saved)
                saved.id == "yandex" || MapSources.yandexWeb(saved) ||
                    MapSources.mapTiler(saved) -> {
                    val key = prefs.getString(saved.keyPref, "").orEmpty()
                    if (key.isNotBlank()) {
                        applyBasemap(saved, key)
                        // Validation is informative only; it MUST NOT overwrite user's
                        // saved choice on a transient connectivity/API failure.
                        verifyConnection(saved.keyPref)
                    } else {
                        mapStatus.text = "Для сохранённой карты нужен API-ключ"
                        applyBasemap(MapSources.providers[0])
                    }
                }
                else -> useMapChoice(saved) // Google requires a fresh session token.
            }
            ready.addOnMapClickListener { tapped ->
                if (offlineSelectionEnabled) true
                else if (isChoosingCrosshair) {
                    ready.animateCamera(CameraUpdateFactory.newLatLng(tapped), 250)
                    true
                } else {
                    selectTower(tapped)
                    true
                }
            }
            ready.addOnCameraMoveListener {
                updateMapCenterCoordinates()
                updateOfflineSelectionInfo()
                if (isChoosingCrosshair) updateCrosshairCoordinates()
                northIndicator.setMapBearing(ready.cameraPosition.bearing)
            }
            ready.addOnCameraIdleListener {
                updateMapCenterCoordinates()
                updateOfflineSelectionInfo()
                persistLastCamera(force = true)
                updateOverlays()
                if (isChoosingCrosshair) updateCrosshairCoordinates()
                northIndicator.setMapBearing(ready.cameraPosition.bearing)
                val selected = MapSources.providers.getOrNull(providerIndex)
                if (selected?.id?.startsWith("google-") == true &&
                    googleMapSession?.valid() != true && !choosingMap) {
                    useMapChoice(selected)
                } else requestGoogleAttribution()
            }
        }
        askLocation()
        verifySavedConnections(skipActive = true)
        showStartupInformation()
    }

    /** Viewing position and search/GPS origin are intentionally independent. */
    private fun readLastCamera(): CameraPosition? {
        val prefs = getPreferences(Context.MODE_PRIVATE)
        if (!prefs.getBoolean("last_camera_valid", false)) return null
        val lat = prefs.getString("last_camera_lat", null)?.toDoubleOrNull()
        val lon = prefs.getString("last_camera_lon", null)?.toDoubleOrNull()
        val zoom = prefs.getString("last_camera_zoom", null)?.toDoubleOrNull()
        val bearing = prefs.getString("last_camera_bearing", null)?.toDoubleOrNull() ?: 0.0
        val tilt = prefs.getString("last_camera_tilt", null)?.toDoubleOrNull() ?: 0.0
        if (lat == null || lon == null || zoom == null ||
            !LastMapCameraModel.valid(lat, lon, zoom, bearing, tilt)) return null
        return CameraPosition.Builder()
            .target(LatLng(lat, lon)).zoom(zoom).bearing(bearing).tilt(tilt).build()
    }

    private fun persistLastCamera(force: Boolean = false) {
        val camera = map?.cameraPosition ?: return
        val now = SystemClock.elapsedRealtime()
        if (!force && now - lastCameraSaveAt < 700L) return
        lastCameraSaveAt = now
        val p = camera.target ?: return
        if (!LastMapCameraModel.valid(p.latitude, p.longitude,
                camera.zoom, camera.bearing, camera.tilt)) return
        // A confirmed write in onStop/onPause survives system-initiated process death.
        getPreferences(Context.MODE_PRIVATE).edit()
            .putBoolean("last_camera_valid", true)
            .putString("last_camera_lat", p.latitude.toString())
            .putString("last_camera_lon", p.longitude.toString())
            .putString("last_camera_zoom", camera.zoom.toString())
            .putString("last_camera_bearing", camera.bearing.toString())
            .putString("last_camera_tilt", camera.tilt.toString())
            .apply()
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

        // Unify the diagnostic readouts in one adaptive full-width bar.
        // The separate scrim protects Android status icons over any map tile.
        statusBarProtection = View(this).apply {
            setBackgroundColor(Color.rgb(37, 49, 36))
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        rootView.addView(statusBarProtection,
            FrameLayout.LayoutParams(-1, dp(25), Gravity.TOP))
        topStatusPanel = TowerStatusHeader(this)
        rootView.addView(topStatusPanel,
            FrameLayout.LayoutParams(-1, dp(50), Gravity.TOP))
        headingView = topStatusPanel.headingLabel
        gpsLabel = topStatusPanel.gpsLabel
        topStatusPanel.onHeadingTap { showPositionOptions() }
        topStatusPanel.onSignalTap { showSignalDetails() }
        // Copyable coordinates sit on the MAP, not inside the dark diagnostics bar.
        mapCoordinatesLabel = text("⌖ Центр карты: ожидание", 15f,
            Color.rgb(255, 220, 110)).apply {
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setSingleLine(true)
            ellipsize = TextUtils.TruncateAt.END
            setShadowLayer(dp(3).toFloat(), 0f, dp(1).toFloat(),
                Color.rgb(21, 26, 20))
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(4), 0, dp(8), 0)
            elevation = dp(5).toFloat()
            isClickable = true
            isFocusable = true
            contentDescription = "Координаты центра карты, нажмите для копирования"
            setOnClickListener { mapCenterCoordinateValue?.let(::copyCoordinates) }
        }
        rootView.addView(mapCoordinatesLabel,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(27),
                Gravity.TOP or Gravity.START))

        // System map-only fullscreen button and settings float BELOW the header.
        val header = row()
        topHeader = header
        rootView.addView(header, FrameLayout.LayoutParams(
            -2, dp(44), Gravity.TOP or Gravity.END
        ).apply { setMargins(0, dp(67), dp(12), 0) })
        fullScreenButton = styledButton("⛶", 19f).apply {
            contentDescription = "Полноэкранная карта"
            setOnClickListener { setMapFullscreen(!mapFullscreen) }
        }
        header.addView(fullScreenButton, LinearLayout.LayoutParams(dp(43), dp(43)).apply {
            marginStart = dp(7)
        })
        settingsButton = styledButton("⚙", 22f).apply {
            contentDescription = "Настройки"
            setOnClickListener {
                hideDirectionPanel()
                showSettings()
            }
        }
        header.addView(settingsButton, LinearLayout.LayoutParams(dp(43), dp(43)).apply {
            marginStart = dp(7)
        })

        // White circular N compass with a red/charcoal pointer above the right rail.
        northIndicator = NorthIndicatorView(this, palette.dark).apply {
            contentDescription = "Направление севера"
            isClickable = true
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
            setOnClickListener {
                val phone = toggleSource()
                Toast.makeText(this@MainActivity,
                    if (phone) "Север по датчику телефона" else "Север относительно карты",
                    Toast.LENGTH_SHORT).show()
            }
        }
        rootView.addView(northIndicator, FrameLayout.LayoutParams(
            dp(53), dp(60), Gravity.END or Gravity.BOTTOM
        ).apply { setMargins(0, 0, dp(10), dp(294)) })

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
        // Menu overlay is independent: expanding text never affects fixed button positions.
        actionPopup = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.END
            visibility = View.GONE
            elevation = dp(9).toFloat()
        }
        rootView.addView(actionPopup, FrameLayout.LayoutParams(-2, -2,
            Gravity.END or Gravity.BOTTOM).apply {
            setMargins(0, 0, dp(12), dp(226))
        })
        fun railButton(button: Button, hint: String) {
            button.contentDescription = hint
            floatingRail.addView(button, LinearLayout.LayoutParams(dp(49), dp(49)).apply {
                bottomMargin = dp(8)
            })
        }
        // Legacy fields remain initialized for non-rail search/status handlers.
        gpsRefresh = styledButton("⌖", 22f)
        searchHere = styledButton("⌕", 22f)
        fun railAction(label: String, kind: MapQuickActionIcon.Kind,
                       selected: () -> Unit): LinearLayout {
            val line = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL or Gravity.END
                visibility = View.GONE
                isClickable = true
                isFocusable = true
                contentDescription = label
                setOnClickListener { setActionMenuExpanded(false); selected() }
            }
            line.addView(text(label, 13.5f, palette.text).apply {
                background = rounded(palette.surface, 18)
                setPadding(dp(14), dp(9), dp(14), dp(9))
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                gravity = Gravity.CENTER
                elevation = dp(3).toFloat()
            })
            val iconPlate = FrameLayout(this).apply {
                background = rounded(palette.accentSoft, 24)
                elevation = dp(5).toFloat()
            }
            iconPlate.addView(MapQuickActionIcon(this, kind, palette.text),
                FrameLayout.LayoutParams(dp(31), dp(31), Gravity.CENTER))
            line.addView(iconPlate, LinearLayout.LayoutParams(dp(49), dp(49)).apply {
                marginStart = dp(9)
            })
            actionPopup.addView(line, LinearLayout.LayoutParams(-2, dp(49)).apply {
                bottomMargin = dp(8)
                gravity = Gravity.END
            })
            return line
        }
        actionHeightRow = railAction("Высота", MapQuickActionIcon.Kind.HEIGHT) {
            map?.cameraPosition?.target?.let(::showElevationAtPoint)
        }
        actionTowerRow = railAction("Добавить вышку", MapQuickActionIcon.Kind.TOWER) {
            showAddUserTower()
        }
        actionExpandButton = styledButton("+", 25f).apply {
            background = rounded(palette.surface, 16)
        }
        railButton(actionExpandButton, "Открыть меню действий")
        actionExpandButton.setOnClickListener { setActionMenuExpanded(!actionMenuExpanded) }
        locate = styledButton("◎", 24f, true)
        railButton(locate, "Уточнить GPS и приблизить карту")
        locate.setOnClickListener {
            setActionMenuExpanded(false)
            gpsFocusPending = true
            useAutomaticPosition()
            if (hasLocation()) {
                startLocationUpdates()
                locationFusion.refresh()
            }
            val point = location?.let { LatLng(it.latitude, it.longitude) }
            if (point != null) {
                gpsFocusPending = false
                map?.animateCamera(CameraUpdateFactory.newLatLngZoom(point, 15.5), 500)
                yandexWebMap?.move(point.latitude, point.longitude, 15.5)
            }
        }
        locate.setOnLongClickListener { showPositionOptions(); true }

        // A slim 50dp collapsed sheet: only true signal bars at lower left and
        // a centered, large touch target labelled "Подробнее".
        sheetView = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(palette.surface, 20)
            elevation = dp(12).toFloat()
            setPadding(dp(10), 0, dp(10), 0)
        }
        rootView.addView(sheetView, FrameLayout.LayoutParams(-1, dp(50),
            Gravity.BOTTOM).apply { setMargins(dp(6), 0, dp(6), 0) })

        compactHandleRow = FrameLayout(this)
        sheetView.addView(compactHandleRow,
            LinearLayout.LayoutParams(-1, dp(50)))
        sheetHandle = text("⌃  Подробнее", 13f, palette.text).apply {
            gravity = Gravity.CENTER
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            contentDescription = "Раскрыть панель управления"
        }
        compactHandleRow.addView(sheetHandle, FrameLayout.LayoutParams(
            -1, -1, Gravity.CENTER))
        sheetHandle.setOnClickListener { expandSheet(!sheetExpanded) }
        var touchY = 0f
        var touchX = 0f
        sheetHandle.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    touchY = event.rawY
                    touchX = event.rawX
                    true
                }
                MotionEvent.ACTION_UP -> {
                    val diff = if (landscape) event.rawX - touchX else event.rawY - touchY
                    if (diff < -dp(24)) expandSheet(true)
                    else if (diff > dp(24)) expandSheet(false)
                    else sheetHandle.performClick()
                    true
                }
                MotionEvent.ACTION_CANCEL -> true
                else -> true
            }
        }
        compactSignalBars = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.BOTTOM
            // Keep the 48dp touch target, but raise the five bars within it.
            setPadding(dp(4), 0, dp(4), dp(10))
            contentDescription = "Уровень сигнала. Нажмите для диагностики"
            isClickable = true
            setOnClickListener { showSignalDetails() }
        }
        for (h in listOf(9, 13, 17, 21, 25)) {
            compactSignalBars.addView(View(this).apply {
                background = rounded(palette.signalMuted, 3)
            }, LinearLayout.LayoutParams(dp(8), dp(h)).apply { marginEnd = dp(3) })
        }
        compactHandleRow.addView(compactSignalBars, FrameLayout.LayoutParams(
            -2, dp(48), Gravity.START or Gravity.CENTER_VERTICAL).apply {
                leftMargin = dp(7)
            })

        // Detailed signal readouts remain in the expanded sheet only.
        expandedSignalRow = row().apply { visibility = View.GONE }
        sheetView.addView(expandedSignalRow, LinearLayout.LayoutParams(-1, dp(29)))
        signalLabel = text("Сеть: ожидаем SIM", 14f, palette.text).apply {
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            gravity = Gravity.CENTER_VERTICAL
            setSingleLine(true)
            ellipsize = android.text.TextUtils.TruncateAt.END
        }
        expandedSignalRow.addView(signalLabel, LinearLayout.LayoutParams(0, dp(29), 1f))
        signalBars = LinearLayout(this).apply {
            gravity = Gravity.BOTTOM
            orientation = LinearLayout.HORIZONTAL
        }
        for (h in listOf(9, 12, 15, 19, 23)) {
            signalBars.addView(View(this).apply {
                background = rounded(palette.signalMuted, 3)
            }, LinearLayout.LayoutParams(dp(9), dp(h)).apply { marginStart = dp(3) })
        }
        expandedSignalRow.addView(signalBars, LinearLayout.LayoutParams(-2, dp(25)))
        signalDetails = text("Сигнал: нет измерения", 11f, palette.secondary).apply {
            visibility = View.GONE
            setSingleLine(true)
            ellipsize = android.text.TextUtils.TruncateAt.END
        }
        sheetView.addView(signalDetails)
        expandedSignalRow.setOnClickListener { showSignalDetails() }
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
        val filters = row()
        controls.addView(filters, LinearLayout.LayoutParams(-1, dp(45)))
        val technologies = listOf("Все технологии", "2G", "3G", "4G", "5G")
        val techSpinner = Spinner(this).apply { adapter = choice(technologies) }
        filters.addView(techSpinner, LinearLayout.LayoutParams(0, dp(44), 1f))
        val providers = listOf("Все источники", "OpenStreetMap",
            "OpenCellID", "mylnikov", "beacondb", "MCC-MNC")
        val providerSpinner = Spinner(this).apply { adapter = choice(providers) }
        filters.addView(providerSpinner, LinearLayout.LayoutParams(0, dp(44), 1f)
            .apply { marginStart = dp(6) })
        techSpinner.onItemSelectedListener = object: AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                technologyFilter = technologies.getOrNull(position)?.takeIf { position > 0 }
                showPins()
            }
        }
        providerSpinner.onItemSelectedListener = object: AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                sourceFilter = providers.getOrNull(position)?.takeIf { position > 0 }
                showPins()
            }
        }

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
        refresh.setOnClickListener { expandSheet(false); load(force = true) }
        searchControl.setOnClickListener {
            map?.cameraPosition?.target?.let { point ->
                center = point
                manualCenter = true
                lastFetch = null
                expandSheet(false)
                load(force = true)
            }
        }

        controls.addView(sectionTitle("КАРТОГРАФИЧЕСКАЯ ПОДЛОЖКА"))
        sourceSpinner = Spinner(this)
        sourceSpinner.adapter = choice(listOf("Автоматически · OSM") + MapSources.providers.map { it.name })
        val prefId = getPreferences(Context.MODE_PRIVATE).getString("map_provider", "auto") ?: "auto"
        sourceSpinner.setSelection(MapSources.providers.indexOfFirst { it.id == prefId } + 1)
        controls.addView(sourceSpinner, LinearLayout.LayoutParams(-1, dp(46)))
        yandexConnectButton = styledButton("Подключить Яндекс Карты", 12f)
        controls.addView(yandexConnectButton, LinearLayout.LayoutParams(-1, dp(42)).apply {
            topMargin = dp(5)
        })
        refreshYandexConnectButton()
        mapStatus = text("Выбираем доступный сервер карты…", 11f, palette.secondary)
        controls.addView(mapStatus)
        sourceSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
            override fun onItemSelected(parent: AdapterView<*>?, view: View?,
                                        position: Int, id: Long) {
                if (map == null) return
                val selectedId = if (position == 0) "auto"
                    else MapSources.providers[position - 1].id
                // Android Spinner calls onItemSelected automatically after restore.
                // Ignore it when it is just restoring the persisted choice.
                if (getPreferences(Context.MODE_PRIVATE)
                        .getString("map_provider", "auto") == selectedId) return
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
        neighborsButton = styledButton("Соседние соты · сканер", 12f)
        controls.addView(neighborsButton, LinearLayout.LayoutParams(-1, dp(43)).apply {
            topMargin = dp(7)
        })
        neighborsButton.setOnClickListener { showNeighborCells() }
        val addTowerControl = styledButton("＋  Добавить вышку вручную", 12f)
        controls.addView(addTowerControl, LinearLayout.LayoutParams(-1, dp(43)).apply {
            topMargin = dp(7)
        })
        addTowerControl.setOnClickListener { showAddUserTower() }
        // Removed duplicate Settings and obsolete antenna-registry menu items.

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
            setOnClickListener { details.visibility = View.GONE; clearAimSelection() }
        }, LinearLayout.LayoutParams(dp(43), dp(40)))
        detailBody = text("", 12f, palette.secondary).apply {
            maxLines = 18
            ellipsize = android.text.TextUtils.TruncateAt.END
        }
        details.addView(detailBody)
        detailCoordinates = text("Координаты вышки", 13.5f, palette.text).apply {
            setPadding(dp(10), dp(10), dp(10), dp(10))
            background = rounded(palette.elevated, 11)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            isClickable = true
            isFocusable = true
            setOnClickListener {
                selectedAimSite?.let { site ->
                    CoordinateFormatter.format(site.latitude, site.longitude)?.let(::copyCoordinates)
                }
            }
        }
        details.addView(detailCoordinates, LinearLayout.LayoutParams(-1, -2).apply {
            topMargin = dp(7)
        })
        val analysisActions = row()
        details.addView(analysisActions, LinearLayout.LayoutParams(-1, dp(44)).apply {
            topMargin = dp(8)
        })
        profileButton = styledButton("⌁  Профиль высот", 11.5f).apply {
            visibility = View.VISIBLE
            setOnClickListener { selectedAimSite?.let(::showElevationProfile) }
        }
        analysisActions.addView(profileButton, LinearLayout.LayoutParams(0, dp(43), 1f).apply {
            marginEnd = dp(6)
        })
        coverageButton = styledButton("◌  Зона покрытия", 11.5f).apply {
            setOnClickListener { selectedAimSite?.let(::chooseCoverage) }
        }
        analysisActions.addView(coverageButton, LinearLayout.LayoutParams(0, dp(43), 1f))
        val manualActions = row()
        details.addView(manualActions, LinearLayout.LayoutParams(-1, dp(42)).apply {
            topMargin = dp(5)
        })
        photoButton = styledButton("▧  Фото", 11.5f).apply {
            setOnClickListener { selectedAimSite?.let(::showUserPhoto) }
        }
        manualActions.addView(photoButton, LinearLayout.LayoutParams(0, dp(41), 1f).apply {
            marginEnd = dp(6)
        })
        removeTowerButton = styledButton("Удалить мою запись", 11.5f).apply {
            setOnClickListener { selectedAimSite?.let(::confirmRemoveUserTower) }
        }
        manualActions.addView(removeTowerButton, LinearLayout.LayoutParams(0, dp(41), 1f))
        manualActions.visibility = View.GONE
        manualTowerActions = manualActions

        // The actual copyright must remain visible, but should not look
        // like another large map control. A subtle outline keeps it legible.
        mapAttribution = text("© OpenStreetMap contributors", 10f,
            if (palette.dark) Color.WHITE else Color.rgb(34, 48, 31)).apply {
            setPadding(dp(3), dp(2), dp(3), dp(2))
            maxWidth = dp(315)
            maxLines = 4
            background = null
            setShadowLayer(dp(1).toFloat(), 0f, dp(1).toFloat(),
                if (palette.dark) Color.BLACK else Color.WHITE)
        }
        // Text changes can alter the actual copyright height. The overlay
        // coordinator runs before drawing; never restart/cancel the sheet animator
        // because a provider's attribution has wrapped to a new line.
        rootView.addView(mapAttribution, FrameLayout.LayoutParams(-2, -2,
            Gravity.START or Gravity.BOTTOM).apply {
            setMargins(dp(8), 0, 0, dp(91))
        })
        // Floating progress strip (approved variant #4).
        progressStrip = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(palette.surface, 12)
            elevation = dp(10).toFloat()
            setPadding(dp(12), dp(7), dp(12), dp(8))
            visibility = View.GONE
        }
        progressLabel = text("Обновление вышек", 11f, palette.text).apply {
            maxLines = 2
            ellipsize = android.text.TextUtils.TruncateAt.END
        }
        progressStrip.addView(progressLabel, LinearLayout.LayoutParams(-1, -2))
        progressBar = ProgressBar(this, null,
            android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            isIndeterminate = true
            progressTintList = ColorStateList.valueOf(palette.olive)
            indeterminateTintList = ColorStateList.valueOf(palette.olive)
        }
        progressStrip.addView(progressBar, LinearLayout.LayoutParams(-1, dp(3))
            .apply { topMargin = dp(6) })
        rootView.addView(progressStrip,
            FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM).apply {
                setMargins(dp(12), 0, dp(12), dp(112))
            })
        // Crosshair mode is a separate, focused location picker: drag the map
        // under the reticle, tap a desired point or zoom for precise placement.
        crosshairReticle = CrosshairView(this, palette.dark).apply {
            visibility = View.VISIBLE
            isClickable = false
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        rootView.addView(crosshairReticle, FrameLayout.LayoutParams(
            dp(64), dp(64), Gravity.CENTER
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
        rootView.viewTreeObserver.addOnPreDrawListener(object : ViewTreeObserver.OnPreDrawListener {
            override fun onPreDraw(): Boolean {
                if (!isDestroyed && ::progressStrip.isInitialized &&
                    ::mapAttribution.isInitialized && ::details.isInitialized) {
                    updateFloatingOverlayPositions()
                }
                return true
            }
        })
        rootView.post { applyResponsiveLayout(false) }

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
            val rawTop = if (Build.VERSION.SDK_INT >= 30)
                insets.getInsets(WindowInsets.Type.statusBars() or
                    WindowInsets.Type.displayCutout()).top
            else if (Build.VERSION.SDK_INT >= 28)
                maxOf(insets.systemWindowInsetTop, insets.displayCutout?.safeInsetTop ?: 0)
            else insets.systemWindowInsetTop
            val top = if (displayMode == DisplayModePolicy.FULLSCREEN)
                maxOf(rawTop, dp(36)) else rawTop
            val bottom = if (Build.VERSION.SDK_INT >= 30)
                insets.getInsets(WindowInsets.Type.navigationBars() or
                    WindowInsets.Type.displayCutout()).bottom
            else insets.systemWindowInsetBottom
            val left = if (Build.VERSION.SDK_INT >= 30)
                insets.getInsets(WindowInsets.Type.navigationBars() or
                    WindowInsets.Type.displayCutout()).left else 0
            val right = if (Build.VERSION.SDK_INT >= 30)
                insets.getInsets(WindowInsets.Type.navigationBars() or
                    WindowInsets.Type.displayCutout()).right else 0
            if (systemTopInset != top || systemBottomInset != bottom ||
                systemLeftInset != left || systemRightInset != right) {
                systemTopInset = top
                systemBottomInset = bottom
                systemLeftInset = left
                systemRightInset = right
                applyResponsiveLayout(false)
            }
            updateCameraStatusLayout(insets)
            insets
        }
        // Layout bounds can change without a new insets dispatch (split-screen,
        // display scaling, rotation). Recalculate from the measured viewport.
        rootView.addOnLayoutChangeListener { _, l, _, r, _, oldL, _, oldR, _ ->
            if (r - l != oldR - oldL)
                rootView.rootWindowInsets?.let(::updateCameraStatusLayout)
        }
        rootView.post { rootView.requestApplyInsets() }
    }

    /**
     * Android 15+ enforces edge-to-edge, so the map remains full-bleed in both modes.
     * Only the status bar is hidden in immersive mode; navigation gestures remain available.
     */
    private fun applyDisplayMode() {
        val fullscreen = displayMode == DisplayModePolicy.FULLSCREEN
        val protectedColor = Color.rgb(37, 49, 36)
        // The status region is always protected by a stable opaque dark surface.
        // This prevents white status icons disappearing over white map tiles.
        window.statusBarColor = if (Build.VERSION.SDK_INT >= 30)
            Color.TRANSPARENT else protectedColor
        if (Build.VERSION.SDK_INT >= 28) {
            val attributes = window.attributes
            attributes.layoutInDisplayCutoutMode = if (fullscreen)
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            else WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_DEFAULT
            window.attributes = attributes
        }
        if (Build.VERSION.SDK_INT >= 30) {
            window.setDecorFitsSystemWindows(false)
            window.insetsController?.let { controller ->
                controller.systemBarsBehavior =
                    WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                if (fullscreen) controller.hide(WindowInsets.Type.statusBars())
                else controller.show(WindowInsets.Type.statusBars())
                // White status icons on the fixed dark scrim, independently of map/theme.
                val statusFlag = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                controller.setSystemBarsAppearance(0, statusFlag)
                val navFlag = WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
                controller.setSystemBarsAppearance(if (palette.dark) 0 else navFlag, navFlag)
            }
        } else {
            val navIcons = if (palette.dark) 0 else View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
            val flags = if (fullscreen)
                View.SYSTEM_UI_FLAG_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                else 0
            window.decorView.systemUiVisibility = navIcons or flags
        }
        if (::topStatusPanel.isInitialized) {
            statusBarProtection.visibility = if (fullscreen) View.GONE else View.VISIBLE
            rootView.requestApplyInsets()
            rootView.post {
                if (!isDestroyed) {
                    rootView.requestApplyInsets()
                    rootView.rootWindowInsets?.let(::updateCameraStatusLayout)
                    applyResponsiveLayout(false)
                }
            }
        }
    }

    private fun refreshCameraStatus() {
        if (!::topStatusPanel.isInitialized) return
        val cell = servingCell
        val radio = radioSignal?.takeIf {
            SystemClock.elapsedRealtime() - it.measuredAt in 0..60000L
        }
        topStatusPanel.updateNetwork(
            cell?.operator ?: radio?.operator,
            cell?.network ?: radio?.network,
            cell?.dbm ?: radio?.dbm,
            cell?.rsrp ?: radio?.rsrp,
            cell?.rsrq ?: radio?.rsrq,
            cell?.sinr ?: radio?.sinr
        )
        topStatusPanel.updateTowers(visible.size, sites.size)
    }

    /** Use the actual per-device cutout geometry; do not assume a central camera. */
    private fun updateCameraStatusLayout(insets: WindowInsets) {
        if (!::topStatusPanel.isInitialized) return
        val immersive = displayMode == DisplayModePolicy.FULLSCREEN
        val cutouts = if (Build.VERSION.SDK_INT >= 28 && immersive)
            insets.displayCutout?.boundingRects.orEmpty()
                .filter { it.top < dp(52) && it.bottom > 0 }
        else emptyList()
        val ranges = cutouts.map { it.left..it.right }
        val bottom = cutouts.maxOfOrNull { it.bottom } ?: 0
        val width = rootView.width.takeIf { it > 0 } ?:
            resources.displayMetrics.widthPixels
        val height = topStatusPanel.arrange(width, ranges, bottom, immersive,
            systemLeftInset, systemRightInset)
        topPanelHeight = height
        val topInset = if (immersive) 0 else systemTopInset
        topStatusPanel.layoutParams =
            (topStatusPanel.layoutParams as FrameLayout.LayoutParams).apply {
                gravity = Gravity.TOP
                this.width = ViewGroup.LayoutParams.MATCH_PARENT
                this.height = height
                leftMargin = 0; rightMargin = 0
                topMargin = topInset
            }
        statusBarProtection.layoutParams =
            (statusBarProtection.layoutParams as FrameLayout.LayoutParams).apply {
                this.width = ViewGroup.LayoutParams.MATCH_PARENT
                this.height = systemTopInset
                topMargin = 0
            }
        statusBarProtection.visibility = if (immersive) View.GONE else View.VISIBLE
        // Only the yellow text and copy glyph are clickable. A full-width
        // TextView was intercepting map taps in the otherwise empty right side.
        mapCoordinatesLabel.maxWidth =
            (width - systemLeftInset - systemRightInset - dp(22))
                .coerceAtLeast(dp(120))
        mapCoordinatesLabel.layoutParams =
            (mapCoordinatesLabel.layoutParams as FrameLayout.LayoutParams).apply {
                gravity = Gravity.TOP or Gravity.START
                this.width = ViewGroup.LayoutParams.WRAP_CONTENT
                this.height = dp(27)
                leftMargin = systemLeftInset + dp(12)
                rightMargin = systemRightInset + dp(10)
                topMargin = topInset + height + dp(1)
            }
        topStatusPanel.visibility = if (immersive && Build.VERSION.SDK_INT >= 30 &&
            insets.isVisible(WindowInsets.Type.statusBars()))
            View.INVISIBLE else View.VISIBLE
        applyResponsiveLayout(false)
    }

    /** The same MapView survives rotation: only existing overlays change bounds. */
    private fun landscapePanelWidth(expanded: Boolean = sheetExpanded): Int {
        val full = rootView.width.takeIf { it > 0 } ?: resources.displayMetrics.widthPixels
        val wide = (full * 0.29f).toInt().coerceAtLeast(dp(190))
            .coerceAtMost(dp(320))
            .coerceAtMost((full * 0.37f).toInt())
        return if (expanded) wide else (wide * 0.81f).toInt().coerceAtLeast(dp(165))
    }

    private fun landscapeSheetHeight(): Int {
        val total = rootView.height.takeIf { it > 0 } ?: resources.displayMetrics.heightPixels
        return (total - systemTopInset - systemBottomInset - dp(69))
            .coerceAtLeast(dp(150))
    }

    private fun applyResponsiveLayout(animate: Boolean) {
        if (!::sheetView.isInitialized || !::rootView.isInitialized) return
        sheetAnimator?.cancel()
        sheetAnimator = null
        val isWide = landscape
        val sideWidth = if (isWide && sheetExpanded && !mapFullscreen && !isChoosingCrosshair)
            landscapePanelWidth() else 0
        val targetWidth = if (isWide) landscapePanelWidth() else ViewGroup.LayoutParams.MATCH_PARENT
        val targetHeight = when {
            isWide && sheetExpanded -> landscapeSheetHeight()
            isWide -> dp(50)
            sheetExpanded -> {
                val total = rootView.height.takeIf { it > 0 } ?: resources.displayMetrics.heightPixels
                (total * .73f).toInt().coerceAtMost(total - systemTopInset - dp(48))
                    .coerceAtLeast(dp(170))
            }
            else -> dp(50)
        }
        sheetView.layoutParams = (sheetView.layoutParams as FrameLayout.LayoutParams).apply {
            width = targetWidth
            height = targetHeight
            gravity = if (isWide) Gravity.END or Gravity.BOTTOM else Gravity.BOTTOM
            leftMargin = if (isWide) 0 else dp(6) + systemLeftInset
            rightMargin = dp(if (isWide) 7 else 6) + systemRightInset
            topMargin = 0
            bottomMargin = systemBottomInset
        }
        sheetHandle.text = when {
            isWide && sheetExpanded -> "›  Свернуть"
            isWide -> "‹  Подробнее"
            sheetExpanded -> "⌄  Свернуть"
            else -> "⌃  Подробнее"
        }
        compactHandleRow.layoutParams = (compactHandleRow.layoutParams
            as LinearLayout.LayoutParams).apply { height = dp(if (sheetExpanded) 40 else 50) }
        compactSignalBars.visibility = if (sheetExpanded) View.GONE else View.VISIBLE
        expandedSignalRow.visibility = if (sheetExpanded) View.VISIBLE else View.GONE
        signalDetails.visibility = if (sheetExpanded) View.VISIBLE else View.GONE
        sheetExtra.visibility = if (sheetExpanded) View.VISIBLE else View.GONE
        sheetView.visibility = if (mapFullscreen || isChoosingCrosshair || offlineSelectionEnabled)
            View.GONE else View.VISIBLE

        topHeader.layoutParams = (topHeader.layoutParams as FrameLayout.LayoutParams).apply {
            width = ViewGroup.LayoutParams.WRAP_CONTENT
            height = dp(44)
            gravity = Gravity.TOP or Gravity.END
            topMargin = (if (displayMode == DisplayModePolicy.FULLSCREEN)
                0 else systemTopInset) + (topPanelHeight.takeIf { it > 0 } ?: dp(50)) +
                dp(if (isWide) 6 else 8)
            leftMargin = systemLeftInset
            rightMargin = systemRightInset + dp(10) + sideWidth
        }
        floatingRail.visibility = if (mapFullscreen || isChoosingCrosshair ||
            offlineSelectionEnabled || (sheetExpanded && !isWide)) View.GONE else View.VISIBLE
        if ((mapFullscreen || isChoosingCrosshair || offlineSelectionEnabled ||
                sheetExpanded) && actionMenuExpanded) {
            actionMenuExpanded = false
            actionHeightRow.visibility = View.GONE
            actionTowerRow.visibility = View.GONE
            actionPopup.visibility = View.GONE
            actionExpandButton.text = "+"
            northIndicator.animate().cancel()
            northIndicator.translationY = 0f
        }
        // A consistent baseline keeps all three buttons directly above the
        // collapsed sheet. The compass sits immediately above the whole rail.
        val sheetIsVisible = !mapFullscreen && !isChoosingCrosshair
        val railBottom = systemBottomInset +
            if (!sheetIsVisible || (isWide && sheetExpanded)) dp(8)
            else targetHeight + dp(8)
        floatingRail.layoutParams = (floatingRail.layoutParams as FrameLayout.LayoutParams).apply {
            gravity = Gravity.END or Gravity.BOTTOM
            width = dp(49)
            rightMargin = systemRightInset + sideWidth + dp(12)
            bottomMargin = railBottom
        }
        actionPopup.layoutParams = (actionPopup.layoutParams as FrameLayout.LayoutParams).apply {
            gravity = Gravity.END or Gravity.BOTTOM
            width = ViewGroup.LayoutParams.WRAP_CONTENT
            rightMargin = systemRightInset + sideWidth + dp(12)
            bottomMargin = railBottom + dp(2 * (49 + 8))
        }
        northIndicator.visibility = if (sheetExpanded && !isWide &&
            !mapFullscreen) View.GONE else View.VISIBLE
        northIndicator.layoutParams =
            (northIndicator.layoutParams as FrameLayout.LayoutParams).apply {
                gravity = Gravity.END or Gravity.BOTTOM
                rightMargin = systemRightInset + sideWidth + dp(12)
                topMargin = 0
                // The footer disappears in map fullscreen: land at the real
                // lower-right SAFE screen corner, above gesture navigation.
                bottomMargin = if (mapFullscreen)
                    systemBottomInset + dp(7)
                else railBottom + dp(2 * (49 + 8) + 5)
            }
        crosshairControls.layoutParams =
            (crosshairControls.layoutParams as FrameLayout.LayoutParams).apply {
                width = if (isWide)
                    landscapePanelWidth(true) else ViewGroup.LayoutParams.MATCH_PARENT
                gravity = Gravity.END or Gravity.BOTTOM
                leftMargin = if (isWide) 0 else dp(10) + systemLeftInset
                rightMargin = dp(10) + systemRightInset
                bottomMargin = dp(if (isWide) 9 else 16) + systemBottomInset
            }
        directionPanel?.let { panel ->
            panel.layoutParams = (panel.layoutParams as FrameLayout.LayoutParams).apply {
                width = if (isWide) landscapePanelWidth(true) else
                    (rootView.width - dp(88)).coerceAtMost(dp(390)).coerceAtLeast(dp(210))
                gravity = if (isWide) Gravity.END or Gravity.CENTER_VERTICAL else
                    Gravity.START or Gravity.BOTTOM
                leftMargin = if (isWide) 0 else systemLeftInset + dp(11)
                rightMargin = if (isWide) systemRightInset + dp(9) else 0
                bottomMargin = if (isWide) 0 else targetHeight + systemBottomInset + dp(12)
            }
        }
        // Horizontal margins and widths depend on side rails, but their
        // VERTICAL position belongs exclusively to updateFloatingOverlayPositions().
        // It follows the measured animated footer and any selected tower card.
        if (::mapAttribution.isInitialized) {
            val lp = mapAttribution.layoutParams as FrameLayout.LayoutParams
            val newLeft = systemLeftInset + dp(8)
            val newRight = systemRightInset + dp(8) + sideWidth
            if (lp.leftMargin != newLeft || lp.rightMargin != newRight) {
                lp.gravity = Gravity.START or Gravity.BOTTOM
                lp.leftMargin = newLeft
                lp.rightMargin = newRight
                mapAttribution.layoutParams = lp
            }
        }
        if (::progressStrip.isInitialized) {
            val railVisible = floatingRail.visibility == View.VISIBLE
            val compassInFooter = mapFullscreen && northIndicator.visibility == View.VISIBLE
            val available = (rootView.width.takeIf { it > 0 }
                ?: resources.displayMetrics.widthPixels) -
                systemLeftInset - systemRightInset - sideWidth - dp(24)
            val reserve = if (railVisible || compassInFooter) dp(77) else 0
            val lift = (railVisible || compassInFooter) && available - reserve < dp(168)
            val lp = progressStrip.layoutParams as FrameLayout.LayoutParams
            val newLeft = systemLeftInset + dp(12)
            val newRight = systemRightInset + sideWidth + dp(12) +
                if (lift) 0 else reserve
            if (lp.leftMargin != newLeft || lp.rightMargin != newRight) {
                lp.gravity = Gravity.BOTTOM
                lp.leftMargin = newLeft
                lp.rightMargin = newRight
                progressStrip.layoutParams = lp
            }
        }
        if (::details.isInitialized) {
            details.layoutParams = (details.layoutParams as FrameLayout.LayoutParams).apply {
                width = if (isWide) (rootView.width - sideWidth - dp(30))
                    .coerceAtMost(dp(395)).coerceAtLeast(dp(195))
                    else ViewGroup.LayoutParams.MATCH_PARENT
                leftMargin = systemLeftInset + dp(11)
                rightMargin = systemRightInset + dp(11)
                bottomMargin = if (isWide) systemBottomInset + dp(8)
                    else systemBottomInset + targetHeight + dp(15)
            }
        }
        updateFloatingOverlayPositions(targetHeight)
        if (animate) {
            // MapLibre camera and raster cache are never torn down during rotation.
            // Animate ONLY the overlays; do not fade or reposition the map itself.
            for (view in listOf(sheetView, topHeader, floatingRail, actionPopup,
                    northIndicator)) {
                view.animate().cancel()
                view.alpha = 0.65f
                view.animate().alpha(1f).setDuration(180L).start()
            }
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        ++compassBounceGeneration
        compassBounceAnimator?.cancel()
        compassBounceAnimator = null
        if (::northIndicator.isInitialized) northIndicator.translationY = 0f
        super.onConfigurationChanged(newConfig)
        // Handle rotation in place: MapView, style, network streams and camera survive.
        sheetAnimator?.cancel()
        applyResponsiveLayout(true)
        rootView.post {
            if (!isDestroyed) {
                rootView.requestApplyInsets()
                rootView.rootWindowInsets?.let(::updateCameraStatusLayout)
                // Read the NEW measured viewport after Android has resized the window.
                applyResponsiveLayout(false)
                updateCrosshairCoordinates()
                updateOverlays()
                map?.let { northIndicator.setMapBearing(it.cameraPosition.bearing) }
            }
        }
    }

    private fun startCrosshairMode() {
        val current = map ?: return
        if (isChoosingCrosshair) return
        hideDirectionPanel()
        isChoosingCrosshair = true
        crosshairStart = current.cameraPosition.target
        crosshairStartZoom = current.cameraPosition.zoom
        details.visibility = View.GONE
        sheetView.visibility = View.GONE
        floatingRail.visibility = View.GONE
        crosshairReticle.visibility = View.VISIBLE
        crosshairControls.visibility = View.VISIBLE
        updateCrosshairCoordinates()
        applyResponsiveLayout(false)
        Toast.makeText(this, "Перемещайте карту или коснитесь нужной точки",
            Toast.LENGTH_SHORT).show()
    }

    private fun setActionMenuExpanded(expanded: Boolean) {
        if (!::actionHeightRow.isInitialized) return
        actionMenuExpanded = expanded
        actionPopup.visibility = if (expanded) View.VISIBLE else View.GONE
        actionHeightRow.visibility = if (expanded) View.VISIBLE else View.GONE
        actionTowerRow.visibility = if (expanded) View.VISIBLE else View.GONE
        actionExpandButton.text = if (expanded) "×" else "+"
        actionExpandButton.contentDescription =
            if (expanded) "Закрыть меню действий" else "Открыть меню действий"
        applyResponsiveLayout(false)
        northIndicator.animate().cancel()
        if (northIndicator.visibility == View.VISIBLE) {
            northIndicator.animate().translationY(
                if (expanded) -dp(2 * (49 + 8)).toFloat() else 0f
            ).setDuration(220L).start()
        } else northIndicator.translationY = 0f
    }

    private fun updateMapCenterCoordinates() {
        // The reticle and this value refer to the camera target, not the GPS marker.
        map?.cameraPosition?.target?.let {
            mapCenterCoordinateValue = CoordinateFormatter.format(it.latitude, it.longitude)
            if (::mapCoordinatesLabel.isInitialized) {
                val value = mapCenterCoordinateValue
                mapCoordinatesLabel.text = if (value != null) "⌖ $value  ⧉"
                    else "⌖ Центр карты: ожидание"
                mapCoordinatesLabel.contentDescription = if (value != null)
                    "Координаты центра карты $value. Нажмите, чтобы скопировать"
                    else "Координаты центра карты пока неизвестны"
            }
        }
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
        crosshairReticle.visibility = View.VISIBLE
        crosshairControls.visibility = View.GONE
        sheetView.visibility = if (mapFullscreen) View.GONE else View.VISIBLE
        floatingRail.visibility = if (mapFullscreen || (sheetExpanded && !landscape))
            View.GONE else View.VISIBLE
        applyResponsiveLayout(false)
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
        if (directionPanel != null) hideDirectionPanel()
        else if (offlineSelectionEnabled) finishOfflineSelection(false)
        else if (isChoosingCrosshair) finishCrosshairMode(false)
        else if (sheetExpanded) expandSheet(false)
        else super.onBackPressed()
    }

    /**
     * Consume the entire tap gesture when dismissing the expanded sheet.
     * This prevents the same tap selecting a tower or moving the map.
     * Bounds are obtained at event time, including during sheet animation.
     */
    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (consumingOutsidePanelGesture) {
            if (event.actionMasked == MotionEvent.ACTION_UP ||
                event.actionMasked == MotionEvent.ACTION_CANCEL) {
                consumingOutsidePanelGesture = false
            }
            return true
        }
        if (event.actionMasked == MotionEvent.ACTION_DOWN &&
            ::sheetView.isInitialized && sheetView.isShown) {
            val panelBounds = Rect()
            sheetView.getGlobalVisibleRect(panelBounds)
            val inside = panelBounds.contains(event.rawX.toInt(), event.rawY.toInt())
            if (PanelUiPolicy.shouldCollapseOnOutsideTouch(
                    sheetExpanded, mapFullscreen, isChoosingCrosshair, inside
                )) {
                consumingOutsidePanelGesture = true
                expandSheet(false)
                return true
            }
        }
        return super.dispatchTouchEvent(event)
    }

    /**
     * Entering map-only fullscreen: the compass drops vertically at its existing
     * right-hand X coordinate, lands at the safe lower screen edge and rebounds three times.
     * Exiting: never animate upwards; restore the normal anchored position instantly.
     */
    private fun setMapFullscreen(full: Boolean) {
        if (mapFullscreen == full) return
        compassBounceAnimator?.cancel()
        compassBounceAnimator = null
        northIndicator.translationY = 0f
        northIndicator.scaleX = 1f
        northIndicator.scaleY = 1f
        val beforeY = if (full && northIndicator.isShown &&
            northIndicator.height > 0) IntArray(2).also {
            northIndicator.getLocationOnScreen(it)
        }[1] else null
        val ticket = ++compassBounceGeneration
        if (full) hideDirectionPanel()
        if (actionMenuExpanded) setActionMenuExpanded(false)
        mapFullscreen = full
        sheetView.visibility = if (full) View.GONE else View.VISIBLE
        floatingRail.visibility =
            if (full || isChoosingCrosshair || (sheetExpanded && !landscape))
                View.GONE else View.VISIBLE
        fullScreenButton.text = if (full) "▣" else "⛶"
        fullScreenButton.contentDescription = if (full) "Вернуть панель" else "На весь экран"
        applyResponsiveLayout(false)
        if (full) details.visibility = View.GONE
        if (full && beforeY != null && ValueAnimator.areAnimatorsEnabled()) {
            // Wait for the new bottom margin to be applied to the same compass view.
            val observer = northIndicator.viewTreeObserver
            observer.addOnPreDrawListener(object : ViewTreeObserver.OnPreDrawListener {
                override fun onPreDraw(): Boolean {
                    if (observer.isAlive) observer.removeOnPreDrawListener(this)
                    if (ticket != compassBounceGeneration || !mapFullscreen ||
                        isDestroyed || isFinishing) return true
                    val after = IntArray(2)
                    northIndicator.getLocationOnScreen(after)
                    val startY = (beforeY - after[1]).toFloat()
                    if (startY >= -dp(12).toFloat()) return true
                    northIndicator.translationY = startY
                    val bounce = ValueAnimator.ofFloat(0f, 1f).apply {
                        duration = CompassBouncePolicy.DURATION_MS
                        interpolator = LinearInterpolator()
                        addUpdateListener { running ->
                            if (ticket == compassBounceGeneration && mapFullscreen) {
                                northIndicator.translationY = CompassBouncePolicy.offset(
                                    running.animatedValue as Float,
                                    startY, resources.displayMetrics.density
                                )
                            }
                        }
                        addListener(object : AnimatorListenerAdapter() {
                            override fun onAnimationEnd(animation: Animator) {
                                if (ticket == compassBounceGeneration) {
                                    northIndicator.translationY = 0f
                                    compassBounceAnimator = null
                                }
                            }
                        })
                    }
                    compassBounceAnimator = bounce
                    bounce.start()
                    return true
                }
            })
            northIndicator.invalidate()
        }
    }

    private fun expandSheet(expanded: Boolean) {
        if (expanded) hideDirectionPanel()
        if (mapFullscreen) setMapFullscreen(false)
        sheetAnimator?.cancel()
        sheetAnimator = null
        sheetExpanded = expanded
        sheetHandle.contentDescription = if (expanded) "Свернуть панель" else "Развернуть панель"
        sheetExtra.visibility = if (expanded) View.VISIBLE else View.GONE
        expandedSignalRow.visibility = if (expanded) View.VISIBLE else View.GONE
        signalDetails.visibility = if (expanded) View.VISIBLE else View.GONE
        compactSignalBars.visibility = if (expanded) View.GONE else View.VISIBLE
        if (expanded) details.visibility = View.GONE
        if (landscape) {
            // Landscape uses a right-hand rail, never a 73%-tall bottom overlay.
            val initial = sheetView.layoutParams as FrameLayout.LayoutParams
            val oldWidth = initial.width.coerceAtLeast(dp(90))
            val oldHeight = initial.height.coerceAtLeast(dp(70))
            applyResponsiveLayout(false)
            val dest = sheetView.layoutParams as FrameLayout.LayoutParams
            val targetWidth = dest.width
            val targetHeight = dest.height
            val animation = ValueAnimator.ofFloat(0f, 1f)
            sheetAnimator = animation
            animation.duration = 190L
            animation.addUpdateListener { value ->
                val fraction = value.animatedFraction
                sheetView.layoutParams = (sheetView.layoutParams as FrameLayout.LayoutParams).apply {
                    width = (oldWidth + (targetWidth - oldWidth) * fraction).toInt()
                    height = (oldHeight + (targetHeight - oldHeight) * fraction).toInt()
                }
            }
            animation.start()
        } else {
            val usable = rootView.height.takeIf { it > 0 } ?: resources.displayMetrics.heightPixels
            val maxHeight = (usable - dp(85)).coerceAtLeast(dp(170))
            val target = if (expanded) ((usable * 0.73f).toInt()).coerceIn(
                dp(170).coerceAtMost(maxHeight), maxHeight
            ) else dp(50)
            val old = sheetView.layoutParams.height
            applyResponsiveLayout(false) // update buttons, bounds and active theme first
            sheetView.layoutParams = (sheetView.layoutParams as FrameLayout.LayoutParams).apply {
                height = old
            }
            updateMapChrome(old)
            val animation = ValueAnimator.ofInt(old, target)
            sheetAnimator = animation
            animation.duration = 210L
            animation.addUpdateListener { value ->
                val height = value.animatedValue as Int
                sheetView.layoutParams =
                    (sheetView.layoutParams as FrameLayout.LayoutParams).apply {
                        this.height = height
                    }
                updateMapChrome(height)
            }
            animation.start()
        }
        floatingRail.visibility = if (expanded && !landscape) View.GONE else View.VISIBLE
        if (expanded && actionMenuExpanded) setActionMenuExpanded(false)
    }

    /**
     * One live layout pass for map attribution and the loading indicator.
     * Geometry comes from the CURRENT measured bottom sheet and tower card;
     * no sticky margins, so shrinking/closing moves both back DOWN.
     */
    private fun updateFloatingOverlayPositions(sheetHeightOverride: Int? = null) {
        if (!::rootView.isInitialized || !::sheetView.isInitialized ||
            !::mapAttribution.isInitialized || !::progressStrip.isInitialized ||
            !::details.isInitialized) return
        val footer = if (!landscape && sheetView.visibility == View.VISIBLE) {
            val params = sheetView.layoutParams as FrameLayout.LayoutParams
            params.bottomMargin + (sheetHeightOverride ?: sheetView.height.takeIf { it > 0 }
                ?: params.height.coerceAtLeast(0))
        } else 0
        val cardVisible = details.visibility == View.VISIBLE
        val card = if (cardVisible) {
            val lp = details.layoutParams as FrameLayout.LayoutParams
            lp.bottomMargin + (details.height.takeIf { it > 0 }
                ?: details.measuredHeight.coerceAtLeast(0))
        } else 0
        val creditsHeight = mapAttribution.height.takeIf { it > 0 }
            ?: mapAttribution.measuredHeight.takeIf { it > 0 } ?: dp(19)
        val target = FloatingOverlayPolicy.positions(
            systemBottomInset + dp(2), footer, card, creditsHeight, dp(7), dp(9))
        val attributionLp = mapAttribution.layoutParams as FrameLayout.LayoutParams
        if (attributionLp.bottomMargin != target.creditsBottom) {
            attributionLp.bottomMargin = target.creditsBottom
            mapAttribution.layoutParams = attributionLp
        }
        val progressLp = progressStrip.layoutParams as FrameLayout.LayoutParams
        val oldBottom = progressLp.bottomMargin
        if (oldBottom != target.progressBottom) {
            progressLp.bottomMargin = target.progressBottom
            progressStrip.layoutParams = progressLp
        }
        // When the card disappears, animate the downward return. On opening,
        // snap ABOVE the card before the first visible frame (never overlap).
        if (lastTowerCardVisible && !cardVisible && sheetAnimator == null &&
            !sheetExpanded && !isChoosingCrosshair && !mapFullscreen &&
            progressRequested && oldBottom > target.progressBottom) {
            progressStrip.animate().cancel()
            progressStrip.translationY = -(oldBottom - target.progressBottom).toFloat()
            progressStrip.animate().translationY(0f).setDuration(190L).start()
        }
        lastTowerCardVisible = cardVisible

        // The status header is always above any card. In an unusually small
        // viewport, don't draw a progress strip on top of controls.
        val progressHeight = progressStrip.height.takeIf { it > 0 }
            ?: progressStrip.measuredHeight.takeIf { it > 0 } ?: dp(44)
        val headerBottom = maxOf(
            topStatusPanel.bottom.takeIf { it > 0 }
                ?: (systemTopInset + topPanelHeight),
            topHeader.bottom.takeIf { it > 0 }
                ?: (systemTopInset + topPanelHeight + dp(52))
        ) + dp(5)
        val tooCrowded = rootView.height > 0 &&
            rootView.height - target.progressBottom - progressHeight < headerBottom
        val wanted = when {
            !progressRequested -> View.GONE
            tooCrowded -> View.INVISIBLE
            else -> View.VISIBLE
        }
        if (progressStrip.visibility != wanted) progressStrip.visibility = wanted
    }

    private fun updateMapChrome(sheetHeight: Int) {
        if (landscape) {
            if (::details.isInitialized) {
                val lp = details.layoutParams as FrameLayout.LayoutParams
                val next = systemBottomInset + dp(8)
                if (lp.bottomMargin != next) {
                    lp.bottomMargin = next
                    details.layoutParams = lp
                }
            }
            updateFloatingOverlayPositions()
            return
        }
        val footerMargin = sheetHeight + dp(7) + systemBottomInset
        directionPanel?.let { panel ->
            val lp = panel.layoutParams as FrameLayout.LayoutParams
            if (lp.bottomMargin != footerMargin + dp(5)) {
                lp.bottomMargin = footerMargin + dp(5)
                panel.layoutParams = lp
            }
        }
        if (::details.isInitialized) {
            val lp = details.layoutParams as FrameLayout.LayoutParams
            if (lp.bottomMargin != footerMargin + dp(8)) {
                lp.bottomMargin = footerMargin + dp(8)
                details.layoutParams = lp
            }
        }
        updateFloatingOverlayPositions(sheetHeight)
    }

    private fun updateOperatorChoices() {
        val names = listOf("Все операторы") +
            (sites + userTowerStore.all()).map { it.operator ?: unknown }
                .distinct().sortedWith(String.CASE_INSENSITIVE_ORDER)
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
        // Start signal independently whenever phone permission is already granted.
        if (cellMonitor.hasSignalPermission()) {
            cellMonitor.start(getPreferences(Context.MODE_PRIVATE).getInt("sim_id", -1))
        }
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
            status.text = "Для GPS и идентификаторов сот нужно точное местоположение. " +
                "Мониторинг сигнала работает отдельно."
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
        val chosenOrigin = selectedOriginPoint
        val needsReselection = manualPoint == null && selectedAimSite != null && (
            chosenOrigin == null ||
                PositionChangePolicy.gpsMovedSinceSelection(
                    TowerData.distanceKm(chosenOrigin.latitude, chosenOrigin.longitude,
                        fix.latitude, fix.longitude) * 1000.0,
                    selectedOriginAccuracy,
                    fix.accuracy.takeIf { fix.hasAccuracy() })
            )
        location = fix
        if (needsReselection) onActivePositionChanged()
        if (gpsFocusPending && !manualCenter) {
            gpsFocusPending = false
            val point = LatLng(fix.latitude, fix.longitude)
            map?.animateCamera(CameraUpdateFactory.newLatLngZoom(point, 15.5), 500)
            yandexWebMap?.move(point.latitude, point.longitude, 15.5)
        }
        gpsLabel.text = if (fix.hasAccuracy())
            "GPS ±" + fix.accuracy.toInt() + " м"
        else "GPS: точность —"
        gpsLabel.contentDescription = if (fix.hasAccuracy())
            "Геопозиция " + fix.provider + ", точность ±" +
                fix.accuracy.toInt() + " метров"
        else "GPS / Wi-Fi: точность не сообщена, источник " + fix.provider
        // Both day and night map styles use a dark header with light text.
        gpsLabel.setTextColor(if (fix.hasAccuracy() && fix.accuracy > 70f)
            Color.rgb(255, 181, 117) else Color.rgb(204, 217, 194))
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
        yandexWebMap?.move(position.latitude, position.longitude, 12.5)
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

    private fun progressStep(label: String, processed: Int? = null, total: Int? = null) {
        runOnUiThread {
            if (!::progressStrip.isInitialized || isDestroyed) return@runOnUiThread
            ++progressSequence
            val measurable = total != null && total > 0 && processed != null
            if (measurable) {
                val part = processed!!
                val whole = total!!
                val percent = (part * 100 / whole).coerceIn(0, 99)
                progressLabel.text = label + " · " + part + "/" + whole + " · " + percent + "%"
                progressBar.isIndeterminate = false
                progressBar.progress = percent
            } else {
                progressLabel.text = label
                progressBar.isIndeterminate = true
            }
            progressRequested = true
            progressStrip.visibility = View.VISIBLE
            updateFloatingOverlayPositions()
        }
    }

    private fun enableDatabaseNotifications() {
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 108)
        } else Toast.makeText(this, "Уведомления уже разрешены", Toast.LENGTH_SHORT).show()
    }

    private fun notifyDatabaseResult(message: String, success: Boolean) {
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED) return
        try {
            val manager = getSystemService(Context.NOTIFICATION_SERVICE)
                as android.app.NotificationManager
            val channel = "towerradar_database_updates"
            if (Build.VERSION.SDK_INT >= 26) {
                manager.createNotificationChannel(android.app.NotificationChannel(
                    channel, "Обновление базы вышек",
                    android.app.NotificationManager.IMPORTANCE_DEFAULT))
            }
            val notification = android.app.Notification.Builder(this, channel)
                .setSmallIcon(android.R.drawable.stat_notify_sync)
                .setContentTitle(if (success) "База TowerRadar проверена"
                    else "Обновление базы не выполнено")
                .setContentText(message.take(150))
                .setAutoCancel(true)
                .build()
            manager.notify(2601, notification)
        } catch (_: Exception) { /* In-app status always remains available. */ }
    }

    private fun progressEnd(message: String, success: Boolean) {
        runOnUiThread {
            if (!::progressStrip.isInitialized || isDestroyed) return@runOnUiThread
            val ticket = ++progressSequence
            progressLabel.text = message
            notifyDatabaseResult(message, success)
            progressBar.isIndeterminate = false
            progressBar.progress = if (success) 100 else 0
            progressRequested = true
            progressStrip.visibility = View.VISIBLE
            updateFloatingOverlayPositions()
            progressHandler.postDelayed({
                if (progressSequence == ticket && ::progressStrip.isInitialized) {
                    progressRequested = false
                    progressStrip.visibility = View.GONE
                }
            }, if (success) 3500L else 7000L)
        }
    }

    private fun load(force: Boolean) {
        val point = center ?: map?.cameraPosition?.target
        if (point == null) {
            status.text = "Сначала выберите точку поиска на карте."
            return
        }
        if (!force && autoUpdateAttempted) return
        if (!force) autoUpdateAttempted = true // One startup attempt; GPS jitter cannot retrigger.
        if (busy) {
            if (force) pendingManualSearch = true
            return
        }
        val previous = lastFetch
        if (!force && previous != null && radiusKm == lastFetchRadius &&
            TowerData.distanceKm(point.latitude, point.longitude,
                previous.latitude, previous.longitude) <
                (radiusKm / 2.0).coerceAtLeast(0.5)) return

        val lat = point.latitude
        val lon = point.longitude
        val range = radiusKm
        busy = true
        refresh.isEnabled = false
        searchHere.isEnabled = false
        details.visibility = View.GONE
        status.text = "Обновляем объекты связи · " + range + " км…"
        lastFetchTime = System.currentTimeMillis()
        progressStep("Проверяем сохранённые данные")
        executor.execute {
            try {
                if (!force && towerDatabase.recentlyChecked(lat, lon, range, 3L * 3600000L)) {
                    val saved = towerDatabase.all()
                    runOnUiThread {
                        if (!isDestroyed && !isFinishing) {
                            sites = saved
                            lastFetch = LatLng(lat, lon)
                            lastFetchRadius = range
                            updateOperatorChoices()
                            showPins()
                            status.text = "Данные актуальны · " + saved.size +
                                " объектов · последняя проверка менее 3 часов назад"
                        }
                    }
                    progressEnd("Сохранённая база актуальна", true)
                } else {
                    progressStep("OpenStreetMap · ожидаем ответ сервера")
                    // Refresh OSM on a due search. Legacy single-area cache is only fallback.
                    val osm = fetch(lat, lon, range)
                    progressStep("OpenCellID · ожидаем ответ сервера")
                    val key = getPreferences(Context.MODE_PRIVATE)
                        .getString("opencellid_key", "").orEmpty()
                    var cellError = false
                    val cells = if (key.isBlank()) emptyList() else try {
                        CellSources.fetch(key, lat, lon)
                    } catch (_: Exception) {
                        cellError = true
                        emptyList()
                    }
                    val found = (osm + cells).distinctBy { it.id }
                    progressStep("Сравнение и запись в базу", 0, found.size)
                    val stats = towerDatabase.merge(found) { done, total ->
                        progressStep("Сравнение и запись в базу", done, total)
                    }
                    // A successful transaction is required before the UI can report 100%.
                    towerDatabase.markChecked(lat, lon, range)
                    val all = towerDatabase.all()
                    runOnUiThread {
                        if (!isDestroyed && !isFinishing) {
                            sites = all
                            lastFetch = LatLng(lat, lon)
                            lastFetchRadius = range
                            updateOperatorChoices()
                            showPins()
                            servingCell?.let(::updateServingCell)
                            status.text = "Сохранено " + all.size + " · новых " +
                                stats.added + " · уточнено " + stats.changed +
                                if (cellError) " · OpenCellID недоступен" else ""
                        }
                    }
                    progressEnd("Завершено · +" + stats.added +
                        " · исправлено " + stats.changed, true)
                }
            } catch (e: Exception) {
                val saved = try { towerDatabase.all() } catch (_: Exception) { emptyList() }
                runOnUiThread {
                    if (!isDestroyed && !isFinishing) {
                        sites = saved
                        updateOperatorChoices()
                        showPins()
                        status.text = "Обновление не выполнено: " +
                            (e.localizedMessage ?: "сервис недоступен").take(90) +
                            " · сохранено вышек " + saved.size
                    }
                }
                progressEnd("Ошибка обновления: " +
                    (e.localizedMessage ?: "нет ответа сервера").take(90) +
                    " · база сохранена", false)
            } finally {
                runOnUiThread {
                    busy = false
                    if (::refresh.isInitialized) refresh.isEnabled = true
                    if (::searchHere.isInitialized) searchHere.isEnabled = true
                    if (pendingManualSearch && !isDestroyed && !isFinishing) {
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
            refreshSignalDisplay()
            cellStatus.text = if (cellMonitor.canReadCellDetails())
                "Нет зарегистрированной соты или модем ограничил доступ."
            else if (!cellMonitor.hasSignalPermission()) "Разрешите доступ к телефону."
            else "Cell ID скрыт: включите системную геолокацию для обнаружения сот."
            if (previousId != null) {
                servingSite = null
                updateOverlays()
                syncYandexObjects()
            }
            return
        }
        if (reading.identity != previousId) {
            servingSite = null
            updateOverlays()
            syncYandexObjects()
        }
        refreshSignalDisplay()
        if (!reading.validId) {
            cellStatus.text = "Сота зарегистрирована, но её идентификатор не раскрыт устройством."
            return
        }
        val matching = sites.firstOrNull {
            it.id == "opencellid/" + reading.identity ||
                it.id == "mylnikov/" + reading.identity ||
                it.id == "beacondb/" + reading.identity ||
                it.id == "mccmnc/" + reading.identity
        }
        if (matching != null) {
            servingSite = matching
            cellStatus.text = "Сота " + reading.cellId + " · " + matching.source
            updateOverlays()
            syncYandexObjects()
            return
        }
        if (servingSite != null) {
            cellStatus.text = "Сота " + reading.cellId + " · " + servingSite?.source
            return
        }
        val key = getPreferences(Context.MODE_PRIVATE)
            .getString("opencellid_key", "").orEmpty()
        // Public databases do not require an OpenCellID key.
        val now = SystemClock.elapsedRealtime()
        if (requestedCellId == reading.identity && now - cellRequestAt < 600000L) {
            cellStatus.text = "Сота " + reading.cellId +
                " · местоположение неизвестно (совпадение не найдено)"
            return
        }
        requestedCellId = reading.identity
        cellRequestAt = now
        cellStatus.text = "Сота " + reading.cellId +
            " · поиск в базах радиосот"
        cellExecutor.execute {
            val site = AdditionalCellSources.lookup(key, reading, this@MainActivity)
            if (site != null) {
                try { towerDatabase.merge(listOf(site)) } catch (_: Exception) {}
            }
            runOnUiThread {
                if (isDestroyed || isFinishing || servingCell?.identity != reading.identity)
                    return@runOnUiThread
                servingSite = site
                if (site != null) {
                    sites = (sites + site).distinctBy { it.id }
                    updateOperatorChoices()
                    showPins()
                }
                cellStatus.text = if (site == null)
                    "Сота " + reading.cellId + " · координаты неизвестны во всех источниках"
                else "Сота " + reading.cellId + " · оценка " + site.source
                updateOverlays()
                syncYandexObjects()
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
        for (barGroup in listOf(signalBars, compactSignalBars)) {
            for (i in 0 until barGroup.childCount) {
                barGroup.getChildAt(i).background =
                    rounded(if (i < count) color else palette.signalMuted, 3)
            }
        }
    }

    private fun emptyFeatures(): FeatureCollection = FeatureCollection.fromFeatures(emptyArray<Feature>())

    private fun showPins() {
        visible = (sites + userTowerStore.all()).distinctBy { it.id }.filter {
            (selectedOperator == null || (selectedOperator == unknown && it.operator == null) ||
                it.operator == selectedOperator) &&
            (technologyFilter == null || when (technologyFilter) {
                "2G" -> it.technology?.contains("GSM", true) == true
                "3G" -> it.technology?.contains("UMTS", true) == true ||
                    it.technology?.contains("WCDMA", true) == true
                "4G" -> it.technology?.contains("LTE", true) == true
                "5G" -> it.technology?.contains("NR", true) == true ||
                    it.technology?.contains("5G", true) == true
                else -> true
            }) &&
            (sourceFilter == null || it.source.contains(sourceFilter!!, true))
        }
        refreshCameraStatus()
        syncYandexObjects()
        if (!styled) return
        val features = visible.map { site ->
            Feature.fromGeometry(Point.fromLngLat(site.longitude, site.latitude)).apply {
                addBooleanProperty("mobile", site.mobile)
                addBooleanProperty("estimated", !site.source.startsWith("OpenStreetMap") &&
                    !site.id.startsWith("user/"))
                addBooleanProperty("manual", site.id.startsWith("user/"))
            }
        }
        (map?.style?.getSource("towers") as? GeoJsonSource)
            ?.setGeoJson(FeatureCollection.fromFeatures(features.toTypedArray()))
    }

    /**
     * A single low-frequency visual pulse at the serving cell estimate.
     * No pulse on arbitrary nearby OSM towers or when the cell is unknown.
     * Stops on pause, no signal-bar animation and no background work.
     */
    private fun updateServingPulse() {
        if (!aimForeground || servingSite == null || !styled ||
            !ValueAnimator.areAnimatorsEnabled() ||
            map?.style?.getLayer("serving-cell-pulse") !is CircleLayer) {
            stopServingPulse()
            return
        }
        if (servingPulseAnimator?.isRunning == true) return
        servingPulseFrameAt = 0L
        servingPulseAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 2200L
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener { tick ->
                if (!aimForeground || !styled || servingSite == null) return@addUpdateListener
                val now = SystemClock.uptimeMillis()
                if (now - servingPulseFrameAt < 60L) return@addUpdateListener
                servingPulseFrameAt = now
                val t = tick.animatedValue as Float
                (map?.style?.getLayer("serving-cell-pulse") as? CircleLayer)
                    ?.setProperties(
                        PropertyFactory.circleRadius(11f + t * 16f),
                        PropertyFactory.circleStrokeOpacity((1f - t) * 0.34f),
                        PropertyFactory.circleStrokeWidth(2.0f - t * .5f)
                    )
            }
            start()
        }
    }

    private fun stopServingPulse() {
        servingPulseAnimator?.cancel()
        servingPulseAnimator = null
        (map?.style?.getLayer("serving-cell-pulse") as? CircleLayer)
            ?.setProperties(PropertyFactory.circleStrokeOpacity(0f))
    }

    private fun updateDot() {
        syncYandexObjects()
        if (!styled) return
        val point = activeUserPoint()
        val features = if (point == null) emptyFeatures() else FeatureCollection.fromFeatures(
            arrayOf(Feature.fromGeometry(Point.fromLngLat(point.longitude, point.latitude)))
        )
        (map?.style?.getSource("my-location") as? GeoJsonSource)?.setGeoJson(features)
        updateSelectedAimLine()
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
        updateCoverageOverlay()
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
        if (visibleDirection && point != null) {
            // Convert *compact on-screen pixel length* into metres. Zooming never
            // creates a massive triangle across most of the screen.
            val scaleMetersPerPixel = 156543.03392 *
                cos(Math.toRadians(point.latitude)).coerceAtLeast(0.01) /
                2.0.pow(map?.cameraPosition?.zoom ?: 13.0)
            val shortestSide = kotlin.math.min(mapView.width, mapView.height)
                .coerceAtLeast(dp(240)).toDouble()
            // Long dashed bearing line, independently shortened translucent fan.
            val rayPx = kotlin.math.min(dp(380).toDouble(), shortestSide * .43)
            val rayLength = (scaleMetersPerPixel * rayPx).coerceIn(20.0, 250000.0)
            val sectorLength = rayLength * sectorLengthPercent / 100.0
            val origin = Point.fromLngLat(point.longitude, point.latitude)
            val angles = (-23..23 step 2).map { headingDegrees.toDouble() + it }
            val arc = angles.map {
                geoPoint(point.latitude, point.longitude, sectorLength, it)
            }
            val fan = listOf(origin) + arc + origin
            val nearArc = angles.map {
                geoPoint(point.latitude, point.longitude, sectorLength * 0.48, it)
            }
            val nearFan = listOf(origin) + nearArc + origin
            val ray = listOf(origin, geoPoint(point.latitude, point.longitude,
                rayLength, headingDegrees.toDouble()))
            (map?.style?.getSource("heading-cone") as? GeoJsonSource)?.setGeoJson(
                FeatureCollection.fromFeatures(arrayOf(
                    Feature.fromGeometry(Polygon.fromLngLats(listOf(fan)))
                )))
            (map?.style?.getSource("heading-near") as? GeoJsonSource)?.setGeoJson(
                FeatureCollection.fromFeatures(arrayOf(
                    Feature.fromGeometry(Polygon.fromLngLats(listOf(nearFan)))
                )))
            (map?.style?.getSource("heading-border") as? GeoJsonSource)?.setGeoJson(
                FeatureCollection.fromFeatures(arrayOf(
                    Feature.fromGeometry(LineString.fromLngLats(arc))
                )))
            (map?.style?.getSource("heading-ray") as? GeoJsonSource)?.setGeoJson(
                FeatureCollection.fromFeatures(arrayOf(
                    Feature.fromGeometry(LineString.fromLngLats(ray))
                )))
        } else {
            for (source in listOf("heading-cone", "heading-near",
                "heading-border", "heading-ray")) {
                (map?.style?.getSource(source) as? GeoJsonSource)?.setGeoJson(emptyFeatures())
            }
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
        updateServingPulse()
    }

    /**
     * Aimed rays are only anchored at a recent real GPS/network fix.
     * A manually chosen search centre must never be mistaken for the user's position.
     */
    private fun aimOrigin(): Location? {
        if (manualPoint != null || !hasLocation()) return null
        val fix = location ?: return null
        val age = if (fix.elapsedRealtimeNanos > 0L)
            (SystemClock.elapsedRealtimeNanos() - fix.elapsedRealtimeNanos) / 1000000L
        else System.currentTimeMillis() - fix.time
        return fix.takeIf { age in 0L..120000L &&
            it.latitude in -90.0..90.0 && it.longitude in -180.0..180.0 }
    }

    /** A single static GeoJSON segment; don't re-create it on every compass sample. */
    private fun updateSelectedAimLine() {
        if (!styled) return
        val target = selectedAimSite
        val origin = aimOrigin()
        val shape = if (origin == null || target == null ||
            TowerAimModel.bearingDegrees(origin.latitude, origin.longitude,
                target.latitude, target.longitude) == null) emptyFeatures()
        else FeatureCollection.fromFeatures(arrayOf(
            Feature.fromGeometry(LineString.fromLngLats(listOf(
                Point.fromLngLat(origin.longitude, origin.latitude),
                Point.fromLngLat(target.longitude, target.latitude)
            )))
        ))
        (map?.style?.getSource("selected-aim") as? GeoJsonSource)?.setGeoJson(shape)
        updateAimAlignment(vibrateOnEnter = false)
        // Also restore normal beam color when a site is deselected or switched.
        updateAimColors()
    }

    private fun clearAimSelection() {
        selectedOriginPoint = null
        selectedOriginAccuracy = null
        if (selectedAimSite == null) return
        selectedAimSite = null
        coverageTowerId = null
        coverageRadiusMetres = 0
        updateCoverageOverlay()
        syncYandexObjects()
        aimAligned = false
        aimAlignmentInitialized = false
        updateSelectedAimLine()
    }

    /**
     * The red lock requires actual device heading and geographic bearing.
     * Enter within 5 degrees, exit beyond 9 to avoid repeated vibrations at the edge.
     */
    private fun updateAimAlignment(vibrateOnEnter: Boolean) {
        val target = selectedAimSite
        val origin = aimOrigin()
        val bearing = if (target != null && origin != null && hasHeadingReading && showCone)
            TowerAimModel.bearingDegrees(origin.latitude, origin.longitude,
                target.latitude, target.longitude)
        else null
        val next = bearing != null &&
            TowerAimModel.aligned(aimAligned, headingDegrees.toDouble(), bearing)
        val firstSample = !aimAlignmentInitialized
        aimAlignmentInitialized = true
        val entered = next && !aimAligned
        if (next != aimAligned) {
            aimAligned = next
            updateAimColors()
        }
        if (entered && !firstSample && vibrateOnEnter && aimForeground) {
            val now = SystemClock.elapsedRealtime()
            if (now - lastAimPulseAt >= 2000L) {
                lastAimPulseAt = now
                vibrateForAim()
            }
        }
    }

    private fun updateAimColors() {
        val redLock = aimAligned && selectedAimSite != null
        (map?.style?.getLayer("selected-aim-guide") as? LineLayer)?.setProperties(
            PropertyFactory.lineColor(if (redLock) Color.rgb(210, 49, 52)
                else Color.rgb(184, 119, 48)),
            PropertyFactory.lineOpacity(if (redLock) 0.95f else 0.74f),
            PropertyFactory.lineWidth(if (redLock) 3.0f else 2.5f)
        )
        (map?.style?.getLayer("heading-center-color") as? LineLayer)?.setProperties(
            PropertyFactory.lineColor(if (redLock) Color.rgb(210, 49, 52)
                else Color.rgb(0, 195, 220))
        )
    }

    private fun vibrateForAim() {
        try {
            val vibrator = if (Build.VERSION.SDK_INT >= 31)
                (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager)
                    .defaultVibrator
            else {
                @Suppress("DEPRECATION")
                (getSystemService(Context.VIBRATOR_SERVICE) as Vibrator)
            }
            if (vibrator.hasVibrator()) {
                vibrator.vibrate(VibrationEffect.createOneShot(70L,
                    VibrationEffect.DEFAULT_AMPLITUDE))
            }
        } catch (_: SecurityException) {
            // Vibration may be disabled by device/user policy.
        }
    }

    /** The sole distance origin. Panning the map never changes this coordinate. */
    private fun activeUserPoint(): LatLng? =
        manualPoint ?: location?.let { LatLng(it.latitude, it.longitude) }


    /** Elevation graph remains fully native, inheriting both Khaki Modern themes. */

    /** Translucent rings are user-selected scenarios, not real base-station coverage. */
    private fun chooseCoverage(site: TowerSite) {
        val radii = CoverageEstimate.radiusChoicesMetres
        val options = arrayOf("Не показывать") + radii.map {
            if (it < 1000) it.toString() + " м" else
                String.format(Locale.US, "%.1f км", it / 1000.0)
        }
        val chosen = if (coverageTowerId == site.id) coverageRadiusMetres else 0
        KhakiDialogBuilder(this, palette).setTitle("Предполагаемая зона покрытия")
            .setMessage("Выберите условный радиус от вышки. Цветной круг НЕ является " +
                "расчётом радиосигнала: неизвестны мощность, частота, " +
                "направление антенн, рельеф и помехи.")
            .setSingleChoiceItems(options,
                if (chosen == 0) 0 else radii.indexOf(chosen) + 1) { _, index ->
                coverageTowerId = if (index == 0) null else site.id
                coverageRadiusMetres = if (index == 0) 0 else radii[index - 1]
                updateCoverageOverlay()
                syncYandexObjects()
                Toast.makeText(this,
                    if (index == 0) "Схематическая зона скрыта"
                    else "Показан условный радиус · не прогноз сигнала",
                    Toast.LENGTH_SHORT).show()
            }.setNegativeButton("Отмена", null).show()
    }

    private fun updateCoverageOverlay() {
        val source = map?.style?.getSource("coverage-preview") as? GeoJsonSource ?: return
        val site = selectedAimSite?.takeIf {
            coverageTowerId == it.id && coverageRadiusMetres > 0
        }
        val shape = if (site == null) emptyFeatures() else {
            val points = CoverageEstimate.ring(site.latitude, site.longitude,
                coverageRadiusMetres).map { (lat, lon) -> Point.fromLngLat(lon, lat) }
            FeatureCollection.fromFeatures(arrayOf(
                Feature.fromGeometry(Polygon.fromLngLats(listOf(points)))
            ))
        }
        source.setGeoJson(shape)
    }

    private fun showElevationProfile(site: TowerSite) {
        // Manual position is an independent valid DEM origin. GPS is required
        // only in automatic mode; compass aiming still needs real sensor GPS.
        val origin = TerrainOriginPolicy.select(
            manualPoint?.let { it.latitude to it.longitude },
            aimOrigin()?.let { it.latitude to it.longitude }
        )
        if (origin == null) {
            KhakiDialogBuilder(this, palette).setTitle("Профиль высот")
                .setMessage("Укажите геопозицию вручную или включите GPS и " +
                    "дождитесь актуальных координат, затем повторно выберите вышку.")
                .setPositiveButton("Понятно", null).show()
            return
        }
        val manual = origin.manual
        val originLabel = if (manual) "Ручная точка" else "GPS"
        val revision = terrainRevision
        val startLat = origin.latitude
        val startLon = origin.longitude
        val graph = TerrainProfileView(this, palette).apply {
            background = rounded(palette.elevated, 15)
            originLabelText = if (manual) "ТОЧКА" else "ВЫ"
            contentDescription = "График высот: " + originLabel +
                " до выбранной вышки"
        }
        val header = text(originLabel + " → вышка · получаем высоты…",
            13f, palette.text)
        val detailsLine = text("Проведите пальцем по графику для просмотра высот",
            12f, palette.secondary)
        graph.onInspect = { detailsLine.text = it }
        val caveat = text("Высоты относятся к рельефу, а не к самой антенне. " +
            "Влияние зданий, деревьев и зоны Френеля не рассчитывается.",
            11f, palette.secondary)
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(3), dp(3), dp(3), dp(3))
        }
        content.addView(header, LinearLayout.LayoutParams(-1, -2).apply {
            bottomMargin = dp(8)
        })
        content.addView(graph, LinearLayout.LayoutParams(-1, dp(232)))
        content.addView(detailsLine, LinearLayout.LayoutParams(-1, -2).apply {
            topMargin = dp(8)
        })
        content.addView(caveat, LinearLayout.LayoutParams(-1, -2).apply {
            topMargin = dp(8)
        })
        val dialog = KhakiDialogBuilder(this, palette)
            .setTitle("Профиль высот · TowerRadar")
            .setView(content)
            .setPositiveButton("Готово", null).show()
        terrainDialog = dialog
        dialog.setOnDismissListener {
            if (terrainDialog === dialog) terrainDialog = null
        }
        val key = getPreferences(Context.MODE_PRIVATE).getString(
            "map_key_maptiler", "").orEmpty()
        apiExecutor.execute {
            val outcome = try {
                TerrainProfileClient.load(applicationContext, startLat, startLon,
                    site.latitude, site.longitude, key) to null
            } catch (e: Exception) {
                null to (e.message ?: "Высоты временно недоступны")
            }
            runOnUiThread {
                if (revision != terrainRevision || !dialog.isShowing ||
                    selectedAimSite?.id != site.id ||
                    isDestroyed || isFinishing) return@runOnUiThread
                val terrain = outcome.first
                if (terrain == null) {
                    header.text = outcome.second
                    detailsLine.text = "Нет достоверных измерений — график не построен"
                    graph.visibility = View.GONE
                    return@runOnUiThread
                }
                graph.profile = terrain
                val first = terrain.samples.firstOrNull()?.elevation
                val last = terrain.samples.lastOrNull()?.elevation
                val a = first?.let { kotlin.math.round(it).toInt().toString() + " м" } ?: "н/д"
                val b = last?.let { kotlin.math.round(it).toInt().toString() + " м" } ?: "н/д"
                val diff = if (first == null || last == null) "н/д"
                    else String.format(Locale.US, "%+.0f м", last - first)
                header.text = originLabel + ": " + a + "   ·   Вышка: " + b +
                    "\nПерепад: " + diff
                detailsLine.text = "Коснитесь линии рельефа для просмотра высоты"
                val chosenHeight = TowerHeightPolicy.resolve(site.height,
                    getPreferences(Context.MODE_PRIVATE).getInt("height_scenario_m", 30)
                        .coerceIn(5, 100))
                graph.towerHeightMeters = chosenHeight.metres
                caveat.text = if (chosenHeight.measured) {
                    "Пунктир до вершины конструкции (" +
                        chosenHeight.metres.toInt() + " м по данным источника). " +
                        "Это НЕ подтверждённая высота антенны. Не учтены здания, " +
                        "деревья и зона Френеля.\n" + terrain.source
                } else {
                    "Пунктир — ИЛЛЮСТРАТИВНЫЙ сценарий высоты " +
                        chosenHeight.metres.toInt() + " м, а не реальная вышка. " +
                        "Сценарий можно изменить в настройках · Вышки и рельеф. " +
                        "Для макросоты ITU-R использует 30 м как типовой параметр модели. " +
                        "Не учтены здания, деревья и зона Френеля.\n" + terrain.source
                }
            }
        }
    }

    /** Same exact coordinates are available while waiting and after the DEM response. */
    private fun pointAltitudeDialog(point: LatLng, message: String): android.app.Dialog {
        val coordinates = CoordinateFormatter.format(point.latitude, point.longitude)
            ?: return KhakiDialogBuilder(this, palette).setTitle("Высота точки")
                .setMessage(message).setPositiveButton("Закрыть", null).show()
        val divider = "\nИсточник: "
        val sourceAt = message.indexOf(divider)
        val summary = if (sourceAt >= 0) message.substring(0, sourceAt) else message
        val source = if (sourceAt >= 0) message.substring(sourceAt + 1) else null
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(4), dp(5), dp(4), dp(2))
        }
        content.addView(text(summary, 14f, palette.secondary),
            LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10) })
        val coordinateRow = row().apply {
            setPadding(dp(10), dp(7), dp(10), dp(7))
            background = rounded(palette.elevated, 12)
            isClickable = true
            isFocusable = true
            contentDescription = "Координаты точки $coordinates. Скопировать"
            setOnClickListener { copyCoordinates(coordinates) }
        }
        coordinateRow.addView(text("⌖  $coordinates", 13f, palette.text),
            LinearLayout.LayoutParams(0, -2, 1f))
        coordinateRow.addView(text("⧉", 18f, palette.olive),
            LinearLayout.LayoutParams(-2, -2).apply { marginStart = dp(8) })
        content.addView(coordinateRow, LinearLayout.LayoutParams(-1, -2))
        source?.let {
            content.addView(text(it, 12f, palette.secondary),
                LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) })
        }
        return KhakiDialogBuilder(this, palette).setTitle("Высота точки")
            .setView(content).setPositiveButton("Закрыть", null).show()
    }

    /** Menu action queries altitude at the fixed map-center reticle. */
    private fun showElevationAtPoint(point: LatLng) {
        val loading = pointAltitudeDialog(point, "Запрашиваем высоту рельефа над уровнем моря…")
        val key = getPreferences(Context.MODE_PRIVATE)
            .getString("map_key_maptiler", "").orEmpty()
        apiExecutor.execute {
            val outcome = try {
                val p = TerrainProfileClient.load(applicationContext,
                    point.latitude, point.longitude, point.latitude, point.longitude, key)
                p.samples.firstNotNullOfOrNull { it.elevation }?.let {
                    String.format(Locale.US, "Высота местности: %.0f м", it) +
                        "\nИсточник: " + p.source
                } ?: "Для этой точки нет сведений о высоте"
            } catch (e: Exception) {
                e.message ?: "Сервис высот недоступен"
            }
            runOnUiThread {
                if (loading.isShowing && !isDestroyed && !isFinishing) {
                    loading.dismiss()
                    pointAltitudeDialog(point, outcome)
                }
            }
        }
    }

    private fun selectTower(tapped: LatLng) {
        val currentMap = map ?: return
        val web = yandexWebMap?.showing == true
        val touch = currentMap.projection.toScreenLocation(tapped)
        val nearest = visible.minByOrNull {
            if (web) TowerData.distanceKm(
                tapped.latitude, tapped.longitude, it.latitude, it.longitude)
            else {
                val p = currentMap.projection.toScreenLocation(LatLng(it.latitude, it.longitude))
                hypot((p.x - touch.x).toDouble(), (p.y - touch.y).toDouble())
            }
        } ?: run {
            details.visibility = View.GONE
            clearAimSelection()
            return
        }
        val p = currentMap.projection.toScreenLocation(LatLng(nearest.latitude, nearest.longitude))
        val far = if (web) TowerData.distanceKm(tapped.latitude, tapped.longitude,
            nearest.latitude, nearest.longitude) > .07
        else hypot((p.x - touch.x).toDouble(), (p.y - touch.y).toDouble()) > dp(30)
        if (far) {
            details.visibility = View.GONE
            clearAimSelection()
            return
        }
        selectedAimSite = nearest
        selectedOriginPoint = manualPoint ?: aimOrigin()?.let {
            LatLng(it.latitude, it.longitude)
        }
        selectedOriginAccuracy = if (manualPoint == null)
            location?.takeIf { it.hasAccuracy() }?.accuracy else null
        syncYandexObjects()
        aimAligned = false
        aimAlignmentInitialized = false
        updateSelectedAimLine()
        detailTitle.text = nearest.name ?: nearest.operator ?: "Объект связи"
        val from = activeUserPoint()
        val distance = from?.let {
            String.format(Locale.US, "%.2f км", TowerData.distanceKm(
                it.latitude, it.longitude, nearest.latitude, nearest.longitude))
        } ?: "неизвестно"
        val gpsForAim = aimOrigin()
        val aimInfo = when {
            manualPoint != null -> "Наведение: отключите ручную точку, используйте GPS"
            gpsForAim == null -> "Наведение: включите геолокацию и дождитесь GPS / Wi-Fi"
            else -> "Наведение: поверните телефон к вышке (совпадение ±5°)"
        }
        val evidence = OperatorEvidence.inspect(nearest)
        detailBody.text = listOfNotNull(
            "Оператор: " + (if (nearest.operator != null) nearest.operator
                else evidence.operator?.let { "предположительно " + it }
                    ?: "не определён"),
            evidence.explanation,
            aimInfo,
            gpsForAim?.takeIf { it.hasAccuracy() && it.accuracy > 70f }?.let {
                "Ориентирование приблизительное: точность GPS ±" + it.accuracy.toInt() + " м"
            },
            when {
                nearest.id.startsWith("user/") ->
                    "Локальная запись пользователя · координаты не проверены"
                !nearest.source.startsWith("OpenStreetMap") ->
                    "Примерное положение радиосоты · НЕ координата физической мачты"
                nearest.mobile -> "Объект связи из OpenStreetMap (OSM)"
                else -> "Назначение вышки не подтверждено"
            },
            nearest.technology?.let { "Технологии: " + it },
            nearest.height?.let { "Высота: " + it },
            nearest.samples?.let { "Наблюдений в базе: " + it },
            nearest.estimatedRangeMeters?.let { "Оценочный радиус: " + it + " м" },
            nearest.averageSignalDbm?.let { "Средний сигнал наблюдений: " + it + " dBm" },
            nearest.extraInfo?.let { "Дополнительные сведения: " + it },
            "Расстояние: " + distance +
                (if (manualPoint != null) " · от ручной точки" else " · от GPS"),
            "Источник / достоверность: " + nearest.source,
            towerDatabase.lastChanged(nearest.id)?.let {
                "Изменена запись: " + java.text.DateFormat.getDateTimeInstance()
                    .format(java.util.Date(it))
            },
            "Источник: " + nearest.url
        ).joinToString("\n")
        detailCoordinates.text = "⧉  " + (CoordinateFormatter.format(
            nearest.latitude, nearest.longitude) ?: "координаты недоступны")
        detailCoordinates.contentDescription = "Координаты вышки. Нажмите, чтобы скопировать"
        profileButton.visibility = View.VISIBLE
        coverageButton.visibility = View.VISIBLE
        manualTowerActions.visibility = if (nearest.id.startsWith("user/"))
            View.VISIBLE else View.GONE
        photoButton.visibility = if (userTowerStore.photoFor(nearest.id) != null)
            View.VISIBLE else View.GONE
        details.visibility = View.VISIBLE
        updateCoverageOverlay()
        syncYandexObjects()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 108) {
            Toast.makeText(this, if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED)
                "Уведомления базы разрешены" else "Уведомления не разрешены",
                Toast.LENGTH_SHORT).show()
            return
        }
        if (requestCode == 101) {
            if (hasLocation()) startLocationUpdates()
            else status.text = "Геолокация запрещена. Можно искать в центре карты."
            if (cellMonitor.hasSignalPermission()) {
                cellMonitor.start(getPreferences(Context.MODE_PRIVATE).getInt("sim_id", -1))
            } else {
                cellStatus.text = "Для мониторинга сигнала нужен доступ к телефону."
            }
        }
    }
    override fun onStart() { super.onStart(); mapView.onStart() }
    override fun onResume() {
        super.onResume()
        pendingOtaInstall?.takeIf { TowerApkInstaller.canInstall(this) }?.let {
            pendingOtaInstall = null
            installVerifiedRelease(it)
        }
        aimForeground = true
        mapView.onResume()
        yandexWebMap?.onResume()
        applyDisplayMode()
        updateSelectedAimLine()
        updateServingPulse()
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
        headingSensor?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
        if (hasLocation()) startLocationUpdates()
        if (cellMonitor.hasSignalPermission()) {
            cellMonitor.start(getPreferences(Context.MODE_PRIVATE).getInt("sim_id", -1))
        }
        if (MapSources.providers.getOrNull(providerIndex)?.id.orEmpty().startsWith("google-") &&
            googleMapSession?.valid() != true && !choosingMap) {
            MapSources.providers.getOrNull(providerIndex)?.let(::useMapChoice)
        }
        if (headingSensor == null) {
            headingView.text = "Компас недоступен"
            headingView.contentDescription = "На устройстве отсутствует датчик ориентации"
        }
    }
    override fun onPause() {
        ++compassBounceGeneration
        compassBounceAnimator?.cancel()
        compassBounceAnimator = null
        if (::northIndicator.isInitialized) northIndicator.translationY = 0f
        stopServingPulse()
        aimForeground = false
        persistLastCamera(force = true)
        sensorManager.unregisterListener(this)
        cellMonitor.stop()
        locationFusion.stop()
        yandexWebMap?.onPause()
        mapView.onPause()
        super.onPause()
    }
    override fun onStop() {
        persistLastCamera(force = true)
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
        persistLastCamera(force = true)
        super.onSaveInstanceState(outState)
        mapView.onSaveInstanceState(outState)
    }
    override fun onDestroy() {
        ++compassBounceGeneration
        compassBounceAnimator?.cancel()
        stopServingPulse()
        executor.shutdownNow()
        apiExecutor.shutdownNow()
        cellExecutor.shutdownNow()
        yandexWebMap?.destroy()
        mapView.onDestroy()
        super.onDestroy()
    }
}
