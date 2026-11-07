package com.russoturissto.towerradar

/**
 * Pure interaction rules, shared by the map screen and Android unit tests.
 */
object PanelUiPolicy {
    /** A touch outside the expanded sheet only dismisses it; no map action. */
    fun shouldCollapseOnOutsideTouch(
        expanded: Boolean,
        fullscreen: Boolean,
        choosingLocation: Boolean,
        isWithinPanel: Boolean
    ): Boolean = expanded && !fullscreen && !choosingLocation && !isWithinPanel

    enum class YandexAction { HIDDEN, CONNECT, VERIFY, FIX, WAIT }

    /** A saved API key is not sufficient proof of a working integration. */
    fun yandexAction(
        hasKey: Boolean,
        checking: Boolean,
        checkResult: Boolean?
    ): YandexAction = when {
        !hasKey -> YandexAction.CONNECT
        checking -> YandexAction.WAIT
        checkResult == true -> YandexAction.HIDDEN
        checkResult == false -> YandexAction.FIX
        else -> YandexAction.VERIFY
    }
}
