package ru.echo.radar.acoustics

import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * ResonanceCalculator — взвешенный расчет акустического резонанса.
 * Учитывает:
 * 1. Гармонию Camelot Wheel (50% вес).
 * 2. Темп BPM, включая half-time и double-time гармоники (25% вес).
 * 3. Аффинитет жанра и звуковых архетипов (25% вес).
 */
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

        // 1. Гармонический фактор (вес 50%)
        val harmonicScore = CamelotWheel.calculateHarmonicDistance(key1, key2)

        // 2. Темповый фактор с поддержкой Half/Double BPM (вес 25%)
        val bpmScore = calculateBpmCompatibility(userBpm, otherBpm)

        // 3. Жанровый аффинитет (вес 25%)
        val genreScore = calculateGenreScore(userGenre, otherGenre)

        val total = (harmonicScore * 0.50f + bpmScore * 0.25f + genreScore * 0.25f) * 100f
        return total.roundToInt().coerceIn(35, 99)
    }

    private fun calculateBpmCompatibility(bpm1: Int, bpm2: Int): Float {
        val b1 = bpm1.coerceIn(40, 220)
        val b2 = bpm2.coerceIn(40, 220)

        // Прямая разница темпов
        val directDiff = abs(b1 - b2)
        // Half-time / Double-time разница (например 70 vs 140)
        val halfDiff = abs(b1 - b2 * 2)
        val doubleDiff = abs(b1 * 2 - b2)
        val minDiff = minOf(directDiff, halfDiff, doubleDiff)

        return when {
            minDiff <= 3 -> 1.0f
            minDiff <= 7 -> 0.88f
            minDiff <= 15 -> 0.72f
            minDiff <= 24 -> 0.52f
            else -> 0.32f
        }
    }

    private fun calculateGenreScore(g1: String, g2: String): Float {
        val s1 = g1.trim().lowercase()
        val s2 = g2.trim().lowercase()
        if (s1.isEmpty() || s2.isEmpty() || s1.startsWith("анализ") || s2.startsWith("анализ")) {
            return 0.70f // Нейтральный фон ожидания
        }
        if (s1 == s2) return 1.0f
        if (s1.contains(s2) || s2.contains(s1)) return 0.88f

        val affinityGroups = listOf(
            setOf("инди", "инди-рок", "пост-панк", "post-punk", "indie", "indie rock"),
            setOf("трип-хоп", "trip-hop", "lo-fi", "downtempo", "эмбиент", "ambient"),
            setOf("электроника", "electronic", "techno", "synthwave", "idm"),
            setOf("рок", "rock", "hard rock", "alternative", "grunge"),
            setOf("джаз", "jazz", "soul", "funk", "neo-soul"),
            setOf("классика", "classical", "neoclassical", "ambient")
        )

        for (group in affinityGroups) {
            val has1 = group.any { s1.contains(it) }
            val has2 = group.any { s2.contains(it) }
            if (has1 && has2) return 0.82f
        }

        return 0.45f
    }
}
