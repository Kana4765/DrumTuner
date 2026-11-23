package com.drumtuner.app.ui.components

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.drumtuner.app.tuning.StarPattern
import com.drumtuner.app.ui.theme.StageColors
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin

/** Visual state of one lug on the radar. */
data class LugVisual(
    val color: Color,
    /** Small label drawn outside the rim, e.g. "+2.1". */
    val label: String?,
)

private const val RIM_FRACTION = 0.74f
private const val LUG_DOT_FRACTION = 0.105f

private fun lugAngle(lug: Int, count: Int): Double = -PI / 2 + (lug - 1) * 2 * PI / count

private fun lugCenter(lug: Int, count: Int, cx: Float, cy: Float, r: Float): Offset {
    val a = lugAngle(lug, count)
    return Offset(cx + r * cos(a).toFloat(), cy + r * sin(a).toFloat())
}

/**
 * Top-down drumhead map: numbered lugs around the rim (Lug 1 at 12 o'clock, clockwise),
 * the star-pattern path drawn faintly across the head, and [center] content in the middle.
 */
@Composable
fun LugRadar(
    lugCount: Int,
    activeLug: Int,
    lugVisual: (Int) -> LugVisual,
    deadLugs: Set<Int>,
    onLugTap: (Int) -> Unit,
    modifier: Modifier = Modifier,
    center: @Composable () -> Unit,
) {
    val tap by rememberUpdatedState(onLugTap)
    val numberPaint = remember {
        Paint().apply {
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
            typeface = Typeface.DEFAULT_BOLD
        }
    }
    val labelPaint = remember {
        Paint().apply {
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
            typeface = Typeface.DEFAULT_BOLD
        }
    }
    val visuals = (1..lugCount).associateWith { lugVisual(it) }
    val sequence = StarPattern.sequence(lugCount)

    Box(
        modifier
            .fillMaxWidth()
            .aspectRatio(1f),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(
            Modifier
                .fillMaxSize()
                .pointerInput(lugCount) {
                    detectTapGestures { pos ->
                        val w = size.width.toFloat()
                        val h = size.height.toFloat()
                        val r = min(w, h) / 2f * RIM_FRACTION
                        val dotR = r * LUG_DOT_FRACTION
                        var best = -1
                        var bestDist = Float.MAX_VALUE
                        for (n in 1..lugCount) {
                            val c = lugCenter(n, lugCount, w / 2f, h / 2f, r)
                            val d = hypot(pos.x - c.x, pos.y - c.y)
                            if (d < bestDist) {
                                bestDist = d
                                best = n
                            }
                        }
                        if (best > 0 && bestDist <= dotR * 2.6f) tap(best)
                    }
                },
        ) {
            val cx = size.width / 2f
            val cy = size.height / 2f
            val r = min(size.width, size.height) / 2f * RIM_FRACTION
            val dotR = r * LUG_DOT_FRACTION
            val c = Offset(cx, cy)

            // Drumhead + rim
            drawCircle(color = StageColors.Head, radius = r, center = c)
            drawCircle(color = StageColors.Rim, radius = r, center = c, style = Stroke(width = 8.dp.toPx()))
            drawCircle(
                color = StageColors.Rim.copy(alpha = 0.5f),
                radius = r * 0.62f,
                center = c,
                style = Stroke(width = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f))),
            )

            // Star sequence, faint
            for (i in 0 until sequence.size - 1) {
                drawLine(
                    color = Color.White.copy(alpha = 0.07f),
                    start = lugCenter(sequence[i], lugCount, cx, cy, r),
                    end = lugCenter(sequence[i + 1], lugCount, cx, cy, r),
                    strokeWidth = 2.dp.toPx(),
                )
            }

            numberPaint.textSize = dotR * 1.05f
            labelPaint.textSize = dotR * 0.85f

            for (n in 1..lugCount) {
                val p = lugCenter(n, lugCount, cx, cy, r)
                val v = visuals.getValue(n)
                if (n == activeLug) {
                    drawCircle(color = Color.White, radius = dotR * 1.45f, center = p, style = Stroke(width = 3.dp.toPx()))
                }
                if (n in deadLugs) {
                    drawCircle(
                        color = StageColors.Bad,
                        radius = dotR * 1.75f,
                        center = p,
                        style = Stroke(width = 2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f))),
                    )
                }
                drawCircle(color = v.color, radius = dotR, center = p)

                val a = lugAngle(n, lugCount)
                val labelR = r + dotR * 2.6f
                val lx = cx + labelR * cos(a).toFloat()
                val ly = cy + labelR * sin(a).toFloat()
                drawIntoCanvas { canvas ->
                    numberPaint.color = android.graphics.Color.BLACK
                    canvas.nativeCanvas.drawText(n.toString(), p.x, p.y + numberPaint.textSize * 0.36f, numberPaint)
                    val label = v.label
                    if (label != null) {
                        labelPaint.color = v.color.toArgb()
                        canvas.nativeCanvas.drawText(label, lx, ly + labelPaint.textSize * 0.36f, labelPaint)
                    }
                }
            }
        }
        Box(Modifier.fillMaxWidth(0.6f), contentAlignment = Alignment.Center) {
            center()
        }
    }
}
