package com.russoturissto.towerradar

import org.junit.Assert.*
import org.junit.Test

class StatusTextPolicyTest {
    private val measure: (String) -> Float = { it.length * 8f }

    @Test fun fullTextContainsNetworkOperatorAndDbmInOneLine() {
        assertEquals("3G · MIRANDA · -120 dBm",
            StatusTextPolicy.network("3G", "MIRANDA", -120, 300f, measure))
    }

    @Test fun removingOperatorPreservesPowerWhenWindowGetsNarrow() {
        assertEquals("3G · -120 dBm",
            StatusTextPolicy.network("3G", "MIRANDA", -120, 120f, measure))
    }

    @Test fun narrowViewStillDisplaysKnownDbm() {
        assertEquals("-120 dBm",
            StatusTextPolicy.network("3G", "MIRANDA", -120, 75f, measure))
    }

    @Test fun unavailableModemDoesNotInventSignal() {
        assertEquals("Сеть",
            StatusTextPolicy.network("Сеть", null, null, 160f, measure))
    }

    @Test fun initialUnknownWidthKeepsAllInformation() {
        assertEquals("4G LTE · T2 · -94 dBm",
            StatusTextPolicy.network("4G LTE", "T2", -94, 0f, measure))
    }
}
