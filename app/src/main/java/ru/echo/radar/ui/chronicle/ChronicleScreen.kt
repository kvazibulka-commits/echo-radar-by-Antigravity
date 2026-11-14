package ru.echo.radar.ui.chronicle

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
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
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
    var activeSegment by remember { mutableStateOf("all") }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .padding(horizontal = 18.dp, vertical = 12.dp)
    ) {
        // Screen Head
        Row(
            modifier = Modifier.fillMaxWidth().height(32.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "ОТЗВУКИ · ЖУРНАЛ",
                color = colors.faint,
                fontFamily = FontFamily.Monospace,
                fontSize = 8.5.sp,
                letterSpacing = 1.sp
            )
            Text(
                text = "${encounters.size} СЛЕДОВ",
                color = colors.faint,
                fontFamily = FontFamily.Monospace,
                fontSize = 7.5.sp,
                letterSpacing = 0.5.sp
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Headline
        val title = buildAnnotatedString {
            append("Отзвуки ")
            withStyle(SpanStyle(color = colors.accent2, fontStyle = FontStyle.Italic)) {
                append("дня")
            }
        }
        Text(
            text = title,
            color = colors.ink,
            fontFamily = FontFamily.Serif,
            fontWeight = FontWeight.Normal,
            fontSize = 26.sp,
            letterSpacing = (-0.4).sp
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Segments row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            SegmentChip("Все", selected = activeSegment == "all") { activeSegment = "all" }
            SegmentChip("Сохранённые", selected = activeSegment == "saved") { activeSegment = "saved" }
            SegmentChip("Волны", selected = activeSegment == "waves") { activeSegment = "waves" }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Privacy Note
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(colors.accent.copy(alpha = 0.06f))
                .border(1.dp, colors.accent.copy(alpha = 0.22f), RoundedCornerShape(8.dp))
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(text = "🔒", fontSize = 10.sp)
            Text(
                text = "Отзвуки сохраняются только на этом устройстве и никуда не передаются.",
                color = colors.sub,
                fontSize = 8.5.sp,
                lineHeight = 12.sp
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Timeline items
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(encounters) { item ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = item.timeLabel,
                            color = colors.faint,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 7.5.sp,
                            modifier = Modifier.width(36.dp)
                        )

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "${item.artist} — ${item.track}",
                                color = colors.ink,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 11.sp,
                                maxLines = 1
                            )
                            Text(
                                text = item.locationContext,
                                color = colors.sub,
                                fontSize = 8.sp,
                                maxLines = 1
                            )
                        }

                        Text(
                            text = "${item.resonancePercent}%",
                            color = colors.sub,
                            fontFamily = FontFamily.Serif,
                            fontSize = 13.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Box(modifier = Modifier.fillMaxWidth().height(0.5.dp).background(colors.line))
                }
            }
        }
    }
}

@Composable
private fun SegmentChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val colors = LocalEchoColors.current
    val bg = if (selected) colors.ink else Color.Transparent
    val textColor = if (selected) colors.background else colors.sub
    val borderCol = if (selected) colors.ink else colors.line

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(bg)
            .border(1.dp, borderCol, RoundedCornerShape(999.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = textColor,
            fontSize = 8.5.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
        )
    }
}
