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
    @Test fun unknownVersionIsHonest() {
        assertFalse(ChangeLog.forVersion("9.99.0").contains("30 км"))
    }
}
