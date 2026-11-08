package com.russoturissto.towerradar

/** Decide text from actual available pixels; never move the dBm to a third row. */
object StatusTextPolicy {
    fun network(type: String, operator: String?, dbm: Int?, widthPx: Float,
                measure: (String) -> Float): String {
        val power = dbm?.let { "$it dBm" }
        val full = listOfNotNull(type, operator?.takeIf { it.isNotBlank() }, power)
            .joinToString(" · ")
        if (widthPx <= 0f || measure(full) <= widthPx) return full
        val noOperator = listOfNotNull(type, power).joinToString(" · ")
        if (measure(noOperator) <= widthPx) return noOperator
        // On a narrow strip, measured power is more useful than the operator name.
        if (power != null && measure(power) <= widthPx) return power
        return type
    }
}
