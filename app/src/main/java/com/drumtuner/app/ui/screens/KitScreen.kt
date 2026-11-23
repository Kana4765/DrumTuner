@file:OptIn(ExperimentalMaterial3Api::class)

package com.drumtuner.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.drumtuner.app.TunerViewModel
import com.drumtuner.app.session.SessionState
import com.drumtuner.app.tuning.BandKey
import com.drumtuner.app.tuning.DrumAcoustics
import com.drumtuner.app.tuning.DrumTarget
import com.drumtuner.app.tuning.DrumType
import com.drumtuner.app.tuning.HeadPly
import com.drumtuner.app.tuning.Shell
import com.drumtuner.app.tuning.StarPattern
import com.drumtuner.app.tuning.TuningStyle
import com.drumtuner.app.ui.components.Badge
import com.drumtuner.app.ui.components.SectionCard
import com.drumtuner.app.ui.components.SegmentedRow
import com.drumtuner.app.ui.components.Stepper
import com.drumtuner.app.ui.components.fmt1
import com.drumtuner.app.ui.components.fmtSigned1
import com.drumtuner.app.ui.theme.StageColors

private fun sizeText(v: Double): String = if (v == Math.rint(v)) "${v.toInt()}\"" else "${fmt1(v)}\""

private fun diameterLimits(drum: DrumType): ClosedFloatingPointRange<Double> = when (drum) {
    DrumType.KICK -> 16.0..28.0
    DrumType.SNARE -> 10.0..15.0
    DrumType.FLOOR -> 13.0..20.0
    else -> 6.0..16.0
}

@Composable
fun KitScreen(vm: TunerViewModel, state: SessionState) {
    val kit = state.kit
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Kit: ${kit.name}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)

        SectionCard("Band key & tuning style") {
            KeyPicker(selected = kit.key, onSelect = { k -> vm.act { setKey(k) } })
            SegmentedRow(
                options = TuningStyle.values().map { it.label },
                selectedIndex = kit.style.ordinal,
                onSelect = { vm.act { setStyle(TuningStyle.values()[it]) } },
            )
            Text(
                when (kit.style) {
                    TuningStyle.PUNCHY -> "Resonant lugs +12% above batter: punchy attack, controlled pitch drop."
                    TuningStyle.MAX_SUSTAIN -> "Resonant = batter: longest, most open sustain."
                },
                color = StageColors.TextDim,
                fontSize = 13.sp,
            )
        }

        SectionCard("Targets in ${kit.key.label}") {
            val targets = state.targets
            if (targets.isEmpty()) Text("Enable at least one drum below.", color = StageColors.TextDim)
            DrumType.values().forEach { drum ->
                val t = targets[drum]
                if (t != null) {
                    TargetRow(t, kit.shell(drum), onNudge = { d -> vm.act { nudge(drum, d) } }, onReset = { vm.act { resetNudge(drum) } })
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
                }
            }
            Text(
                "Lug targets are f11 overtone frequencies (≈1.593 × fundamental). Toms are spaced by major 3rds / perfect 4ths inside each shell's safe range.",
                color = StageColors.TextDim,
                fontSize = 12.sp,
            )
        }

        DrumType.values().forEach { drum ->
            ShellEditor(
                shell = kit.shell(drum),
                onChange = { transform -> vm.act { updateShell(drum, transform) } },
            )
        }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun KeyPicker(selected: BandKey, onSelect: (BandKey) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Band key", color = StageColors.TextDim, modifier = Modifier.weight(1f))
        Box {
            OutlinedButton(onClick = { expanded = true }) { Text("${selected.label}  ▾") }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                BandKey.values().forEach { key ->
                    DropdownMenuItem(
                        text = { Text(key.label, fontWeight = if (key == selected) FontWeight.Bold else FontWeight.Normal) },
                        onClick = {
                            expanded = false
                            onSelect(key)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun TargetRow(t: DrumTarget, shell: Shell, onNudge: (Double) -> Unit, onReset: () -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "${t.drum.label} ${shell.sizeLabel}",
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            Text(
                "${t.noteName}  ${fmt1(t.fundamentalHz)} Hz" + (t.intervalFromAbove?.let { "  ($it)" } ?: ""),
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
            )
        }
        Text(
            "Batter lug ${fmt1(t.batterLugHz)} Hz  ·  Reso lug ${fmt1(t.resonantLugHz)} Hz",
            color = StageColors.TextDim,
            fontSize = 13.sp,
        )
        val w = t.buzzWarning
        if (w != null) {
            Badge(
                "⚠ Buzz risk: ${w.targetKind} ${fmt1(w.distanceHz)} Hz from ${w.conflictLabel} (${fmt1(w.conflictHz)} Hz)",
                StageColors.Warn,
            )
        }
        if (w != null || shell.nudgeHz != 0.0) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = { onNudge(-DrumAcoustics.NUDGE_STEP_HZ) }) { Text("−6 Hz") }
                OutlinedButton(onClick = { onNudge(DrumAcoustics.NUDGE_STEP_HZ) }) { Text("+6 Hz") }
                if (shell.nudgeHz != 0.0) {
                    TextButton(onClick = onReset) { Text("Reset (${fmtSigned1(shell.nudgeHz)})") }
                }
            }
        }
    }
}

@Composable
private fun ShellEditor(shell: Shell, onChange: ((Shell) -> Shell) -> Unit) {
    SectionCard(title = null) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "${shell.drum.label}  ${shell.sizeLabel}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            Switch(checked = shell.enabled, onCheckedChange = { on -> onChange { it.copy(enabled = on) } })
        }
        if (shell.enabled) {
            val limits = diameterLimits(shell.drum)
            Stepper(
                label = "Diameter",
                value = sizeText(shell.diameterIn),
                onMinus = { onChange { it.copy(diameterIn = (it.diameterIn - 1.0).coerceIn(limits)) } },
                onPlus = { onChange { it.copy(diameterIn = (it.diameterIn + 1.0).coerceIn(limits)) } },
            )
            Stepper(
                label = "Depth",
                value = sizeText(shell.depthIn),
                onMinus = { onChange { it.copy(depthIn = (it.depthIn - 0.5).coerceIn(3.0, 24.0)) } },
                onPlus = { onChange { it.copy(depthIn = (it.depthIn + 0.5).coerceIn(3.0, 24.0)) } },
            )
            Text("Batter head", color = StageColors.TextDim, fontSize = 13.sp)
            SegmentedRow(
                options = HeadPly.values().map { it.label },
                selectedIndex = shell.ply.ordinal,
                onSelect = { i -> onChange { it.copy(ply = HeadPly.values()[i]) } },
            )
            Text("Lugs", color = StageColors.TextDim, fontSize = 13.sp)
            val counts = StarPattern.SUPPORTED_LUG_COUNTS
            SegmentedRow(
                options = counts.map { it.toString() },
                selectedIndex = counts.indexOf(shell.lugCount).coerceAtLeast(0),
                onSelect = { i -> onChange { it.copy(lugCount = counts[i]) } },
            )
        }
    }
}
