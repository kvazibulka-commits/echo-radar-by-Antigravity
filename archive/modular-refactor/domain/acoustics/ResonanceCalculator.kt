package ru.echo.radar.domain.acoustics

import kotlin.math.abs
import kotlin.math.roundToInt

object ResonanceCalculator {
    fun calculateResonance(
        userCamelot: String,
        userBpm: Int,
        userGenre: String,
        otherCamelot: String,
        otherBpm: Int,
        otherGenre: String
    ): Int {
        val key1 = CamelotWheel.parse(userCamelot)
        val key2 = CamelotWheel.parse(otherCamelot)

        // 1. Harmonic factor (50% weight)
        val harmonicScore = CamelotWheel.calculateHarmonicDistance(key1, key2)

        // 2. Tempo compatibility factor (25% weight)
        val bpmDiff = abs(userBpm - otherBpm)
        val bpmScore = when {
            bpmDiff <= 4 -> 1.0f
            bpmDiff <= 8 -> 0.85f
            bpmDiff <= 16 -> 0.70f
            bpmDiff <= 25 -> 0.50f
            else -> 0.30f
        }

        // 3. Genre affinity factor (25% weight)
        val genreScore = when {
            userGenre.equals(otherGenre, ignoreCase = true) -> 1.0f
            userGenre.contains(otherGenre, ignoreCase = true) || otherGenre.contains(userGenre, ignoreCase = true) -> 0.85f
            isRelatedGenre(userGenre, otherGenre) -> 0.75f
            else -> 0.45f
        }

        val total = (harmonicScore * 0.50f + bpmScore * 0.25f + genreScore * 0.25f) * 100f
        return total.roundToInt().coerceIn(35, 99)
    }

    private fun isRelatedGenre(g1: String, g2: String): Boolean {
        val pairs = listOf(
            setOf("Инди", "Инди-рок"),
            setOf("Пост-панк", "Инди-рок"),
            setOf("Пост-панк", "Авант-поп"),
            setOf("Джаз", "Соул"),
            setOf("Электроника", "Эмбиент")
        )
        val check = setOf(g1.trim(), g2.trim())
        return pairs.any { it == check }
    }
}
