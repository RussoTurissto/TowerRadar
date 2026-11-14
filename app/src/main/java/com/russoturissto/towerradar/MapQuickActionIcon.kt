package com.russoturissto.towerradar

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.view.View

/** Matching precise outline pictograms for the two map quick actions. */
class MapQuickActionIcon(context: Context, private val kind: Kind,
                         color: Int = Color.WHITE) : View(context) {
    enum class Kind { HEIGHT, TOWER }
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        style = Paint.Style.STROKE
        strokeWidth = 2.4f
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        style = Paint.Style.FILL
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.save()
        canvas.translate(width / 2f, height / 2f)
        val scale = minOf(width, height) / 40f
        canvas.scale(scale, scale)
        if (kind == Kind.HEIGHT) {
            val silhouette = Path().apply {
                moveTo(-17f, 12f)
                lineTo(-5f, -12f)
                lineTo(2f, 0f)
                lineTo(8f, -8f)
                lineTo(18f, 12f)
                close()
            }
            canvas.drawPath(silhouette, stroke)
            // Visible snowcaps make this read as MOUNTAINS, not a generic zigzag.
            canvas.drawLine(-9.5f, -3f, -5f, -12f, stroke)
            canvas.drawLine(-5f, -12f, -0.7f, -4.5f, stroke)
            canvas.drawLine(5f, -3f, 8f, -8f, stroke)
            canvas.drawLine(8f, -8f, 11f, -2f, stroke)
        } else {
            // Narrow lattice transmission mast, clear diagonal braces and radio arcs.
            canvas.drawLine(0f, -13f, -11f, 15f, stroke)
            canvas.drawLine(0f, -13f, 11f, 15f, stroke)
            canvas.drawLine(-11f, 15f, 11f, 15f, stroke)
            canvas.drawLine(-5f, 0f, 5f, 0f, stroke)
            canvas.drawLine(-8f, 8f, 8f, 8f, stroke)
            canvas.drawLine(-5f, 0f, 8f, 8f, stroke)
            canvas.drawLine(5f, 0f, -8f, 8f, stroke)
            canvas.drawCircle(0f, -14f, 1.9f, fill)
            canvas.drawArc(-11f, -24f, 11f, -4f, 130f, 100f, false, stroke)
            canvas.drawArc(-18f, -29f, 18f, 3f, 133f, 94f, false, stroke)
        }
        canvas.restore()
    }
}
