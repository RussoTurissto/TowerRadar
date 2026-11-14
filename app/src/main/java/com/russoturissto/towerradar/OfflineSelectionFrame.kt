package com.russoturissto.towerradar

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.View

/** Stationary viewport rectangle; pinch-zoom changes its geographic coverage. */
class OfflineSelectionFrame(context: Context) : View(context) {
    private val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(187, 222, 117)
        strokeWidth = resources.displayMetrics.density * 2.5f
        style = Paint.Style.STROKE
    }
    private val shade = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(76, 18, 29, 18)
        style = Paint.Style.FILL
    }
    private val inside = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(20, 176, 217, 107)
        style = Paint.Style.FILL
    }
    fun selectionRect(): RectF = RectF(width * 0.10f, height * 0.29f,
        width * 0.90f, height * 0.71f)
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val r = selectionRect()
        canvas.drawRect(0f, 0f, width.toFloat(), r.top, shade)
        canvas.drawRect(0f, r.bottom, width.toFloat(), height.toFloat(), shade)
        canvas.drawRect(0f, r.top, r.left, r.bottom, shade)
        canvas.drawRect(r.right, r.top, width.toFloat(), r.bottom, shade)
        canvas.drawRect(r, inside)
        canvas.drawRect(r, border)
    }
    override fun onTouchEvent(event: android.view.MotionEvent): Boolean = false
}
