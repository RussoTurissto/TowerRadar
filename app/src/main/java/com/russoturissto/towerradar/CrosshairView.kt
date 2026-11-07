package com.russoturissto.towerradar

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.View

/** Fixed on-screen aiming reticle while the user moves/zooms the MapLibre map beneath. */
class CrosshairView(context: Context) : View(context) {
    private val outline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(18, 40, 42)
        style = Paint.Style.STROKE
        strokeWidth = dp(6f)
        strokeCap = Paint.Cap.ROUND
    }
    private val bright = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(24, 207, 223)
        style = Paint.Style.STROKE
        strokeWidth = dp(3f)
        strokeCap = Paint.Cap.ROUND
    }
    private val center = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
    }
    private fun dp(v: Float) = v * resources.displayMetrics.density

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val x = width * 0.5f
        val y = height * 0.5f
        val gap = dp(10f)
        val length = dp(34f)
        val parts = arrayOf(
            floatArrayOf(x - length, y, x - gap, y),
            floatArrayOf(x + gap, y, x + length, y),
            floatArrayOf(x, y - length, x, y - gap),
            floatArrayOf(x, y + gap, x, y + length)
        )
        for (segment in parts) {
            canvas.drawLine(segment[0], segment[1], segment[2], segment[3], outline)
            canvas.drawLine(segment[0], segment[1], segment[2], segment[3], bright)
        }
        canvas.drawCircle(x, y, dp(7f), outline)
        canvas.drawCircle(x, y, dp(4f), bright)
        canvas.drawCircle(x, y, dp(1.8f), center)
    }
}
