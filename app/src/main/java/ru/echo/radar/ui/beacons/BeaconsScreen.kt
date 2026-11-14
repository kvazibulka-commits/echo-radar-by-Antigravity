package ru.echo.radar.ui.beacons

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.echo.radar.domain.model.TimeCapsule
import ru.echo.radar.domain.model.VenueBeacon
import ru.echo.radar.ui.theme.LocalEchoColors

@Composable
fun BeaconsScreen(
    venueBeacons: List<VenueBeacon>,
    timeCapsules: List<TimeCapsule>,
    userResonancePercent: Int,
    onShowBaristaCode: (VenueBeacon) -> Unit,
    onCreateCapsule: () -> Unit,
    onClaimVenueB2B: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalEchoColors.current
    var selectedRadius by remember { mutableStateOf<Int?>(null) } // null = all

    val filteredVenues = remember(venueBeacons, selectedRadius) {
        if (selectedRadius == null) venueBeacons else venueBeacons.filter { it.distanceMeters <= selectedRadius!! }
    }

    val filteredCapsules = remember(timeCapsules, selectedRadius) {
        if (selectedRadius == null) timeCapsules else timeCapsules.filter { it.distanceMeters <= selectedRadius!! }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .padding(horizontal = 18.dp, vertical = 12.dp)
    ) {
        // Head
        Row(
            modifier = Modifier.fillMaxWidth().height(32.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "ТАЙНИКИ · КАРТА",
                color = colors.faint,
                fontFamily = FontFamily.Monospace,
                fontSize = 8.5.sp,
                letterSpacing = 1.sp
            )
            Text(
                text = "${filteredVenues.size + filteredCapsules.size} АКТИВНО",
                color = colors.faint,
                fontFamily = FontFamily.Monospace,
                fontSize = 7.5.sp,
                letterSpacing = 0.5.sp
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = "Тайники города",
            color = colors.ink,
            fontFamily = FontFamily.Serif,
            fontWeight = FontWeight.Normal,
            fontSize = 26.sp,
            letterSpacing = (-0.4).sp
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Radius row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            RadiusChip("Все", selected = selectedRadius == null) { selectedRadius = null }
            RadiusChip("до 250 м", selected = selectedRadius == 250) { selectedRadius = 250 }
            RadiusChip("до 500 м", selected = selectedRadius == 500) { selectedRadius = 500 }
            RadiusChip("до 1 км", selected = selectedRadius == 1000) { selectedRadius = 1000 }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // List
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Venue Beacons (Proof-of-Music)
            items(filteredVenues) { venue ->
                val isUnlocked = venue.isUnlocked(userResonancePercent)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onShowBaristaCode(venue) }
                        .padding(vertical = 10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Diamond glyph
                        Box(
                            modifier = Modifier
                                .size(11.dp)
                                .rotate(45f)
                                .background(colors.accent.copy(alpha = 0.12f))
                                .border(1.dp, colors.accent)
                        )

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "ЗАВЕДЕНИЕ · PROOF-OF-MUSIC",
                                color = colors.faint,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 6.5.sp,
                                letterSpacing = 0.8.sp
                            )
                            Text(
                                text = venue.name,
                                color = colors.ink,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.5.sp,
                                letterSpacing = (-0.2).sp
                            )
                            Text(
                                text = "${venue.distanceMeters} м · порог ${venue.minResonancePercent}% · до 18:00",
                                color = colors.sub,
                                fontSize = 8.sp
                            )
                            Text(
                                text = venue.discountDescription,
                                color = colors.accent,
                                fontSize = 8.5.sp
                            )
                        }

                        Text(
                            text = "${venue.minResonancePercent}%",
                            color = colors.gold,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Box(modifier = Modifier.fillMaxWidth().height(0.5.dp).background(colors.line))
                }
            }

            // Time Capsules
            items(filteredCapsules) { capsule ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Diamond amber glyph
                        Box(
                            modifier = Modifier
                                .size(11.dp)
                                .rotate(45f)
                                .background(colors.gold.copy(alpha = 0.14f))
                                .border(1.dp, colors.gold)
                        )

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (capsule.minResonancePercent > 0) "ТАЙНИК · ${capsule.daysRemaining()} ДНЕЙ" else "ШЁПОТ · БЕЗ ЗАМКА",
                                color = colors.faint,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 6.5.sp,
                                letterSpacing = 0.8.sp
                            )
                            Text(
                                text = capsule.locationName,
                                color = colors.ink,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.5.sp,
                                letterSpacing = (-0.2).sp
                            )
                            Text(
                                text = "${capsule.distanceMeters} м · ${capsule.artist} — ${capsule.track}",
                                color = colors.sub,
                                fontSize = 8.sp
                            )
                            Text(
                                text = "«${capsule.message}»",
                                color = colors.sub,
                                fontSize = 8.5.sp
                            )
                        }

                        Text(
                            text = "→",
                            color = colors.faint,
                            fontSize = 13.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Box(modifier = Modifier.fillMaxWidth().height(0.5.dp).background(colors.line))
                }
            }

            // Leave Capsule button
            item {
                Spacer(modifier = Modifier.height(12.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(38.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(colors.surface)
                        .border(1.dp, colors.line, RoundedCornerShape(8.dp))
                        .clickable(onClick = onCreateCapsule),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "+ Оставить свой тайник",
                        color = colors.sub,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

@Composable
private fun RadiusChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val colors = LocalEchoColors.current
    val bg = if (selected) colors.accent.copy(alpha = 0.12f) else Color.Transparent
    val textCol = if (selected) colors.accent else colors.sub
    val borderCol = if (selected) colors.accent.copy(alpha = 0.45f) else colors.line

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(bg)
            .border(1.dp, borderCol, RoundedCornerShape(999.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 9.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = textCol,
            fontFamily = FontFamily.Monospace,
            fontSize = 7.5.sp
        )
    }
}
