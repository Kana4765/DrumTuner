package com.drumtuner.app.session

import com.drumtuner.app.tuning.DrumType
import com.drumtuner.app.tuning.HeadSide
import com.drumtuner.app.tuning.StarPattern
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TunerSessionTest {

    private var ids = 0
    private fun session() = TunerSession(emptyList(), null) { "id${ids++}" }
    private var hitId = 1L

    private fun TunerSession.hit(hz: Double?, sizzle: Double = 0.0) = onHit(hitId++, hz, sizzle)

    @Test
    fun walksTheStarPatternLockingEachLug() {
        val s = session()
        s.selectDrum(DrumType.TOM1) // 6 lugs in the default kit
        assertEquals(6, s.state.currentHead.lugCount)
        val visited = mutableListOf<Int>()
        repeat(6) {
            visited += s.state.currentHead.activeLug
            s.hit(180.0)
            s.hit(180.3)
        }
        assertEquals(StarPattern.sequence(6), visited)
        assertTrue(s.state.currentHead.complete)
        assertEquals("Head complete ✓", s.state.message)
    }

    @Test
    fun thirdHitMedianIsLocked() {
        val s = session()
        s.hit(200.0)
        s.hit(230.0)            // rimshot spike
        assertEquals(1, s.state.currentHead.activeLug)
        assertEquals("● ● ○", s.readout().dots)
        s.hit(200.4)
        assertEquals(200.4, s.state.currentHead.lug(1).lockedHz!!, 1e-9)
        assertEquals(StarPattern.sequence(10)[1], s.state.currentHead.activeLug) // snare: 10 lugs
    }

    @Test
    fun relativeModeComparesWithLug1() {
        val s = session()
        s.selectDrum(DrumType.TOM2)
        s.hit(180.0); s.hit(180.0)               // Lug 1 baseline
        assertEquals(TuneStatus.BASELINE, s.lugReadout(1).status)
        assertEquals(4, s.state.currentHead.activeLug)
        s.hit(176.4)
        val r = s.readout()
        assertEquals(-3.6, r.deltaHz!!, 1e-9)
        assertEquals(TuneStatus.TIGHTEN, r.status)
        assertEquals(Severity.WARN, r.severity)
        assertEquals("TIGHTEN ↑", r.prompt)
        s.hit(176.5)
        s.hit(186.5) // next lug +6.5 → red LOOSEN
        assertEquals(TuneStatus.LOOSEN, s.readout().status)
        assertEquals(Severity.BAD, s.readout().severity)
        s.hit(180.6)
        s.hit(180.7) // 3rd-hit median 180.7... (186.5,180.6 disagree)
        assertEquals(TuneStatus.IN_TUNE, s.lugReadout(2).status)
    }

    @Test
    fun targetModeComparesWithKitTarget() {
        val s = session()
        s.selectMode(TuningMode.TARGET)
        s.selectDrum(DrumType.FLOOR)
        val target = s.state.targets.getValue(DrumType.FLOOR).batterLugHz
        s.hit(target + 0.4)
        assertEquals(TuneStatus.IN_TUNE, s.readout().status)
        s.selectSide(HeadSide.RESONANT)
        assertEquals(s.state.targets.getValue(DrumType.FLOOR).resonantLugHz, s.referenceHz()!!, 1e-9)
        // Search band narrows around the target
        val band = s.searchBand()
        assertTrue(s.referenceHz()!! in band)
        assertTrue(band.width < 200.0)
    }

    @Test
    fun tapLugClearsAndReMeasures() {
        val s = session()
        s.hit(200.0); s.hit(200.0)
        s.tapLug(1)
        assertEquals(1, s.state.currentHead.activeLug)
        assertNull(s.state.currentHead.lug(1).lockedHz)
        s.resetHead()
        assertTrue(s.state.currentHead.lugs.isEmpty())
    }

    @Test
    fun decayAttachesToTheRightLugAndFlagsDeadHead() {
        val s = session()
        s.selectDrum(DrumType.TOM1)
        val seq = StarPattern.sequence(6)
        val decays = listOf(420.0, 410.0, 430.0, 200.0, 415.0, 425.0) // 4th in sequence is dead
        for (i in 0 until 6) {
            val a = hitId; s.hit(180.0)
            val b = hitId; s.hit(180.1)
            s.onDecay(a, decays[i]); s.onDecay(b, decays[i])
        }
        assertEquals(420.0, s.state.currentHead.lug(seq[0]).decayMs!!, 1e-9)
        assertEquals(listOf(seq[3]), s.deadLugs())
    }

    @Test
    fun noToneDoesNotAdvance() {
        val s = session()
        s.hit(null)
        assertTrue(s.state.message!!.startsWith("No clear tone"))
        assertTrue(s.state.currentHead.lug(1).hits.isEmpty())
    }

    @Test
    fun lugCountChangeResetsDrum() {
        val s = session()
        s.hit(200.0)
        s.setLugCount(8)
        assertEquals(8, s.state.kit.shell(DrumType.SNARE).lugCount)
        assertTrue(s.state.currentHead.lugs.isEmpty())
        s.setLugCount(7) // ignored
        assertEquals(8, s.state.currentHead.lugCount)
    }

    @Test
    fun wireMeterAndLineCheck() {
        val s = session()
        s.selectTab(AppTab.SOUNDCHECK)
        s.hit(220.0, sizzle = 2.0)
        assertNotNull(s.state.wire)
        assertEquals(com.drumtuner.app.dsp.WireZone.RATTLY, s.state.wire!!.zone)

        s.selectSoundcheckMode(SoundcheckMode.LINE_CHECK)
        s.startLineCheck()
        assertEquals(DrumType.SNARE, s.state.lineCheckDrum)
        val order = mutableListOf<DrumType>()
        while (s.state.lineCheckDrum != null) {
            order += s.state.lineCheckDrum!!
            val id = hitId
            s.hit(100.0 + order.size)
            s.onDecay(id, 500.0)
        }
        assertEquals(DrumType.values().toList(), order)
        assertEquals(500.0, s.state.lineCheck.getValue(DrumType.KICK).decayMs!!, 0.0)
        s.saveLineCheckAsReference()
        assertEquals(105.0, s.state.kit.shell(DrumType.KICK).refPitchHz!!, 0.0)
        assertEquals(105.0, s.lineCheckReferenceHz(DrumType.KICK)!!, 0.0)
    }

    @Test
    fun hitsOnKitTabAreIgnored() {
        val s = session()
        s.selectTab(AppTab.KIT)
        s.hit(200.0)
        assertTrue(s.state.heads.isEmpty())
    }

    @Test
    fun nudgeShiftsTarget() {
        val s = session()
        val before = s.state.targets.getValue(DrumType.SNARE).fundamentalHz
        s.nudge(DrumType.SNARE, 6.0)
        assertEquals(before + 6.0, s.state.targets.getValue(DrumType.SNARE).fundamentalHz, 1e-9)
        s.resetNudge(DrumType.SNARE)
        assertEquals(before, s.state.targets.getValue(DrumType.SNARE).fundamentalHz, 1e-9)
    }

    @Test
    fun presetsExportImportRoundTrip() {
        val s = session()
        s.renameKit(s.state.activeKitId, "Studio \"Maple\" Kit")
        s.updateShell(DrumType.TOM1) { it.copy(diameterIn = 8.0, depthIn = 6.5, enabled = false) }
        s.setKey(com.drumtuner.app.tuning.BandKey.DROP_C)
        val json = s.exportJson()

        val other = session()
        assertEquals(1, other.importJson(json))
        assertEquals(2, other.state.kits.size)
        val imported = other.state.kits.last()
        assertEquals("Studio \"Maple\" Kit", imported.name)
        assertEquals(com.drumtuner.app.tuning.BandKey.DROP_C, imported.key)
        val tom = imported.shell(DrumType.TOM1)
        assertEquals(8.0, tom.diameterIn, 0.0)
        assertEquals(6.5, tom.depthIn, 0.0)
        assertFalse(tom.enabled)
        // Importing the same file again creates distinct ids
        other.importJson(json)
        assertEquals(3, other.state.kits.map { it.id }.toSet().size)
    }

    @Test
    fun deleteLastKitKeepsADefault() {
        val s = session()
        s.deleteKit(s.state.activeKitId)
        assertEquals(1, s.state.kits.size)
    }
}
