package com.russoturissto.towerradar

import org.junit.Assert.*
import org.junit.Test

class TowerDataTest {
    @Test fun distanceSamePoint() {
        assertEquals(0.0, TowerData.distanceKm(52.37, 4.9, 52.37, 4.9), 0.000001)
    }
    @Test fun parisLondonDistance() {
        assertTrue(TowerData.distanceKm(51.5074, -0.1278, 48.8566, 2.3522) in 340.0..350.0)
    }
    @Test fun operatorIsNotInvented() {
        assertEquals("T2 / Tele2", TowerData.operator("Tele2"))
        assertEquals("Vodafone", TowerData.operator("Vodafone"))
        assertNull(TowerData.operator(" "))
    }
    @Test fun overpassUsesCorrectRadiusAndCoordinates() {
        assertTrue(TowerData.query(52.37, 4.9, 5).contains("around:5000,52.370000,4.900000"))
    }
    @Test fun broadenedOverpassQueryHasMobileAntennas() {
        val query = TowerData.query(49.28, 39.22, 20)
        assertTrue(query.contains("telecom"))
        assertTrue(query.contains("mast:type"))
        assertTrue(query.contains("communication:mobile_phone"))
    }
    @Test fun opencellIdResponseContainsEstimatedCells() {
        val response = """{"count":1,"cells":[{"lat":49.281,"lon":39.218,"mcc":250,"mnc":20,"lac":100,"cellid":12345,"radio":"LTE"}]}"""
        val sites = CellSources.parse(response, 49.28, 39.22)
        assertEquals(1, sites.size)
        assertEquals("OpenCellID · приблизительно", sites[0].source)
        assertEquals("LTE", sites[0].technology)
    }
    @Test fun osmObjectsAreDistinctAndDoNotInventOperators() {
        val response = """{"elements":[{"type":"node","id":1,"lat":49.281,"lon":39.218,"tags":{"man_made":"mast"}},{"type":"node","id":1,"lat":49.281,"lon":39.218,"tags":{}}]}"""
        val sites = TowerData.parse(response, 49.28, 39.22, 5)
        assertEquals(1, sites.size)
        assertNull(sites[0].operator)
    }
    @Test fun russianProvidersNeedKeys() {
        val yandex = MapSources.providers.first { it.id == "yandex" }
        val twoGis = MapSources.providers.first { it.id == "2gis" }
        assertTrue(MapSources.tileTemplate(yandex, "secret123").contains("lang=ru_RU"))
        assertTrue(MapSources.tileTemplate(yandex, "secret123").contains("projection=web_mercator"))
        assertTrue(MapSources.tileTemplate(twoGis, "test").contains("online_sd"))
        assertTrue(MapSources.tileTemplate(twoGis, "test").contains("key=test"))
        assertTrue(MapSources.unkeyed().all { it.keyPref == null })
    }

    @Test(expected = IllegalArgumentException::class)
    fun russianProvidersRejectMissingKey() {
        MapSources.tileTemplate(MapSources.providers.first { it.id == "yandex" })
    }

    @Test fun servingCellMatchesExactOpenCellIdIdentity() {
        val cell = ServingCell("T2", 1, "LTE", 250, 20, 100, 12345L,
            -85, -90, -10, 17f, 3)
        assertTrue(cell.validId)
        assertTrue(cell.validDbm)
        assertEquals("250/20/100/12345/LTE", cell.identity)
        val response = """{"cells":[{"lat":49.281,"lon":39.218,"mcc":250,"mnc":20,"lac":100,"cellid":12345,"radio":"LTE"}]}"""
        val result = CellSources.parse(response, 49.28, 39.22)
        assertEquals("opencellid/" + cell.identity, result.first().id)
    }

    @Test fun missingRadioIdentifiersMustNotDrawFalseServingLine() {
        val unavailable = ServingCell("", -1, "LTE", -1, -1, Int.MAX_VALUE,
            Long.MAX_VALUE, null, null, null, null, 0)
        assertFalse(unavailable.validId)
        assertFalse(unavailable.validDbm)
    }

    @Test(expected = IllegalArgumentException::class)
    fun radiusMustBeAllowed() {
        TowerData.query(52.37, 4.9, 200)
    }
}
