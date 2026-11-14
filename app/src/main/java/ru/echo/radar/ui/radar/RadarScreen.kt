package ru.echo.radar.ui.radar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.echo.radar.domain.model.EchoSignal
import ru.echo.radar.ui.components.IndustrialBadge
import ru.echo.radar.ui.components.IndustrialButton
import ru.echo.radar.ui.components.IndustrialRadarCanvas
import ru.echo.radar.ui.theme.LocalEchoColors

@Composable
fun RadarScreen(
    signals: List<EchoSignal>,
    selectedSignal: EchoSignal?,
    onSignalSelect: (EchoSignal) -> Unit,
    onOpenDeck: (EchoSignal) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalEchoColors.current
    var currentFilter by remember { mutableStateOf("all") }

    val filteredSignals = remember(signals, currentFilter) {
        when (currentFilter) {
            "tracks" -> signals.filter { it.source.name != "BEACON_VENUE" && it.source.name != "TIME_CAPSULE" }
            "venues" -> signals.filter { it.source.name == "BEACON_VENUE" }
            "capsules" -> signals.filter { it.source.name == "TIME_CAPSULE" }
            else -> signals
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .padding(16.dp)
    ) {
        // 1. Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "ЭФИР",
                    color = colors.textPrimary,
                    fontWeight = FontWeight.Black,
                    fontSize = 22.sp
                )
                Text(
                    text = "${filteredSignals.size} сигналов в радиусе 500 м",
                    color = colors.textSecondary,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp
                )
            }
            IndustrialBadge(
                text = "● СОНАР АКТИВЕН",
                highlight = true
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // 2. Filter Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            FilterChip(text = "Все (${signals.size})", active = currentFilter == "all") { currentFilter = "all" }
            FilterChip(text = "Треки", active = currentFilter == "tracks") { currentFilter = "tracks" }
            FilterChip(text = "Заведения", active = currentFilter == "venues") { currentFilter = "venues" }
            FilterChip(text = "Капсулы", active = currentFilter == "capsules") { currentFilter = "capsules" }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // 3. Radar Box
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(8.dp))
                .background(colors.radarBg)
                .border(1.5.dp, colors.border, RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center
        ) {
            IndustrialRadarCanvas(
                signals = filteredSignals,
                selectedSignalId = selectedSignal?.id,
                onSignalSelected = onSignalSelect
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // 4. Quick Signal Bottom Card
        selectedSignal?.let { sig ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.surface, RoundedCornerShape(8.dp))
                    .border(1.5.dp, colors.border, RoundedCornerShape(8.dp))
                    .padding(12.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = sig.source.displayName.uppercase(),
                            color = colors.textSecondary,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp
                        )
                        IndustrialBadge(
                            text = "${sig.resonancePercent}% СОЗВУЧИЕ",
                            highlight = true
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = sig.track,
                                color = colors.textPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                maxLines = 1
                            )
                            Text(
                                text = sig.artist,
                                color = colors.textSecondary,
                                fontSize = 12.sp,
                                maxLines = 1
                            )
                        }
                        IndustrialButton(
                            text = "Дека →",
                            onClick = { onOpenDeck(sig) }
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Дистанция: ${sig.distanceMeters} м",
                            fontFamily = FontFamily.Monospace,
                            color = colors.textSubtle,
                            fontSize = 11.sp
                        )
                        Text(
                            text = "Camelot: ${sig.camelotKey} • ${sig.bpm} BPM",
                            fontFamily = FontFamily.Monospace,
                            color = colors.textSubtle,
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FilterChip(
    text: String,
    active: Boolean,
    onClick: () -> Unit
) {
    val colors = LocalEchoColors.current
    val bg = if (active) colors.accent else colors.surface
    val contentColor = if (active) Color.White else colors.textSecondary

    Box(
        modifier = Modifier
            .background(bg, RoundedCornerShape(4.dp))
            .border(1.dp, if (active) colors.accent else colors.border, RoundedCornerShape(4.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text(
            text = text,
            color = contentColor,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            fontSize = 10.sp
        )
    }
}
