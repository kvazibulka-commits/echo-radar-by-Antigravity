package ru.echo.radar.theme

import androidx.compose.ui.graphics.Color

/**
 * EchoTokens — централизованная цветовая система и константы ночного инструмента ЭХО.
 * В строгом соответствии с разделом 02 MASTER PROMPT:
 * Analog Hi-Fi Equipment × Nocturnal Observatory × Contemporary Digital Instrument.
 */
object EchoTokens {
    // Централизованная палитра
    val Background = Color(0xFF07090E)         // Глубокий обсерваторный фон
    val Surface = Color(0xFF0D121B)            // Базовые панели прибора
    val SurfaceElevated = Color(0xFF151E2A)    // Карточки, модальные окна, парящие блоки
    val SurfaceHighlight = Color(0xFF1C2736)   // Интерактивные нажатия, выделенные секции

    val SignalCyan = Color(0xFF00F0FF)         // Активный сигнал, сонарный луч, действия
    val SignalCyanDim = Color(0x3300F0FF)      // Рассеянное свечение сонара
    val VinylAmber = Color(0xFFFFB800)         // Тактильный винил, редкие импульсы, физика
    val VinylAmberDim = Color(0x33FFB800)      // Ореол иглы и винила
    val Gold = Color(0xFFFFD700)               // Резонанс >= 80%, открытие Proof-of-Music тайников
    val GoldDim = Color(0x33FFD700)            // Золотой ореол тайников

    val TextPrimary = Color(0xFFF2F5F7)        // Основная информация, названия треков
    val TextSecondary = Color(0xFF85909E)      // Вспомогательные подписи, артисты, жанры
    val TextMuted = Color(0xFF505A68)          // Неактивные метки, разделители времени

    val Divider = Color(0xFF26313E)            // Тонкие технические разделительные линии (0.8 dp)
    val BorderSubtle = Color(0xFF1E2837)       // Контур карточек и панелей

    // Дополнительные специализированные оттенки
    val VinylBody = Color(0xFF0C0E14)          // Полотно винилового диска
    val VinylGroove = Color(0xFF161A22)        // Концентрические звуковые дорожки
    val TonearmMetal = Color(0xFFC0C7D0)       // Металл тонарма
    val RadarGrid = Color(0x2200F0FF)          // Координатная сетка и кольца сонара
    val ErrorRed = Color(0xFFFF4D4D)           // Предупреждения и ошибки подключения
    val SuccessGreen = Color(0xFF00E676)       // Успешный синхрон
}
