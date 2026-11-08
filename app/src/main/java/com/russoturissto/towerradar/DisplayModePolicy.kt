package com.russoturissto.towerradar

/**
 * Pure geometry and saved display preferences. No hard-coded phone, screen or camera offsets.
 * Segments exclude all cutout bounds plus a safety gap on either side.
 */
object DisplayModePolicy {
    const val STANDARD = "standard"
    const val FULLSCREEN = "fullscreen"

    fun normalize(raw: String?): String = if (raw == FULLSCREEN) FULLSCREEN else STANDARD

    data class Segment(val start: Int, val endExclusive: Int) {
        val width: Int get() = endExclusive - start
    }

    fun topSegments(width: Int, cutouts: List<IntRange>, edge: Int): List<Segment> {
        if (width <= 0) return emptyList()
        val margin = edge.coerceAtLeast(0)
        val end = (width - margin).coerceAtLeast(margin)
        var next = margin
        val result = ArrayList<Segment>()
        for (cutout in cutouts.sortedBy { it.first }) {
            val left = (cutout.first - margin).coerceIn(margin, end)
            val right = (cutout.last + margin).coerceIn(margin, end)
            if (left > next) result.add(Segment(next, left))
            next = maxOf(next, right)
        }
        if (end > next) result.add(Segment(next, end))
        return result
    }
}
