package com.russoturissto.towerradar

import android.content.Context
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import android.view.MotionEvent
import android.view.View
import java.util.Locale
import kotlin.math.roundToInt

/** A native, touch-inspectable elevation profile in the same Khaki Modern palette. */
class TerrainProfileView(context: Context, private val colors: KhakiPalette) : View(context) {
    var profile: TerrainProfile? = null
        set(value) { field = value; selectedIndex = -1; invalidate() }
    var onInspect: ((String) -> Unit)? = null
    var towerHeightMeters: Double? = null
        set(value) { field = value; invalidate() }
    private var selectedIndex = -1
    private val density = resources.displayMetrics.density
    private val scaled = resources.displayMetrics.scaledDensity
    private fun dp(value: Float) = value * density
    private val pen = Paint(Paint.ANTI_ALIAS_FLAG)

    private fun print(canvas: Canvas, value: String, x: Float, y: Float, sp: Float,
                      color: Int, align: Paint.Align = Paint.Align.LEFT) {
        pen.shader = null; pen.style = Paint.Style.FILL; pen.color = color
        pen.textSize = sp * scaled; pen.textAlign = align
        canvas.drawText(value, x, y, pen)
    }

    override fun onMeasure(w: Int, h: Int) {
        setMeasuredDimension(MeasureSpec.getSize(w), dp(232f).roundToInt())
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val terrain = profile ?: run {
            print(canvas, "Получаем высоты местности…", width / 2f, height / 2f,
                13f, colors.secondary, Paint.Align.CENTER)
            return
        }
        val valid = terrain.samples.mapNotNull { it.elevation?.takeIf(Double::isFinite) }
        if (valid.size < 2) return
        val lower = valid.minOrNull() ?: return
        val higher = valid.maxOrNull() ?: return
        val first = terrain.samples.first().elevation
        val last = terrain.samples.last().elevation
        val topOfMast = if (last != null && towerHeightMeters != null)
            last + towerHeightMeters!! else higher
        val topValue = maxOf(higher, topOfMast)
        val range = (topValue - lower).coerceAtLeast(15.0)
        val minY = lower - range * .13
        val maxY = topValue + range * .15
        val left = dp(39f); val right = width - dp(12f)
        val top = dp(20f); val bottom = height - dp(34f)
        val xOf: (Int) -> Float = { i ->
            left + (right - left) * i / terrain.samples.lastIndex.coerceAtLeast(1)
        }
        val yOf: (Double) -> Float = { value ->
            bottom - ((value - minY) / (maxY - minY)).toFloat() * (bottom - top)
        }
        for (i in 0..2) {
            val valY = lower + (topValue - lower) * i / 2
            val ypos = yOf(valY)
            pen.shader = null; pen.style = Paint.Style.STROKE
            pen.color = colors.divider; pen.strokeWidth = dp(.85f)
            canvas.drawLine(left, ypos, right, ypos, pen)
            print(canvas, valY.roundToInt().toString(), left - dp(6f), ypos + dp(3f),
                9f, colors.secondary, Paint.Align.RIGHT)
        }
        var path = Path()
        var segmentStart = -1
        fun drawSection(end: Int) {
            if (segmentStart == -1) return
            val fill = Path(path).apply {
                lineTo(xOf(end), bottom)
                lineTo(xOf(segmentStart), bottom)
                close()
            }
            pen.style = Paint.Style.FILL
            pen.shader = LinearGradient(0f, top, 0f, bottom,
                (colors.olive and 0x00ffffff) or 0x48000000,
                (colors.olive and 0x00ffffff) or 0x08000000,
                Shader.TileMode.CLAMP)
            canvas.drawPath(fill, pen)
            pen.shader = null; pen.color = colors.olive
            pen.style = Paint.Style.STROKE
            pen.strokeWidth = dp(2.4f)
            pen.strokeJoin = Paint.Join.ROUND
            canvas.drawPath(path, pen)
            path = Path(); segmentStart = -1
        }
        terrain.samples.forEachIndexed { index, sample ->
            val v = sample.elevation
            if (v == null) { drawSection(index - 1); return@forEachIndexed }
            val x = xOf(index); val y = yOf(v)
            if (segmentStart == -1) { segmentStart = index; path.moveTo(x, y) }
            else path.lineTo(x, y)
        }
        drawSection(terrain.samples.lastIndex)
        if (first != null && last != null && towerHeightMeters != null) {
            pen.shader = null; pen.style = Paint.Style.STROKE
            pen.color = colors.secondary; pen.strokeWidth = dp(1.1f)
            pen.pathEffect = DashPathEffect(floatArrayOf(dp(5f), dp(4f)), 0f)
            canvas.drawLine(left, yOf(first + 1.5), right, yOf(last + towerHeightMeters!!), pen)
            pen.pathEffect = null
        }
        print(canvas, "ВЫ", left, bottom + dp(20f), 10f, colors.text)
        val totalKm = String.format(Locale.US, "%.2f", terrain.distanceKm)
        print(canvas, "ВЫШКА · " + totalKm + " км", right, bottom + dp(20f),
            10f, colors.text, Paint.Align.RIGHT)
        if (selectedIndex in terrain.samples.indices) {
            val i = selectedIndex
            val x = xOf(i)
            pen.shader = null; pen.style = Paint.Style.STROKE
            pen.color = colors.secondary; pen.strokeWidth = dp(1f)
            canvas.drawLine(x, top, x, bottom, pen)
            terrain.samples[i].elevation?.let { v ->
                pen.style = Paint.Style.FILL; pen.color = colors.olive
                canvas.drawCircle(x, yOf(v), dp(5f), pen)
                pen.color = colors.surface
                canvas.drawCircle(x, yOf(v), dp(2f), pen)
            }
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val data = profile ?: return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                val left = dp(39f); val right = width - dp(12f)
                if (right <= left) return false
                selectedIndex = (((event.x - left) / (right - left)) *
                    data.samples.lastIndex).roundToInt().coerceIn(0, data.samples.lastIndex)
                val v = data.samples[selectedIndex].elevation
                val km = data.distanceKm * selectedIndex / data.samples.lastIndex
                val altitude = if (v == null) "нет данных" else v.roundToInt().toString() + " м"
                onInspect?.invoke(String.format(Locale.US, "Расстояние %.2f км · %s", km, altitude))
                invalidate()
                return true
            }
            MotionEvent.ACTION_UP -> { performClick(); return true }
            MotionEvent.ACTION_CANCEL -> return true
        }
        return false
    }
    override fun performClick(): Boolean { super.performClick(); return true }
}
