package com.russoturissto.towerradar

/** The map selected by the user is authoritative; a different map must never be cached in its name. */
enum class OfflineMapCapability { REGIONAL_DOWNLOAD, VIEWED_TILE_CACHE, NO_REGION_DOWNLOAD }

object OfflineMapPolicy {
    fun capability(providerId: String): OfflineMapCapability = when (providerId) {
        "openfreemap-offline" -> OfflineMapCapability.REGIONAL_DOWNLOAD
        "yandex", "osmfr", "osm", "osmde", "osmhot" ->
            OfflineMapCapability.VIEWED_TILE_CACHE
        else -> OfflineMapCapability.NO_REGION_DOWNLOAD
    }

    fun canCreateRegion(providerId: String): Boolean =
        capability(providerId) == OfflineMapCapability.REGIONAL_DOWNLOAD
}
