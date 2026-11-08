package com.russoturissto.towerradar

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.view.View
import kotlin.math.min

/**
 * White circular north control: an upright N and a rotating red/charcoal arrow.
 * Tap toggles device north and MapLibre map north (same behavior as before).
 */
class NorthIndicatorView(context: Context, darkTheme: Boolean = false) : View(context) {
    private fun dp(v: Float) = v * resources.displayMetrics.density
    private val disk = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
        setShadowLayer(dp(2.5f), 0f, dp(1.2f), Color.argb(95, 0, 0, 0))
    }
    private val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(171, 177, 173)
        style = Paint.Style.STROKE
        strokeWidth = dp(1.2f)
    }
    private val label = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(27, 34, 30)
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textSize = resources.displayMetrics.scaledDensity * 14f
        textAlign = Paint.Align.CENTER
    }
    private val red = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(224, 49, 50)
        style = Paint.Style.FILL
    }
    private val charcoal = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(44, 51, 48)
        style = Paint.Style.FILL
    }
    private var deviceAzimuth: Float? = null
    private var mapBearing: Double = 0.0
    private var preferDevice = true
    private var rotationDegrees = 0f

    init {
        // The small software layer permits a subtle circle shadow on both themes.
        setLayerType(LAYER_TYPE_SOFTWARE, null)
    }

    fun setMapBearing(bearing: Double) {
        mapBearing = bearing
        updateRotation()
    }

    fun setCompassHeading(trueNorthDegrees: Float) {
        if (!trueNorthDegrees.isFinite()) return
        deviceAzimuth = trueNorthDegrees
        updateRotation()
    }

    fun toggleSource(): Boolean {
        preferDevice = !preferDevice
        updateRotation()
        return preferDevice
    }

    private fun updateRotation() {
        val degrees = if (preferDevice && deviceAzimuth != null)
            NorthDirectionModel.arrowRotation(deviceAzimuth!!.toDouble())
        else NorthDirectionModel.arrowRotation(mapBearing)
        rotationDegrees = degrees
        contentDescription = if (preferDevice && deviceAzimuth != null)
            "Север по датчикам телефона. Нажмите для севера карты"
        else "Север относительно карты. Нажмите для компаса телефона"
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = height / 2f
        val radius = (min(width, height) / 2f - dp(4.0f)).coerceAtLeast(0f)
        canvas.drawCircle(cx, cy, radius, disk)
        canvas.drawCircle(cx, cy, radius, border)
        // Letter never tilts; only the two-tone arrow rotates.
        canvas.drawText("N", cx, cy - dp(6.5f), label)
        canvas.save()
        val arrowCenter = cy + dp(7f)
        canvas.rotate(rotationDegrees, cx, arrowCenter)
        val northHalf = Path().apply {
            moveTo(cx, arrowCenter - dp(9f))
            lineTo(cx + dp(6.6f), arrowCenter + dp(7f))
            lineTo(cx, arrowCenter + dp(3.5f))
            close()
        }
        val darkHalf = Path().apply {
            moveTo(cx, arrowCenter - dp(9f))
            lineTo(cx, arrowCenter + dp(3.5f))
            lineTo(cx - dp(6.6f), arrowCenter + dp(7f))
            close()
        }
        canvas.drawPath(northHalf, red)
        canvas.drawPath(darkHalf, charcoal)
        canvas.restore()
    }
}
