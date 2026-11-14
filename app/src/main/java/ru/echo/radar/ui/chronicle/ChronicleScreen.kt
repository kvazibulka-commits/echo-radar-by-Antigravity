package ru.echo.radar.ui.chronicle

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.echo.radar.domain.model.LocalEncounter
import ru.echo.radar.ui.theme.LocalEchoColors

@Composable
fun ChronicleScreen(
    encounters: List<LocalEncounter>,
    modifier: Modifier = Modifier
) {
    val colors = LocalEchoColors.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .padding(16.dp)
    ) {
        // Header
        Column(modifier = Modifier.padding(bottom = 14.dp)) {
            Text(
                text = "ХРОНИКА",
                color = colors.textPrimary,
                fontWeight = FontWeight.Black,
                fontSize = 22.sp
            )
            Text(
                text = "Музыкальные пересечения за день • ${encounters.size} следов",
                color = colors.textSecondary,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp
            )
        }

        // Timeline items
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(encounters) { item ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(colors.surface, RoundedCornerShape(8.dp))
                        .border(
                            width = 1.5.dp,
                            color = if (item.isVenue) colors.venueGold else colors.border,
                            shape = RoundedCornerShape(8.dp)
                        )
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Time Badge
                    Box(
                        modifier = Modifier
                            .width(52.dp)
                            .background(colors.surfaceElevated, RoundedCornerShape(4.dp))
                            .border(1.dp, colors.border, RoundedCornerShape(4.dp))
                            .padding(vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = item.timeLabel,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                color = colors.textPrimary,
                                fontSize = 11.sp
                            )
                            Text(
                                text = item.periodLabel,
                                fontFamily = FontFamily.Monospace,
                                color = colors.textSecondary,
                                fontSize = 9.sp
                            )
                        }
                    }

                    // Content
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = item.track,
                            color = colors.textPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            maxLines = 1
                        )
                        Text(
                            text = item.artist,
                            color = colors.textSecondary,
                            fontSize = 12.sp,
                            maxLines = 1
                        )
                        Text(
                            text = item.locationContext,
                            color = colors.textSubtle,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            maxLines = 1,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }

                    // Resonance badge
                    Text(
                        text = "${item.resonancePercent}%",
                        color = colors.accent,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }
            }

            // Quiet Slow-Tech Footer (NO AGGRESSIVE PAYWALLS)
            item {
                Spacer(modifier = Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "✦ Эфир сохраняется локально на вашем устройстве.\nТолько чистый звук и память города, без слежки за координатами.",
                        color = colors.textSubtle,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        textAlign = TextAlign.Center,
                        lineHeight = 16.sp
                    )
                }
            }
        }
    }
}
