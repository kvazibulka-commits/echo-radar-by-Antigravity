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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.echo.radar.domain.model.TimeCapsule
import ru.echo.radar.domain.model.VenueBeacon
import ru.echo.radar.ui.components.IndustrialBadge
import ru.echo.radar.ui.components.IndustrialButton
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
                    text = "ТАЙНИКИ",
                    color = colors.textPrimary,
                    fontWeight = FontWeight.Black,
                    fontSize = 22.sp
                )
                Text(
                    text = "Звуковые маяки заведений и капсулы времени",
                    color = colors.textSecondary,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp
                )
            }
            IndustrialBadge(
                text = "${filteredVenues.size + filteredCapsules.size} АКТИВНО",
                highlight = true
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // 2. Radius Chips Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            RadiusChip("Все", selected = selectedRadius == null) { selectedRadius = null }
            RadiusChip("до 250 м", selected = selectedRadius == 250) { selectedRadius = 250 }
            RadiusChip("до 500 м", selected = selectedRadius == 500) { selectedRadius = 500 }
            RadiusChip("до 1 км", selected = selectedRadius == 1000) { selectedRadius = 1000 }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // 3. Beacons & Capsules List
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Venue Beacons
            items(filteredVenues) { venue ->
                val isUnlocked = venue.isUnlocked(userResonancePercent)

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(colors.surface, RoundedCornerShape(8.dp))
                        .border(
                            width = 1.5.dp,
                            color = if (isUnlocked) colors.venueGold else colors.border,
                            shape = RoundedCornerShape(8.dp)
                        )
                        .padding(14.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${venue.venueType.icon} ${venue.venueType.label.uppercase()}",
                                color = colors.textSecondary,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "${venue.distanceMeters} м рядом",
                                color = colors.accent,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Text(
                            text = venue.name,
                            color = colors.textPrimary,
                            fontWeight = FontWeight.Black,
                            fontSize = 16.sp
                        )

                        // Music playing in venue
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(colors.surfaceElevated, RoundedCornerShape(4.dp))
                                .border(1.dp, colors.border, RoundedCornerShape(4.dp))
                                .padding(8.dp)
                        ) {
                            Text(
                                text = "В зале: ${venue.currentArtist} — ${venue.currentTrack} (${venue.streamingSource})",
                                color = colors.textSecondary,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp
                            )
                        }

                        // Proof-of-music status box
                        if (isUnlocked) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(colors.venueGold.copy(alpha = 0.12f), RoundedCornerShape(4.dp))
                                    .border(1.dp, colors.venueGold, RoundedCornerShape(4.dp))
                                    .padding(10.dp)
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(
                                        text = "🔓 АКУСТИЧЕСКИЙ ПАРОЛЬ ОТКРЫТ (Созвучие $userResonancePercent% ≥ ${venue.minResonancePercent}%)",
                                        color = colors.venueGold,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 10.sp
                                    )
                                    Text(
                                        text = venue.discountDescription,
                                        color = colors.textPrimary,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }

                            IndustrialButton(
                                text = "🎫 Показать экран бариста",
                                onClick = { onShowBaristaCode(venue) },
                                isPrimary = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(colors.surfaceElevated, RoundedCornerShape(4.dp))
                                    .border(1.dp, colors.border, RoundedCornerShape(4.dp))
                                    .padding(10.dp)
                            ) {
                                Text(
                                    text = "🔒 Замок вкуса: Созвучие $userResonancePercent% (нужно ≥ ${venue.minResonancePercent}%)\nВключите в наушниках трек схожего жанра, чтобы взломать пароль.",
                                    color = colors.textSecondary,
                                    fontSize = 11.sp,
                                    lineHeight = 16.sp
                                )
                            }
                        }
                    }
                }
            }

            // Time Capsules
            items(filteredCapsules) { capsule ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(colors.surface, RoundedCornerShape(8.dp))
                        .border(1.5.dp, colors.capsuleEmerald, RoundedCornerShape(8.dp))
                        .padding(14.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "⏳ КАПСУЛА ВРЕМЕНИ • ${capsule.locationName.uppercase()}",
                                color = colors.capsuleEmerald,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "${capsule.distanceMeters} м • ${capsule.daysRemaining()} дн.",
                                color = colors.textSecondary,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp
                            )
                        }

                        Text(
                            text = "${capsule.artist} — «${capsule.track}»",
                            color = colors.textPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )

                        Text(
                            text = "«${capsule.message}»",
                            color = colors.textSecondary,
                            fontSize = 12.sp,
                            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                            lineHeight = 16.sp
                        )
                    }
                }
            }

            // Create Capsule action button
            item {
                Spacer(modifier = Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(colors.surface, RoundedCornerShape(8.dp))
                        .border(1.5.dp, colors.accent, RoundedCornerShape(8.dp))
                        .clickable(onClick = onCreateCapsule)
                        .padding(14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "+ Оставить звуковую капсулу здесь",
                            color = colors.textPrimary,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                        Text(
                            text = "(1 бесплатная на этой неделе)",
                            color = colors.textSecondary,
                            fontSize = 10.sp
                        )
                    }
                }
            }

            // Subtle B2B link at bottom
            item {
                Spacer(modifier = Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onClaimVenueB2B)
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Для владельцев кофеен и баров: Заявить звуковой маяк квартала →",
                        color = colors.textSubtle,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp
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
    val bg = if (selected) colors.accent else colors.surface
    val contentColor = if (selected) Color.White else colors.textSecondary

    Box(
        modifier = Modifier
            .background(bg, RoundedCornerShape(4.dp))
            .border(1.dp, if (selected) colors.accent else colors.border, RoundedCornerShape(4.dp))
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
