package ru.echo.radar.data.local

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import ru.echo.radar.domain.model.LocalEncounter

class LocalChronicleStorage {
    private val _encounters = MutableStateFlow<List<LocalEncounter>>(
        listOf(
            LocalEncounter(
                id = "enc-1",
                timeLabel = "08:45",
                periodLabel = "УТРО",
                artist = "Электрофорез",
                track = "Куда бежишь",
                locationContext = "Серпуховская линия • 18 м рядом",
                resonancePercent = 91
            ),
            LocalEncounter(
                id = "enc-2",
                timeLabel = "13:15",
                periodLabel = "ОБЕД",
                artist = "Сироткин",
                track = "Выше домов",
                locationContext = "Б. Никитская • 120 м рядом",
                resonancePercent = 94
            ),
            LocalEncounter(
                id = "enc-3",
                timeLabel = "15:30",
                periodLabel = "МАЯК",
                artist = "Хадн Дадн",
                track = "Мы сегодня дома",
                locationContext = "Кофейня «Фонотека & Зерно» • Пароль открыт",
                resonancePercent = 82,
                isVenue = true
            ),
            LocalEncounter(
                id = "enc-4",
                timeLabel = "19:10",
                periodLabel = "ВЕЧЕР",
                artist = "Дайте танк (!)",
                track = "Смешно",
                locationContext = "Трамвай «А» • 65 м рядом",
                resonancePercent = 88
            )
        )
    )

    val encounters: StateFlow<List<LocalEncounter>> = _encounters.asStateFlow()

    fun recordEncounter(encounter: LocalEncounter) {
        val current = _encounters.value.toMutableList()
        current.add(0, encounter)
        _encounters.value = current.take(50)
    }
}
