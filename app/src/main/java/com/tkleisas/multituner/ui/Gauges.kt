package com.tkleisas.multituner.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs

private fun centsColor(cents: Double, hasPitch: Boolean): Color = when {
    !hasPitch -> MutedText
    abs(cents) <= 5.0 -> InTuneGreen
    abs(cents) <= 15.0 -> WarnYellow
    else -> ErrorRed
}

@Composable
fun NeedleGauge(
    cents: Double,
    hasPitch: Boolean,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    Canvas(modifier = modifier.fillMaxWidth().height(118.dp)) {
        val w = size.width
        val h = size.height
        val x0 = w * 0.06f
        val gaugeW = w - x0 * 2f
        val baselineY = h * 0.70f

        drawRoundRect(
            color = PanelBorder.copy(alpha = 0.55f),
            topLeft = Offset(x0, baselineY - 1.5.dp.toPx()),
            size = Size(gaugeW, 3.dp.toPx()),
            cornerRadius = CornerRadius(2.dp.toPx()),
        )

        val zoneHalf = gaugeW * (5.0f / 100.0f)
        drawRoundRect(
            color = InTuneGreen.copy(alpha = 0.5f),
            topLeft = Offset(w * 0.5f - zoneHalf, baselineY - 3.dp.toPx()),
            size = Size(zoneHalf * 2f, 6.dp.toPx()),
            cornerRadius = CornerRadius(3.dp.toPx()),
        )

        for (c in -50..50 step 5) {
            val x = x0 + ((c + 50f) / 100f) * gaugeW
            val tickHeight = when {
                c % 25 == 0 -> 16.dp.toPx()
                c % 10 == 0 -> 11.dp.toPx()
                else -> 7.dp.toPx()
            }
            val tickColor = if (c == 0) {
                Color.White.copy(alpha = 0.75f)
            } else {
                MutedText.copy(alpha = 0.45f)
            }
            drawLine(
                color = tickColor,
                start = Offset(x, baselineY - tickHeight * 0.5f),
                end = Offset(x, baselineY + tickHeight * 0.5f),
                strokeWidth = 1.dp.toPx(),
            )
        }

        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            typeface = android.graphics.Typeface.MONOSPACE
            textSize = with(density) { 10.sp.toPx() }
            textAlign = android.graphics.Paint.Align.CENTER
            color = MutedText.copy(alpha = 0.7f).toArgb()
        }
        val labelY = baselineY + 26.dp.toPx()
        drawContext.canvas.nativeCanvas.apply {
            drawText("-50", x0, labelY, paint)
            drawText("0", w * 0.5f, labelY, paint)
            drawText("+50", x0 + gaugeW, labelY, paint)
        }

        if (hasPitch) {
            val clamped = cents.coerceIn(-50.0, 50.0)
            val x = x0 + ((clamped + 50.0) / 100.0).toFloat() * gaugeW
            val color = centsColor(cents, true)

            drawLine(
                color = color.copy(alpha = 0.35f),
                start = Offset(x, baselineY - 46.dp.toPx()),
                end = Offset(x, baselineY - 6.dp.toPx()),
                strokeWidth = 8.dp.toPx(),
            )
            drawLine(
                color = color,
                start = Offset(x, baselineY - 46.dp.toPx()),
                end = Offset(x, baselineY - 6.dp.toPx()),
                strokeWidth = 2.dp.toPx(),
            )

            val triangle = Path().apply {
                moveTo(x - 8.dp.toPx(), baselineY - 52.dp.toPx())
                lineTo(x + 8.dp.toPx(), baselineY - 52.dp.toPx())
                lineTo(x, baselineY - 39.dp.toPx())
                close()
            }
            drawPath(triangle, color)
        }
    }
}

@Composable
fun StrobeBand(
    cents: Double,
    hasPitch: Boolean,
    inTune: Boolean,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val periodPx = with(density) { 22.dp.toPx() }
    var phase by remember { mutableStateOf(0f) }
    val latestCents by rememberUpdatedState(cents)
    val latestHasPitch by rememberUpdatedState(hasPitch)

    LaunchedEffect(Unit) {
        var last = withFrameNanos { it }
        while (true) {
            val now = withFrameNanos { it }
            val dt = ((now - last) / 1_000_000_000.0).toFloat()
            last = now

            if (!latestHasPitch) {
                phase = 0f
                continue
            }

            if (abs(latestCents) > 0.2) {
                phase = (phase + dt * latestCents.toFloat() * 2.2f) % periodPx
                if (phase < 0f) phase += periodPx
            } else {
                val nearest = if (phase > periodPx * 0.5f) phase - periodPx else phase
                phase = nearest * 0.82f
                if (phase < 0f) phase += periodPx
            }
        }
    }

    Canvas(modifier = modifier.fillMaxWidth().height(52.dp)) {
        val w = size.width
        val h = size.height
        drawRoundRect(
            color = PanelColor,
            size = size,
            cornerRadius = CornerRadius(16.dp.toPx()),
        )
        drawRoundRect(
            color = PanelBorder,
            size = size,
            cornerRadius = CornerRadius(16.dp.toPx()),
            style = Stroke(1.dp.toPx()),
        )

        val stripeW = 9.dp.toPx()
        val stripeH = h * 0.52f
        val stripeY = (h - stripeH) * 0.5f
        val stripeColor = when {
            !hasPitch -> MutedText.copy(alpha = 0.16f)
            inTune || abs(cents) <= 2.0 -> InTuneGreen.copy(alpha = 0.8f)
            abs(cents) <= 15.0 -> WarnYellow.copy(alpha = 0.72f)
            else -> ErrorRed.copy(alpha = 0.62f)
        }

        val base = w * 0.5f - stripeW * 0.5f
        var x = base - periodPx * 3f + phase
        while (x < w + periodPx) {
            if (x + stripeW > 0f) {
                drawRoundRect(
                    color = stripeColor,
                    topLeft = Offset(x, stripeY),
                    size = Size(stripeW, stripeH),
                    cornerRadius = CornerRadius(stripeW * 0.5f),
                )
            }
            x += periodPx
        }

        drawLine(
            color = Color.White.copy(alpha = 0.8f),
            start = Offset(w * 0.5f, h * 0.14f),
            end = Offset(w * 0.5f, h * 0.86f),
            strokeWidth = 2.dp.toPx(),
        )
    }
}

@Composable
fun LevelMeter(
    level: Double,
    modifier: Modifier = Modifier,
    label: String = "IN",
    segments: Int = 16,
    activeColor: Color = InTuneGreen,
    clip: Boolean = false,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MutedText,
        )
        repeat(segments) { index ->
            val lit = level > (index + 1).toDouble() / segments
            Box(
                modifier = Modifier
                    .width(5.dp)
                    .height(if (index % 4 == 0) 12.dp else 8.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(if (lit) activeColor else PanelBorder),
            )
        }
        if (clip) {
            Text(
                text = "CLIP",
                style = MaterialTheme.typography.labelSmall,
                color = ErrorRed,
            )
        }
    }
}
