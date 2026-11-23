package com.drumtuner.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.drumtuner.app.ui.theme.StageColors
import java.util.Locale
import kotlin.math.roundToInt

fun fmt1(v: Double): String = String.format(Locale.US, "%.1f", v)

fun fmtSigned1(v: Double): String = String.format(Locale.US, "%+.1f", v)

fun fmtMs(v: Double?): String = if (v == null) "— ms" else "${v.roundToInt()} ms"

/** A row of mutually exclusive options (stable replacement for experimental segmented buttons). */
@Composable
fun SegmentedRow(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(10.dp)
    Row(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .border(1.dp, MaterialTheme.colorScheme.outline, shape),
    ) {
        options.forEachIndexed { i, label ->
            val selected = i == selectedIndex
            Box(
                modifier = Modifier
                    .weight(1f)
                    .background(if (selected) MaterialTheme.colorScheme.primary else Color.Transparent)
                    .clickable { onSelect(i) }
                    .padding(vertical = 10.dp, horizontal = 4.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
fun SectionCard(
    title: String?,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (title != null) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            content()
        }
    }
}

/** Coloured pill badge, e.g. a warning. */
@Composable
fun Badge(text: String, color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier
            .clip(RoundedCornerShape(8.dp))
            .background(color.copy(alpha = 0.18f))
            .border(1.dp, color, RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        Text(text, color = color, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

/** − value + stepper. */
@Composable
fun Stepper(
    label: String,
    value: String,
    onMinus: () -> Unit,
    onPlus: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), color = StageColors.TextDim)
        OutlinedButton(onClick = onMinus) { Text("−") }
        Text(value, Modifier.width(72.dp), textAlign = TextAlign.Center, fontWeight = FontWeight.Bold)
        OutlinedButton(onClick = onPlus) { Text("+") }
    }
}

/** Horizontal input meter with the trigger-threshold marker. */
@Composable
fun LevelMeter(levelDb: Float, thresholdDb: Float, modifier: Modifier = Modifier) {
    val track = MaterialTheme.colorScheme.surface
    Canvas(
        modifier
            .fillMaxWidth()
            .height(16.dp),
    ) {
        val minDb = -70f
        val maxDb = 0f
        val frac = ((levelDb - minDb) / (maxDb - minDb)).coerceIn(0f, 1f)
        val thr = ((thresholdDb - minDb) / (maxDb - minDb)).coerceIn(0f, 1f)
        val radius = CornerRadius(size.height / 2f, size.height / 2f)
        drawRoundRect(color = track, cornerRadius = radius)
        drawRoundRect(
            color = if (levelDb >= thresholdDb) StageColors.InTune else StageColors.Accent,
            size = Size(size.width * frac, size.height),
            cornerRadius = radius,
        )
        val x = size.width * thr
        drawLine(
            color = Color.White,
            start = Offset(x, 0f),
            end = Offset(x, size.height),
            strokeWidth = 3.dp.toPx(),
        )
    }
}
