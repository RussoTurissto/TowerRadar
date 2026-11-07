package com.russoturissto.towerradar

/** MapLibre camera bearing is clockwise; north appears counter-clockwise on screen. */
object NorthDirectionModel {
    fun arrowRotation(cameraBearing: Double): Float {
        if (!cameraBearing.isFinite()) return 0f
        val normalized = ((-cameraBearing % 360.0) + 360.0) % 360.0
        return normalized.toFloat()
    }
}
