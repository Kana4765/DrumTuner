package com.drumtuner.app.tuning

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class StarPatternTest {

    @Test
    fun exactSequences() {
        assertEquals(listOf(1, 3, 2, 4), StarPattern.sequence(4))
        assertEquals(listOf(1, 4, 2, 5, 3, 6), StarPattern.sequence(6))
        assertEquals(listOf(1, 5, 3, 7, 2, 6, 4, 8), StarPattern.sequence(8))
        assertEquals(listOf(1, 6, 3, 8, 5, 10, 2, 7, 4, 9), StarPattern.sequence(10))
        assertEquals(listOf(1, 7, 4, 10, 2, 8, 5, 11, 3, 9, 6, 12), StarPattern.sequence(12))
    }

    @Test
    fun everySequenceVisitsEachLugOnce() {
        for (n in StarPattern.SUPPORTED_LUG_COUNTS) {
            assertEquals((1..n).toList(), StarPattern.sequence(n).sorted())
        }
        assertEquals(listOf(4, 6, 8, 10, 12), StarPattern.SUPPORTED_LUG_COUNTS)
    }

    @Test
    fun consecutiveStepsCrossTheHead() {
        // Each move goes at least a quarter of the way around the rim (no neighbour-to-neighbour).
        for (n in StarPattern.SUPPORTED_LUG_COUNTS) {
            val seq = StarPattern.sequence(n)
            for (i in 0 until seq.size - 1) {
                val d = Math.floorMod(seq[i + 1] - seq[i], n)
                val dist = minOf(d, n - d)
                assertTrue("$n lugs: ${seq[i]}→${seq[i + 1]}", dist >= n / 4)
            }
        }
    }

    @Test
    fun autoAdvanceFollowsStarAndSkipsLocked() {
        val locked = mutableSetOf<Int>()
        val order = mutableListOf(1)
        var current = 1
        locked += 1
        while (true) {
            val next = StarPattern.nextLug(8, current) { it in locked } ?: break
            order += next
            locked += next
            current = next
        }
        assertEquals(StarPattern.sequence(8), order)
    }

    @Test
    fun manualReMeasureContinuesFromThatLug() {
        // Lugs 1,5,3 locked; user manually re-measured lug 5 → next unlocked after 5 is 7
        val locked = setOf(1, 5, 3)
        assertEquals(7, StarPattern.nextLug(8, 5) { it in locked })
        // Wraps around: after lug 8 (last in the sequence) it restarts at 1 → 5 → 3 (locked) → 7
        assertEquals(7, StarPattern.nextLug(8, 8) { it in locked })
    }

    @Test
    fun allLockedReturnsNull() {
        assertNull(StarPattern.nextLug(4, 1) { true })
    }

    @Test
    fun unsupportedCountThrows() {
        assertThrows(IllegalArgumentException::class.java) { StarPattern.sequence(7) }
    }
}
