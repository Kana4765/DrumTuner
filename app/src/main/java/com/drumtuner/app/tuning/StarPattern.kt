package com.drumtuner.app.tuning

/** True star (cross) tightening sequences, 1-based lug numbers. */
object StarPattern {
    private val PATTERNS: Map<Int, List<Int>> = mapOf(
        4 to listOf(1, 3, 2, 4),
        6 to listOf(1, 4, 2, 5, 3, 6),
        8 to listOf(1, 5, 3, 7, 2, 6, 4, 8),
        10 to listOf(1, 6, 3, 8, 5, 10, 2, 7, 4, 9),
        12 to listOf(1, 7, 4, 10, 2, 8, 5, 11, 3, 9, 6, 12),
    )

    val SUPPORTED_LUG_COUNTS: List<Int> = PATTERNS.keys.sorted()

    fun sequence(lugCount: Int): List<Int> =
        PATTERNS[lugCount] ?: throw IllegalArgumentException("Unsupported lug count: $lugCount")

    /**
     * Next lug to measure after [current]: walks the star sequence (wrapping) and returns the
     * first lug that is not yet locked. Returns null when every lug is locked.
     */
    fun nextLug(lugCount: Int, current: Int, isLocked: (Int) -> Boolean): Int? {
        val seq = sequence(lugCount)
        val start = seq.indexOf(current).let { if (it < 0) -1 else it }
        for (step in 1..seq.size) {
            val lug = seq[Math.floorMod(start + step, seq.size)]
            if (!isLocked(lug)) return lug
        }
        return null
    }
}
