package com.russoturissto.towerradar

import org.junit.Assert.*
import org.junit.Test

class ViewedCacheAreaStoreTest {
    @Test fun selectedYandexAreaRemainsAProviderSpecificBookmark() {
        val bounds = OfflineBounds(49.0, 39.0, 49.1, 39.1)
        val text = ViewedCacheAreaStore.append("[]",
            ViewedCacheAreaStore.Area("yandex", bounds, 1000L))
        val loaded = ViewedCacheAreaStore.parse(text)
        assertEquals(1, loaded.size)
        assertEquals("yandex", loaded[0].providerId)
        assertEquals(bounds, loaded[0].bounds)
        assertEquals(1000L, loaded[0].markedAt)
        assertEquals(0, ViewedCacheAreaStore.parse(
            ViewedCacheAreaStore.remove(text, 0)).size)
    }
    @Test fun invalidAreasAreRejectedAndListIsBounded() {
        assertTrue(ViewedCacheAreaStore.parse("not json").isEmpty())
        var raw = "[]"
        repeat(25) { n ->
            raw = ViewedCacheAreaStore.append(raw,
                ViewedCacheAreaStore.Area("osm", OfflineBounds(1.0, 1.0, 1.1, 1.1), n.toLong()))
        }
        assertEquals(20, ViewedCacheAreaStore.parse(raw).size)
    }
}
