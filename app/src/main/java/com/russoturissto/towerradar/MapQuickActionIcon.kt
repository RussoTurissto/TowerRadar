package com.russoturissto.towerradar

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.View

/** Two crisp vector pictograms for map quick actions. */
class MapQuickActionIcon(context: Context, private val kind: Kind,
                         color: Int = Color.WHITE) : View(context) {
    enum class Kind { HEIGHT, TOWER }
    private val ink = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        style = Paint.Style.STROKE
        strokeWidth = resources.displayMetrics.density * 2f
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.save()
        canvas.translate(width / 2f, height / 2f)
        canvas.scale(minOf(width, height) / 34f, minOf(width, height) / 34f)
        if (kind == Kind.HEIGHT) {
            canvas.drawLine(-15f, 12f, -4f, -9f, ink)
            canvas.drawLine(-4f, -9f, 3f, 2f, ink)
            canvas.drawLine(3f, 2f, 9f, -6f, ink)
            canvas.drawLine(9f, -6f, 16f, 12f, ink)
            canvas.drawLine(-15f, 12f, 16f, 12f, ink)
        } else {
            canvas.drawLine(0f, -12f, -9f, 13f, ink)
            canvas.drawLine(0f, -12f, 9f, 13f, ink)
            canvas.drawLine(-7f, 5f, 7f, 5f, ink)
            canvas.drawLine(-4f, -1f, 4f, -1f, ink)
            canvas.drawLine(-9f, 13f, 9f, 13f, ink)
            canvas.drawCircle(0f, -13f, 2f, ink)
            canvas.drawArc(-12f, -20f, 12f, 3f, 125f, 110f, false, ink)
            canvas.drawArc(-18f, -25f, 18f, 8f, 135f, 90f, false, ink)
        }
        canvas.restore()
    }
}
