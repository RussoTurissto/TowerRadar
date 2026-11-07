package com.russoturissto.towerradar

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.view.View

/** Four fine unobtrusive map-corner brackets with an unobstructed center point. */
class CrosshairView(context: Context, darkMap: Boolean = false) : View(context) {
    private fun dp(v: Float) = v * resources.displayMetrics.density
    private val rim = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = if (darkMap) Color.rgb(30, 40, 29) else Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = dp(3.4f)
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
    }
    private val ink = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = if (darkMap) Color.rgb(217, 226, 190) else Color.rgb(41, 56, 43)
        style = Paint.Style.STROKE
        strokeWidth = dp(1.65f)
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
    }
    private val dotRim = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = if (darkMap) Color.rgb(30, 40, 29) else Color.WHITE
        style = Paint.Style.FILL
    }
    private val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = if (darkMap) Color.rgb(217, 226, 190) else Color.rgb(41, 56, 43)
        style = Paint.Style.FILL
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = height / 2f
        val outer = dp(22f)
        val inner = dp(11f)
        val brackets = Path().apply {
            moveTo(cx - outer, cy - inner)
            lineTo(cx - outer, cy - outer)
            lineTo(cx - inner, cy - outer)
            moveTo(cx + inner, cy - outer)
            lineTo(cx + outer, cy - outer)
            lineTo(cx + outer, cy - inner)
            moveTo(cx + outer, cy + inner)
            lineTo(cx + outer, cy + outer)
            lineTo(cx + inner, cy + outer)
            moveTo(cx - inner, cy + outer)
            lineTo(cx - outer, cy + outer)
            lineTo(cx - outer, cy + inner)
        }
        canvas.drawPath(brackets, rim)
        canvas.drawPath(brackets, ink)
        canvas.drawCircle(cx, cy, dp(2.7f), dotRim)
        canvas.drawCircle(cx, cy, dp(1.4f), dot)
    }
}
