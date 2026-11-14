package ru.echo.radar.ui.deck

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.echo.radar.domain.model.EchoSignal
import ru.echo.radar.ui.components.IndustrialBadge
import ru.echo.radar.ui.components.IndustrialButton
import ru.echo.radar.ui.components.SegmentedResonanceMeter
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

    val infiniteTransition = rememberInfiniteTransition(label = "vinyl_spin")
    val vinylRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 8000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "ДЕКА",
                color = colors.textPrimary,
                fontWeight = FontWeight.Black,
                fontSize = 22.sp
            )
            IndustrialBadge(
                text = signal?.source?.displayName?.uppercase() ?: "ЭФИР",
                highlight = false
            )
        }

        Spacer(modifier = Modifier.height(18.dp))

        // Vinyl Stage
        Box(
            modifier = Modifier
                .size(200.dp)
                .rotate(if (isSynced) vinylRotation else 0f),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val cx = size.width / 2f
                val cy = size.height / 2f
                val r = size.width / 2f

                // Outer edge
                drawCircle(color = Color(0xFF161719), radius = r)

                // Grooves
                for (g in 1..6) {
                    drawCircle(
                        color = Color(0xFF282A2E),
                        radius = r * (0.35f + g * 0.09f),
                        style = Stroke(width = 1f)
                    )
                }

                // Center label (Apple)
                drawCircle(color = colors.accent, radius = r * 0.35f)
                drawCircle(color = colors.background, radius = r * 0.08f)
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Track Details
        Text(
            text = signal?.track ?: "Тишина в эфире",
            color = colors.textPrimary,
            fontWeight = FontWeight.Black,
            fontSize = 20.sp,
            textAlign = TextAlign.Center
        )
        Text(
            text = signal?.artist ?: "Включите Яндекс Музыку или VK",
            color = colors.textSecondary,
            fontSize = 14.sp,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Resonance Box
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.surface, RoundedCornerShape(8.dp))
                .border(1.5.dp, colors.border, RoundedCornerShape(8.dp))
                .padding(14.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "АКУСТИЧЕСКИЙ РЕЗОНАНС",
                        fontFamily = FontFamily.Monospace,
                        color = colors.textSubtle,
                        fontSize = 10.sp
                    )
                    Text(
                        text = "${signal?.resonancePercent ?: 0}% СОЗВУЧИЕ",
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = colors.accent,
                        fontSize = 11.sp
                    )
                }

                SegmentedResonanceMeter(resonancePercent = signal?.resonancePercent ?: 0)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "CAMELOT: ${signal?.camelotKey ?: "8A"}",
                        fontFamily = FontFamily.Monospace,
                        color = colors.textSecondary,
                        fontSize = 11.sp
                    )
                    Text(
                        text = "ТЕМП: ${signal?.bpm ?: 120} BPM",
                        fontFamily = FontFamily.Monospace,
                        color = colors.textSecondary,
                        fontSize = 11.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Actions Column
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            IndustrialButton(
                text = if (isSynced) "✓ ИГЛА В УНИСОН (СЛУШАЕТЕ ВМЕСТЕ)" else "♫ СЛУШАТЬ ВМЕСТЕ (СИНХРОН)",
                onClick = onToggleSync,
                isPrimary = true,
                modifier = Modifier.fillMaxWidth()
            )

            IndustrialButton(
                text = "⚡ Послать тихий импульс «♫ Спасибо»",
                onClick = onSendPulse,
                isPrimary = false,
                modifier = Modifier.fillMaxWidth()
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        // Discrete aesthetic customization
        Text(
            text = "🎨 Материалы вертушки (латунь / пробка / титан) →",
            color = colors.textSubtle,
            fontFamily = FontFamily.Monospace,
            fontSize = 10.sp,
            modifier = Modifier.padding(bottom = 8.dp)
        )
    }
}
