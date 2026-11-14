package ru.echo.radar.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class TimeCapsule(
    val id: String,
    val artist: String,
    val track: String,
    val message: String,
    val authorLabel: String = "Анонимный след",
    val distanceMeters: Int,
    val createdAt: Long = System.currentTimeMillis(),
    val expiresAt: Long = System.currentTimeMillis() + 7 * 24 * 60 * 60 * 1000L,
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val locationName: String = "Сквер",
    val angleDegrees: Float = 172f,
    val normalizedRadius: Float = 0.92f,
    val label: String? = null,
    val reward: String = "",
    val minResonancePercent: Int = 0,
    val isLocked: Boolean = false
) {
    fun daysRemaining(): Int {
        val diff = expiresAt - System.currentTimeMillis()
        return if (diff > 0) (diff / (24 * 60 * 60 * 1000L)).toInt().coerceAtLeast(1) else 0
    }
}
