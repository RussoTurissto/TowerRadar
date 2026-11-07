package com.russoturissto.towerradar

import org.junit.Assert.*
import org.junit.Test

class ApiMapConfigurationTest {
    @Test fun googleHasThreeOfficialSessionBasedModes() {
        val modes = MapSources.providers.filter { it.id.startsWith("google-") }
        assertEquals(3, modes.size)
        assertEquals(setOf("google-road", "google-satellite", "google-hybrid"),
            modes.map { it.id }.toSet())
        assertTrue(modes.all { it.keyPref == "map_key_google" })
        assertTrue(modes.all { it.tiles.startsWith("https://tile.googleapis.com/v1/2dtiles/") })
    }

    @Test fun googleNeverLoadsTilesWithoutASession() {
        val provider = MapSources.providers.first { it.id == "google-road" }
        try {
            MapSources.tileTemplate(provider, "example-key")
            fail("Google tiles cannot be requested without an official session")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message.orEmpty().contains("сеанс"))
        }
    }

    @Test fun googleSessionAndKeyAreEscapedForUrlTemplate() {
        val provider = MapSources.providers.first { it.id == "google-hybrid" }
        val url = MapSources.tileTemplate(provider, "a+b/1", "s%1")
        assertTrue(url.startsWith("https://tile.googleapis.com/v1/2dtiles/"))
        assertTrue(url.contains("key=a%2Bb%2F1"))
        assertTrue(url.contains("session=s%251"))
        assertFalse(url.contains("{api_key}"))
    }

    @Test fun yandexStillUsesProperWebMercatorTiles() {
        val provider = MapSources.providers.first { it.id == "yandex" }
        val url = MapSources.sampleTileUrl(provider, 55.75, 37.62, "example-key")
        assertTrue(url.contains("projection=web_mercator"))
        assertTrue(url.contains("lang=ru_RU"))
        assertTrue(url.contains("apikey=example-key"))
    }

    @Test fun yandexHighDensityTileKeepsProperWebMercatorProjection() {
        val provider = MapSources.providers.first { it.id == "yandex" }
        val url = MapSources.sampleTileUrl(provider, 55.75, 37.62, "test-key")
        assertTrue(url.contains("scale=2"))
        assertTrue(url.contains("projection=web_mercator"))
        assertTrue(url.contains("lang=ru_RU"))
    }

    @Test fun cacheOnlyProvidersWithKnownTemporaryStoragePermission() {
        assertTrue(MapTileCache.diskCacheAllowed(
            MapSources.providers.first { it.id == "yandex" }))
        assertTrue(mapCacheCheck("osm"))
        assertFalse(MapTileCache.diskCacheAllowed(
            MapSources.providers.first { it.id == "google-road" }))
        assertFalse(MapTileCache.diskCacheAllowed(
            MapSources.providers.first { it.id == "google-hybrid" }))
        assertFalse(MapTileCache.diskCacheAllowed(
            MapSources.providers.first { it.id == "2gis" }))
    }

    private fun MapCacheCheck(id: String): Boolean =
        MapTileCache.diskCacheAllowed(MapSources.providers.first { it.id == id })

    @Test fun expiredGoogleSessionDoesNotCountAsValid() {
        val expired = GoogleTileSession("example-session", 1L)
        assertFalse(expired.valid())
        assertFalse(GoogleTileSession("", Long.MAX_VALUE).valid())
        assertTrue(GoogleTileSession("example-session", Long.MAX_VALUE).valid())
    }

    @Test fun checkingKeyIsNotProofOfSuccessfulIntegration() {
        assertFalse(ApiProbe(false, "Ключ сохранён").connected)
        assertTrue(ApiProbe(true, "Тайл получен", 200).connected)
    }
}
