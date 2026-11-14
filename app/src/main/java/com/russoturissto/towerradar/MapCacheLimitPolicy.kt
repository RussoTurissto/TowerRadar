package com.russoturissto.towerradar

/** -1: no application-defined size cap; the device and provider retention rules still apply. */
object MapCacheLimitPolicy {
    const val UNLIMITED_MB = -1
    val optionsMb = intArrayOf(100, 250, 500, UNLIMITED_MB)

    fun valid(mb: Int) = mb in optionsMb
    fun maxBytes(mb: Int): Long =
        if (mb == UNLIMITED_MB) Long.MAX_VALUE
        else mb.toLong() * 1024L * 1024L
    fun label(mb: Int): String =
        if (mb == UNLIMITED_MB) "Без ограничений" else "$mb МБ"
}
