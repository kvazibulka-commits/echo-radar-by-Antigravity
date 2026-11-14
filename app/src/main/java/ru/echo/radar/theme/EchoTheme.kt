package ru.echo.radar.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * EchoTypography — строгая техническая и аналоговая иерархия шрифтов.
 * JetBrains Mono (Monospace) для точных физических показателей (BPM, Camelot, RSSI, Hz, Dist).
 * SansSerif / Grotesk для экранных заголовков, поэтических метафор и названий треков.
 */
object EchoTypography {
    val Display = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        lineHeight = 34.sp,
        letterSpacing = (-0.5).sp,
        color = EchoTokens.TextPrimary
    )

    val ScreenTitle = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.5.sp,
        color = EchoTokens.TextPrimary
    )

    val TrackTitle = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 19.sp,
        lineHeight = 24.sp,
        letterSpacing = (-0.2).sp,
        color = EchoTokens.TextPrimary
    )

    val Body = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        color = EchoTokens.TextSecondary
    )

    val Secondary = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        color = EchoTokens.TextSecondary
    )

    // Технические метки и физические величины прибора
    val Technical = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        lineHeight = 17.sp,
        letterSpacing = 0.5.sp,
        color = EchoTokens.SignalCyan
    )

    val TechnicalAmber = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        lineHeight = 17.sp,
        letterSpacing = 0.5.sp,
        color = EchoTokens.VinylAmber
    )

    val Label = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.SemiBold,
        fontSize = 10.sp,
        lineHeight = 14.sp,
        letterSpacing = 1.0.sp,
        color = EchoTokens.TextSecondary
    )
}

object EchoShapes {
    val Small = RoundedCornerShape(4.dp)
    val Medium = RoundedCornerShape(8.dp)
    val Large = RoundedCornerShape(12.dp)
    val Card = RoundedCornerShape(10.dp)
    val Pill = RoundedCornerShape(50)
}

private val DarkColorScheme = darkColorScheme(
    primary = EchoTokens.SignalCyan,
    onPrimary = EchoTokens.Background,
    secondary = EchoTokens.VinylAmber,
    onSecondary = EchoTokens.Background,
    tertiary = EchoTokens.Gold,
    background = EchoTokens.Background,
    surface = EchoTokens.Surface,
    surfaceVariant = EchoTokens.SurfaceElevated,
    onBackground = EchoTokens.TextPrimary,
    onSurface = EchoTokens.TextPrimary,
    onSurfaceVariant = EchoTokens.TextSecondary,
    outline = EchoTokens.Divider,
    error = EchoTokens.ErrorRed
)

@Composable
fun EchoTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        shapes = Shapes(
            small = EchoShapes.Small,
            medium = EchoShapes.Medium,
            large = EchoShapes.Large
        ),
        content = content
    )
}
