package com.drumtuner.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.drumtuner.app.session.Severity

/** High-contrast stage palette: readable under hot lights and in dark rooms. */
object StageColors {
    val InTune = Color(0xFF00E676)
    val Warn = Color(0xFFFFB300)
    val Bad = Color(0xFFFF3D00)
    val Accent = Color(0xFF00E5FF)
    val Idle = Color(0xFF616161)
    val Rim = Color(0xFF3C3C3C)
    val Head = Color(0xFF141414)
    val TextDim = Color(0xFFB0B0B0)
}

fun Severity.color(): Color = when (this) {
    Severity.OK -> StageColors.InTune
    Severity.WARN -> StageColors.Warn
    Severity.BAD -> StageColors.Bad
    Severity.NEUTRAL -> StageColors.Accent
}

private val StageColorScheme = darkColorScheme(
    primary = Color(0xFFFFB300),
    onPrimary = Color.Black,
    primaryContainer = Color(0xFF3D2B00),
    onPrimaryContainer = Color(0xFFFFE08A),
    secondary = Color(0xFF00E5FF),
    onSecondary = Color.Black,
    secondaryContainer = Color(0xFF003640),
    onSecondaryContainer = Color(0xFFB3F4FF),
    background = Color.Black,
    onBackground = Color.White,
    surface = Color(0xFF0E0E0E),
    onSurface = Color.White,
    surfaceVariant = Color(0xFF1E1E1E),
    onSurfaceVariant = Color(0xFFD0D0D0),
    error = Color(0xFFFF5252),
    onError = Color.Black,
    outline = Color(0xFF5A5A5A),
)

@Composable
fun DrumTunerTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = StageColorScheme,
        typography = Typography(),
        content = content,
    )
}
