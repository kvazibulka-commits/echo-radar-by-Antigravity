package ru.echo.radar.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

data class EchoColors(
    val background: Color,
    val surface: Color,
    val surfaceElevated: Color,
    val border: Color,
    val borderBold: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textSubtle: Color,
    val accent: Color,
    val accentSecondary: Color,
    val radarBg: Color,
    val radarGrid: Color,
    val venueGold: Color,
    val capsuleEmerald: Color,
    val isEvening: Boolean
)

val LocalEchoColors = staticCompositionLocalOf {
    EchoColors(
        background = IndustrialDayColors.Background,
        surface = IndustrialDayColors.Surface,
        surfaceElevated = IndustrialDayColors.SurfaceElevated,
        border = IndustrialDayColors.Border,
        borderBold = IndustrialDayColors.BorderBold,
        textPrimary = IndustrialDayColors.TextPrimary,
        textSecondary = IndustrialDayColors.TextSecondary,
        textSubtle = IndustrialDayColors.TextSubtle,
        accent = IndustrialDayColors.Accent,
        accentSecondary = IndustrialDayColors.AccentSecondary,
        radarBg = IndustrialDayColors.RadarBg,
        radarGrid = IndustrialDayColors.RadarGrid,
        venueGold = IndustrialDayColors.VenueGold,
        capsuleEmerald = IndustrialDayColors.CapsuleEmerald,
        isEvening = false
    )
}

@Composable
fun EchoIndustrialTheme(
    isEvening: Boolean = false,
    content: @Composable () -> Unit
) {
    val echoColors = if (isEvening) {
        EchoColors(
            background = IndustrialEveningColors.Background,
            surface = IndustrialEveningColors.Surface,
            surfaceElevated = IndustrialEveningColors.SurfaceElevated,
            border = IndustrialEveningColors.Border,
            borderBold = IndustrialEveningColors.BorderBold,
            textPrimary = IndustrialEveningColors.TextPrimary,
            textSecondary = IndustrialEveningColors.TextSecondary,
            textSubtle = IndustrialEveningColors.TextSubtle,
            accent = IndustrialEveningColors.Accent,
            accentSecondary = IndustrialEveningColors.AccentSecondary,
            radarBg = IndustrialEveningColors.RadarBg,
            radarGrid = IndustrialEveningColors.RadarGrid,
            venueGold = IndustrialEveningColors.VenueGold,
            capsuleEmerald = IndustrialEveningColors.CapsuleEmerald,
            isEvening = true
        )
    } else {
        EchoColors(
            background = IndustrialDayColors.Background,
            surface = IndustrialDayColors.Surface,
            surfaceElevated = IndustrialDayColors.SurfaceElevated,
            border = IndustrialDayColors.Border,
            borderBold = IndustrialDayColors.BorderBold,
            textPrimary = IndustrialDayColors.TextPrimary,
            textSecondary = IndustrialDayColors.TextSecondary,
            textSubtle = IndustrialDayColors.TextSubtle,
            accent = IndustrialDayColors.Accent,
            accentSecondary = IndustrialDayColors.AccentSecondary,
            radarBg = IndustrialDayColors.RadarBg,
            radarGrid = IndustrialDayColors.RadarGrid,
            venueGold = IndustrialDayColors.VenueGold,
            capsuleEmerald = IndustrialDayColors.CapsuleEmerald,
            isEvening = false
        )
    }

    CompositionLocalProvider(LocalEchoColors provides echoColors) {
        MaterialTheme(
            typography = IndustrialTypography,
            content = content
        )
    }
}
