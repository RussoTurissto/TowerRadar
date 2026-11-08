package com.russoturissto.towerradar

/** Keep map credits visible without additional floating shortcut badges. */
object AttributionUiPolicy {
    /** Only providers whose map credits can link to their website. */
    fun creditLink(providerId: String): String? = when {
        providerId == "yandex" || providerId.startsWith("yandex-js-") ->
            "https://yandex.ru/legal/maps_api/"
        providerId.startsWith("maptiler-") -> "https://www.maptiler.com/copyright/"
        providerId.startsWith("google-") -> "https://www.google.com/maps"
        else -> null
    }
}
