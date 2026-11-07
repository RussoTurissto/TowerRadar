package com.russoturissto.towerradar

/** Keep map credits visible without additional floating shortcut badges. */
object AttributionUiPolicy {
    /** Only providers whose map credits can link to their website. */
    fun creditLink(providerId: String): String? = when {
        providerId == "yandex" -> "https://yandex.ru/maps/"
        providerId.startsWith("google-") -> "https://www.google.com/maps"
        else -> null
    }
}
