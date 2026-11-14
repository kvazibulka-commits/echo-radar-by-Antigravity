package ru.echo.radar.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.echo.radar.ui.theme.LocalEchoColors

@Composable
fun IndustrialButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isPrimary: Boolean = true,
    enabled: Boolean = true
) {
    val colors = LocalEchoColors.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(if (isPressed) 0.97f else 1.0f, label = "button_scale")

    val bg = if (isPrimary) colors.accent else colors.surfaceElevated
    val contentColor = if (isPrimary) Color.White else colors.textPrimary

    Box(
        modifier = modifier
            .scale(scale)
            .background(bg, RoundedCornerShape(6.dp))
            .border(1.5.dp, if (isPrimary) colors.borderBold else colors.border, RoundedCornerShape(6.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                onClick = onClick
            )
            .padding(vertical = 12.dp, horizontal = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = contentColor,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp,
            letterSpacing = 0.5.sp
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
    val bg = if (highlight) colors.accent.copy(alpha = 0.15f) else colors.surfaceElevated
    val textCol = if (highlight) colors.accent else colors.textSecondary

    Box(
        modifier = modifier
            .background(bg, RoundedCornerShape(4.dp))
            .border(1.dp, if (highlight) colors.accent else colors.border, RoundedCornerShape(4.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = textCol,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            fontSize = 10.sp
        )
    }
}

@Composable
fun SegmentedResonanceMeter(
    resonancePercent: Int,
    modifier: Modifier = Modifier
) {
    val colors = LocalEchoColors.current
    val totalSegments = 10
    val activeSegments = (resonancePercent / 10).coerceIn(0, 10)

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        for (i in 0 until totalSegments) {
            val isActive = i < activeSegments
            val segColor = when {
                !isActive -> colors.surfaceElevated
                i >= 7 -> colors.accent
                else -> colors.accentSecondary
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(8.dp)
                    .background(segColor, RoundedCornerShape(2.dp))
                    .border(0.5.dp, colors.border, RoundedCornerShape(2.dp))
            )
        }
    }
}
