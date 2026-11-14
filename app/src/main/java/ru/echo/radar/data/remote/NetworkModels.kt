package ru.echo.radar.data.remote

import kotlinx.serialization.Serializable

@Serializable
data class SignalRequest(
    val deviceToken: String,
    val artist: String,
    val track: String,
    val album: String = "",
    val source: String = "Яндекс Музыка",
    val latitude: Double,
    val longitude: Double,
    val isPlaying: Boolean = true,
    val bpm: Int = 120,
    val camelot: String = "8A",
    val genre: String = "Инди"
)

@Serializable
data class StateResponse(
    val status: String = "ok",
    val signals: List<RemoteSignal> = emptyList(),
    val venueBeacons: List<RemoteVenueBeacon> = emptyList(),
    val timeCapsules: List<RemoteTimeCapsule> = emptyList()
)

@Serializable
data class RemoteSignal(
    val id: String,
    val artist: String,
    val track: String,
    val album: String = "",
    val source: String = "Яндекс Музыка",
    val distanceMeters: Int = 50,
    val angleDegrees: Float = 0f,
    val resonancePercent: Int = 75,
    val camelot: String = "8A",
    val bpm: Int = 120,
    val genre: String = "Инди",
    val isPlaying: Boolean = true,
    val timestamp: Long = 0L
)

@Serializable
data class RemoteVenueBeacon(
    val id: String,
    val name: String,
    val venueType: String = "COFFEE",
    val currentTrack: String,
    val currentArtist: String,
    val streamingSource: String = "Звук",
    val distanceMeters: Int = 85,
    val minResonancePercent: Int = 75,
    val proofCode: String = "ЭХО-82",
    val discountDescription: String,
    val address: String = "Большая Никитская, 14"
)

@Serializable
data class RemoteTimeCapsule(
    val id: String,
    val artist: String,
    val track: String,
    val message: String,
    val distanceMeters: Int = 300,
    val locationName: String = "Сквер",
    val expiresAt: Long = 0L
)

@Serializable
data class CreateCapsuleRequest(
    val deviceToken: String,
    val artist: String,
    val track: String,
    val message: String,
    val latitude: Double,
    val longitude: Double,
    val locationName: String = "Точка в городе"
)

@Serializable
data class QuietPulseRequest(
    val deviceToken: String,
    val targetSignalId: String,
    val pulseType: String = "THANKS_FOR_MUSIC"
)
