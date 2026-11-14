package com.russoturissto.towerradar

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.View.MeasureSpec
import android.widget.FrameLayout
import android.widget.TextView
import java.util.Locale

/**
 * Two-line edge-to-edge panel. It has no top stroke or decorative square.
 * Rows remain in actual DisplayCutout-safe horizontal slots and grow with font scale.
 */
class TowerStatusHeader(ctx: Context) : FrameLayout(ctx) {
    private val surface = Color.rgb(37, 49, 36)
    private val accent = Color.rgb(186, 206, 139)
    private val primary = Color.rgb(246, 247, 239)
    private val secondary = Color.rgb(204, 217, 194)

    private fun dp(n: Int) = (n * resources.displayMetrics.density + .5f).toInt()

    private fun label(sp: Float, color: Int, bold: Boolean = false) =
        TextView(context).apply {
            textSize = sp
            setTextColor(color)
            setSingleLine(true)
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            includeFontPadding = true
            gravity = Gravity.CENTER_VERTICAL
            if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
        }

    val headingLabel = label(15f, accent, true).apply { text = "➤ —°" }
    val gpsLabel = label(11f, secondary).apply {
        text = "GPS: ожидание"
        gravity = Gravity.END or Gravity.CENTER_VERTICAL
    }
    private val coordinatesLabel = label(13f, primary).apply {
        text = "⌖ Центр карты: ожидание"
        contentDescription = "Координаты центра карты пока неизвестны"
    }
    private val copyCoordinatesLabel = label(17f, accent, true).apply {
        text = "⧉"
        gravity = Gravity.END or Gravity.CENTER_VERTICAL
        contentDescription = "Скопировать координаты"
    }
    private var coordinateValue: String? = null
    private var canShowCoordinateCopy = false
    private val networkLabel = label(11.5f, primary)
    private val towersLabel = label(11.5f, primary, true).apply {
        gravity = Gravity.END or Gravity.CENTER_VERTICAL
    }
    private var savedOperator: String? = null
    private var savedNetwork: String? = null
    private var savedDbm: Int? = null
    private var countVisible = 0
    private var countSaved = 0
    private var slotLeadingWidth = 0
    private var slotTrailingWidth = 0

    init {
        // No outline on the top edge: the protected status area has the same
        // opaque colour and joins seamlessly on every map and display shape.
        background = GradientDrawable().apply {
            setColor(surface)
            cornerRadii = floatArrayOf(0f, 0f, 0f, 0f,
                dp(16).toFloat(), dp(16).toFloat(),
                dp(16).toFloat(), dp(16).toFloat())
        }
        elevation = dp(4).toFloat()
        addView(headingLabel, LayoutParams(-2, -2))
        addView(networkLabel, LayoutParams(-2, -2))
        addView(towersLabel, LayoutParams(-2, -2))
        addView(gpsLabel, LayoutParams(-2, -2))
        addView(coordinatesLabel, LayoutParams(-2, -2))
        addView(copyCoordinatesLabel, LayoutParams(-2, -2))
        updateNetwork(null, null, null, null, null, null)
        updateTowers(0, 0)
    }

    fun onCoordinatesTap(action: (String) -> Unit) {
        coordinatesLabel.setOnClickListener { coordinateValue?.let(action) }
        copyCoordinatesLabel.setOnClickListener { coordinateValue?.let(action) }
    }

    fun updateCoordinates(latitude: Double, longitude: Double) {
        coordinateValue = CoordinateFormatter.format(latitude, longitude)
        coordinatesLabel.text = coordinateValue?.let { "⌖ $it" } ?: "⌖ Центр карты: ожидание"
        coordinatesLabel.contentDescription = coordinateValue?.let {
            "Координаты центра карты $it. Нажмите, чтобы скопировать"
        } ?: "Координаты центра карты пока неизвестны"
        copyCoordinatesLabel.visibility = if (coordinateValue != null && canShowCoordinateCopy)
            View.VISIBLE else View.GONE
    }

    fun onHeadingTap(action: () -> Unit) {
        headingLabel.setOnClickListener { action() }
    }

    fun onSignalTap(action: () -> Unit) {
        networkLabel.setOnClickListener { action() }
        networkLabel.setOnLongClickListener { action(); true }
    }

    fun updateNetwork(operator: String?, network: String?, dbm: Int?,
                      rsrp: Int?, rsrq: Int?, sinr: Float?) {
        savedOperator = operator?.trim()?.takeIf {
            it.isNotEmpty() && it != "Мобильная сеть"
        }
        savedNetwork = when (network?.uppercase(Locale.ROOT)) {
            "LTE" -> "4G LTE"
            "NR" -> "5G NR"
            "UMTS", "WCDMA" -> "3G"
            "GSM" -> "2G"
            else -> network?.takeIf { it.isNotBlank() } ?: "Сеть"
        }
        savedDbm = dbm
        renderNetwork()
        val detail = listOfNotNull(
            dbm?.let { "$it dBm" }, rsrp?.let { "RSRP $it" },
            rsrq?.let { "RSRQ $it" },
            sinr?.takeIf { it.isFinite() }?.let {
                "SINR " + String.format(Locale.US, "%.1f", it)
            }
        )
        networkLabel.contentDescription = "Сеть " + networkLabel.text +
            (if (detail.isEmpty()) ". Модем не сообщил сигнал"
             else ". " + detail.joinToString(", "))
    }

    fun updateTowers(visible: Int, saved: Int) {
        countVisible = visible
        countSaved = saved
        renderTowers()
        towersLabel.contentDescription =
            "Показано вышек $visible. Сохранено объектов $saved"
    }

    private fun renderNetwork() {
        networkLabel.text = StatusTextPolicy.network(
            savedNetwork ?: "Сеть", savedOperator, savedDbm,
            slotLeadingWidth.toFloat(), networkLabel.paint::measureText
        )
    }

    private fun renderTowers() {
        val full = "Вышек: $countVisible · База: $countSaved"
        val short = "Вышек: $countVisible"
        val width = slotTrailingWidth
        towersLabel.text = if (width > 0 && towersLabel.paint.measureText(full) > width)
            short else full
    }

    private fun textHeight(view: TextView): Int {
        view.measure(MeasureSpec.makeMeasureSpec(dp(300), MeasureSpec.AT_MOST),
            MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED))
        return view.measuredHeight.coerceAtLeast(dp(17))
    }

    /**
     * Returns measured height in pixels. The camera's physical position is
     * supplied by Android, not guessed from a device model or screen ratio.
     */
    fun arrange(width: Int, cutouts: List<IntRange>, cutoutBottom: Int,
                immersive: Boolean, safeLeft: Int = 0, safeRight: Int = 0): Int {
        val edge = dp(12)
        val blocked = listOfNotNull(
            if (safeLeft > 0) (0..safeLeft) else null,
            if (safeRight > 0) ((width - safeRight)..width) else null
        ) + if (immersive) cutouts else emptyList()
        val slots = HeaderPanelGeometry.allocate(width, blocked, edge, dp(98), dp(8))
        val left = slots.leading
        val right = slots.trailing

        // No false third row: dBm stays beside the network indicator.
        slotLeadingWidth = (left?.width ?: 0).coerceAtLeast(0)
        slotTrailingWidth = (right?.width ?: 0).coerceAtLeast(0)
        renderNetwork()
        renderTowers()

        val row1Height = maxOf(dp(21), textHeight(headingLabel), textHeight(towersLabel))
        val row2Height = maxOf(dp(19), textHeight(networkLabel), textHeight(gpsLabel))
        val y1 = dp(6)
        val y2 = y1 + row1Height + dp(1)
        // The copyable coordinates row occupies the full width, below camera cutouts.
        val row3Height = maxOf(dp(23), textHeight(coordinatesLabel))
        val y3 = maxOf(y2 + row2Height + dp(4),
            if (immersive) cutoutBottom + dp(5) else 0)
        val totalHeight = y3 + row3Height + dp(6)

        fun place(v: TextView, segment: DisplayModePolicy.Segment?,
                  top: Int, height: Int, alignRight: Boolean) {
            if (segment == null || segment.width < dp(34)) {
                v.visibility = View.GONE
                return
            }
            v.visibility = View.VISIBLE
            v.layoutParams = LayoutParams(segment.width, height,
                Gravity.TOP or Gravity.START).apply {
                    leftMargin = segment.start
                    topMargin = top
                }
            v.gravity = (if (alignRight) Gravity.END else Gravity.START) or
                Gravity.CENTER_VERTICAL
        }
        place(headingLabel, left, y1, row1Height, false)
        place(networkLabel, left, y2, row2Height, false)
        place(towersLabel, right, y1, row1Height, true)
        place(gpsLabel, right, y2, row2Height, true)
        val coordinatesStart = (safeLeft + edge).coerceAtMost(width)
        val coordinatesEnd = (width - safeRight - edge).coerceAtLeast(coordinatesStart)
        val coordinatesWidth = coordinatesEnd - coordinatesStart
        canShowCoordinateCopy = coordinatesWidth >= dp(160)
        if (coordinatesWidth < dp(85)) {
            coordinatesLabel.visibility = View.GONE
            copyCoordinatesLabel.visibility = View.GONE
        } else {
            val copyWidth = if (canShowCoordinateCopy) dp(29) else 0
            coordinatesLabel.visibility = View.VISIBLE
            coordinatesLabel.layoutParams = LayoutParams(coordinatesWidth - copyWidth, row3Height,
                Gravity.TOP or Gravity.START).apply {
                leftMargin = coordinatesStart
                topMargin = y3
            }
            copyCoordinatesLabel.visibility =
                if (coordinateValue != null && canShowCoordinateCopy) View.VISIBLE else View.GONE
            if (canShowCoordinateCopy) {
                copyCoordinatesLabel.layoutParams = LayoutParams(copyWidth, row3Height,
                    Gravity.TOP or Gravity.START).apply {
                    leftMargin = coordinatesEnd - copyWidth
                    topMargin = y3
                }
            }
        }
        // If the notch occupies most of the top edge, we deliberately omit
        // secondary indicators instead of painting them under the camera.
        return totalHeight
    }
}
