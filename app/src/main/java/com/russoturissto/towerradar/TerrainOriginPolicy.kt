package com.russoturissto.towerradar

/** The elevation graph accepts either a deliberate manual origin or a fresh GPS fix. */
object TerrainOriginPolicy {
    data class Origin(val latitude: Double, val longitude: Double, val manual: Boolean)

    fun select(manual: Pair<Double, Double>?, freshGps: Pair<Double, Double>?): Origin? {
        fun valid(p: Pair<Double, Double>?) = p?.takeIf {
            it.first.isFinite() && it.second.isFinite() &&
                it.first in -90.0..90.0 && it.second in -180.0..180.0
        }
        val chosenManual = valid(manual)
        if (chosenManual != null) return Origin(chosenManual.first, chosenManual.second, true)
        val gps = valid(freshGps) ?: return null
        return Origin(gps.first, gps.second, false)
    }
}
