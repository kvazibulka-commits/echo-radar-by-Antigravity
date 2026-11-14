package ru.echo.radar.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ru.echo.radar.state.AppSessionState
import ru.echo.radar.theme.EchoShapes
import ru.echo.radar.theme.EchoTokens
import ru.echo.radar.theme.EchoTypography

/**
 * MiniPlayer — компактный парящий мини-плеер над Navigation Dock.
 * В строгом соответствии с разделом 05 MASTER PROMPT:
 * - Отображается на ETHER, ECHOES, CACHES. Скрыт на DECK.
 * - Вращающийся мини-винил синхронизирован с общим углом винила деки.
 * - При нажатии плавно переходит на экран DECK.
 */
@Composable
fun MiniPlayer(
    state: AppSessionState,
    onNavigateToDeck: () -> Unit,
    onTogglePlayPause: () -> Unit,
    modifier: Modifier = Modifier
) {
    val track = state.currentTrack
    val isWaiting = track.artist.startsWith("Ожидание")

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .height(56.dp)
            .clip(EchoShapes.Medium)
            .background(EchoTokens.SurfaceElevated)
            .border(0.8.dp, EchoTokens.BorderSubtle, EchoShapes.Medium)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(color = EchoTokens.SignalCyan),
                onClick = onNavigateToDeck
            )
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Миниатюра винилового диска с непрерывным вращением
        Box(
            modifier = Modifier.size(38.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val cx = size.width / 2f
                val cy = size.height / 2f
                val r = size.width / 2f - 1.5f

                rotate(state.vinylRotationAngle, pivot = Offset(cx, cy)) {
                    // Основа винила
                    drawCircle(color = EchoTokens.VinylBody, radius = r, center = Offset(cx, cy))
                    drawCircle(color = EchoTokens.VinylGroove, radius = r * 0.85f, center = Offset(cx, cy), style = Stroke(width = 1f))
                    drawCircle(color = EchoTokens.VinylGroove, radius = r * 0.65f, center = Offset(cx, cy), style = Stroke(width = 1f))
                    // Центральное яблоко
                    drawCircle(color = EchoTokens.VinylAmber, radius = r * 0.40f, center = Offset(cx, cy))
                    // Шпиндель
                    drawCircle(color = EchoTokens.Background, radius = 2.5f, center = Offset(cx, cy))
                }
            }
        }

        Spacer(modifier = Modifier.width(10.dp))

        // Метаданные трека
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = track.title,
                style = EchoTypography.TrackTitle.copy(fontSize = 14.sp, lineHeight = 18.sp),
                color = EchoTokens.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = track.artist,
                    style = EchoTypography.Secondary.copy(fontSize = 11.sp),
                    color = EchoTokens.TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (!isWaiting) {
                    Text(
                        text = "• ${track.bpm} BPM",
                        style = EchoTypography.Technical.copy(fontSize = 10.sp),
                        color = EchoTokens.SignalCyan
                    )
                    Text(
                        text = track.camelotKey,
                        style = EchoTypography.TechnicalAmber.copy(fontSize = 10.sp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Кнопка Play / Pause
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(EchoShapes.Pill)
                .background(if (state.isPlaying) EchoTokens.SignalCyanDim else EchoTokens.Surface)
                .border(0.8.dp, if (state.isPlaying) EchoTokens.SignalCyan else EchoTokens.Divider, EchoShapes.Pill)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = ripple(bounded = false, radius = 20.dp, color = EchoTokens.SignalCyan),
                    onClick = onTogglePlayPause
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (state.isPlaying) "❚❚" else "▶",
                style = EchoTypography.Technical.copy(fontSize = 12.sp),
                color = if (state.isPlaying) EchoTokens.SignalCyan else EchoTokens.TextPrimary
            )
        }
    }
}
