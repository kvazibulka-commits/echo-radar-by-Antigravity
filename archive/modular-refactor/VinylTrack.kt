package ru.echo.radar

/**
 * VinylTrack — унифицированная модель трека для летописи и виниловой деки.
 */
data class VinylTrack(
    val id: String,
    val title: String,
    val artist: String,
    val sourceApp: String,
    val distanceLabel: String = "~75 м",
    val poeticPersona: String = "Слушатель",
    val observation: String = "",
    val story: String = "",
    val compatibility: Int = 80,
    val angleDeg: Float = 0f,
    val ringFactor: Float = 0.5f,
    val colorHex: Long = 0xFF00F0FF,
    val isCapsule: Boolean = false,
    val isGhost: Boolean = false,
    val isSelf: Boolean = false,
    val coverUrl: String? = null,
    val isVenue: Boolean = false,
    val venueName: String = "",
    val minResonance: Int = 0,
    val secretPerk: String = "",
    val isUnlocked: Boolean = true,
    val bpm: Int = 120,
    val camelotKey: String = "8A",
    val genre: String = "indie"
)

fun PlayingTrack.toVinylTrack(): VinylTrack = VinylTrack(
    id = "self_track",
    title = title,
    artist = artist,
    sourceApp = sourceApp,
    distanceLabel = "Вы",
    poeticPersona = poeticPersona,
    observation = mood,
    story = "",
    compatibility = 100,
    angleDeg = 0f,
    ringFactor = 0f,
    colorHex = 0xFF00F0FF,
    isSelf = true,
    coverUrl = coverUrl,
    bpm = bpm,
    camelotKey = camelotKey,
    genre = genre
)

fun NearbyEchoSignal.toVinylTrack(): VinylTrack = VinylTrack(
    id = id,
    title = title,
    artist = artist,
    sourceApp = sourceApp,
    distanceLabel = distanceLabel,
    poeticPersona = poeticName,
    observation = aiObservation,
    story = resonanceStory,
    compatibility = compatibility,
    angleDeg = angleDeg,
    ringFactor = ringFactor,
    colorHex = colorHex,
    coverUrl = coverUrl,
    bpm = bpm,
    camelotKey = camelotKey,
    genre = genre
)

fun EchoCapsule.toVinylTrack(): VinylTrack = VinylTrack(
    id = id,
    title = title,
    artist = artist,
    sourceApp = "Тайник",
    distanceLabel = distanceLabel,
    poeticPersona = personaTitle,
    observation = secretMessage,
    story = secretMessage,
    compatibility = compatibility,
    angleDeg = angleDeg,
    ringFactor = ringFactor,
    colorHex = 0xFFFFD700,
    isCapsule = true,
    isVenue = isVenue,
    venueName = venueName,
    minResonance = minResonance,
    secretPerk = secretPerk,
    isUnlocked = isUnlocked
)
