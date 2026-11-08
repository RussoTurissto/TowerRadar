package com.russoturissto.towerradar

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.view.View
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.PI

/**
 * Unobstructed N + arrow; no circular compass icon or background.
 * Defaults to live device north when rotation-vector sensor is available.
 * Tap to switch to the cartographic north shown by MapLibre's map bearing.
 */
class NorthIndicatorView(context: Context, darkTheme: Boolean = false) : View(context) {
    private fun dp(v: Float) = v * resources.displayMetrics.density
    private val foreground = if (darkTheme) Color.rgb(217, 226, 190) else Color.rgb(41, 56, 43)
    private val contrast = if (darkTheme) Color.rgb(35, 45, 31) else Color.WHITE
    private val outline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = contrast; style = Paint.Style.STROKE
        strokeWidth = dp(2.8f); strokeJoin = Paint.Join.ROUND
    }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = foreground; style = Paint.Style.FILL
    }
    private val label = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = foreground; textSize = resources.displayMetrics.scaledDensity * 14f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textAlign = Paint.Align.CENTER
    }
    private val labelOutline = Paint(label).apply {
        color = contrast; style = Paint.Style.STROKE
        strokeWidth = dp(2.2f); strokeJoin = Paint.Join.ROUND
    }
    private var deviceAzimuth: Float? = null
    private var mapBearing: Double = 0.0
    private var preferDevice = true
    private var rotationDegrees = 0f

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
        val cx = width * .5f
        val cy = height * .59f
        // A slender filled arrow with a small hollow notch.
        val arrow = Path().apply {
            moveTo(cx, cy - dp(13f))
            lineTo(cx + dp(5f), cy + dp(10f))
            lineTo(cx, cy + dp(4f))
            lineTo(cx - dp(5f), cy + dp(10f))
            close()
        }
        canvas.save()
        canvas.rotate(rotationDegrees, cx, cy)
        canvas.drawPath(arrow, outline)
        canvas.drawPath(arrow, fill)
        canvas.restore()
        // Keep N upright while moving it to the arrow tip on the invisible circle.
        val rad = rotationDegrees * PI / 180.0
        val labelDistance = dp(26f)
        val nx = cx + (sin(rad) * labelDistance).toFloat()
        val ny = cy - (cos(rad) * labelDistance).toFloat()
        canvas.drawText("N", nx, ny + dp(4f), labelOutline)
        canvas.drawText("N", nx, ny + dp(4f), label)
    }
}
