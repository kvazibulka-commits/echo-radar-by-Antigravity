package ru.echo.radar.domain.model

import kotlinx.serialization.Serializable

@Serializable
enum class VenueType(val label: String, val icon: String) {
    COFFEE("Спешлти кофейня", "☕"),
    BAR("Виниловый бар", "🍸"),
    RECORD_STORE("Магазин винила", "💿"),
    BOOKSHOP("Книжная лавка", "📚"),
    BARBERSHOP("Барбершоп", "✂️")
}

@Serializable
data class VenueBeacon(
    val id: String,
    val name: String,
    val venueType: VenueType,
    val currentTrack: String,
    val currentArtist: String,
    val streamingSource: String = "Звук",
    val distanceMeters: Int,
    val minResonancePercent: Int = 75,
    val proofCode: String,
    val discountDescription: String,
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val address: String = "Большая Никитская, 14",
    val angleDegrees: Float = 100f,
    val normalizedRadius: Float = 0.44f,
    val label: String = "Соль · 120 м",
    val whisper: String = "Сегодня здесь варят кофе под пост-панк. Скажите бариста кодовое слово — и получите чашку с созвучием."
) {
    fun isUnlocked(userResonancePercent: Int): Boolean {
        return userResonancePercent >= minResonancePercent
    }
}
