package ru.echo.radar.domain.model

import kotlinx.serialization.Serializable

@Serializable
enum class SignalSource(val displayName: String) {
    YANDEX_MUSIC("Яндекс Музыка"),
    VK_MUSIC("VK Музыка"),
    SPOTIFY("Spotify"),
    ZVUK("Звук"),
    BEACON_VENUE("Маяк заведения"),
    TIME_CAPSULE("Капсула времени"),
    UNKNOWN("Эфир")
}

@Serializable
data class EchoSignal(
    val id: String,
    val artist: String,
    val track: String,
    val album: String = "",
    val source: SignalSource = SignalSource.UNKNOWN,
    val distanceMeters: Int = 50,
    val angleDegrees: Float = 0f,
    val normalizedRadius: Float = 0.35f,
    val resonancePercent: Int = 75,
    val camelotKey: String = "8A",
    val bpm: Int = 120,
    val genre: String = "Инди",
    val timestamp: Long = System.currentTimeMillis(),
    val isPlaying: Boolean = true,
    val isSyncedWithUser: Boolean = false,
    val albumArtUrl: String? = null,
    val persona: String = "",
    val story: String = "",
    val label: String? = null,
    val hue: Float = 212f
)
