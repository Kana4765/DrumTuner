@file:OptIn(ExperimentalMaterial3Api::class)

package com.drumtuner.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.drumtuner.app.TunerViewModel
import com.drumtuner.app.session.AppTab
import com.drumtuner.app.ui.components.SectionCard
import com.drumtuner.app.ui.screens.KitScreen
import com.drumtuner.app.ui.screens.PresetsScreen
import com.drumtuner.app.ui.screens.SoundcheckScreen
import com.drumtuner.app.ui.screens.TunerScreen
import com.drumtuner.app.ui.theme.StageColors

private fun tabGlyph(tab: AppTab): String = when (tab) {
    AppTab.TUNER -> "◎"
    AppTab.KIT -> "♪"
    AppTab.SOUNDCHECK -> "≋"
    AppTab.PRESETS -> "▤"
}

@Composable
fun DrumTunerApp(
    viewModel: TunerViewModel,
    hasMicPermission: Boolean,
    onRequestPermission: () -> Unit,
) {
    val state by viewModel.state.collectAsState()
    val source by viewModel.sourceLabel.collectAsState()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                AppTab.values().forEach { tab ->
                    NavigationBarItem(
                        selected = state.tab == tab,
                        onClick = { viewModel.act { selectTab(tab) } },
                        icon = { Text(tabGlyph(tab), fontSize = 20.sp, fontWeight = FontWeight.Bold) },
                        label = { Text(tab.label) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.onPrimary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            indicatorColor = MaterialTheme.colorScheme.primary,
                            unselectedIconColor = StageColors.TextDim,
                            unselectedTextColor = StageColors.TextDim,
                        ),
                    )
                }
            }
        },
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            Column(Modifier.fillMaxSize()) {
                if (!hasMicPermission) {
                    Box(Modifier.padding(14.dp)) {
                        SectionCard("Microphone access needed") {
                            Text(
                                "DrumTuner listens to your drum through the microphone (or a USB/piezo input). " +
                                    "Audio never leaves the phone — the app has no internet permission.",
                                color = StageColors.TextDim,
                            )
                            Button(onClick = onRequestPermission) { Text("Allow microphone") }
                        }
                    }
                }
                when (state.tab) {
                    AppTab.TUNER -> TunerScreen(viewModel, state, viewModel.inputLevelDb, source)
                    AppTab.KIT -> KitScreen(viewModel, state)
                    AppTab.SOUNDCHECK -> SoundcheckScreen(viewModel, state)
                    AppTab.PRESETS -> PresetsScreen(viewModel, state)
                }
            }
        }
    }
}
