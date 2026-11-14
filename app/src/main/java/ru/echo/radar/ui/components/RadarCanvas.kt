package ru.echo.radar.ui.components

import android.graphics.Paint
import android.graphics.RectF
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import ru.echo.radar.domain.model.EchoSignal
import ru.echo.radar.domain.model.VenueBeacon
import ru.echo.radar.ui.theme.LocalEchoColors
import kotlin.math.*

@Composable
fun AtlasRadarCanvas(
    signals: List<EchoSignal>,
    venueBeacons: List<VenueBeacon>,
    selectedSignalId: String?,
    onSignalSelected: (EchoSignal) -> Unit,
    onBeaconSelected: (VenueBeacon) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalEchoColors.current

    // 16s linear sweep rotation matching design stand
    val infiniteTransition = rememberInfiniteTransition(label = "atlas_sweep")
    val sweepAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 16000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "sweep_angle"
    )

    // 3s ping pulse for live dot
    val pingProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ping_pulse"
    )

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .pointerInput(signals, venueBeacons) {
                detectTapGestures { tapOffset ->
                    val cx = size.width / 2f
                    val cy = size.height / 2f
                    val maxR = size.width * 0.44f

                    // Check signal taps
                    val clickedSignal = signals.minByOrNull { s ->
                        val rad = (s.angleDegrees) * (PI.toFloat() / 180f)
                        val r = s.normalizedRadius * maxR
                        val bx = cx + r * cos(rad)
                        val by = cy + r * sin(rad)
                        hypot(tapOffset.x - bx, tapOffset.y - by)
                    }

                    if (clickedSignal != null) {
                        val rad = (clickedSignal.angleDegrees) * (PI.toFloat() / 180f)
                        val r = clickedSignal.normalizedRadius * maxR
                        val bx = cx + r * cos(rad)
                        val by = cy + r * sin(rad)
                        if (hypot(tapOffset.x - bx, tapOffset.y - by) < 60f) {
                            onSignalSelected(clickedSignal)
                            return@detectTapGestures
                        }
                    }

                    // Check venue beacon taps
                    val clickedVenue = venueBeacons.minByOrNull { v ->
                        val rad = (v.angleDegrees) * (PI.toFloat() / 180f)
                        val r = v.normalizedRadius * maxR
                        val bx = cx + r * cos(rad)
                        val by = cy + r * sin(rad)
                        hypot(tapOffset.x - bx, tapOffset.y - by)
                    }

                    if (clickedVenue != null) {
                        val rad = (clickedVenue.angleDegrees) * (PI.toFloat() / 180f)
                        val r = clickedVenue.normalizedRadius * maxR
                        val bx = cx + r * cos(rad)
                        val by = cy + r * sin(rad)
                        if (hypot(tapOffset.x - bx, tapOffset.y - by) < 60f) {
                            onBeaconSelected(clickedVenue)
                        }
                    }
                }
            }
    ) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val unit = size.width / 300f
        val maxR = size.width * 0.44f

        // Topographic Rings (r4 = 132, r3 = 103, r2 = 74, r1 = 45, core = 10 in 300x300 viewBox)
        val r4 = 132f * unit
        val r3 = 103f * unit
        val r2 = 74f * unit
        val r1 = 45f * unit
        val rCore = 10f * unit

        // Ring 4 (accent color with 0.4 alpha)
        drawCircle(
            color = colors.accent.copy(alpha = 0.4f),
            radius = r4,
            center = Offset(cx, cy),
            style = Stroke(width = 1.dp.toPx())
        )

        // Ring 3 (lineStrong with 0.7 alpha)
        drawCircle(
            color = colors.lineStrong.copy(alpha = 0.7f),
            radius = r3,
            center = Offset(cx, cy),
            style = Stroke(width = 1.dp.toPx())
        )

        // Ring 2 (dashed line 2dp 5dp)
        drawCircle(
            color = colors.line,
            radius = r2,
            center = Offset(cx, cy),
            style = Stroke(
                width = 1.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(2.dp.toPx(), 5.dp.toPx()), 0f)
            )
        )

        // Ring 1 (solid line)
        drawCircle(
            color = colors.line,
            radius = r1,
            center = Offset(cx, cy),
            style = Stroke(width = 1.dp.toPx())
        )

        // Core Dot
        drawCircle(
            color = colors.live,
            radius = rCore,
            center = Offset(cx, cy)
        )

        // Crosshairs: dashed lines (2dp, 6dp)
        val crosshairEffect = PathEffect.dashPathEffect(floatArrayOf(2.dp.toPx(), 6.dp.toPx()), 0f)
        drawLine(
            color = colors.line,
            start = Offset(cx, cy - r4),
            end = Offset(cx, cy + r4),
            strokeWidth = 1.dp.toPx(),
            pathEffect = crosshairEffect
        )
        drawLine(
            color = colors.line,
            start = Offset(cx - r4, cy),
            end = Offset(cx + r4, cy),
            strokeWidth = 1.dp.toPx(),
            pathEffect = crosshairEffect
        )

        // Axis and diagonal ticks
        val tickLen = 6f * unit
        // North & South
        drawLine(colors.faint, Offset(cx, cy - r4), Offset(cx, cy - r4 + tickLen), strokeWidth = 1.dp.toPx())
        drawLine(colors.faint, Offset(cx, cy + r4 - tickLen), Offset(cx, cy + r4), strokeWidth = 1.dp.toPx())
        // West & East
        drawLine(colors.faint, Offset(cx - r4, cy), Offset(cx - r4 + tickLen, cy), strokeWidth = 1.dp.toPx())
        drawLine(colors.faint, Offset(cx + r4 - tickLen, cy), Offset(cx + r4, cy), strokeWidth = 1.dp.toPx())

        // Radar Sweep (Conic sector)
        val sweepRad = sweepAngle * (PI.toFloat() / 180f)
        val sweepColor = colors.accent.copy(alpha = if (colors.isEvening) 0.55f else 0.28f)
        val sweepBrush = Brush.sweepGradient(
            0.0f to Color.Transparent,
            0.82f to Color.Transparent,
            0.94f to sweepColor.copy(alpha = 0.08f),
            1.0f to sweepColor,
            center = Offset(cx, cy)
        )

        drawCircle(
            brush = sweepBrush,
            radius = r4,
            center = Offset(cx, cy)
        )

        // Live dot ping expanding ring
        val liveSignal = signals.firstOrNull()
        if (liveSignal != null) {
            val a = liveSignal.angleDegrees * (PI.toFloat() / 180f)
            val r = liveSignal.normalizedRadius * maxR
            val lx = cx + r * cos(a)
            val ly = cy + r * sin(a)

            drawCircle(
                color = colors.live.copy(alpha = (1f - pingProgress) * 0.5f),
                radius = (8.dp.toPx() + 18.dp.toPx() * pingProgress),
                center = Offset(lx, ly),
                style = Stroke(width = 1.dp.toPx())
            )
        }

        // Draw Signals
        signals.forEachIndexed { i, sig ->
            val a = sig.angleDegrees * (PI.toFloat() / 180f)
            val r = sig.normalizedRadius * maxR
            val sx = cx + r * cos(a)
            val sy = cy + r * sin(a)
            val isMuted = i == 5
            val isSelected = sig.id == selectedSignalId

            val baseRadius = if (isMuted) 3.dp.toPx() else (if (isSelected) 5.dp.toPx() else 4.dp.toPx())
            val dotColor = if (isMuted) colors.live.copy(alpha = 0.45f) else colors.live

            // White/background 2dp border
            drawCircle(
                color = colors.background,
                radius = baseRadius + 2.dp.toPx(),
                center = Offset(sx, sy)
            )
            // Core
            drawCircle(
                color = dotColor,
                radius = baseRadius,
                center = Offset(sx, sy)
            )

            // Label pill if available
            sig.label?.let { labelText ->
                drawIntoCanvas { canvas ->
                    val paint = Paint().apply {
                        color = android.graphics.Color.argb(
                            (colors.sub.alpha * 255).toInt(),
                            (colors.sub.red * 255).toInt(),
                            (colors.sub.green * 255).toInt(),
                            (colors.sub.blue * 255).toInt()
                        )
                        textSize = 8.dp.toPx()
                        typeface = android.graphics.Typeface.MONOSPACE
                        isAntiAlias = true
                    }
                    val bgPaint = Paint().apply {
                        color = android.graphics.Color.argb(
                            215,
                            (colors.background.red * 255).toInt(),
                            (colors.background.green * 255).toInt(),
                            (colors.background.blue * 255).toInt()
                        )
                    }

                    val textWidth = paint.measureText(labelText)
                    val labelX = if (sx > cx) sx - textWidth - 12.dp.toPx() else sx + 10.dp.toPx()
                    val labelY = sy - 4.dp.toPx()

                    canvas.nativeCanvas.drawRoundRect(
                        RectF(labelX - 4.dp.toPx(), labelY - 9.dp.toPx(), labelX + textWidth + 4.dp.toPx(), labelY + 3.dp.toPx()),
                        3.dp.toPx(),
                        3.dp.toPx(),
                        bgPaint
                    )
                    canvas.nativeCanvas.drawText(labelText, labelX, labelY, paint)
                }
            }
        }

        // Draw Venue Beacons / Capsules (Diamond shape: square rotated 45°)
        venueBeacons.forEach { v ->
            val a = v.angleDegrees * (PI.toFloat() / 180f)
            val r = v.normalizedRadius * maxR
            val vx = cx + r * cos(a)
            val vy = cy + r * sin(a)

            drawIntoCanvas { canvas ->
                canvas.save()
                canvas.translate(vx, vy)
                canvas.rotate(45f)

                // 2dp border in background color
                val borderSide = 8.dp.toPx()
                drawRect(
                    color = colors.background,
                    topLeft = Offset(-borderSide / 2, -borderSide / 2),
                    size = Size(borderSide, borderSide)
                )

                // Gold diamond fill
                val side = 6.5.dp.toPx()
                drawRect(
                    color = colors.gold,
                    topLeft = Offset(-side / 2, -side / 2),
                    size = Size(side, side)
                )

                canvas.restore()
            }

            // Venue Label
            if (v.label.isNotEmpty()) {
                drawIntoCanvas { canvas ->
                    val paint = Paint().apply {
                        color = android.graphics.Color.argb(
                            (colors.sub.alpha * 255).toInt(),
                            (colors.sub.red * 255).toInt(),
                            (colors.sub.green * 255).toInt(),
                            (colors.sub.blue * 255).toInt()
                        )
                        textSize = 8.dp.toPx()
                        typeface = android.graphics.Typeface.MONOSPACE
                        isAntiAlias = true
                    }
                    val bgPaint = Paint().apply {
                        color = android.graphics.Color.argb(
                            215,
                            (colors.background.red * 255).toInt(),
                            (colors.background.green * 255).toInt(),
                            (colors.background.blue * 255).toInt()
                        )
                    }

                    val textWidth = paint.measureText(v.label)
                    val labelX = if (vx > cx) vx - textWidth - 12.dp.toPx() else vx + 10.dp.toPx()
                    val labelY = vy - 4.dp.toPx()

                    canvas.nativeCanvas.drawRoundRect(
                        RectF(labelX - 4.dp.toPx(), labelY - 9.dp.toPx(), labelX + textWidth + 4.dp.toPx(), labelY + 3.dp.toPx()),
                        3.dp.toPx(),
                        3.dp.toPx(),
                        bgPaint
                    )
                    canvas.nativeCanvas.drawText(v.label, labelX, labelY, paint)
                }
            }
        }
    }
}
