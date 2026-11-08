package com.russoturissto.towerradar

import org.junit.Assert.*
import org.junit.Test

class AttributionUiPolicyTest {
    @Test fun yandexAttributionRetainsRequiredProviderLink() {
        assertEquals("https://yandex.ru/legal/maps_api/",
            AttributionUiPolicy.creditLink("yandex"))
        for (id in listOf("yandex-js-road", "yandex-js-satellite", "yandex-js-hybrid"))
            assertEquals("https://yandex.ru/legal/maps_api/", AttributionUiPolicy.creditLink(id))
    }

    @Test fun osmSourcesDoNotCreateStrayYandexLinks() {
        for (id in listOf("auto", "osm", "osm-de", "osm-hot", "2gis")) {
            assertNull("Unexpected link on $id", AttributionUiPolicy.creditLink(id))
        }
    }

    @Test fun mapTilerShowsRequiredLicensingLink() {
        for (id in listOf("maptiler-streets", "maptiler-satellite", "maptiler-hybrid"))
            assertEquals("https://www.maptiler.com/copyright/",
                AttributionUiPolicy.creditLink(id))
    }

    @Test fun googleMapProvidersKeepProviderAttribution() {
        for (id in listOf("google-road", "google-satellite", "google-hybrid")) {
            assertEquals("https://www.google.com/maps", AttributionUiPolicy.creditLink(id))
        }
    }
}
