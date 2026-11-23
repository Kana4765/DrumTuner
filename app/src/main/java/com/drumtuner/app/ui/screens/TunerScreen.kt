@file:OptIn(ExperimentalMaterial3Api::class)

package com.drumtuner.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.drumtuner.app.TunerViewModel
import com.drumtuner.app.session.Readout
import com.drumtuner.app.session.SessionState
import com.drumtuner.app.session.Severity
import com.drumtuner.app.session.TuneStatus
import com.drumtuner.app.session.TuningMode
import com.drumtuner.app.tuning.DrumType
import com.drumtuner.app.tuning.HeadSide
import com.drumtuner.app.tuning.StarPattern
import com.drumtuner.app.ui.components.Badge
import com.drumtuner.app.ui.components.LevelMeter
import com.drumtuner.app.ui.components.LugRadar
import com.drumtuner.app.ui.components.LugVisual
import com.drumtuner.app.ui.components.SegmentedRow
import com.drumtuner.app.ui.components.fmt1
import com.drumtuner.app.ui.components.fmtMs
import com.drumtuner.app.ui.components.fmtSigned1
import com.drumtuner.app.ui.theme.StageColors
import com.drumtuner.app.ui.theme.color
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.roundToInt

@Composable
fun TunerScreen(
    vm: TunerViewModel,
    state: SessionState,
    inputLevelDb: StateFlow<Float>,
    sourceLabel: String,
) {
    val head = state.currentHead
    val readout = vm.readout(state)
    val dead = vm.deadLugs(state)
    val target = state.targets[state.drum]

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        SegmentedRow(
            options = listOf("A · Relative (Lug 1)", "B · Kit & Key Target"),
            selectedIndex = state.mode.ordinal,
            onSelect = { vm.act { selectMode(TuningMode.values()[it]) } },
        )

        ScrollableTabRow(
            selectedTabIndex = state.drum.ordinal,
            edgePadding = 0.dp,
            containerColor = MaterialTheme.colorScheme.background,
        ) {
            DrumType.values().forEach { drum ->
                val enabled = state.kit.shell(drum).enabled
                Tab(
                    selected = drum == state.drum,
                    onClick = { vm.act { selectDrum(drum) } },
                    text = {
                        Text(
                            drum.label,
                            fontWeight = if (drum == state.drum) FontWeight.Bold else FontWeight.Normal,
                            color = if (enabled) MaterialTheme.colorScheme.onSurface else StageColors.Idle,
                        )
                    },
                )
            }
        }

        SegmentedRow(
            options = HeadSide.values().map { it.label },
            selectedIndex = state.side.ordinal,
            onSelect = { vm.act { selectSide(HeadSide.values()[it]) } },
        )

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Lugs", color = StageColors.TextDim, modifier = Modifier.width(44.dp))
            val counts = StarPattern.SUPPORTED_LUG_COUNTS
            SegmentedRow(
                options = counts.map { it.toString() },
                selectedIndex = counts.indexOf(head.lugCount).coerceAtLeast(0),
                onSelect = { vm.act { setLugCount(counts[it]) } },
            )
        }

        // Reference line
        val refText = when (state.mode) {
            TuningMode.RELATIVE -> head.lug(1).lockedHz?.let { "Baseline: Lug 1 = ${fmt1(it)} Hz" }
                ?: "Hit Lug 1 to set the 0.0 Hz baseline"
            TuningMode.TARGET -> if (target != null) {
                "Target ${target.noteName} (${fmt1(target.fundamentalHz)} Hz) · ${if (state.side == HeadSide.BATTER) "batter" else "reso"} lug ${fmt1(target.lugTarget(state.side))} Hz"
            } else {
                "${state.drum.label} is disabled in the kit — using Lug 1 baseline"
            }
        }
        Text(refText, color = StageColors.TextDim, fontSize = 13.sp)

        LugRadar(
            lugCount = head.lugCount,
            activeLug = head.activeLug,
            lugVisual = { n -> lugVisual(vm.lugReadout(n, state)) },
            deadLugs = dead.toSet(),
            onLugTap = { n -> vm.act { tapLug(n) } },
        ) {
            CenterReadout(readout)
        }

        Text(
            text = state.message ?: "Tap ~1\" in from Lug ${head.activeLug}. Mute the other head.",
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold,
        )

        if (dead.isNotEmpty()) {
            Badge(
                "⚠ Dead-head warning: lug ${dead.joinToString(", ")} sustain >35% below the head's median — check for dents or uneven seating",
                StageColors.Bad,
            )
        }
        val buzz = target?.buzzWarning
        if (state.mode == TuningMode.TARGET && buzz != null) {
            Badge(
                "⚠ Sympathetic buzz: ${buzz.targetKind} within ${fmt1(buzz.distanceHz)} Hz of ${buzz.conflictLabel} — nudge on the Kit & Key tab",
                StageColors.Warn,
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = { vm.act { resetHead() } }) { Text("Reset Head") }
            Spacer(Modifier.weight(1f))
            Text(sourceLabel, color = StageColors.TextDim, fontSize = 12.sp)
        }

        Text(
            "Trigger threshold: ${state.thresholdDb.roundToInt()} dBFS",
            color = StageColors.TextDim,
            fontSize = 13.sp,
        )
        LiveLevelMeter(level = inputLevelDb, thresholdDb = state.thresholdDb)
        Slider(
            value = state.thresholdDb,
            onValueChange = { vm.setThreshold(it) },
            valueRange = -60f..-6f,
        )
        Text(
            "Hits must cross the white marker. Raise it in loud rooms; lower it for quiet taps or contact mics.",
            color = StageColors.TextDim,
            fontSize = 12.sp,
        )
        Spacer(Modifier.padding(bottom = 12.dp))
    }
}

/** Collects the fast-changing input level here so only the meter recomposes ~43×/s. */
@Composable
private fun LiveLevelMeter(level: StateFlow<Float>, thresholdDb: Float) {
    val db by level.collectAsState()
    LevelMeter(levelDb = db, thresholdDb = thresholdDb)
}

private fun lugVisual(r: Readout): LugVisual {
    val color = when {
        r.displayHz == null -> StageColors.Idle
        r.status == TuneStatus.BASELINE -> StageColors.Accent
        else -> r.severity.color()
    }
    val label = when {
        r.displayHz == null -> null
        r.status == TuneStatus.BASELINE -> "0.0"
        r.deltaHz != null -> fmtSigned1(r.deltaHz)
        else -> null
    }
    return LugVisual(color, label)
}

@Composable
private fun CenterReadout(r: Readout) {
    val color = if (r.displayHz == null) StageColors.Idle else when (r.status) {
        TuneStatus.BASELINE -> StageColors.Accent
        else -> r.severity.color()
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("LUG ${r.lug}", color = StageColors.TextDim, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = r.displayHz?.let { fmt1(it) } ?: "---.-",
                fontSize = 40.sp,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
            )
            Text(" Hz", fontSize = 16.sp, color = StageColors.TextDim, modifier = Modifier.padding(bottom = 7.dp))
        }
        val delta = r.deltaHz
        Text(
            text = when {
                delta != null -> "${fmtSigned1(delta)} Hz"
                r.status == TuneStatus.BASELINE -> "0.0 Hz"
                else -> " "
            },
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = color,
        )
        Text(
            text = r.prompt,
            fontSize = 18.sp,
            fontWeight = FontWeight.Black,
            color = if (r.severity == Severity.NEUTRAL) StageColors.TextDim else color,
            textAlign = TextAlign.Center,
        )
        Text(
            text = listOfNotNull(r.noteLabel, r.decayMs?.let { fmtMs(it) }).joinToString("  ·  ").ifEmpty { " " },
            fontSize = 13.sp,
            color = StageColors.TextDim,
        )
        Text(r.dots, modifier = Modifier.padding(top = 2.dp), fontSize = 16.sp, color = MaterialTheme.colorScheme.primary)
    }
}
