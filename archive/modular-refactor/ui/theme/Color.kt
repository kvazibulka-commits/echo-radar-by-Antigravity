package ru.echo.radar.ui.theme

import androidx.compose.ui.graphics.Color

// Concept A «Атлас» — Точные токены дизайна из дизайн-стенда ЭХО Студия 2.0
// День: Бумага #F3EFE6 и чернила #171A1C, контраст ~15:1
object AtlasDayColors {
    val Background = Color(0xFFF3EFE6)    // Матовая бумага
    val Surface = Color(0xFFFBF8F1)       // Светлая бумага
    val Surface2 = Color(0xFFE9E2D4)      // Вспомогательная бумага
    val Ink = Color(0xFF171A1C)           // Типографские чернила
    val Sub = Color(0xFF5C625F)           // Второстепенный текст
    val Faint = Color(0xFF8A8D86)         // Микротекст, метки, разметка
    val Line = Color(0xFFD8D2C4)          // Тонкая линия разметки
    val LineStrong = Color(0xFFB9B2A2)    // Усиленная линия
    val Accent = Color(0xFF0B6E75)        // Теаль (основной акцент и живая точка)
    val Accent2 = Color(0xFFA85A0A)       // Терракота (капсулы, тайники, курсив заголовка)
    val Live = Color(0xFF0B6E75)
    val Gold = Color(0xFFA85A0A)
}

// Сумерки: Лампа и янтарь
object AtlasDuskColors {
    val Background = Color(0xFF241F1B)    // Тёмный ламповый графит
    val Surface = Color(0xFF2E2823)
    val Surface2 = Color(0xFF3A322B)
    val Ink = Color(0xFFF4EBDD)
    val Sub = Color(0xFFBCAF9E)
    val Faint = Color(0xFF8C8071)
    val Line = Color(0xFF453C33)
    val LineStrong = Color(0xFF5D5145)
    val Accent = Color(0xFFF0A45C)        // Янтарь лампы
    val Accent2 = Color(0xFF7DD3C7)       // Мята
    val Live = Color(0xFFF0A45C)
    val Gold = Color(0xFFE3B778)
}

// Ночь: Зелёно-чёрный и мята
object AtlasNightColors {
    val Background = Color(0xFF0A0D11)    // Зелёно-чёрный глубокий фон
    val Surface = Color(0xFF11161B)
    val Surface2 = Color(0xFF182027)
    val Ink = Color(0xFFEEF1EF)
    val Sub = Color(0xFF94A199)
    val Faint = Color(0xFF5F6B65)
    val Line = Color(0xFF24302C)
    val LineStrong = Color(0xFF34443D)
    val Accent = Color(0xFF5EEAD4)        // Неоновая мята
    val Accent2 = Color(0xFFE0A45C)       // Янтарь
    val Live = Color(0xFF5EEAD4)
    val Gold = Color(0xFFE0A45C)
}
