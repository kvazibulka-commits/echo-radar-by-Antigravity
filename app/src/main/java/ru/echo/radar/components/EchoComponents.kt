package ru.echo.radar.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import ru.echo.radar.theme.EchoShapes
import ru.echo.radar.theme.EchoTokens
import ru.echo.radar.theme.EchoTypography

/**
 * SignalIndicator — светодиодный аналоговый индикатор статуса эфира/BLE.
 */
@Composable
fun SignalIndicator(
    isActive: Boolean,
    color: Color = EchoTokens.SignalCyan,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "led_pulse")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "led_alpha"
    )

    Box(
        modifier = modifier
            .size(8.dp)
            .clip(EchoShapes.Pill)
            .background(if (isActive) color.copy(alpha = alpha) else EchoTokens.TextMuted)
    )
}

/**
 * TechnicalTag — компактный моноширинный бейдж физических параметров трека (BPM, Camelot).
 */
@Composable
fun TechnicalTag(
    label: String,
    value: String,
    accentColor: Color = EchoTokens.SignalCyan,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .clip(EchoShapes.Small)
            .background(EchoTokens.SurfaceElevated)
            .border(0.6.dp, EchoTokens.Divider, EchoShapes.Small)
            .padding(horizontal = 6.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = label.uppercase(),
            style = EchoTypography.Label,
            color = EchoTokens.TextSecondary
        )
        Text(
            text = value,
            style = EchoTypography.Technical,
            color = accentColor
        )
    }
}

/**
 * ResonanceBadge — индикатор музыкального созвучия (процент совместимости).
 */
@Composable
fun ResonanceBadge(
    score: Int,
    modifier: Modifier = Modifier
) {
    val badgeColor = when {
        score >= 85 -> EchoTokens.Gold
        score >= 70 -> EchoTokens.SignalCyan
        else -> EchoTokens.VinylAmber
    }

    Row(
        modifier = modifier
            .clip(EchoShapes.Pill)
            .background(badgeColor.copy(alpha = 0.12f))
            .border(0.8.dp, badgeColor.copy(alpha = 0.4f), EchoShapes.Pill)
            .padding(horizontal = 8.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Text(text = "✦", style = EchoTypography.Label, color = badgeColor)
        Text(
            text = "$score%",
            style = EchoTypography.Technical,
            color = badgeColor
        )
    }
}

/**
 * TactileButton — физическая аналоговая кнопка с точным откликом.
 */
@Composable
fun TactileButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accentColor: Color = EchoTokens.SignalCyan,
    leadingIcon: (@Composable () -> Unit)? = null,
    isPrimary: Boolean = false
) {
    val bg = if (isPrimary) accentColor.copy(alpha = 0.15f) else EchoTokens.SurfaceElevated
    val borderCol = if (isPrimary) accentColor else EchoTokens.BorderSubtle

    Row(
        modifier = modifier
            .height(46.dp)
            .clip(EchoShapes.Medium)
            .background(bg)
            .border(1.dp, borderCol, EchoShapes.Medium)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(color = accentColor),
                onClick = onClick
            )
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        if (leadingIcon != null) {
            leadingIcon()
            Spacer(modifier = Modifier.width(8.dp))
        }
        Text(
            text = text,
            style = EchoTypography.Label,
            color = if (isPrimary) accentColor else EchoTokens.TextPrimary
        )
    }
}

/**
 * EchoCard — базовая рамка прибора для карточек созвучий и тайников.
 */
@Composable
fun EchoCard(
    modifier: Modifier = Modifier,
    borderColor: Color = EchoTokens.BorderSubtle,
    backgroundColor: Color = EchoTokens.Surface,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(EchoShapes.Card)
            .background(backgroundColor)
            .border(0.8.dp, borderColor, EchoShapes.Card)
            .padding(14.dp),
        content = content
    )
}
