package ru.echo.radar.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import ru.echo.radar.state.EchoDestination
import ru.echo.radar.theme.EchoTokens
import ru.echo.radar.theme.EchoTypography

/**
 * NavigationDock — постоянный нижний док навигации аналогового прибора ЭХО.
 * В строгом соответствии с разделом 04 MASTER PROMPT:
 * 4 равнозначных направления (ETHER, DECK, ECHOES, CACHES), высота 68 dp,
 * индикация активного пункта Signal Cyan, тонкий верхний делитель Divider.
 */
@Composable
fun NavigationDock(
    currentDestination: EchoDestination,
    onDestinationSelected: (EchoDestination) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(EchoTokens.Surface)
    ) {
        // Тонкий технический разделитель (0.8 dp)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(0.8.dp)
                .background(EchoTokens.Divider)
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(68.dp)
                .navigationBarsPadding(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            EchoDestination.entries.forEach { dest ->
                val isSelected = dest == currentDestination
                val itemColor = if (isSelected) EchoTokens.SignalCyan else EchoTokens.TextSecondary

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable(onClick = { onDestinationSelected(dest) }),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(modifier = Modifier.size(24.dp), contentAlignment = Alignment.Center) {
                        when (dest) {
                            EchoDestination.ETHER -> NavEtherIcon(color = itemColor)
                            EchoDestination.DECK -> NavDeckIcon(color = itemColor)
                            EchoDestination.ECHOES -> NavEchoesIcon(color = itemColor)
                            EchoDestination.CACHES -> NavCachesIcon(color = itemColor)
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = dest.title,
                        style = EchoTypography.Label,
                        color = itemColor
                    )
                }
            }
        }
    }
}

@Composable
private fun NavEtherIcon(color: Color) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        // Центральная точка
        drawCircle(color = color, radius = 2.5f, center = Offset(cx, cy))
        // Внутреннее и внешнее сонарные кольца
        drawCircle(color = color, radius = size.width * 0.28f, center = Offset(cx, cy), style = Stroke(width = 1.4f))
        drawCircle(color = color, radius = size.width * 0.44f, center = Offset(cx, cy), style = Stroke(width = 1.4f))
        // Сонарный луч на 45 градусов
        drawLine(
            color = color,
            start = Offset(cx, cy),
            end = Offset(cx + size.width * 0.38f, cy - size.height * 0.38f),
            strokeWidth = 1.6f
        )
    }
}

@Composable
private fun NavDeckIcon(color: Color) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        // Виниловый диск
        drawCircle(color = color, radius = size.width * 0.42f, center = Offset(cx, cy), style = Stroke(width = 1.5f))
        drawCircle(color = color, radius = size.width * 0.22f, center = Offset(cx, cy), style = Stroke(width = 1.2f))
        drawCircle(color = color, radius = 2f, center = Offset(cx, cy))
        // Тонарм (игла)
        drawLine(
            color = color,
            start = Offset(size.width * 0.88f, size.height * 0.12f),
            end = Offset(cx + size.width * 0.15f, cy + size.height * 0.10f),
            strokeWidth = 1.5f
        )
    }
}

@Composable
private fun NavEchoesIcon(color: Color) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        // Акустические частотные волны памяти
        val p1 = Path().apply {
            moveTo(w * 0.15f, h * 0.70f)
            cubicTo(w * 0.35f, h * 0.25f, w * 0.65f, h * 0.75f, w * 0.85f, h * 0.30f)
        }
        val p2 = Path().apply {
            moveTo(w * 0.15f, h * 0.85f)
            cubicTo(w * 0.35f, h * 0.45f, w * 0.65f, h * 0.90f, w * 0.85f, h * 0.50f)
        }
        drawPath(p1, color = color, style = Stroke(width = 1.5f))
        drawPath(p2, color = color.copy(alpha = 0.65f), style = Stroke(width = 1.2f))
    }
}

@Composable
private fun NavCachesIcon(color: Color) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val r = size.width * 0.40f
        // Ромб тайника локации
        val path = Path().apply {
            moveTo(cx, cy - r)
            lineTo(cx + r, cy)
            lineTo(cx, cy + r)
            lineTo(cx - r, cy)
            close()
        }
        drawPath(path, color = color, style = Stroke(width = 1.5f))
        drawCircle(color = color, radius = 2.5f, center = Offset(cx, cy))
    }
}
