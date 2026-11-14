package ru.echo.radar.ui.deck

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.echo.radar.domain.model.EchoSignal
import ru.echo.radar.ui.components.GenArtCover
import ru.echo.radar.ui.components.IndustrialButton
import ru.echo.radar.ui.theme.LocalEchoColors

@Composable
fun DeckScreen(
    signal: EchoSignal?,
    isSynced: Boolean,
    onToggleSync: () -> Unit,
    onSendPulse: () -> Unit,
    onOpenStreaming: (String) -> Unit,
    onOpenCustomization: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalEchoColors.current

    val sig = signal ?: EchoSignal(
        id = "default",
        artist = "Молчат Дома",
        track = "Судно (Борис Рижий)",
        genre = "Пост-панк",
        resonancePercent = 94,
        bpm = 128,
        camelotKey = "8A",
        story = "Упругий бас пустых дворов и ровный стук драм-машины под мерцанием тусклых окон."
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .padding(horizontal = 18.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 1. Screen Head
        Row(
            modifier = Modifier.fillMaxWidth().height(32.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "ДЕКА · СТОРОНА A",
                color = colors.faint,
                fontFamily = FontFamily.Monospace,
                fontSize = 8.5.sp,
                letterSpacing = 1.sp
            )
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .border(1.dp, colors.line, RoundedCornerShape(999.dp))
                    .padding(horizontal = 8.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Box(modifier = Modifier.size(4.dp).background(colors.live, CircleShape))
                Text(
                    text = sig.source.displayName.uppercase(),
                    color = colors.sub,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 7.sp,
                    letterSpacing = 0.5.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // 2. Paper Cover (Concept A: Tilted at -1.4 degrees)
        Box(
            modifier = Modifier
                .size(200.dp)
                .rotate(-1.4f)
                .clip(RoundedCornerShape(3.dp))
                .border(1.dp, colors.lineStrong.copy(alpha = 0.65f), RoundedCornerShape(3.dp))
                .background(colors.surface),
            contentAlignment = Alignment.Center
        ) {
            GenArtCover(
                hue = sig.hue,
                modifier = Modifier.fillMaxSize().padding(8.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 3. Track Title (Serif 24sp)
        Text(
            text = sig.track,
            color = colors.ink,
            fontFamily = FontFamily.Serif,
            fontWeight = FontWeight.Bold,
            fontSize = 22.sp,
            textAlign = TextAlign.Center,
            letterSpacing = (-0.3).sp,
            maxLines = 1
        )
        Text(
            text = "${sig.artist} · 2026 · ${sig.genre}",
            color = colors.sub,
            fontSize = 10.sp,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(14.dp))

        // 4. Acoustic Stats Table (3 columns: Резонанс, Темп, Camelot)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .border(width = 0.5.dp, color = colors.line)
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(text = "РЕЗОНАНС", color = colors.faint, fontFamily = FontFamily.Monospace, fontSize = 7.sp, letterSpacing = 1.sp)
                Row(verticalAlignment = Alignment.Baseline) {
                    Text(text = "${sig.resonancePercent}", color = colors.ink, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Text(text = "%", color = colors.faint, fontSize = 9.sp)
                }
            }
            Box(modifier = Modifier.width(0.5.dp).height(30.dp).background(colors.line))
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(text = "ТЕМП", color = colors.faint, fontFamily = FontFamily.Monospace, fontSize = 7.sp, letterSpacing = 1.sp)
                Row(verticalAlignment = Alignment.Baseline) {
                    Text(text = "${sig.bpm}", color = colors.ink, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Text(text = " bpm", color = colors.faint, fontSize = 7.sp)
                }
            }
            Box(modifier = Modifier.width(0.5.dp).height(30.dp).background(colors.line))
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(text = "CAMELOT", color = colors.faint, fontFamily = FontFamily.Monospace, fontSize = 7.sp, letterSpacing = 1.sp)
                Text(text = sig.camelotKey, color = colors.ink, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // 5. Actions
        IndustrialButton(
            text = if (isSynced) "В УНИСОНЕ (ОТКЛЮЧИТЬ)" else "СЛУШАТЬ ВМЕСТЕ",
            onClick = onToggleSync,
            isPrimary = true
        )

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(38.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(colors.surface)
                    .border(1.dp, colors.line, RoundedCornerShape(8.dp))
                    .clickable(onClick = onSendPulse),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Тихий импульс",
                    color = colors.ink,
                    fontSize = 9.5.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(38.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(colors.surface)
                    .border(1.dp, colors.line, RoundedCornerShape(8.dp))
                    .clickable { onOpenStreaming(sig.source.displayName) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = sig.source.displayName,
                    color = colors.ink,
                    fontSize = 9.5.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Story note
        if (sig.story.isNotEmpty()) {
            Text(
                text = "Резонанс ${sig.resonancePercent}%: ${sig.story}",
                color = colors.faint,
                fontSize = 9.sp,
                textAlign = TextAlign.Center,
                lineHeight = 13.sp,
                modifier = Modifier.padding(horizontal = 12.dp)
            )
        }
    }
}
