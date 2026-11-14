package ru.echo.radar.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.echo.radar.EchoCapsule
import ru.echo.radar.components.EchoCard
import ru.echo.radar.components.ResonanceBadge
import ru.echo.radar.components.TactileButton
import ru.echo.radar.state.AppSessionState
import ru.echo.radar.state.EchoModalSheet
import ru.echo.radar.theme.EchoShapes
import ru.echo.radar.theme.EchoTokens
import ru.echo.radar.theme.EchoTypography
import kotlin.math.cos
import kotlin.math.sin

/**
 * SCREEN 04 — CACHES / ТАЙНИКИ
 * Акустическая карта частот города и музыкальные капсулы заведений (Proof-of-Music).
 * В строгом соответствии с разделом 03 MASTER PROMPT:
 * - Исследование акустической географии города.
 * - Маркеры заведений (бары, кофейни, виниловые лавки) и капсулы слушателей.
 * - Создание новых капсул через кнопку «ОСТАВИТЬ ТАЙНИК».
 */
@Composable
fun CachesScreen(
    state: AppSessionState,
    onOpenSheet: (EchoModalSheet) -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(EchoTokens.Background)
            .statusBarsPadding()
    ) {
        // --- HEADER: CITY FREQUENCIES ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(text = "ТАЙНИКИ", style = EchoTypography.Display.copy(fontSize = 20.sp, letterSpacing = 2.sp))
                Text(text = "CITY FREQUENCIES", style = EchoTypography.Label, color = EchoTokens.Gold)
            }
            TactileButton(
                text = "+ ОСТАВИТЬ ТАЙНИК",
                onClick = { onOpenSheet(EchoModalSheet.DropCapsuleDialog) },
                accentColor = EchoTokens.SignalCyan,
                isPrimary = true,
                modifier = Modifier.height(34.dp)
            )
        }

        // --- ACOUSTIC MAP CANVAS (Top Half) ---
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .padding(horizontal = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            AcousticMapCanvas(
                capsules = state.capsules,
                onCapsuleClick = { onOpenSheet(EchoModalSheet.CapsuleDetail(it)) }
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // --- CAPSULES LIST ---
        Text(
            text = "АКТИВНЫЕ МУЗЫКАЛЬНЫЕ МАЯКИ (${state.capsules.size})",
            style = EchoTypography.Label,
            color = EchoTokens.TextSecondary,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
        )

        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(top = 4.dp, bottom = 80.dp)
        ) {
            if (state.capsules.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 30.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "В радиусе сканирования нет активных тайников.\nОставьте первую музыкальную капсулу в эфире.",
                            style = EchoTypography.Secondary,
                            color = EchoTokens.TextMuted,
                            modifier = Modifier.padding(horizontal = 20.dp)
                        )
                    }
                }
            } else {
                items(state.capsules) { capsule ->
                    CapsuleListItem(
                        capsule = capsule,
                        onClick = { onOpenSheet(EchoModalSheet.CapsuleDetail(capsule)) }
                    )
                }
            }
        }
    }
}

@Composable
private fun AcousticMapCanvas(
    capsules: List<EchoCapsule>,
    onCapsuleClick: (EchoCapsule) -> Unit
) {
    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .background(EchoTokens.Surface, shape = EchoShapes.Card)
            .pointerInput(capsules) {
                detectTapGestures { tapOffset ->
                    val cx = size.width / 2f
                    val cy = size.height / 2f
                    val maxR = minOf(cx, cy) * 0.88f

                    val clicked = capsules.firstOrNull { c ->
                        val rad = Math.toRadians((c.angleDeg - 90.0)).toFloat()
                        val r = c.ringFactor * maxR
                        val px = cx + r * cos(rad)
                        val py = cy + r * sin(rad)
                        (Offset(px, py) - tapOffset).getDistance() < 36f
                    }
                    clicked?.let { onCapsuleClick(it) }
                }
            }
    ) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val maxR = minOf(cx, cy) * 0.88f

        // Городская акустическая координатная сетка
        drawCircle(color = EchoTokens.Divider.copy(alpha = 0.4f), radius = maxR, center = Offset(cx, cy), style = Stroke(width = 0.8f))
        drawCircle(color = EchoTokens.Divider.copy(alpha = 0.25f), radius = maxR * 0.5f, center = Offset(cx, cy), style = Stroke(width = 0.8f))

        // Точка слушателя
        drawCircle(color = EchoTokens.SignalCyan, radius = 3.5f, center = Offset(cx, cy))

        // Ромбы тайников на карте
        capsules.forEach { cap ->
            val rad = Math.toRadians((cap.angleDeg - 90.0)).toFloat()
            val r = cap.ringFactor * maxR
            val px = cx + r * cos(rad)
            val py = cy + r * sin(rad)
            val markerColor = if (cap.isVenue) EchoTokens.Gold else EchoTokens.SignalCyan

            val diamond = Path().apply {
                moveTo(px, py - 8f)
                lineTo(px + 8f, py)
                lineTo(px, py + 8f)
                lineTo(px - 8f, py)
                close()
            }
            drawPath(diamond, color = markerColor, style = Stroke(width = 1.4f))
            drawCircle(color = markerColor, radius = 2f, center = Offset(px, py))
        }
    }
}

@Composable
private fun CapsuleListItem(
    capsule: EchoCapsule,
    onClick: () -> Unit
) {
    val borderColor = if (capsule.isVenue) EchoTokens.Gold.copy(alpha = 0.45f) else EchoTokens.BorderSubtle

    EchoCard(borderColor = borderColor) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (capsule.isVenue) "ТАЙНИК [${capsule.venueName.ifBlank { "ЗАВЕДЕНИЕ" }}]" else "КАПСУЛА [${capsule.zoneName}]",
                style = EchoTypography.Label,
                color = if (capsule.isVenue) EchoTokens.Gold else EchoTokens.SignalCyan
            )
            ResonanceBadge(score = capsule.compatibility)
        }

        Spacer(modifier = Modifier.height(4.dp))

        Text(text = capsule.title, style = EchoTypography.TrackTitle.copy(fontSize = 16.sp))
        Text(text = "${capsule.artist} • ${capsule.distanceLabel}", style = EchoTypography.Body)

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = "«${capsule.secretMessage}»",
            style = EchoTypography.Secondary,
            maxLines = 2
        )

        if (capsule.isVenue && capsule.secretPerk.isNotBlank()) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "★ Привилегия: ${capsule.secretPerk}",
                style = EchoTypography.TechnicalAmber.copy(fontSize = 11.sp)
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        TactileButton(
            text = "ОТКРЫТЬ ТАЙНИК",
            onClick = onClick,
            accentColor = if (capsule.isVenue) EchoTokens.Gold else EchoTokens.SignalCyan,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
