package com.drumtuner.app.tuning

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HitMedianFilterTest {

    @Test
    fun firstHitAsksForSecond() {
        assertEquals(FilterResult.NeedMore(0, 2), HitMedianFilter.evaluate(emptyList()))
        assertEquals(FilterResult.NeedMore(1, 2), HitMedianFilter.evaluate(listOf(182.0)))
    }

    @Test
    fun twoCloseHitsLockTheirAverage() {
        val r = HitMedianFilter.evaluate(listOf(182.2, 182.6))
        assertTrue(r is FilterResult.Locked)
        r as FilterResult.Locked
        assertEquals(182.4, r.valueHz, 1e-9)
        assertEquals(false, r.usedMedian)
    }

    @Test
    fun exactlyHalfHzApartStillLocks() {
        assertTrue(HitMedianFilter.evaluate(listOf(100.0, 100.5)) is FilterResult.Locked)
    }

    @Test
    fun disagreeingHitsRequestThird() {
        assertEquals(FilterResult.NeedMore(2, 3), HitMedianFilter.evaluate(listOf(182.4, 195.0)))
    }

    @Test
    fun medianRejectsRimshotOutlier() {
        // Hit 2 is a rimshot spike; median of the three ignores it
        val high = HitMedianFilter.evaluate(listOf(182.4, 211.7, 182.9)) as FilterResult.Locked
        assertEquals(182.9, high.valueHz, 1e-9)
        assertTrue(high.usedMedian)
        // Outlier on the first hit, low side
        val low = HitMedianFilter.evaluate(listOf(150.1, 182.5, 182.1)) as FilterResult.Locked
        assertEquals(182.1, low.valueHz, 1e-9)
    }

    @Test
    fun median3AllOrderings() {
        val values = listOf(1.0, 2.0, 3.0)
        for (a in values) for (b in values) for (c in values) {
            if (setOf(a, b, c).size == 3) assertEquals(2.0, HitMedianFilter.median3(a, b, c), 0.0)
        }
    }

    @Test
    fun progressDots() {
        assertEquals("○ ○", HitMedianFilter.dots(emptyList()))
        assertEquals("● ○", HitMedianFilter.dots(listOf(180.0)))
        assertEquals("● ●", HitMedianFilter.dots(listOf(180.0, 180.2)))
        assertEquals("● ● ○", HitMedianFilter.dots(listOf(180.0, 190.0)))
        assertEquals("● ● ●", HitMedianFilter.dots(listOf(180.0, 190.0, 180.3)))
    }
}
