package ru.echo.radar.acoustics

import kotlin.math.abs

/**
 * CamelotWheel — 12-секторный гармонический круг для расчета музыкальной совместимости.
 * Непреложное правило Master Prompt: алгоритмы Camelot Wheel и BPM сохраняются без упрощений.
 */
object CamelotWheel {
    data class CamelotKey(val hour: Int, val isMinor: Boolean) {
        val code: String get() = "$hour${if (isMinor) "A" else "B"}"
    }

    private val KEY_MAP = mapOf(
        "Am" to CamelotKey(8, true), "C" to CamelotKey(8, false),
        "Em" to CamelotKey(9, true), "G" to CamelotKey(9, false),
        "Bm" to CamelotKey(10, true), "D" to CamelotKey(10, false),
        "F#m" to CamelotKey(11, true), "A" to CamelotKey(11, false),
        "C#m" to CamelotKey(12, true), "E" to CamelotKey(12, false),
        "G#m" to CamelotKey(1, true), "B" to CamelotKey(1, false),
        "D#m" to CamelotKey(2, true), "F#" to CamelotKey(2, false),
        "A#m" to CamelotKey(3, true), "C#" to CamelotKey(3, false),
        "Fm" to CamelotKey(4, true), "G#" to CamelotKey(4, false),
        "Cm" to CamelotKey(5, true), "D#" to CamelotKey(5, false),
        "Gm" to CamelotKey(6, true), "A#" to CamelotKey(6, false),
        "Dm" to CamelotKey(7, true), "F" to CamelotKey(7, false)
    )

    fun parse(codeOrKey: String): CamelotKey {
        val clean = codeOrKey.trim()
        if (clean.length >= 2 && clean.last() in listOf('A', 'B', 'a', 'b')) {
            val hour = clean.dropLast(1).toIntOrNull() ?: 8
            val isMinor = clean.last().uppercaseChar() == 'A'
            return CamelotKey(hour.coerceIn(1, 12), isMinor)
        }
        return KEY_MAP[clean] ?: CamelotKey(8, true)
    }

    /**
     * Расчет гармонической дистанции:
     * - Идеальный унисон (тот же час и режим) = 1.0 (100%)
     * - Смежный час (+-1) = 0.90 (90%)
     * - Относительный мажор/минор (тот же час, сменился лад) = 0.85 (85%)
     * - Диагональный переход (+-1 час и лад) = 0.75 (75%)
     * - Энергетический скачок (+-2 часа) = 0.60 (60%)
     */
    fun calculateHarmonicDistance(key1: CamelotKey, key2: CamelotKey): Float {
        val hourDiff = abs(key1.hour - key2.hour).let { if (it > 6) 12 - it else it }
        val modeDiff = if (key1.isMinor == key2.isMinor) 0 else 1

        return when {
            hourDiff == 0 && modeDiff == 0 -> 1.0f
            hourDiff == 1 && modeDiff == 0 -> 0.90f
            hourDiff == 0 && modeDiff == 1 -> 0.85f
            hourDiff == 1 && modeDiff == 1 -> 0.75f
            hourDiff == 2 && modeDiff == 0 -> 0.60f
            else -> (0.50f - hourDiff * 0.05f).coerceAtLeast(0.20f)
        }
    }
}
