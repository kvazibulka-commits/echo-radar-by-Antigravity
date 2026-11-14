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
import androidx.compose.ui.graphics.drawscope.rotate
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

    // Smooth continuous 12s linear sweep rotation (360 deg)
    val infiniteTransition = rememberInfiniteTransition(label = "atlas_sweep")
    val sweepAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 10000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "sweep_angle"
    )

    // Concentric sonar ping pulse expanding every 2.8 seconds
    val pingProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2800, easing = LinearEasing),
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
                        val rad = s.angleDegrees * (PI.toFloat() / 180f)
                        val r = s.normalizedRadius * maxR
                        val bx = cx + r * cos(rad)
                        val by = cy + r * sin(rad)
                        hypot(tapOffset.x - bx, tapOffset.y - by)
                    }

                    if (clickedSignal != null) {
                        val rad = clickedSignal.angleDegrees * (PI.toFloat() / 180f)
                        val r = clickedSignal.normalizedRadius * maxR
                        val bx = cx + r * cos(rad)
                        val by = cy + r * sin(rad)
                        if (hypot(tapOffset.x - bx, tapOffset.y - by) < 70f) {
                            onSignalSelected(clickedSignal)
                            return@detectTapGestures
                        }
                    }

                    // Check venue beacon taps
                    val clickedVenue = venueBeacons.minByOrNull { v ->
                        val rad = v.angleDegrees * (PI.toFloat() / 180f)
                        val r = v.normalizedRadius * maxR
                        val bx = cx + r * cos(rad)
                        val by = cy + r * sin(rad)
                        hypot(tapOffset.x - bx, tapOffset.y - by)
                    }

                    if (clickedVenue != null) {
                        val rad = clickedVenue.angleDegrees * (PI.toFloat() / 180f)
                        val r = clickedVenue.normalizedRadius * maxR
                        val bx = cx + r * cos(rad)
                        val by = cy + r * sin(rad)
                        if (hypot(tapOffset.x - bx, tapOffset.y - by) < 70f) {
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

        // Subtle paper / metal background disk
        drawCircle(
            color = colors.surface.copy(alpha = 0.5f),
            radius = r4,
            center = Offset(cx, cy)
        )

        // Ring 4 (accent color with 0.4 alpha)
        drawCircle(
            color = colors.accent.copy(alpha = 0.45f),
            radius = r4,
            center = Offset(cx, cy),
            style = Stroke(width = 1.2.dp.toPx())
        )

        // Ring 3 (lineStrong with 0.7 alpha)
        drawCircle(
            color = colors.lineStrong.copy(alpha = 0.75f),
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
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 5.dp.toPx()), 0f)
            )
        )

        // Ring 1 (solid line)
        drawCircle(
            color = colors.line,
            radius = r1,
            center = Offset(cx, cy),
            style = Stroke(width = 1.dp.toPx())
        )

        // Expanding concentric sonar pulse wave
        val waveRadius = r4 * pingProgress
        drawCircle(
            color = colors.accent.copy(alpha = (1f - pingProgress) * 0.35f),
            radius = waveRadius,
            center = Offset(cx, cy),
            style = Stroke(width = 1.5.dp.toPx())
        )

        // Crosshairs: dashed lines (2dp, 6dp)
        val crosshairEffect = PathEffect.dashPathEffect(floatArrayOf(2.5.dp.toPx(), 6.dp.toPx()), 0f)
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
        val tickLen = 7f * unit
        // North & South
        drawLine(colors.faint, Offset(cx, cy - r4), Offset(cx, cy - r4 + tickLen), strokeWidth = 1.dp.toPx())
        drawLine(colors.faint, Offset(cx, cy + r4 - tickLen), Offset(cx, cy + r4), strokeWidth = 1.dp.toPx())
        // West & East
        drawLine(colors.faint, Offset(cx - r4, cy), Offset(cx - r4 + tickLen, cy), strokeWidth = 1.dp.toPx())
        drawLine(colors.faint, Offset(cx + r4 - tickLen, cy), Offset(cx + r4, cy), strokeWidth = 1.dp.toPx())

        // ═══════════════════════════════════════════════════════
        // ACTIVE ROTATING RADAR SWEEP (EXPLICIT ROTATION MATRIX)
        // ═══════════════════════════════════════════════════════
        rotate(degrees = sweepAngle, pivot = Offset(cx, cy)) {
            // Luminous conic gradient trailing sector
            val sweepColor = colors.accent.copy(alpha = if (colors.isEvening) 0.55f else 0.32f)
            val sweepBrush = Brush.sweepGradient(
                0.0f to Color.Transparent,
                0.80f to Color.Transparent,
                0.93f to sweepColor.copy(alpha = 0.08f),
                1.0f to sweepColor,
                center = Offset(cx, cy)
            )

            drawCircle(
                brush = sweepBrush,
                radius = r4,
                center = Offset(cx, cy)
            )

            // Bright scanning ray line from center outwards
            drawLine(
                brush = Brush.horizontalGradient(
                    colors = listOf(colors.live.copy(alpha = 0.2f), colors.live),
                    startX = cx,
                    endX = cx + r4
                ),
                start = Offset(cx, cy),
                end = Offset(cx + r4, cy),
                strokeWidth = 2.dp.toPx()
            )
        }

        // Center User Core Dot (live pulse)
        drawCircle(
            color = colors.live.copy(alpha = 0.3f),
            radius = rCore + 4.dp.toPx(),
            center = Offset(cx, cy)
        )
        drawCircle(
            color = colors.live,
            radius = rCore,
            center = Offset(cx, cy)
        )
        drawCircle(
            color = colors.background,
            radius = rCore - 3.dp.toPx(),
            center = Offset(cx, cy)
        )

        // Live dot ping expanding ring (for first active signal)
        val liveSignal = signals.firstOrNull()
        if (liveSignal != null) {
            val a = liveSignal.angleDegrees * (PI.toFloat() / 180f)
            val r = liveSignal.normalizedRadius * maxR
            val lx = cx + r * cos(a)
            val ly = cy + r * sin(a)

            drawCircle(
                color = colors.live.copy(alpha = (1f - pingProgress) * 0.6f),
                radius = (8.dp.toPx() + 20.dp.toPx() * pingProgress),
                center = Offset(lx, ly),
                style = Stroke(width = 1.2.dp.toPx())
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

            val baseRadius = if (isMuted) 3.5.dp.toPx() else (if (isSelected) 6.dp.toPx() else 4.5.dp.toPx())
            val dotColor = if (isMuted) colors.live.copy(alpha = 0.45f) else colors.live

            // Subtle glow aura if selected
            if (isSelected) {
                drawCircle(
                    color = colors.live.copy(alpha = 0.25f),
                    radius = baseRadius + 6.dp.toPx(),
                    center = Offset(sx, sy)
                )
            }

            // White/background 2.5dp border
            drawCircle(
                color = colors.background,
                radius = baseRadius + 2.5.dp.toPx(),
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
                        textSize = 8.5.dp.toPx()
                        typeface = android.graphics.Typeface.MONOSPACE
                        isAntiAlias = true
                    }
                    val bgPaint = Paint().apply {
                        color = android.graphics.Color.argb(
                            230,
                            (colors.surface.red * 255).toInt(),
                            (colors.surface.green * 255).toInt(),
                            (colors.surface.blue * 255).toInt()
                        )
                    }
                    val borderPaint = Paint().apply {
                        color = android.graphics.Color.argb(
                            180,
                            (colors.line.red * 255).toInt(),
                            (colors.line.green * 255).toInt(),
                            (colors.line.blue * 255).toInt()
                        )
                        style = Paint.Style.STROKE
                        strokeWidth = 1.dp.toPx()
                        isAntiAlias = true
                    }

                    val textWidth = paint.measureText(labelText)
                    val labelX = if (sx > cx) sx - textWidth - 14.dp.toPx() else sx + 12.dp.toPx()
                    val labelY = sy - 4.dp.toPx()

                    val rect = RectF(
                        labelX - 5.dp.toPx(),
                        labelY - 10.dp.toPx(),
                        labelX + textWidth + 5.dp.toPx(),
                        labelY + 4.dp.toPx()
                    )
                    canvas.nativeCanvas.drawRoundRect(rect, 4.dp.toPx(), 4.dp.toPx(), bgPaint)
                    canvas.nativeCanvas.drawRoundRect(rect, 4.dp.toPx(), 4.dp.toPx(), borderPaint)
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

                // 2.5dp border in background color
                val borderSide = 9.dp.toPx()
                drawRect(
                    color = colors.background,
                    topLeft = Offset(-borderSide / 2, -borderSide / 2),
                    size = Size(borderSide, borderSide)
                )

                // Gold diamond fill
                val side = 7.dp.toPx()
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
                        textSize = 8.5.dp.toPx()
                        typeface = android.graphics.Typeface.MONOSPACE
                        isAntiAlias = true
                    }
                    val bgPaint = Paint().apply {
                        color = android.graphics.Color.argb(
                            230,
                            (colors.surface.red * 255).toInt(),
                            (colors.surface.green * 255).toInt(),
                            (colors.surface.blue * 255).toInt()
                        )
                    }
                    val borderPaint = Paint().apply {
                        color = android.graphics.Color.argb(
                            180,
                            (colors.line.red * 255).toInt(),
                            (colors.line.green * 255).toInt(),
                            (colors.line.blue * 255).toInt()
                        )
                        style = Paint.Style.STROKE
                        strokeWidth = 1.dp.toPx()
                        isAntiAlias = true
                    }

                    val textWidth = paint.measureText(v.label)
                    val labelX = if (vx > cx) vx - textWidth - 14.dp.toPx() else vx + 12.dp.toPx()
                    val labelY = vy - 4.dp.toPx()

                    val rect = RectF(
                        labelX - 5.dp.toPx(),
                        labelY - 10.dp.toPx(),
                        labelX + textWidth + 5.dp.toPx(),
                        labelY + 4.dp.toPx()
                    )
                    canvas.nativeCanvas.drawRoundRect(rect, 4.dp.toPx(), 4.dp.toPx(), bgPaint)
                    canvas.nativeCanvas.drawRoundRect(rect, 4.dp.toPx(), 4.dp.toPx(), borderPaint)
                    canvas.nativeCanvas.drawText(v.label, labelX, labelY, paint)
                }
            }
        }
    }
}
