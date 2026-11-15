package ru.echo.radar.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.echo.radar.ui.theme.LocalEchoColors

@Composable
fun LivePulseDot(
    modifier: Modifier = Modifier
) {
    val colors = LocalEchoColors.current
    val infiniteTransition = rememberInfiniteTransition(label = "live_pulse")
    val pulseProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulse_scale"
    )

    Box(
        modifier = modifier.size(14.dp),
        contentAlignment = Alignment.Center
    ) {
        // Expanding ring
        Box(
            modifier = Modifier
                .size((6.dp + 8.dp * pulseProgress))
                .border(
                    width = 1.dp,
                    color = colors.live.copy(alpha = (1f - pulseProgress) * 0.6f),
                    shape = CircleShape
                )
        )
        // Solid core
        Box(
            modifier = Modifier
                .size(5.dp)
                .background(colors.live, CircleShape)
        )
    }
}

@Composable
fun AirPill(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalEchoColors.current

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .border(1.dp, colors.line, RoundedCornerShape(999.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        LivePulseDot()
        Text(
            text = text,
            color = colors.sub,
            fontFamily = FontFamily.Monospace,
            fontSize = 8.sp,
            letterSpacing = 0.5.sp
        )
    }
}

@Composable
fun GenArtCover(
    hue: Float,
    modifier: Modifier = Modifier
) {
    // Generative art gradient based on track hue
    val c1 = Color.hsl(hue % 360f, 0.65f, 0.45f)
    val c2 = Color.hsl((hue + 70f) % 360f, 0.55f, 0.35f)
    val c3 = Color.hsl((hue + 145f) % 360f, 0.50f, 0.25f)

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(
                Brush.sweepGradient(listOf(c1, c2, c3, c1))
            )
            .border(0.5.dp, Color.Black.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
    )
}

@Composable
fun NightDropCard(
    onListenFragment: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalEchoColors.current

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(colors.surface)
            .border(1.dp, colors.line, RoundedCornerShape(10.dp))
            .clickable(onClick = onListenFragment)
            .padding(12.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(
                text = "НОЧНОЙ ДРОП · 00:00",
                color = colors.gold,
                fontFamily = FontFamily.Monospace,
                fontSize = 7.5.sp,
                letterSpacing = 1.sp
            )
            Text(
                text = "Burial — Untrue",
                color = colors.ink,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                letterSpacing = (-0.2).sp
            )
            Text(
                text = "Фрагмент откроется на набережной в 00:00 — за 12 часов до релиза.",
                color = colors.sub,
                fontSize = 8.5.sp,
                lineHeight = 12.sp
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "Слушать фрагмент",
                    color = colors.accent,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "→",
                    color = colors.accent,
                    fontSize = 11.sp
                )
            }
        }
    }
}

@Composable
fun IndustrialButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isPrimary: Boolean = true,
    enabled: Boolean = true
) {
    val colors = LocalEchoColors.current
    val bg = if (isPrimary) colors.accent else colors.surface
    val contentColor = if (isPrimary) colors.background else colors.ink

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(40.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(bg)
            .border(1.dp, if (isPrimary) colors.accent else colors.line, RoundedCornerShape(9.dp))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = contentColor,
            fontWeight = FontWeight.Bold,
            fontSize = 11.sp,
            letterSpacing = 0.2.sp
        )
    }
}

@Composable
fun IndustrialBadge(
    text: String,
    modifier: Modifier = Modifier,
    highlight: Boolean = false
) {
    val colors = LocalEchoColors.current
    val bg = if (highlight) colors.accent.copy(alpha = 0.12f) else colors.surface2
    val textCol = if (highlight) colors.accent else colors.sub

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(bg)
            .border(1.dp, if (highlight) colors.accent.copy(alpha = 0.4f) else colors.line, RoundedCornerShape(4.dp))
            .padding(horizontal = 7.dp, vertical = 3.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = textCol,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            fontSize = 9.sp
        )
    }
}
