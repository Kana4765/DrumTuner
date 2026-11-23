@file:OptIn(ExperimentalMaterial3Api::class)

package com.drumtuner.app.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import com.drumtuner.app.data.KitProfile
import com.drumtuner.app.session.SessionState
import com.drumtuner.app.ui.components.Badge
import com.drumtuner.app.ui.components.SectionCard
import com.drumtuner.app.ui.theme.StageColors

private sealed interface PresetDialog {
    data object New : PresetDialog
    data class Rename(val kit: KitProfile) : PresetDialog
    data class Delete(val kit: KitProfile) : PresetDialog
}

@Composable
fun PresetsScreen(vm: TunerViewModel, state: SessionState) {
    var dialog by remember { mutableStateOf<PresetDialog?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri: Uri? -> if (uri != null) vm.exportTo(uri) }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? -> if (uri != null) vm.importFrom(uri) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Kit Presets", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(
            "Saved on this device only. No account, no internet.",
            color = StageColors.TextDim,
            fontSize = 13.sp,
        )
        state.message?.let { Text(it, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold) }

        state.kits.forEach { kit ->
            KitCard(
                kit = kit,
                active = kit.id == state.activeKitId,
                onUse = { vm.act { selectKit(kit.id) } },
                onRename = { dialog = PresetDialog.Rename(kit) },
                onDuplicate = { vm.act { duplicateKit(kit.id) } },
                onDelete = { dialog = PresetDialog.Delete(kit) },
            )
        }

        Button(onClick = { dialog = PresetDialog.New }, modifier = Modifier.fillMaxWidth()) { Text("+ New kit") }

        SectionCard("Offline JSON backup") {
            Text(
                "Export all kits to a .json file (e.g. Downloads or a USB stick) and import them on another phone.",
                color = StageColors.TextDim,
                fontSize = 13.sp,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { exportLauncher.launch("drumtuner-kits.json") }) { Text("Export .json") }
                OutlinedButton(onClick = { importLauncher.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) }) {
                    Text("Import .json")
                }
            }
        }
        Spacer(Modifier.height(12.dp))
    }

    when (val d = dialog) {
        null -> Unit
        PresetDialog.New -> NameDialog(
            title = "New kit",
            initial = "",
            onConfirm = { name -> vm.act { newKit(name) }; dialog = null },
            onDismiss = { dialog = null },
        )
        is PresetDialog.Rename -> NameDialog(
            title = "Rename kit",
            initial = d.kit.name,
            onConfirm = { name -> vm.act { renameKit(d.kit.id, name) }; dialog = null },
            onDismiss = { dialog = null },
        )
        is PresetDialog.Delete -> AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text("Delete ${d.kit.name}?") },
            text = { Text("This removes the kit from this device. Export a backup first if you might need it.") },
            confirmButton = {
                TextButton(onClick = { vm.act { deleteKit(d.kit.id) }; dialog = null }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { dialog = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun KitCard(
    kit: KitProfile,
    active: Boolean,
    onUse: () -> Unit,
    onRename: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
) {
    SectionCard(title = null) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(kit.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            if (active) Badge("ACTIVE", StageColors.InTune)
        }
        Text("${kit.key.label} · ${kit.style.label}", color = MaterialTheme.colorScheme.primary, fontSize = 13.sp)
        Text(
            kit.shells.filter { it.enabled }.joinToString("  ·  ") { "${it.drum.label} ${it.sizeLabel}" },
            color = StageColors.TextDim,
            fontSize = 13.sp,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            if (!active) Button(onClick = onUse) { Text("Use") }
            TextButton(onClick = onRename) { Text("Rename") }
            TextButton(onClick = onDuplicate) { Text("Duplicate") }
            TextButton(onClick = onDelete) { Text("Delete", color = MaterialTheme.colorScheme.error) }
        }
    }
}

@Composable
private fun NameDialog(title: String, initial: String, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it.take(40) },
                    label = { Text("Kit name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(text) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
