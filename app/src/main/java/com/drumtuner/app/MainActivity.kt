package com.drumtuner.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import com.drumtuner.app.ui.DrumTunerApp
import com.drumtuner.app.ui.theme.DrumTunerTheme

class MainActivity : ComponentActivity() {

    private val viewModel: TunerViewModel by viewModels()

    private var hasMicPermission by mutableStateOf(false)

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            hasMicPermission = granted
            if (granted) startAudio()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        hasMicPermission = isMicGranted()

        setContent {
            DrumTunerTheme {
                DrumTunerApp(
                    viewModel = viewModel,
                    hasMicPermission = hasMicPermission,
                    onRequestPermission = { permissionLauncher.launch(Manifest.permission.RECORD_AUDIO) },
                )
            }
        }

        if (!hasMicPermission && savedInstanceState == null) {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    override fun onStart() {
        super.onStart()
        hasMicPermission = isMicGranted()
        if (hasMicPermission) startAudio()
    }

    override fun onStop() {
        viewModel.stopListening()
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        super.onStop()
    }

    private fun startAudio() {
        if (viewModel.startListening()) {
            // Keep the screen awake while tuning
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    private fun isMicGranted(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
}
