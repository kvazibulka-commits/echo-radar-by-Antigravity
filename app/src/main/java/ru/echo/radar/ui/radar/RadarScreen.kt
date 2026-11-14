package ru.echo.radar.ui.radar

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
        contentPadding = PaddingValues(top = 8.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. App Header
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

                // Air pill & mode toggle
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AirPill(
                        text = "${signals.size} в эфире",
                        onClick = {}
                    )

                    // Sun / Moon icon button
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .clip(CircleShape)
                            .border(1.dp, colors.line, CircleShape)
                            .clickable(onClick = onToggleEveningMode),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (isEveningMode) "🌙" else "☀️",
                            fontSize = 13.sp
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

        // 2. Editorial Intro Block (Concept A «Атлас»)
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "ПОКРОВКА · РАДИУС 500 М",
                    color = colors.faint,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 8.5.sp,
                    letterSpacing = 1.2.sp
                )

                // Large Serif Headline: "День звучит тише и теплее"
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

                // Subtext with bold figures
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
                    fontSize = 10.sp
                )
            }
        }

        // 3. Atlas Topographic Radar (242x242 Frame)
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

        // 4. Signal List Header
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

        // 5. Signal Rows (matching .sig-row from design stand)
        itemsIndexed(signals) { index, sig ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSignalSelect(sig) }
                    .padding(vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Index 01, 02...
                    Text(
                        text = String.format("%02d", index + 1),
                        color = colors.faint,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 8.sp,
                        modifier = Modifier.width(18.dp)
                    )

                    // Main Info: Persona, Track, Meta
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(1.dp)
                    ) {
                        Text(
                            text = sig.persona.ifEmpty { "Слушатель эфира" },
                            color = colors.sub,
                            fontSize = 8.sp,
                            letterSpacing = 0.6.sp,
                            maxLines = 1
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "${sig.artist} — ",
                                color = colors.ink,
                                fontSize = 12.sp,
                                maxLines = 1
                            )
                            Text(
                                text = sig.track,
                                color = colors.ink,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                maxLines = 1
                            )
                        }
                        Text(
                            text = "${sig.genre} · ${sig.bpm} BPM · ${sig.camelotKey}",
                            color = colors.faint,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 7.5.sp,
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
                            color = colors.ink,
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

                Spacer(modifier = Modifier.height(8.dp))
                Box(modifier = Modifier.fillMaxWidth().height(0.5.dp).background(colors.line))
            }
        }

        // 6. Night Drop Card
        item {
            Spacer(modifier = Modifier.height(4.dp))
            NightDropCard(onListenFragment = onOpenNightDrop)
            Spacer(modifier = Modifier.height(60.dp)) // Leave space for mini-player
        }
    }
}
