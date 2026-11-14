package com.russoturissto.towerradar

import org.junit.Assert.*
import org.junit.Test

class UserTowerStoreTest {
    private fun draft(lat: Double = 50.0, lon: Double = 30.0,
                      name: String = "Тестовая вышка") =
        UserTowerDraft(lat, lon, name, null, null, 30.0, null, null)

    @Test fun acceptsValidCoordinates() {
        assertNull(UserTowerStore.validate(draft()))
        assertNull(UserTowerStore.validate(draft(-90.0, 180.0)))
    }
    @Test fun rejectsInvalidCoordinates() {
        assertNotNull(UserTowerStore.validate(draft(lat = 91.0)))
        assertNotNull(UserTowerStore.validate(draft(lon = Double.NaN)))
        assertNotNull(UserTowerStore.validate(draft(lon = -181.0)))
    }
    @Test fun requiresNameAndPlausibleHeight() {
        assertNotNull(UserTowerStore.validate(draft(name = "")))
        assertNotNull(UserTowerStore.validate(draft().copy(heightMeters = -3.0)))
        assertNotNull(UserTowerStore.validate(draft().copy(heightMeters = 1200.0)))
    }
}
