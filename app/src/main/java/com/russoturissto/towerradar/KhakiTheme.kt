package com.russoturissto.towerradar

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color

data class KhakiPalette(
    val dark: Boolean,
    val canvas: Int,
    val surface: Int,
    val elevated: Int,
    val text: Int,
    val secondary: Int,
    val olive: Int,
    val oliveText: Int,
    val divider: Int,
    val accentSoft: Int,
    val signalMuted: Int
)

object KhakiTheme {
    const val SYSTEM = "system"
    const val DAY = "light"
    const val NIGHT = "dark"
    val names = arrayOf("Системная", "Дневная", "Ночная")
    val values = arrayOf(SYSTEM, DAY, NIGHT)

    fun dark(context: Context, mode: String): Boolean = when (mode) {
        DAY -> false
        NIGHT -> true
        else -> context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
            Configuration.UI_MODE_NIGHT_YES
    }

    fun palette(context: Context, mode: String): KhakiPalette =
        if (dark(context, mode)) KhakiPalette(
            true, Color.rgb(29, 34, 27), Color.rgb(46, 54, 43),
            Color.rgb(56, 65, 50), Color.rgb(236, 240, 225),
            Color.rgb(185, 195, 177), Color.rgb(169, 185, 119),
            Color.rgb(29, 36, 23), Color.rgb(86, 99, 78),
            Color.rgb(64, 75, 51), Color.rgb(83, 93, 77)
        ) else KhakiPalette(
            false, Color.rgb(233, 231, 216), Color.rgb(249, 248, 241),
            Color.WHITE, Color.rgb(43, 51, 38),
            Color.rgb(101, 112, 94), Color.rgb(105, 121, 76),
            Color.WHITE, Color.rgb(207, 213, 192),
            Color.rgb(224, 230, 208), Color.rgb(206, 211, 197)
        )
}
