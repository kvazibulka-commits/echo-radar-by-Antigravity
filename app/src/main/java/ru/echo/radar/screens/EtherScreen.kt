package ru.echo.radar.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.echo.radar.EchoCapsule
import ru.echo.radar.NearbyEchoSignal
import ru.echo.radar.components.ResonanceBadge
import ru.echo.radar.components.SignalIndicator
import ru.echo.radar.components.TactileButton
import ru.echo.radar.state.AppSessionState
import ru.echo.radar.state.EchoModalSheet
import ru.echo.radar.theme.EchoTokens
import ru.echo.radar.theme.EchoTypography
import kotlin.math.cos
import kotlin.math.sin

/**
 * SCREEN 01 — ETHER / ЭФИР
 * Пространство музыкального присутствия.
 * Живой акустический сонар, непрерывно исследующий ночной эфир вокруг слушателя.
 * В строгом соответствии с разделом 03 MASTER PROMPT:
 * - Полноразмерный SonarCanvas с концентрическими кольцами 50-200м.
 * - Вращающийся сонарный луч RadarSweep (период 6 секунд) с шлейфом затухания.
 * - Точки присутствия сигналов, капсул, тепловых следов и Точки Лагранжа.
 */
@Composable
fun EtherScreen(
    state: AppSessionState,
    onOpenSheet: (EchoModalSheet) -> Unit,
    onTogglePresence: () -> Unit,
    onSendWarmth: (targetId: String, x: Float, y: Float) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(EchoTokens.Background)
            .statusBarsPadding()
    ) {
        // --- HEADER: NIGHT / PRESENCE ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(text = "ЭХО", style = EchoTypography.Display.copy(fontSize = 20.sp, letterSpacing = 2.sp))
                    SignalIndicator(isActive = state.isPresenceActive)
                }
                Text(text = "NIGHT / PRESENCE", style = EchoTypography.Label, color = EchoTokens.SignalCyan)
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                // Статус соединения и обнаружения
                Box(
                    modifier = Modifier
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { onOpenSheet(EchoModalSheet.ServerSettingsDialog) }
                        )
                ) {
                    Text(
                        text = state.connectionStatus,
                        style = EchoTypography.Technical.copy(fontSize = 11.sp),
                        color = EchoTokens.TextSecondary
                    )
                }

                // Тумблер присутствия
                TactileButton(
                    text = if (state.isPresenceActive) "В ЭФИРЕ" else "ТИХИЙ",
                    onClick = onTogglePresence,
                    accentColor = if (state.isPresenceActive) EchoTokens.SignalCyan else EchoTokens.TextMuted,
                    isPrimary = state.isPresenceActive,
                    modifier = Modifier.height(34.dp)
                )
            }
        }

        // --- MAIN: LIVING ACOUSTIC SONAR ---
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentAlignment = Alignment.Center
        ) {
            SonarCanvas(
                signals = state.nearbySignals,
                capsules = state.capsules,
                lagrangePoint = state.lagrangePoint,
                onSignalClick = { onOpenSheet(EchoModalSheet.ResonanceDetail(it)) },
                onCapsuleClick = { onOpenSheet(EchoModalSheet.CapsuleDetail(it)) },
                onLagrangeClick = { state.lagrangePoint?.let { onOpenSheet(EchoModalSheet.LagrangeDetail(it)) } },
                onBackgroundTap = { offset, size ->
                    // Посылаем призрачное тепло на холст
                    val normX = offset.x / size.width
                    val normY = offset.y / size.height
                    onSendWarmth("broadcast", normX, normY)
                }
            )

            // Баннер входящего отклика
            state.incomingReaction?.let { reaction ->
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 16.dp, start = 20.dp, end = 20.dp)
                ) {
                    IncomingReactionBanner(reaction = reaction)
                }
            }
        }
    }
}

@Composable
private fun SonarCanvas(
    signals: List<NearbyEchoSignal>,
    capsules: List<EchoCapsule>,
    lagrangePoint: ru.echo.radar.LagrangePointInfo?,
    onSignalClick: (NearbyEchoSignal) -> Unit,
    onCapsuleClick: (EchoCapsule) -> Unit,
    onLagrangeClick: () -> Unit,
    onBackgroundTap: (Offset, androidx.compose.ui.geometry.Size) -> Unit
) {
    // Бесконечная плавная анимация сканирующего луча сонара (6 секунд на полный оборот)
    val infiniteTransition = rememberInfiniteTransition(label = "sonar_sweep_transition")
    val sweepAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 6000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "sweep_angle"
    )

    // Текстовый измеритель для дистанционных отметок на кольцах
    val textMeasurer = rememberTextMeasurer()

    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(signals, capsules) {
                detectTapGestures { tapOffset ->
                    val cx = size.width / 2f
                    val cy = size.height / 2f
                    val maxR = minOf(cx, cy) * 0.92f

                    // Проверяем нажатие по сигналам слушателей
                    val clickedSignal = signals.firstOrNull { s ->
                        val rad = Math.toRadians((s.angleDeg - 90.0)).toFloat()
                        val r = s.ringFactor * maxR
                        val sx = cx + r * cos(rad)
                        val sy = cy + r * sin(rad)
                        (Offset(sx, sy) - tapOffset).getDistance() < 42f
                    }
                    if (clickedSignal != null) {
                        onSignalClick(clickedSignal)
                        return@detectTapGestures
                    }

                    // Проверяем нажатие по капсулам-тайникам
                    val clickedCapsule = capsules.firstOrNull { c ->
                        val rad = Math.toRadians((c.angleDeg - 90.0)).toFloat()
                        val r = c.ringFactor * maxR
                        val cxPos = cx + r * cos(rad)
                        val cyPos = cy + r * sin(rad)
                        (Offset(cxPos, cyPos) - tapOffset).getDistance() < 36f
                    }
                    if (clickedCapsule != null) {
                        onCapsuleClick(clickedCapsule)
                        return@detectTapGestures
                    }

                    // Тап по фону для отправки призрачного тепла
                    onBackgroundTap(tapOffset, androidx.compose.ui.geometry.Size(size.width.toFloat(), size.height.toFloat()))
                }
            }
    ) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val maxR = minOf(cx, cy) * 0.92f

        // 1. Концентрические кольца дистанции (50м, 100м, 150м, 200м)
        val rings = listOf(0.25f to "50м", 0.50f to "100м", 0.75f to "150м", 1.0f to "200м")
        rings.forEach { (factor, label) ->
            val r = maxR * factor
            drawCircle(
                color = EchoTokens.Divider.copy(alpha = 0.5f),
                radius = r,
                center = Offset(cx, cy),
                style = Stroke(width = 1f)
            )
            // Метка дистанции
            drawText(
                textMeasurer = textMeasurer,
                text = label,
                topLeft = Offset(cx + 4f, cy - r + 3f),
                style = EchoTypography.Label.copy(color = EchoTokens.TextMuted, fontSize = 9.sp)
            )
        }

        // 2. Тонкие координатные оси
        drawLine(
            color = EchoTokens.Divider.copy(alpha = 0.35f),
            start = Offset(cx - maxR, cy),
            end = Offset(cx + maxR, cy),
            strokeWidth = 0.8f
        )
        drawLine(
            color = EchoTokens.Divider.copy(alpha = 0.35f),
            start = Offset(cx, cy - maxR),
            end = Offset(cx, cy + maxR),
            strokeWidth = 0.8f
        )

        // 3. ВРАЩАЮЩИЙСЯ СОНАРНЫЙ ЛУЧ (RadarSweep) со шлейфом затухания
        rotate(sweepAngle, pivot = Offset(cx, cy)) {
            val sweepBrush = Brush.sweepGradient(
                0.0f to EchoTokens.SignalCyan.copy(alpha = 0.28f),
                0.08f to EchoTokens.SignalCyan.copy(alpha = 0.08f),
                0.20f to Color.Transparent,
                1.0f to Color.Transparent,
                center = Offset(cx, cy)
            )
            drawCircle(
                brush = sweepBrush,
                radius = maxR,
                center = Offset(cx, cy)
            )
            // Линия фронта луча
            drawLine(
                color = EchoTokens.SignalCyan.copy(alpha = 0.85f),
                start = Offset(cx, cy),
                end = Offset(cx + maxR, cy),
                strokeWidth = 1.8f
            )
        }

        // 4. Точка пользователя в центре
        drawCircle(color = EchoTokens.SignalCyanDim, radius = 12f, center = Offset(cx, cy))
        drawCircle(color = EchoTokens.SignalCyan, radius = 4f, center = Offset(cx, cy))

        // 5. Сигналы других слушателей (NearbyEchoSignal)
        signals.forEach { sig ->
            val rad = Math.toRadians((sig.angleDeg - 90.0)).toFloat()
            val r = sig.ringFactor * maxR
            val px = cx + r * cos(rad)
            val py = cy + r * sin(rad)

            val sigColor = when {
                sig.compatibility >= 85 -> EchoTokens.Gold
                sig.compatibility >= 70 -> EchoTokens.SignalCyan
                else -> EchoTokens.VinylAmber
            }

            // Ореол
            drawCircle(color = sigColor.copy(alpha = 0.22f), radius = 16f, center = Offset(px, py))
            drawCircle(color = sigColor, radius = 5f, center = Offset(px, py))

            // Подпись трека
            drawText(
                textMeasurer = textMeasurer,
                text = "${sig.compatibility}% ${sig.title.take(16)}",
                topLeft = Offset(px + 8f, py - 6f),
                style = EchoTypography.Label.copy(color = EchoTokens.TextPrimary, fontSize = 9.sp)
            )
        }

        // 6. Музыкальные тайники и капсулы (EchoCapsule)
        capsules.forEach { cap ->
            val rad = Math.toRadians((cap.angleDeg - 90.0)).toFloat()
            val r = cap.ringFactor * maxR
            val px = cx + r * cos(rad)
            val py = cy + r * sin(rad)
            val capColor = if (cap.isVenue) EchoTokens.Gold else EchoTokens.SignalCyan

            // Ромб тайника
            val diamond = Path().apply {
                moveTo(px, py - 7f)
                lineTo(px + 7f, py)
                lineTo(px, py + 7f)
                lineTo(px - 7f, py)
                close()
            }
            drawPath(diamond, color = capColor, style = Stroke(width = 1.4f))
            drawCircle(color = capColor, radius = 2f, center = Offset(px, py))
        }

        // 7. Точка Лагранжа (при 3+ слушателях)
        lagrangePoint?.let { lag ->
            val rad = Math.toRadians((lag.angleDegrees - 90.0)).toFloat()
            val r = lag.distanceRatio * maxR
            val px = cx + r * cos(rad)
            val py = cy + r * sin(rad)

            drawCircle(color = EchoTokens.Gold.copy(alpha = 0.35f), radius = 22f, center = Offset(px, py), style = Stroke(width = 1.5f))
            drawCircle(color = EchoTokens.Gold, radius = 6f, center = Offset(px, py))
            drawText(
                textMeasurer = textMeasurer,
                text = "ТОЧКА ЛАГРАНЖА",
                topLeft = Offset(px + 10f, py - 6f),
                style = EchoTypography.Label.copy(color = EchoTokens.Gold, fontSize = 9.sp)
            )
        }
    }
}

@Composable
private fun IncomingReactionBanner(reaction: ru.echo.radar.IncomingReaction) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(EchoTokens.SurfaceElevated, shape = ru.echo.radar.theme.EchoShapes.Card)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(text = reaction.symbol, style = EchoTypography.Display.copy(fontSize = 18.sp, color = EchoTokens.SignalCyan))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = "ОТКЛИК ИЗ ЭФИРА", style = EchoTypography.Label, color = EchoTokens.SignalCyan)
            Text(text = reaction.message, style = EchoTypography.Body, color = EchoTokens.TextPrimary)
        }
    }
}
