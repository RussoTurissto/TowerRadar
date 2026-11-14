package com.russoturissto.towerradar

import org.junit.Assert.*
import org.junit.Test

class ChangeLogTest {
    @Test fun currentNotesAreBundledAndActionable() {
        val notes = ChangeLog.forVersion("0.27.0")
        assertTrue(notes.contains("тестовых APK"))
        assertTrue(notes.contains("Да / Нет"))
        assertTrue(notes.contains("30 км"))
    }
    @Test fun patchReleaseContainsAllVisibleChanges() {
        val notes = ChangeLog.forVersion("0.27.1")
        assertTrue(notes.contains("координаты", ignoreCase = true))
        assertTrue(notes.contains("Компас"))
        assertTrue(notes.contains("Без ограничений"))
    }
    @Test fun providerSwitchFixIsDocumented() {
        val notes = ChangeLog.forVersion("0.27.2")
        assertTrue(notes.contains("Яндекс"))
        assertTrue(notes.contains("не переключают"))
        assertTrue(notes.contains("временный кеш"))
    }
    @Test fun coordinateTapRegionFixIsDocumented() {
        val notes = ChangeLog.forVersion("0.27.3")
        assertTrue(notes.contains("текстом и значком"))
        assertTrue(notes.contains("не вызывают копирование"))
    }
    @Test fun unknownVersionIsHonest() {
        assertFalse(ChangeLog.forVersion("9.99.0").contains("30 км"))
    }
}
