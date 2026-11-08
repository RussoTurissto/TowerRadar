package com.russoturissto.towerradar

/** Validates persisted map viewport independently of GPS or selected search origin. */
object LastMapCameraModel {
    fun valid(lat: Double, lon: Double, zoom: Double, bearing: Double, tilt: Double): Boolean =
        lat.isFinite() && lat in -85.05112878..85.05112878 &&
        lon.isFinite() && lon in -180.0..180.0 &&
        zoom.isFinite() && zoom in 0.0..22.0 &&
        bearing.isFinite() && tilt.isFinite() && tilt in 0.0..60.0
}
