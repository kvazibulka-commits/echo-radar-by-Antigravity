package ru.echo.radar.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class LocalEncounter(
    val id: String,
    val timeLabel: String,
    val periodLabel: String,
    val artist: String,
    val track: String,
    val locationContext: String,
    val resonancePercent: Int,
    val isVenue: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
)
