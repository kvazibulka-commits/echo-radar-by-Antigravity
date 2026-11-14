package ru.echo.radar.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.echo.radar.EchoChronicleManager
import ru.echo.radar.NearbyEchoSignal
import ru.echo.radar.components.EchoCard
import ru.echo.radar.components.ResonanceBadge
import ru.echo.radar.components.TactileButton
import ru.echo.radar.components.TechnicalTag
import ru.echo.radar.state.AppSessionState
import ru.echo.radar.state.EchoModalSheet
import ru.echo.radar.theme.EchoShapes
import ru.echo.radar.theme.EchoTokens
import ru.echo.radar.theme.EchoTypography

private enum class EchoesFilter(val title: String) {
    RESONANCES("СОЗВУЧИЯ"),
    WAVES("ПОЙМАННЫЕ ВОЛНЫ"),
    STORIES("ИСТОРИИ КУРАТОРА")
}

/**
 * SCREEN 03 — ECHOES / ОТЗВУКИ
 * Архив музыкальной памяти ночного города.
 * Журнал акустических наблюдений без социальных профилей, лайков, аватарок и чатов.
 * В строгом соответствии с разделом 03 MASTER PROMPT:
 * - Фильтры: Созвучия, Пойманные волны, Истории.
 * - Карточки созвучий с физическими параметрами (BPM, Camelot, Resonance Score).
 * - Сохранение позиции скролла LazyListState.
 */
@Composable
fun EchoesScreen(
    state: AppSessionState,
    onOpenSheet: (EchoModalSheet) -> Unit,
    onNeedleSync: (String, String) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedFilter by remember { mutableStateOf(EchoesFilter.RESONANCES) }
    val listState = rememberLazyListState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(EchoTokens.Background)
            .statusBarsPadding()
    ) {
        // --- HEADER: ECHOES / ARCHIVE ---
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 10.dp)
        ) {
            Text(text = "ОТЗВУКИ", style = EchoTypography.Display.copy(fontSize = 20.sp, letterSpacing = 2.sp))
            Text(text = "ECHOES / ARCHIVE", style = EchoTypography.Label, color = EchoTokens.SignalCyan)
        }

        // --- FILTER PILLS ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            EchoesFilter.entries.forEach { filter ->
                val isSelected = filter == selectedFilter
                val bg = if (isSelected) EchoTokens.SignalCyanDim else EchoTokens.SurfaceElevated
                val borderCol = if (isSelected) EchoTokens.SignalCyan else EchoTokens.Divider
                val textCol = if (isSelected) EchoTokens.SignalCyan else EchoTokens.TextSecondary

                Box(
                    modifier = Modifier
                        .clip(EchoShapes.Pill)
                        .background(bg)
                        .border(0.8.dp, borderCol, EchoShapes.Pill)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = ripple(color = EchoTokens.SignalCyan),
                            onClick = { selectedFilter = filter }
                        )
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = filter.title,
                        style = EchoTypography.Label,
                        color = textCol
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // --- MAIN: ARCHIVE FEED ---
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(top = 4.dp, bottom = 80.dp)
        ) {
            when (selectedFilter) {
                EchoesFilter.RESONANCES -> {
                    if (state.nearbySignals.isEmpty() && EchoChronicleManager.savedTracks.isEmpty()) {
                        item { EmptyArchivePlaceholder("Нет зафиксированных созвучий в текущем сеансе") }
                    } else {
                        items(state.nearbySignals) { signal ->
                            EchoResonanceCard(
                                signal = signal,
                                onInspect = { onOpenSheet(EchoModalSheet.ResonanceDetail(signal)) },
                                onNeedleSync = { onNeedleSync(signal.id, signal.title) }
                            )
                        }
                    }
                }
                EchoesFilter.WAVES -> {
                    val incomingEchoes = EchoChronicleManager.incomingEchoes
                    if (incomingEchoes.isEmpty()) {
                        item { EmptyArchivePlaceholder("Эфир спокоен. Импульсы волн еще не дошли") }
                    } else {
                        items(incomingEchoes) { reaction ->
                            EchoCard {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(text = "ИМПУЛЬС ${reaction.symbol}", style = EchoTypography.Label, color = EchoTokens.VinylAmber)
                                    Text(text = "ID: ${reaction.id}", style = EchoTypography.Label, color = EchoTokens.TextMuted)
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(text = reaction.message, style = EchoTypography.Body, color = EchoTokens.TextPrimary)
                            }
                        }
                    }
                }
                EchoesFilter.STORIES -> {
                    item {
                        CuratorStoryCard(
                            title = "Ноктюрн спящего мегаполиса",
                            persona = "Архивариус ночных шорохов",
                            text = "В 02:40 на перекрестках города замирает шум автострад. В эфире звучат треки, в которых ритм совпадает с пульсом редких прохожих. Акустический инструмент фиксирует гармонию...",
                            onRead = {
                                onOpenSheet(
                                    EchoModalSheet.CuratorStory(
                                        title = "Ноктюрн спящего мегаполиса",
                                        text = "В 02:40 на перекрестках города замирает шум автострад. В эфире звучат треки, в которых ритм совпадает с пульсом редких прохожих. Акустический инструмент фиксирует гармонию на частотах 124 BPM.",
                                        persona = "Архивариус ночных шорохов"
                                    )
                                )
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EchoResonanceCard(
    signal: NearbyEchoSignal,
    onInspect: () -> Unit,
    onNeedleSync: () -> Unit
) {
    EchoCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = signal.distanceLabel.uppercase(), style = EchoTypography.Label, color = EchoTokens.TextSecondary)
            ResonanceBadge(score = signal.compatibility)
        }

        Spacer(modifier = Modifier.height(6.dp))

        Text(text = signal.title, style = EchoTypography.TrackTitle.copy(fontSize = 17.sp))
        Text(text = "${signal.artist} • ${signal.sourceApp}", style = EchoTypography.Body)

        Spacer(modifier = Modifier.height(8.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            TechnicalTag(label = "BPM", value = "${signal.bpm}")
            TechnicalTag(label = "KEY", value = signal.camelotKey, accentColor = EchoTokens.VinylAmber)
        }

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = signal.resonanceStory,
            style = EchoTypography.Secondary,
            color = EchoTokens.TextSecondary
        )

        Spacer(modifier = Modifier.height(12.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TactileButton(
                text = "ИЗУЧИТЬ",
                onClick = onInspect,
                modifier = Modifier.weight(1f)
            )
            TactileButton(
                text = "СИНХРОН ИГЛЫ",
                onClick = onNeedleSync,
                accentColor = EchoTokens.SignalCyan,
                isPrimary = true,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun CuratorStoryCard(
    title: String,
    persona: String,
    text: String,
    onRead: () -> Unit
) {
    EchoCard(borderColor = EchoTokens.VinylAmber.copy(alpha = 0.4f)) {
        Text(text = "ИСТОРИЯ ИИ-КУРАТОРА", style = EchoTypography.Label, color = EchoTokens.VinylAmber)
        Spacer(modifier = Modifier.height(4.dp))
        Text(text = title, style = EchoTypography.TrackTitle)
        Text(text = persona, style = EchoTypography.Secondary)
        Spacer(modifier = Modifier.height(6.dp))
        Text(text = text, style = EchoTypography.Body, maxLines = 3)
        Spacer(modifier = Modifier.height(10.dp))
        TactileButton(text = "ЧИТАТЬ ПОЛНОСТЬЮ", onClick = onRead, accentColor = EchoTokens.VinylAmber)
    }
}

@Composable
private fun EmptyArchivePlaceholder(message: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 40.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = "∿", style = EchoTypography.Display.copy(fontSize = 32.sp, color = EchoTokens.TextMuted))
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = message,
                style = EchoTypography.Secondary,
                color = EchoTokens.TextMuted
            )
        }
    }
}
