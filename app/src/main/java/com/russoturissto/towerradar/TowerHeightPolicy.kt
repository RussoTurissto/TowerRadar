package com.russoturissto.towerradar

/** ITU-R representative height is only an example scenario, never a measured mast height. */
object TowerHeightPolicy {
    data class Height(val metres: Double, val measured: Boolean)
    fun resolve(rawHeight: String?, assumedMetres: Int = 30): Height {
        require(assumedMetres in 5..100)
        val number = rawHeight?.trim()?.replace(',', '.')?.let {
            Regex("^([0-9]+(?:\\.[0-9]+)?)\\s*(?:м|m)?$", RegexOption.IGNORE_CASE)
                .matchEntire(it)?.groupValues?.getOrNull(1)?.toDoubleOrNull()
        }?.takeIf { it in 1.0..500.0 }
        return if (number != null) Height(number, true)
            else Height(assumedMetres.toDouble(), false)
    }
}
