package com.russoturissto.towerradar

/** Pure geometry used by the top header to avoid camera notches. */
object HeaderPanelGeometry {
    data class Slots(
        val leading: DisplayModePolicy.Segment?,
        val trailing: DisplayModePolicy.Segment?
    )

    fun allocate(width: Int, cutouts: List<IntRange>, margin: Int,
                 preferred: Int, gap: Int): Slots {
        val segments = DisplayModePolicy.topSegments(width, cutouts, margin)
            .filter { it.width > 0 }
        if (segments.isEmpty()) return Slots(null, null)
        val eligible = segments.filter { it.width >= preferred / 2 }
        // Select actual usable regions: tiny corner slots are intentionally skipped.
        if (eligible.size >= 2) {
            return Slots(eligible.first(), eligible.last())
        }
        val largest = segments.maxByOrNull { it.width } ?: return Slots(null, null)
        if (largest.width < preferred * 2 + gap) return Slots(largest, null)
        val leftWidth = (largest.width - gap) / 2
        return Slots(
            DisplayModePolicy.Segment(largest.start, largest.start + leftWidth),
            DisplayModePolicy.Segment(largest.start + leftWidth + gap, largest.endExclusive)
        )
    }
}
