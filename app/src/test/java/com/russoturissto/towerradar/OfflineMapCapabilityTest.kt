package com.russoturissto.towerradar

import org.junit.Assert.*
import org.junit.Test

class OfflineMapCapabilityTest {
    @Test fun onlyOfficiallySupportedOpenFreeMapHasRegionalDownload() {
        assertTrue(OfflineMapPolicy.canCreateRegion("openfreemap-offline"))
        for (id in listOf("yandex", "yandex-js-road", "yandex-js-satellite",
                "yandex-js-hybrid", "maptiler-streets", "maptiler-satellite",
                "maptiler-hybrid", "osm", "osmfr", "google-road")) {
            assertFalse("Never download a substitute for $id", OfflineMapPolicy.canCreateRegion(id))
        }
    }

    @Test fun yandexTilesHaveTemporaryVisitedCacheNotRegionDownload() {
        assertEquals(OfflineMapCapability.VIEWED_TILE_CACHE,
            OfflineMapPolicy.capability("yandex"))
        assertEquals(OfflineMapCapability.NO_REGION_DOWNLOAD,
            OfflineMapPolicy.capability("yandex-js-satellite"))
    }
}
