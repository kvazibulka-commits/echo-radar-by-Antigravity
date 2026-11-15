package ru.echo.radar.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

data class EchoColors(
    val background: Color,
    val surface: Color,
    val surface2: Color,
    val ink: Color,
    val sub: Color,
    val faint: Color,
    val line: Color,
    val lineStrong: Color,
    val accent: Color,
    val accent2: Color,
    val live: Color,
    val gold: Color,
    val isEvening: Boolean = false
) {
    // Backwards compatibility aliases
    val textPrimary: Color get() = ink
    val textSecondary: Color get() = sub
    val textSubtle: Color get() = faint
    val border: Color get() = line
    val borderBold: Color get() = lineStrong
    val radarBg: Color get() = surface
    val radarGrid: Color get() = line
    val venueGold: Color get() = gold
    val capsuleEmerald: Color get() = accent
    val surfaceElevated: Color get() = surface2
    val accentSecondary: Color get() = accent2
}

val LocalEchoColors = staticCompositionLocalOf {
    EchoColors(
        background = AtlasDayColors.Background,
        surface = AtlasDayColors.Surface,
        surface2 = AtlasDayColors.Surface2,
        ink = AtlasDayColors.Ink,
        sub = AtlasDayColors.Sub,
        faint = AtlasDayColors.Faint,
        line = AtlasDayColors.Line,
        lineStrong = AtlasDayColors.LineStrong,
        accent = AtlasDayColors.Accent,
        accent2 = AtlasDayColors.Accent2,
        live = AtlasDayColors.Live,
        gold = AtlasDayColors.Gold,
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
            background = AtlasNightColors.Background,
            surface = AtlasNightColors.Surface,
            surface2 = AtlasNightColors.Surface2,
            ink = AtlasNightColors.Ink,
            sub = AtlasNightColors.Sub,
            faint = AtlasNightColors.Faint,
            line = AtlasNightColors.Line,
            lineStrong = AtlasNightColors.LineStrong,
            accent = AtlasNightColors.Accent,
            accent2 = AtlasNightColors.Accent2,
            live = AtlasNightColors.Live,
            gold = AtlasNightColors.Gold,
            isEvening = true
        )
    } else {
        EchoColors(
            background = AtlasDayColors.Background,
            surface = AtlasDayColors.Surface,
            surface2 = AtlasDayColors.Surface2,
            ink = AtlasDayColors.Ink,
            sub = AtlasDayColors.Sub,
            faint = AtlasDayColors.Faint,
            line = AtlasDayColors.Line,
            lineStrong = AtlasDayColors.LineStrong,
            accent = AtlasDayColors.Accent,
            accent2 = AtlasDayColors.Accent2,
            live = AtlasDayColors.Live,
            gold = AtlasDayColors.Gold,
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
