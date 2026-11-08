package com.russoturissto.towerradar

/**
 * Pure bottom-up stacking of floating map chrome.
 *
 * Every value is recalculated from the CURRENT obstacle geometry, never from
 * the previous floating bar margin. Therefore opening AND closing a sheet or
 * tower card work symmetrically, including during height animations.
 */
object FloatingOverlayPolicy {
    data class Positions(val creditsBottom: Int, val progressBottom: Int)

    fun positions(
        navigationClearance: Int,
        sheetClearance: Int,
        towerCardClearance: Int,
        creditsHeight: Int,
        gap: Int,
        creditsToProgressGap: Int
    ): Positions {
        require(navigationClearance >= 0 && sheetClearance >= 0 &&
            towerCardClearance >= 0 && creditsHeight >= 0 &&
            gap >= 0 && creditsToProgressGap >= 0)
        val highestObstacle = maxOf(navigationClearance, sheetClearance, towerCardClearance)
        val creditsBottom = highestObstacle + gap
        return Positions(creditsBottom,
            creditsBottom + creditsHeight + creditsToProgressGap)
    }
}
