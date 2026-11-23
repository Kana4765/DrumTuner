package com.drumtuner.app.tuning

import kotlin.math.abs

sealed interface FilterResult {
    /** More hits needed; [hitsRequired] is 2 or 3. */
    data class NeedMore(val hitsSoFar: Int, val hitsRequired: Int) : FilterResult

    /** Reading locked for this lug. */
    data class Locked(val valueHz: Double, val usedMedian: Boolean) : FilterResult
}

/**
 * Smart 2-to-3 hit filter.
 *  - Hit 1 and Hit 2 within ±0.5 Hz → lock their average.
 *  - Otherwise ask for Hit 3 and lock the median of all three, which rejects a single
 *    rimshot or a wild overtone spike.
 */
object HitMedianFilter {
    const val TOLERANCE_HZ = 0.5

    fun evaluate(hits: List<Double>, toleranceHz: Double = TOLERANCE_HZ): FilterResult = when {
        hits.isEmpty() -> FilterResult.NeedMore(0, 2)
        hits.size == 1 -> FilterResult.NeedMore(1, 2)
        hits.size == 2 -> {
            val (a, b) = hits
            if (abs(a - b) <= toleranceHz) FilterResult.Locked((a + b) / 2.0, usedMedian = false)
            else FilterResult.NeedMore(2, 3)
        }
        else -> FilterResult.Locked(median3(hits[0], hits[1], hits[2]), usedMedian = true)
    }

    fun median3(a: Double, b: Double, c: Double): Double = maxOf(minOf(a, b), minOf(maxOf(a, b), c))

    /** Progress dots, e.g. "● ○", "● ● ○", "● ● ●". */
    fun dots(hits: List<Double>, toleranceHz: Double = TOLERANCE_HZ): String {
        val required = when (val r = evaluate(hits, toleranceHz)) {
            is FilterResult.NeedMore -> r.hitsRequired
            is FilterResult.Locked -> minOf(hits.size, 3)
        }
        val filled = minOf(hits.size, required)
        return List(required) { if (it < filled) "●" else "○" }.joinToString(" ")
    }
}
