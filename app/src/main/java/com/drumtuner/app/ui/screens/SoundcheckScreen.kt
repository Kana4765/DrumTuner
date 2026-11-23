@file:OptIn(ExperimentalMaterial3Api::class)

package com.drumtuner.app.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.drumtuner.app.TunerViewModel
import com.drumtuner.app.dsp.PitchMath
import com.drumtuner.app.dsp.SnareWire
import com.drumtuner.app.dsp.WireReading
import com.drumtuner.app.dsp.WireZone
import com.drumtuner.app.session.SessionState
import com.drumtuner.app.session.SoundcheckMode
import com.drumtuner.app.tuning.DrumType
import com.drumtuner.app.ui.components.SectionCard
import com.drumtuner.app.ui.components.SegmentedRow
import com.drumtuner.app.ui.components.fmt1
import com.drumtuner.app.ui.components.fmtMs
import com.drumtuner.app.ui.theme.StageColors
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

@Composable
fun SoundcheckScreen(vm: TunerViewModel, state: SessionState) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SegmentedRow(
            options = SoundcheckMode.values().map { it.label },
            selectedIndex = state.soundcheckMode.ordinal,
            onSelect = { vm.act { selectSoundcheckMode(SoundcheckMode.values()[it]) } },
        )
        when (state.soundcheckMode) {
            SoundcheckMode.WIRES -> WirePanel(state)
            SoundcheckMode.LINE_CHECK -> LineCheckPanel(vm, state)
        }
    }
}

private fun zoneColor(zone: WireZone): Color = when (zone) {
    WireZone.RATTLY -> StageColors.Warn
    WireZone.CRISP -> StageColors.InTune
    WireZone.CHOKED -> StageColors.Bad
}

@Composable
private fun WirePanel(state: SessionState) {
    SectionCard("Snare Wire Tension Meter") {
        Text("Engage the snares and hit the snare centre once.", color = StageColors.TextDim, fontSize = 13.sp)
        WireGauge(state.wire)
        val w = state.wire
        Text(
            text = w?.zone?.label ?: "Waiting for a hit…",
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
            fontSize = 26.sp,
            fontWeight = FontWeight.Black,
            color = w?.let { zoneColor(it.zone) } ?: StageColors.Idle,
        )
        if (w != null) {
            val pitch = state.wirePitchHz
            Text(
                "Sizzle ratio ${fmt1(w.ratioDb)} dB (3–8 kHz vs 150–450 Hz)" +
                    (pitch?.let { "  ·  pitch ${fmt1(it)} Hz ${PitchMath.reading(it)?.label ?: ""}" } ?: ""),
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
                color = StageColors.TextDim,
                fontSize = 13.sp,
            )
            Text(
                when (w.zone) {
                    WireZone.RATTLY -> "Wires too loose: tighten the strainer ¼ turn and hit again."
                    WireZone.CRISP -> "Sweet spot: crisp response without choking the drum."
                    WireZone.CHOKED -> "Wires too tight: back the strainer off ¼ turn."
                },
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

/** Semicircular gauge: Rattly (left) → Crisp Sweet Spot → Choked (right). */
@Composable
private fun WireGauge(reading: WireReading?) {
    val rattlyEnd = 1f - ((SnareWire.RATTLY_ABOVE_DB + 32.0) / 34.0).toFloat()   // display x where Rattly ends
    val chokedStart = 1f - ((SnareWire.CHOKED_BELOW_DB + 32.0) / 34.0).toFloat() // display x where Choked starts
    Canvas(
        Modifier
            .fillMaxWidth()
            .aspectRatio(2f),
    ) {
        val stroke = 22.dp.toPx()
        val r = min(size.width / 2f, size.height) - stroke
        val c = Offset(size.width / 2f, size.height - stroke / 2f)
        val topLeft = Offset(c.x - r, c.y - r)
        val arcSize = Size(r * 2, r * 2)
        val segments = listOf(
            Triple(0f, rattlyEnd, StageColors.Warn),
            Triple(rattlyEnd, chokedStart, StageColors.InTune),
            Triple(chokedStart, 1f, StageColors.Bad),
        )
        for ((from, to, color) in segments) {
            drawArc(
                color = color.copy(alpha = 0.85f),
                startAngle = 180f + from * 180f,
                sweepAngle = (to - from) * 180f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Butt),
            )
        }
        if (reading != null) {
            val x = 1f - reading.gaugePosition
            val angle = PI + x * PI
            val end = Offset(c.x + (r * 0.92f) * cos(angle).toFloat(), c.y + (r * 0.92f) * sin(angle).toFloat())
            drawLine(color = Color.White, start = c, end = end, strokeWidth = 5.dp.toPx(), cap = StrokeCap.Round)
        }
        drawCircle(color = Color.White, radius = 8.dp.toPx(), center = c)
    }
    Row(Modifier.fillMaxWidth()) {
        Text("Rattly", color = StageColors.Warn, fontSize = 12.sp, modifier = Modifier.weight(1f))
        Text("Crisp Sweet Spot", color = StageColors.InTune, fontSize = 12.sp, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
        Text("Choked", color = StageColors.Bad, fontSize = 12.sp, modifier = Modifier.weight(1f), textAlign = TextAlign.End)
    }
}

@Composable
private fun LineCheckPanel(vm: TunerViewModel, state: SessionState) {
    SectionCard("60-Second Line Check") {
        Text(
            "Hit each drum once in the centre. Pitch is compared with the saved reference (or the key target), sustain with the saved reference.",
            color = StageColors.TextDim,
            fontSize = 13.sp,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { vm.act { startLineCheck() } }) {
                Text(if (state.lineCheck.isEmpty() && state.lineCheckDrum == null) "Start" else "Restart")
            }
            OutlinedButton(onClick = { vm.act { saveLineCheckAsReference() } }, enabled = state.lineCheck.isNotEmpty()) {
                Text("Save as reference")
            }
        }
        state.message?.let {
            Text(it, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
        }
        DrumType.values().filter { state.kit.shell(it).enabled }.forEach { drum ->
            LineCheckRow(vm, state, drum)
        }
        OutlinedButton(onClick = { vm.act { clearReferences() } }) { Text("Clear saved references") }
    }
}

@Composable
private fun LineCheckRow(vm: TunerViewModel, state: SessionState, drum: DrumType) {
    val active = state.lineCheckDrum == drum
    val entry = state.lineCheck[drum]
    val ref = vm.lineCheckReferenceHz(drum, state)
    val refDecay = state.kit.shell(drum).refDecayMs
    val shape = RoundedCornerShape(10.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface)
            .clickable { vm.act { selectLineCheckDrum(drum) } }
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(drum.label, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            if (active) Text("◀ HIT NOW", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Black)
        }
        val pitch = entry?.pitchHz
        val pitchText = when {
            pitch == null -> "Pitch: —"
            ref == null -> "Pitch: ${fmt1(pitch)} Hz"
            else -> {
                val cents = PitchMath.centsBetween(pitch, ref).roundToInt()
                val tag = if (abs(cents) <= 15) "✓" else if (cents < 0) "flat" else "sharp"
                "Pitch: ${fmt1(pitch)} Hz vs ${fmt1(ref)} Hz  (${if (cents >= 0) "+" else ""}$cents¢ $tag)"
            }
        }
        val pitchColor = when {
            pitch == null || ref == null -> StageColors.TextDim
            abs(PitchMath.centsBetween(pitch, ref)) <= 15 -> StageColors.InTune
            abs(PitchMath.centsBetween(pitch, ref)) <= 50 -> StageColors.Warn
            else -> StageColors.Bad
        }
        Text(pitchText, color = pitchColor, fontSize = 14.sp)
        val decay = entry?.decayMs
        val sustainText = when {
            decay == null -> "Sustain: —"
            refDecay == null -> "Sustain: ${fmtMs(decay)}"
            else -> {
                val pct = ((decay / refDecay - 1.0) * 100).roundToInt()
                "Sustain: ${fmtMs(decay)} vs ${fmtMs(refDecay)}  (${if (pct >= 0) "+" else ""}$pct%)"
            }
        }
        val sustainColor = if (decay != null && refDecay != null && decay < refDecay * 0.65) StageColors.Bad else StageColors.TextDim
        Text(sustainText, color = sustainColor, fontSize = 14.sp)
    }
    Spacer(Modifier.height(2.dp))
}
