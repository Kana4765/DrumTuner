package com.drumtuner.app.tuning

import com.drumtuner.app.dsp.PitchMath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class DrumAcousticsTest {

    private val shells = DrumAcoustics.defaultShells()

    @Test
    fun tomsDescendByMajorThirdOrPerfectFourthInKey() {
        for (key in BandKey.values()) {
            val t = DrumAcoustics.calculateTargets(shells, key, TuningStyle.PUNCHY)
            val tom1 = t.getValue(DrumType.TOM1)
            val tom2 = t.getValue(DrumType.TOM2)
            val floor = t.getValue(DrumType.FLOOR)
            assertTrue("$key", tom1.noteMidi > tom2.noteMidi && tom2.noteMidi > floor.noteMidi)
            for (target in t.values) assertTrue("$key ${target.drum} in key", key.contains(target.noteMidi))
            val gaps = listOf(tom1.noteMidi - tom2.noteMidi, tom2.noteMidi - floor.noteMidi)
            for (g in gaps) assertTrue("$key gap $g", g == 4 || g == 5)
            assertTrue(tom2.intervalFromAbove == "M3" || tom2.intervalFromAbove == "P4")
        }
    }

    @Test
    fun targetsStayInsideShellSafeRanges() {
        for (key in BandKey.values()) {
            val t = DrumAcoustics.calculateTargets(shells, key, TuningStyle.MAX_SUSTAIN)
            for (shell in shells) {
                val f0 = PitchMath.midiToHz(t.getValue(shell.drum).noteMidi)
                val r = DrumAcoustics.shellRange(shell)
                assertTrue("$key ${shell.drum} $f0 in ${r}", f0 in r)
            }
        }
    }

    @Test
    fun lugTargetsFallInsideDetectionBands() {
        for (key in BandKey.values()) for (style in TuningStyle.values()) {
            val t = DrumAcoustics.calculateTargets(shells, key, style)
            for (target in t.values) {
                for (side in HeadSide.values()) {
                    val hz = target.lugTarget(side)
                    assertTrue("$key $style ${target.drum} $side $hz", hz in DetectionBands.lugBand(target.drum, side))
                }
            }
        }
    }

    @Test
    fun punchyResonantIs12PercentAboveBatterAndSustainIsEqual() {
        val (b, r) = DrumAcoustics.lugTargets(150.0, HeadPly.ONE_PLY_10, TuningStyle.PUNCHY)
        assertEquals(1.12, r / b, 1e-12)
        // Mean of both heads stays at fundamental × 1.593
        assertEquals(150.0 * DrumAcoustics.F11_OVER_F01, (b + r) / 2, 1e-9)
        val (b2, r2) = DrumAcoustics.lugTargets(150.0, HeadPly.ONE_PLY_10, TuningStyle.MAX_SUSTAIN)
        assertEquals(b2, r2, 0.0)
        assertEquals(150.0 * 1.593, b2, 1e-9)
    }

    @Test
    fun twoPlyHeadsTargetSlightlyLower() {
        val (one, _) = DrumAcoustics.lugTargets(150.0, HeadPly.ONE_PLY_10, TuningStyle.PUNCHY)
        val (two, _) = DrumAcoustics.lugTargets(150.0, HeadPly.TWO_PLY_14, TuningStyle.PUNCHY)
        assertTrue(two < one)
        assertTrue(two > one * 0.95)
    }

    @Test
    fun buzzWarningWhenRackTomSitsOnRootOrFifth() {
        // E2 = 82.4 Hz, E3 = 164.8 Hz: a rack tom on E3 must warn, and 6 Hz away must not.
        val e3 = PitchMath.midiToHz(52)
        val w = DrumAcoustics.buzzWarning(DrumType.TOM2, e3 + 1.0, 400.0, BandKey.E_MINOR)
        assertNotNull(w)
        assertEquals("E (root)", w!!.conflictLabel)
        assertEquals("fundamental", w.targetKind)
        assertNull(DrumAcoustics.buzzWarning(DrumType.TOM2, e3 + 6.0, 400.0, BandKey.E_MINOR))
        // B is the fifth of E
        val b3 = PitchMath.midiToHz(59)
        assertEquals("B (fifth)", DrumAcoustics.buzzWarning(DrumType.SNARE, 300.0, b3 - 2.0, BandKey.E_MINOR)!!.conflictLabel)
        // Kick and floor tom never warn
        assertNull(DrumAcoustics.buzzWarning(DrumType.KICK, e3, e3, BandKey.E_MINOR))
        assertNull(DrumAcoustics.buzzWarning(DrumType.FLOOR, e3, e3, BandKey.E_MINOR))
    }

    @Test
    fun nudgeMovesTargetAndClearsWarning() {
        // Find a key where some rack tom or the snare warns, then nudge it by +6 Hz.
        var tested = false
        for (key in BandKey.values()) {
            val t = DrumAcoustics.calculateTargets(shells, key, TuningStyle.PUNCHY)
            val warned = t.values.firstOrNull { it.buzzWarning != null } ?: continue
            val nudged = shells.map { if (it.drum == warned.drum) it.copy(nudgeHz = DrumAcoustics.NUDGE_STEP_HZ) else it }
            val t2 = DrumAcoustics.calculateTargets(nudged, key, TuningStyle.PUNCHY).getValue(warned.drum)
            assertEquals(warned.fundamentalHz + 6.0, t2.fundamentalHz, 1e-9)
            val w2 = t2.buzzWarning
            assertTrue(w2 == null || abs(w2.conflictHz - warned.buzzWarning!!.conflictHz) > 1e-9)
            tested = true
        }
        assertTrue("expected at least one key with a buzz warning", tested)
    }

    @Test
    fun largerShellsHaveLowerRanges() {
        val r10 = DrumAcoustics.shellRange(Shell(DrumType.TOM1, 10.0, 7.0, lugCount = 6))
        val r12 = DrumAcoustics.shellRange(Shell(DrumType.TOM2, 12.0, 8.0, lugCount = 6))
        val r16 = DrumAcoustics.shellRange(Shell(DrumType.FLOOR, 16.0, 14.0, lugCount = 8))
        assertTrue(r10.lowHz > r12.lowHz && r12.lowHz > r16.lowHz)
        // Deeper shell sits slightly lower
        val shallow = DrumAcoustics.shellRange(Shell(DrumType.TOM1, 12.0, 7.0, lugCount = 6))
        val deep = DrumAcoustics.shellRange(Shell(DrumType.TOM1, 12.0, 10.0, lugCount = 6))
        assertTrue(deep.highHz < shallow.highHz)
    }

    @Test
    fun disabledShellsGetNoTarget() {
        val noTom2 = shells.map { if (it.drum == DrumType.TOM2) it.copy(enabled = false) else it }
        val t = DrumAcoustics.calculateTargets(noTom2, BandKey.D_MINOR, TuningStyle.PUNCHY)
        assertNull(t[DrumType.TOM2])
        assertTrue(t.getValue(DrumType.TOM1).noteMidi > t.getValue(DrumType.FLOOR).noteMidi)
    }

    @Test
    fun trackingBandNarrowsAroundTarget() {
        val b = DetectionBands.trackingBand(DetectionBands.RACK_TOM, 250.0)
        assertTrue(b.lowHz >= 250.0 / 1.3 - 1e-9 && b.highHz <= 250.0 * 1.3 + 1e-9)
        assertEquals(DetectionBands.RACK_TOM, DetectionBands.trackingBand(DetectionBands.RACK_TOM, null))
    }
}
