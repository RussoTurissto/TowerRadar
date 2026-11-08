package com.russoturissto.towerradar

import android.content.Context
 import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView

/**
 * One edge-to-edge diagnostic header shared by standard and immersive display modes.
 * The top row occupies only real safe regions around DisplayCutout bounding rectangles.
 * A separate metrics row begins below the physical cutout (on devices with one).
 */
class TowerStatusHeader(private val uiContext: Context) : FrameLayout(uiContext) {
    private val surface = Color.rgb(37, 49, 36)
    private val accent = Color.rgb(186, 206, 139)
    private val foreground = Color.rgb(246, 247, 239)
    private val secondary = Color.rgb(204, 217, 194)

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density + 0.5f).toInt()

    private fun label(size: Float, color: Int, bold: Boolean = false) = TextView(uiContext).apply {
        textSize = size
        setTextColor(color)
        maxLines = 1
        ellipsize = TextUtils.TruncateAt.END
        setSingleLine(true)
        gravity = Gravity.CENTER_VERTICAL
        includeFontPadding = false
        if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
    }

    val headingLabel = label(15f, accent, true).apply { text = "➤ —°" }
    val gpsLabel = label(11f, secondary).apply { text = "GPS: ожидание"; gravity = Gravity.END or Gravity.CENTER_VERTICAL }
    private val networkLabel = label(11f, secondary)
    private val towersLabel = label(12f, foreground, true).apply { gravity = Gravity.END or Gravity.CENTER_VERTICAL }
    private val metricsLabel = label(11f, accent)
    private var countVisible = 0
    private var countSaved = 0

    init {
        background = GradientDrawable().apply {
            setColor(surface)
            cornerRadii = floatArrayOf(0f, 0f, 0f, 0f,
                dp(18).toFloat(), dp(18).toFloat(),
                dp(18).toFloat(), dp(18).toFloat())
            setStroke(dp(1).coerceAtLeast(1), Color.rgb(96, 113, 78))
        }
        elevation = dp(5).toFloat()
        isClickable = false
        addView(headingLabel, LayoutParams(dp(140), dp(20), Gravity.TOP or Gravity.START))
        addView(networkLabel, LayoutParams(dp(140), dp(16), Gravity.TOP or Gravity.START))
        addView(towersLabel, LayoutParams(dp(140), dp(20), Gravity.TOP or Gravity.START))
        addView(gpsLabel, LayoutParams(dp(140), dp(16), Gravity.TOP or Gravity.START))
        addView(metricsLabel, LayoutParams(-1, dp(18), Gravity.TOP or Gravity.START))
        networkLabel.text = "Ожидаем данные сети"
        towersLabel.text = "Вышек: 0 · База: 0"
        metricsLabel.text = "Параметры радиосигнала: —"
    }

    fun onHeadingTap(action: () -> Unit) {
        headingLabel.setOnClickListener { action() }
        networkLabel.setOnClickListener { action() }
    }
    fun onSignalTap(action: () -> Unit) {
        metricsLabel.setOnClickListener { action() }
    }

    fun updateNetwork(operator: String?, network: String?, dbm: Int?,
                      rsrp: Int?, rsrq: Int?, sinr: Float?) {
        val type = when (network?.uppercase(java.util.Locale.ROOT)) {
            "LTE" -> "4G LTE"
            "NR" -> "5G NR"
            "UMTS", "WCDMA" -> "3G"
            "GSM" -> "2G"
            else -> network?.takeIf { it.isNotBlank() } ?: "Сеть —"
        }
        val provider = operator?.takeIf {
            it.isNotBlank() && it != "Мобильная сеть"
        }?.take(22)
        networkLabel.text = listOfNotNull(type, provider).joinToString(" · ")
        val numbers = listOfNotNull(
            dbm?.let { it.toString() + " dBm" },
            rsrp?.let { "RSRP " + it },
            rsrq?.let { "RSRQ " + it },
            sinr?.takeIf { it.isFinite() }?.let {
                "SINR " + String.format(java.util.Locale.US, "%.1f", it)
            }
        )
        metricsLabel.text = numbers.joinToString("  ·  ")
            .ifBlank { "Модем не сообщил параметры сигнала" }
        metricsLabel.contentDescription = metricsLabel.text
    }

    fun updateTowers(visible: Int, saved: Int) {
        countVisible = visible
        countSaved = saved
        towersLabel.text = "Вышек: " + visible + " · База: " + saved
        towersLabel.contentDescription = "Отображается вышек " + visible +
            ", сохранено объектов в базе " + saved
    }

    /**
     * Each label stays inside its assigned safe horizontal slot.
     * On very narrow/complex displays optional secondary labels are suppressed.
     * Returns the actual pixel height needed, including the radio-metric row.
     */
    fun arrange(width: Int, cutouts: List<IntRange>, cutoutBottom: Int,
                immersive: Boolean, safeLeft: Int = 0, safeRight: Int = 0): Int {
        val edge = dp(9)
        val safeWidth = width.coerceAtLeast(dp(1))
        val sideBlockers = listOfNotNull(
            if (safeLeft > 0) (0..safeLeft) else null,
            if (safeRight > 0) ((width - safeRight)..width) else null
        )
        val slots = HeaderPanelGeometry.allocate(
            safeWidth, (if (immersive) cutouts else emptyList()) + sideBlockers,
            edge, dp(112), dp(8)
        )
        val largeFont = resources.configuration.fontScale >= 1.25f
        val row2Y = if (largeFont) 30 else 25
        val row2Height = if (largeFont) 19 else 15
        val metricTop = maxOf(
            dp(if (largeFont) 53 else 43), if (immersive) cutoutBottom + dp(6) else 0
        )
        val height = metricTop + dp(if (largeFont) 25 else 22)
        val l = slots.leading
        val r = slots.trailing
        fun frame(view: TextView, segment: DisplayModePolicy.Segment?,
                  y: Int, h: Int, alignRight: Boolean = false) {
            if (segment == null || segment.width < dp(32)) {
                view.visibility = View.GONE
                return
            }
            view.visibility = View.VISIBLE
            view.layoutParams = LayoutParams(segment.width, dp(h), Gravity.TOP or Gravity.START).apply {
                leftMargin = segment.start
                topMargin = dp(y)
            }
            view.gravity = (if (alignRight) Gravity.END else Gravity.START) or Gravity.CENTER_VERTICAL
        }
        frame(headingLabel, l, 5, if (largeFont) 25 else 20)
        frame(networkLabel, l, row2Y, row2Height)
        // A camera cutout never intersects the lower strip below cutoutBottom.
        val metricLeft = maxOf(edge, safeLeft + edge)
        val metricRight = maxOf(edge, safeRight + edge)
        val metricWidth = (safeWidth - metricLeft - metricRight).coerceAtLeast(1)
        if (r != null) {
            towersLabel.text = if (r.width < dp(155))
                "Вышек: " + countVisible else
                "Вышек: " + countVisible + " · База: " + countSaved
            frame(towersLabel, r, 5, if (largeFont) 25 else 20, true)
            frame(gpsLabel, r, row2Y, row2Height, true)
            metricsLabel.visibility = if (metricWidth >= dp(135)) View.VISIBLE else View.GONE
        } else {
            // A corner/large notch may leave only one top slot.
            // Keep the tower count visible in the lower safe row.
            towersLabel.text = "Вышек: " + countVisible + " · База: " + countSaved
            frame(towersLabel,
                DisplayModePolicy.Segment(metricLeft, safeWidth - metricRight),
                if (largeFont) 53 else 43, if (largeFont) 23 else 18, true)
            gpsLabel.visibility = View.GONE
            metricsLabel.visibility = View.GONE
        }
        metricsLabel.layoutParams = LayoutParams(
            metricWidth, dp(if (largeFont) 23 else 18),
            Gravity.TOP or Gravity.START
        ).apply { leftMargin = metricLeft; topMargin = metricTop }
        return height
    }
}
