package com.russoturissto.towerradar

/**
 * MapLibre raster tiles are 256 physical pixels. A Mapsforge DisplayModel
 * normally scales text and symbols by Android display density, which is not
 * appropriate for those fixed-size tile images.
 */
internal object MapsforgeRenderPolicy {
    fun userScaleForDevice(deviceScale: Float): Float =
        if (deviceScale.isFinite() && deviceScale > 0f) 1f / deviceScale else 1f
}
