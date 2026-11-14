package ru.echo.radar.ui.radar

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.echo.radar.domain.model.EchoSignal
import ru.echo.radar.domain.model.VenueBeacon
import ru.echo.radar.ui.components.*
import ru.echo.radar.ui.theme.LocalEchoColors

@Composable
fun RadarScreen(
    signals: List<EchoSignal>,
    venueBeacons: List<VenueBeacon>,
    selectedSignal: EchoSignal?,
    isEveningMode: Boolean,
    isServerOnline: Boolean,
    serverUrl: String,
    hasNotificationAccess: Boolean,
    onRequestNotificationAccess: () -> Unit,
    onOpenServerSettings: () -> Unit,
    onToggleEveningMode: () -> Unit,
    onSignalSelect: (EchoSignal) -> Unit,
    onBeaconSelect: (VenueBeacon) -> Unit,
    onOpenNightDrop: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalEchoColors.current

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .padding(horizontal = 18.dp),
        contentPadding = PaddingValues(top = 8.dp, bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 1. Permission Warning Card (if notification capture is not enabled)
        if (!hasNotificationAccess) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(colors.accent2.copy(alpha = 0.12f))
                        .border(1.dp, colors.accent2.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                        .clickable(onClick = onRequestNotificationAccess)
                        .padding(10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(text = "🔔", fontSize = 16.sp)
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Требуется доступ к уведомлениям",
                                color = colors.ink,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            )
                            Text(
                                text = "Для захвата музыки из Яндекс Музыки, VK, Звука и Spotify без внешних API.",
                                color = colors.sub,
                                fontSize = 9.sp,
                                lineHeight = 12.sp
                            )
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(colors.accent2)
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "Включить",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 9.5.sp
                            )
                        }
                    }
                }
            }
        }

        // 2. App Header
        item {
            Row(
                modifier = Modifier.fillMaxWidth().height(44.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Brand "ЭХО" + teal dot
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Text(
                        text = "ЭХО",
                        color = colors.ink,
                        fontFamily = FontFamily.Serif,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        letterSpacing = 3.sp
                    )
                    Box(
                        modifier = Modifier
                            .size(4.dp)
                            .background(colors.live, CircleShape)
                    )
                }

                // Air pill, Server status, & mode toggle
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Server connection pill
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .border(1.dp, if (isServerOnline) colors.live.copy(alpha = 0.5f) else colors.line, RoundedCornerShape(999.dp))
                            .clickable(onClick = onOpenServerSettings)
                            .padding(horizontal = 7.dp, vertical = 3.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(5.dp)
                                    .background(if (isServerOnline) colors.live else colors.gold, CircleShape)
                            )
                            Text(
                                text = if (isServerOnline) "ОНЛАЙН" else "АВТОНОМНО",
                                color = if (isServerOnline) colors.live else colors.sub,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 7.sp,
                                letterSpacing = 0.5.sp
                            )
                        }
                    }

                    AirPill(
                        text = "${signals.size} в эфире",
                        onClick = {}
                    )

                    // Sun / Moon icon button
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .border(1.dp, colors.line, CircleShape)
                            .clickable(onClick = onToggleEveningMode),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (isEveningMode) "🌙" else "☀️",
                            fontSize = 12.sp
                        )
                    }
                }
            }

            // Context line: pin dot + city zone + time
            Row(
                modifier = Modifier.fillMaxWidth().height(20.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(4.dp)
                            .background(colors.accent, CircleShape)
                    )
                    Text(
                        text = "Покровка · Москва",
                        color = colors.sub,
                        fontSize = 9.sp
                    )
                }
                Text(
                    text = if (isEveningMode) "23:10 · НОЧЬ" else "14:20 · ДЕНЬ",
                    color = colors.faint,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 7.5.sp,
                    letterSpacing = 0.8.sp
                )
            }
        }

        // 3. Editorial Intro Block
        item {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    text = "ПОКРОВКА · РАДИУС 500 М",
                    color = colors.faint,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 8.sp,
                    letterSpacing = 1.2.sp
                )

                // Large Serif Headline
                val headlineText = buildAnnotatedString {
                    if (isEveningMode) {
                        append("Ночь звучит\n")
                        withStyle(SpanStyle(color = colors.accent2, fontStyle = FontStyle.Italic)) {
                            append("холодным пост-панком")
                        }
                    } else {
                        append("День звучит\n")
                        withStyle(SpanStyle(color = colors.accent2, fontStyle = FontStyle.Italic)) {
                            append("тише и теплее")
                        }
                    }
                }

                Text(
                    text = headlineText,
                    color = colors.ink,
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.Normal,
                    fontSize = 28.sp,
                    lineHeight = 30.sp,
                    letterSpacing = (-0.5).sp
                )

                // Subtext with bold numbers
                val subText = buildAnnotatedString {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = colors.ink)) {
                        append("${signals.size}")
                    }
                    append(" слушателей · ")
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = colors.ink)) {
                        append("${venueBeacons.size}")
                    }
                    append(" тайника · резонанс ")
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = colors.ink)) {
                        append("85%")
                    }
                }

                Text(
                    text = subText,
                    color = colors.sub,
                    fontSize = 9.5.sp
                )
            }
        }

        // 4. Atlas Dynamic Topographic Radar (242x242 Frame)
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(260.dp)
                ) {
                    AtlasRadarCanvas(
                        signals = signals,
                        venueBeacons = venueBeacons,
                        selectedSignalId = selectedSignal?.id,
                        onSignalSelected = onSignalSelect,
                        onBeaconSelected = onBeaconSelect,
                        modifier = Modifier.fillMaxSize()
                    )

                    // Corner labels
                    Text(
                        text = "СОНАР · 500 М",
                        color = colors.faint,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 7.sp,
                        letterSpacing = 1.sp,
                        modifier = Modifier.align(Alignment.TopStart).padding(top = 2.dp, start = 2.dp)
                    )

                    Text(
                        text = "${signals.size} СИГНАЛОВ · ${venueBeacons.size} ТАЙНИКА",
                        color = colors.faint,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 7.sp,
                        letterSpacing = 1.sp,
                        modifier = Modifier.align(Alignment.BottomEnd).padding(bottom = 2.dp, end = 2.dp)
                    )
                }
            }
        }

        // 5. Signal List Header
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp, bottom = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Text(
                    text = "В ЭФИРЕ СЕЙЧАС",
                    color = colors.faint,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 7.5.sp,
                    letterSpacing = 1.2.sp
                )
                Text(
                    text = "РЕЗОНАНС",
                    color = colors.faint,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 7.5.sp,
                    letterSpacing = 1.2.sp
                )
            }
            Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(colors.line))
        }

        // 6. Signal Rows with tactile paper depth and generative art
        itemsIndexed(signals) { index, sig ->
            val isSelected = sig.id == selectedSignal?.id
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (isSelected) colors.surface2.copy(alpha = 0.5f) else Color.Transparent)
                    .clickable { onSignalSelect(sig) }
                    .padding(vertical = 8.dp, horizontal = 4.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Index 01, 02...
                    Text(
                        text = String.format("%02d", index + 1),
                        color = colors.faint,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 8.sp,
                        modifier = Modifier.width(18.dp)
                    )

                    // Small generative art box
                    GenArtCover(
                        hue = sig.hue,
                        modifier = Modifier.size(28.dp)
                    )

                    // Main Info: Persona, Track, Meta
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(1.dp)
                    ) {
                        Text(
                            text = sig.persona.ifEmpty { "Слушатель эфира" },
                            color = colors.sub,
                            fontSize = 7.5.sp,
                            letterSpacing = 0.5.sp,
                            maxLines = 1
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "${sig.artist} — ",
                                color = colors.ink,
                                fontSize = 11.5.sp,
                                maxLines = 1
                            )
                            Text(
                                text = sig.track,
                                color = colors.ink,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.5.sp,
                                maxLines = 1
                            )
                        }
                        Text(
                            text = "${sig.genre} · ${sig.bpm} BPM · ${sig.camelotKey}",
                            color = colors.faint,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 7.sp,
                            letterSpacing = 0.2.sp,
                            maxLines = 1
                        )
                    }

                    // Resonance in Serif (17sp) + small %
                    Row(
                        verticalAlignment = Alignment.Top
                    ) {
                        Text(
                            text = "${sig.resonancePercent}",
                            color = if (isSelected) colors.accent else colors.ink,
                            fontFamily = FontFamily.Serif,
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp,
                            letterSpacing = (-0.5).sp
                        )
                        Text(
                            text = "%",
                            color = colors.faint,
                            fontSize = 8.sp,
                            modifier = Modifier.padding(top = 2.dp, start = 1.dp)
                        )
                    }
                }
            }
            Box(modifier = Modifier.fillMaxWidth().height(0.5.dp).background(colors.line))
        }

        // 7. Night Drop Card
        item {
            Spacer(modifier = Modifier.height(4.dp))
            NightDropCard(onListenFragment = onOpenNightDrop)
            Spacer(modifier = Modifier.height(60.dp))
        }
    }
}
