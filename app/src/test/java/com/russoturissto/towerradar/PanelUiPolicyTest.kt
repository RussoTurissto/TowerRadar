package com.russoturissto.towerradar

import org.junit.Assert.*
import org.junit.Test

class PanelUiPolicyTest {
    @Test fun outsideTapCollapsesExpandedPanel() {
        assertTrue(PanelUiPolicy.shouldCollapseOnOutsideTouch(
            expanded = true, fullscreen = false, choosingLocation = false, isWithinPanel = false))
    }

    @Test fun tappingInsideExpandedPanelDoesNotCollapseIt() {
        assertFalse(PanelUiPolicy.shouldCollapseOnOutsideTouch(
            expanded = true, fullscreen = false, choosingLocation = false, isWithinPanel = true))
    }

    @Test fun outsideTapLeavesCompactSheetAlone() {
        assertFalse(PanelUiPolicy.shouldCollapseOnOutsideTouch(
            expanded = false, fullscreen = false, choosingLocation = false, isWithinPanel = false))
    }

    @Test fun crosshairAndFullscreenModesCannotBeInterruptedBySheetDismissal() {
        assertFalse(PanelUiPolicy.shouldCollapseOnOutsideTouch(
            expanded = true, fullscreen = false, choosingLocation = true, isWithinPanel = false))
        assertFalse(PanelUiPolicy.shouldCollapseOnOutsideTouch(
            expanded = true, fullscreen = true, choosingLocation = false, isWithinPanel = false))
    }

    @Test fun successfulYandexVerificationHidesConnectButton() {
        assertEquals(PanelUiPolicy.YandexAction.HIDDEN,
            PanelUiPolicy.yandexAction(hasKey = true, checking = false, checkResult = true))
    }

    @Test fun missingKeyMustShowConnectAction() {
        assertEquals(PanelUiPolicy.YandexAction.CONNECT,
            PanelUiPolicy.yandexAction(hasKey = false, checking = false, checkResult = null))
    }

    @Test fun savedKeyIsNotEquivalentToVerifiedIntegration() {
        assertEquals(PanelUiPolicy.YandexAction.VERIFY,
            PanelUiPolicy.yandexAction(hasKey = true, checking = false, checkResult = null))
    }

    @Test fun failedVerificationOffersRepairAndCheckingShowsWait() {
        assertEquals(PanelUiPolicy.YandexAction.FIX,
            PanelUiPolicy.yandexAction(hasKey = true, checking = false, checkResult = false))
        assertEquals(PanelUiPolicy.YandexAction.WAIT,
            PanelUiPolicy.yandexAction(hasKey = true, checking = true, checkResult = false))
    }
}
