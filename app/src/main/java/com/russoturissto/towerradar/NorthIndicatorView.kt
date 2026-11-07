package com.russoturissto.towerradar

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.view.View

/** Transparent N + north arrow. Orientation refers to the MAP, not phone azimuth. */
class NorthIndicatorView(context: Context, darkTheme: Boolean = false) : View(context) {
    private fun dp(v: Float) = v * resources.displayMetrics.density
    private val foreground = if (darkTheme) Color.rgb(217, 226, 190) else Color.rgb(41, 56, 43)
    private val contrast = if (darkTheme) Color.rgb(35, 45, 31) else Color.WHITE
    private val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = contrast
        style = Paint.Style.STROKE
        strokeWidth = dp(2.7f)
        strokeJoin = Paint.Join.ROUND
    }
    private val ink = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = foreground
        style = Paint.Style.FILL
    }
    private val letter = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = foreground
        textSize = resources.displayMetrics.scaledDensity * 16f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textAlign = Paint.Align.CENTER
    }
    private val letterBorder = Paint(letter).apply {
        color = contrast
        style = Paint.Style.STROKE
        strokeWidth = dp(2.1f)
        strokeJoin = Paint.Join.ROUND
    }

    private var rotationDegrees = 0f

    fun setMapBearing(bearing: Double) {
        val next = NorthDirectionModel.arrowRotation(bearing)
        if (next != rotationDegrees) {
            rotationDegrees = next
            invalidate()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width * 0.5f
        val cy = dp(39f)
        val baseline = dp(18f)
        canvas.drawText("N", cx, baseline, letterBorder)
        canvas.drawText("N", cx, baseline, letter)

        val arrow = Path().apply {
            moveTo(cx, cy - dp(12f))
            lineTo(cx + dp(6f), cy + dp(11f))
            lineTo(cx, cy + dp(5f))
            lineTo(cx - dp(6f), cy + dp(11f))
            close()
        }
        canvas.save()
        canvas.rotate(rotationDegrees, cx, cy)
        canvas.drawPath(arrow, border)
        canvas.drawPath(arrow, ink)
        canvas.restore()
    }
}
