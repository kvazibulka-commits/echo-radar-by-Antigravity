package ru.echo.radar.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import ru.echo.radar.domain.model.EchoSignal
import ru.echo.radar.ui.theme.LocalEchoColors
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

@Composable
fun IndustrialRadarCanvas(
    signals: List<EchoSignal>,
    selectedSignalId: String?,
    onSignalSelected: (EchoSignal) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalEchoColors.current

    // Continuous sweep animation
    val infiniteTransition = rememberInfiniteTransition(label = "radar_sweep")
    val sweepAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 4000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "sweep_angle"
    )

    // Pulse animation for signals
    val pulseProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "signal_pulse"
    )

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .pointerInput(signals) {
                detectTapGestures { tapOffset ->
                    val cx = size.width / 2f
                    val cy = size.height / 2f
                    val maxRadius = size.width * 0.44f

                    val clicked = signals.minByOrNull { sig ->
                        val rad = (sig.angleDegrees - 90f) * (PI.toFloat() / 180f)
                        val r = sig.normalizedRadius * maxRadius
                        val bx = cx + r * cos(rad)
                        val by = cy + r * sin(rad)
                        hypot(tapOffset.x - bx, tapOffset.y - by)
                    }

                    if (clicked != null) {
                        val rad = (clicked.angleDegrees - 90f) * (PI.toFloat() / 180f)
                        val r = clicked.normalizedRadius * maxRadius
                        val bx = cx + r * cos(rad)
                        val by = cy + r * sin(rad)
                        if (hypot(tapOffset.x - bx, tapOffset.y - by) < 80f) {
                            onSignalSelected(clicked)
                        }
                    }
                }
            }
    ) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val maxRadius = size.width * 0.44f

        // 1. Concentric circles (50m, 150m, 300m, 500m)
        val rings = listOf(0.25f, 0.5f, 0.75f, 1.0f)
        val dashEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)

        rings.forEach { ring ->
            drawCircle(
                color = colors.radarGrid,
                radius = maxRadius * ring,
                center = Offset(cx, cy),
                style = Stroke(width = 1.dp.toPx(), pathEffect = dashEffect)
            )
        }

        // 2. Crosshair grid lines
        drawLine(
            color = colors.radarGrid,
            start = Offset(cx - maxRadius, cy),
            end = Offset(cx + maxRadius, cy),
            strokeWidth = 1.dp.toPx(),
            pathEffect = dashEffect
        )
        drawLine(
            color = colors.radarGrid,
            start = Offset(cx, cy - maxRadius),
            end = Offset(cx, cy + maxRadius),
            strokeWidth = 1.dp.toPx(),
            pathEffect = dashEffect
        )

        // 3. Sweeping ray
        val sweepRad = (sweepAngle - 90f) * (PI.toFloat() / 180f)
        val rayEnd = Offset(
            x = cx + maxRadius * cos(sweepRad),
            y = cy + maxRadius * sin(sweepRad)
        )
        drawLine(
            color = colors.accent,
            start = Offset(cx, cy),
            end = rayEnd,
            strokeWidth = 2.dp.toPx()
        )

        // 4. Center User Beacon
        drawCircle(
            color = colors.accent,
            radius = 6.dp.toPx(),
            center = Offset(cx, cy)
        )
        drawCircle(
            color = Color.White,
            radius = 6.dp.toPx(),
            center = Offset(cx, cy),
            style = Stroke(width = 2.dp.toPx())
        )

        // 5. Signals (Blips)
        signals.forEach { sig ->
            val rad = (sig.angleDegrees - 90f) * (PI.toFloat() / 180f)
            val r = sig.normalizedRadius * maxRadius
            val bx = cx + r * cos(rad)
            val by = cy + r * sin(rad)
            val isSelected = sig.id == selectedSignalId

            val blipColor = when (sig.source.name) {
                "BEACON_VENUE" -> colors.venueGold
                "TIME_CAPSULE" -> colors.capsuleEmerald
                else -> if (isSelected) colors.accent else colors.accentSecondary
            }

            // Pulsing ring
            drawCircle(
                color = blipColor.copy(alpha = 1f - pulseProgress),
                radius = (8.dp + 16.dp * pulseProgress).toPx(),
                center = Offset(bx, by),
                style = Stroke(width = 1.5.dp.toPx())
            )

            // Blip core
            drawCircle(
                color = blipColor,
                radius = if (isSelected) 8.dp.toPx() else 6.dp.toPx(),
                center = Offset(bx, by)
            )
            drawCircle(
                color = Color.White,
                radius = if (isSelected) 8.dp.toPx() else 6.dp.toPx(),
                center = Offset(bx, by),
                style = Stroke(width = if (isSelected) 2.5.dp.toPx() else 1.5.dp.toPx())
            )
        }
    }
}
