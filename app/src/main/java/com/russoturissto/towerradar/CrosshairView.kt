package com.russoturissto.towerradar

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.View

/** Fixed four-ray reticle (#1): fine white rays, dark halo, olive center dot. */
class CrosshairView(context: Context, darkMap: Boolean = false) : View(context) {
    private fun dp(v: Float) = v * resources.displayMetrics.density
    private val halo = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(205, 20, 31, 22)
        style = Paint.Style.STROKE
        strokeWidth = dp(3.4f)
        strokeCap = Paint.Cap.ROUND
    }
    private val ray = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = dp(1.65f)
        strokeCap = Paint.Cap.ROUND
    }
    private val dotHalo = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(30, 40, 29)
        style = Paint.Style.FILL
    }
    private val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(190, 215, 118)
        style = Paint.Style.FILL
    }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val x = width / 2f
        val y = height / 2f
        val inner = dp(9f)
        val outer = dp(23f)
        for (paint in listOf(halo, ray)) {
            canvas.drawLine(x, y - outer, x, y - inner, paint)
            canvas.drawLine(x, y + inner, x, y + outer, paint)
            canvas.drawLine(x - outer, y, x - inner, y, paint)
            canvas.drawLine(x + inner, y, x + outer, y, paint)
        }
        canvas.drawCircle(x, y, dp(4.0f), dotHalo)
        canvas.drawCircle(x, y, dp(2.25f), dot)
    }
}
