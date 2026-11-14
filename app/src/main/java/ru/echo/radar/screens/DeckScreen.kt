package ru.echo.radar.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.echo.radar.components.EchoCard
import ru.echo.radar.components.TactileButton
import ru.echo.radar.components.TechnicalTag
import ru.echo.radar.state.AppSessionState
import ru.echo.radar.theme.EchoShapes
import ru.echo.radar.theme.EchoTokens
import ru.echo.radar.theme.EchoTypography

/**
 * SCREEN 02 — DECK / ДЕКА
 * Главный музыкальный инструмент ЭХО.
 * Премиальная аналоговая виниловая дека в современной цифровой интерпретации.
 * В строгом соответствии с разделом 03 MASTER PROMPT:
 * - Большой виниловый диск (~65% ширины экрана).
 * - Наклейка VinylLabel, тонарм, реалистичная геометрия диска.
 * - Вращение непрерывно связано с реальным состоянием аудиодвижка (33⅓ RPM).
 * - Механики «Needle Sync» (Синхронизация иглы) и «Камень в воду».
 */
@Composable
fun DeckScreen(
    state: AppSessionState,
    onTogglePlayPause: () -> Unit,
    onNeedleSync: (String, String) -> Unit,
    onWaveImpulse: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val track = state.currentTrack
    val isWaiting = track.artist.startsWith("Ожидание")
    var detailsExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(EchoTokens.Background)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 80.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // --- HEADER: DECK / PLAYBACK ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(text = "ДЕКА", style = EchoTypography.Display.copy(fontSize = 20.sp, letterSpacing = 2.sp))
                Text(text = "DECK / PLAYBACK", style = EchoTypography.Label, color = EchoTokens.VinylAmber)
            }
            Text(
                text = if (state.isPlaying) "33⅓ RPM • ВОСПРОИЗВЕДЕНИЕ" else "33⅓ RPM • ПАУЗА",
                style = EchoTypography.Technical.copy(fontSize = 11.sp),
                color = if (state.isPlaying) EchoTokens.VinylAmber else EchoTokens.TextMuted
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        // --- MAIN: VINYL PLATTER WITH TONEARM (~65% screen width) ---
        Box(
            modifier = Modifier
                .fillMaxWidth(0.78f)
                .aspectRatio(1f),
            contentAlignment = Alignment.Center
        ) {
            VinylPlatter(
                rotationAngle = state.vinylRotationAngle,
                isPlaying = state.isPlaying
            )
            Tonearm(isPlaying = state.isPlaying)
        }

        Spacer(modifier = Modifier.height(20.dp))

        // --- TRACK METADATA ---
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = track.title,
                style = EchoTypography.TrackTitle.copy(fontSize = 20.sp),
                color = EchoTokens.TextPrimary,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "${track.artist} • ${track.sourceApp}",
                style = EchoTypography.Body,
                color = EchoTokens.TextSecondary,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Технические бейджи прибора
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TechnicalTag(label = "BPM", value = "${track.bpm}", accentColor = EchoTokens.SignalCyan)
                TechnicalTag(label = "КЛЮЧ", value = track.camelotKey, accentColor = EchoTokens.VinylAmber)
                TechnicalTag(label = "ЖАНР", value = track.genre, accentColor = EchoTokens.TextPrimary)
            }
        }

        Spacer(modifier = Modifier.height(22.dp))

        // --- TACTILE CONTROLS DECK ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Кнопка «Синхронизация иглы» (Needle Sync)
            TactileButton(
                text = "СИНХРОН ИГЛЫ",
                onClick = {
                    val peer = state.nearbySignals.firstOrNull()
                    if (peer != null) {
                        onNeedleSync(peer.id, peer.title)
                    } else {
                        onNeedleSync("local_sync", track.title)
                    }
                },
                accentColor = EchoTokens.SignalCyan,
                isPrimary = true,
                modifier = Modifier.weight(1f)
            )

            // Центральная большая кнопка PLAY / PAUSE
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .clip(EchoShapes.Pill)
                    .background(if (state.isPlaying) EchoTokens.VinylAmber else EchoTokens.SurfaceElevated)
                    .border(1.2.dp, if (state.isPlaying) EchoTokens.VinylAmber else EchoTokens.Divider, EchoShapes.Pill)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = ripple(bounded = false, radius = 28.dp, color = EchoTokens.VinylAmber),
                        onClick = onTogglePlayPause
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (state.isPlaying) "❚❚" else "▶",
                    style = EchoTypography.Display.copy(fontSize = 18.sp),
                    color = if (state.isPlaying) EchoTokens.Background else EchoTokens.TextPrimary
                )
            }

            // Кнопка «Камень в воду» (Wave impulse)
            TactileButton(
                text = "КАМЕНЬ В ВОДУ",
                onClick = {
                    val peer = state.nearbySignals.firstOrNull()
                    onWaveImpulse(peer?.id ?: "broadcast")
                },
                accentColor = EchoTokens.VinylAmber,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(18.dp))

        // --- EXPANDABLE SIGNAL DETAILS ---
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(EchoShapes.Small)
                    .clickable { detailsExpanded = !detailsExpanded }
                    .padding(vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (detailsExpanded) "СВЕРНУТЬ ПАРАМЕТРЫ СИГНАЛА ▲" else "АНАЛИТИКА ЭФИРА И ИИ-ОБРАЗ ▼",
                    style = EchoTypography.Label,
                    color = EchoTokens.TextSecondary
                )
            }

            AnimatedVisibility(visible = detailsExpanded) {
                EchoCard(modifier = Modifier.padding(top = 8.dp)) {
                    Text(text = "ПОЭТИЧЕСКИЙ АРХЕТИП", style = EchoTypography.Label, color = EchoTokens.VinylAmber)
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(text = track.poeticPersona, style = EchoTypography.TrackTitle.copy(fontSize = 15.sp))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = "НАСТРОЕНИЕ И СРЕДА", style = EchoTypography.Label, color = EchoTokens.SignalCyan)
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(text = track.mood, style = EchoTypography.Body)
                }
            }
        }
    }
}

/**
 * VinylPlatter — аналоговый виниловый диск со звуковыми дорожками и центральным яблоком.
 */
@Composable
private fun VinylPlatter(
    rotationAngle: Float,
    isPlaying: Boolean,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.fillMaxSize()) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val outerRadius = size.width / 2f - 4f

        rotate(rotationAngle, pivot = Offset(cx, cy)) {
            // Тело пластинки
            drawCircle(color = EchoTokens.VinylBody, radius = outerRadius, center = Offset(cx, cy))
            drawCircle(color = EchoTokens.BorderSubtle, radius = outerRadius, center = Offset(cx, cy), style = Stroke(width = 1.2f))

            // Звуковые дорожки (концентрические канавки)
            val grooveSteps = listOf(0.92f, 0.86f, 0.80f, 0.74f, 0.68f, 0.62f, 0.56f, 0.50f, 0.44f)
            grooveSteps.forEach { factor ->
                drawCircle(
                    color = EchoTokens.VinylGroove,
                    radius = outerRadius * factor,
                    center = Offset(cx, cy),
                    style = Stroke(width = 0.8f)
                )
            }

            // Центральное аналоговое яблоко (VinylLabel)
            val labelRadius = outerRadius * 0.36f
            drawCircle(color = EchoTokens.VinylAmber, radius = labelRadius, center = Offset(cx, cy))
            drawCircle(color = EchoTokens.Background, radius = labelRadius * 0.85f, center = Offset(cx, cy), style = Stroke(width = 1f))
            drawCircle(color = EchoTokens.TextPrimary.copy(alpha = 0.5f), radius = labelRadius * 0.40f, center = Offset(cx, cy), style = Stroke(width = 0.8f))

            // Центральный шпиндель
            drawCircle(color = EchoTokens.Background, radius = 6f, center = Offset(cx, cy))
        }
    }
}

/**
 * Tonearm — металлический тонарм с иглой звукоснимателя.
 * При воспроизведении плавно опускается на дорожку, при паузе слегка отводится.
 */
@Composable
private fun Tonearm(
    isPlaying: Boolean,
    modifier: Modifier = Modifier
) {
    val needleAngle by animateFloatAsState(
        targetValue = if (isPlaying) 18f else 0f,
        animationSpec = tween(durationMillis = 400),
        label = "tonearm_angle"
    )

    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val pivot = Offset(w * 0.88f, h * 0.12f)

        rotate(needleAngle, pivot = pivot) {
            // Основание тонарма
            drawCircle(color = EchoTokens.SurfaceElevated, radius = 10f, center = pivot)
            drawCircle(color = EchoTokens.TonearmMetal, radius = 4f, center = pivot)

            // Металлическая трубка тонарма
            val path = Path().apply {
                moveTo(pivot.x, pivot.y)
                lineTo(w * 0.75f, h * 0.35f)
                lineTo(w * 0.58f, h * 0.62f)
            }
            drawPath(path, color = EchoTokens.TonearmMetal, style = Stroke(width = 2.4f))

            // Головка звукоснимателя (картридж)
            val cartridgeOffset = Offset(w * 0.58f, h * 0.62f)
            drawCircle(color = EchoTokens.SignalCyan, radius = 4.5f, center = cartridgeOffset)
        }
    }
}
