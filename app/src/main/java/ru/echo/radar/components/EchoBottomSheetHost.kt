package ru.echo.radar.components

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.echo.radar.EchoCapsule
import ru.echo.radar.LagrangePointInfo
import ru.echo.radar.NearbyEchoSignal
import ru.echo.radar.state.EchoModalSheet
import ru.echo.radar.theme.EchoShapes
import ru.echo.radar.theme.EchoTokens
import ru.echo.radar.theme.EchoTypography

/**
 * EchoBottomSheetHost — единая модальная система прибора.
 * В соответствии с разделом 07 MASTER PROMPT:
 * Одновременно может быть открыт строго один лист. Единое затемнение, радиусы и физика.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EchoBottomSheetHost(
    activeSheet: EchoModalSheet?,
    onDismiss: () -> Unit,
    onNeedleSync: (String, String) -> Unit,
    onWaveImpulse: (String) -> Unit,
    onDropCapsuleSubmit: (artist: String, title: String, message: String, zone: String) -> Unit,
    onServerConfigSubmit: (host: String, token: String) -> Unit
) {
    if (activeSheet == null) return

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = EchoTokens.SurfaceElevated,
        contentColor = EchoTokens.TextPrimary,
        scrimColor = EchoTokens.Background.copy(alpha = 0.78f),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 10.dp)
                    .width(36.dp)
                    .height(4.dp)
                    .clip(EchoShapes.Pill)
                    .background(EchoTokens.Divider)
            )
        },
        shape = EchoShapes.Large
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 10.dp)
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
        ) {
            when (activeSheet) {
                is EchoModalSheet.ResonanceDetail -> {
                    ResonanceDetailContent(
                        signal = activeSheet.signal,
                        onNeedleSync = {
                            onNeedleSync(activeSheet.signal.id, activeSheet.signal.title)
                            onDismiss()
                        },
                        onWaveImpulse = {
                            onWaveImpulse(activeSheet.signal.id)
                            onDismiss()
                        }
                    )
                }
                is EchoModalSheet.LagrangeDetail -> {
                    LagrangeDetailContent(info = activeSheet.info)
                }
                is EchoModalSheet.CapsuleDetail -> {
                    CapsuleDetailContent(capsule = activeSheet.capsule)
                }
                is EchoModalSheet.CuratorStory -> {
                    CuratorStoryContent(
                        title = activeSheet.title,
                        text = activeSheet.text,
                        persona = activeSheet.persona
                    )
                }
                is EchoModalSheet.DropCapsuleDialog -> {
                    DropCapsuleContent(onSubmit = onDropCapsuleSubmit, onCancel = onDismiss)
                }
                is EchoModalSheet.ServerSettingsDialog -> {
                    ServerSettingsContent(onSubmit = onServerConfigSubmit, onCancel = onDismiss)
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun ResonanceDetailContent(
    signal: NearbyEchoSignal,
    onNeedleSync: () -> Unit,
    onWaveImpulse: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = "АКУСТИЧЕСКИЙ РЕЗОНАНС", style = EchoTypography.Label, color = EchoTokens.SignalCyan)
        ResonanceBadge(score = signal.compatibility)
    }

    Spacer(modifier = Modifier.height(10.dp))

    Text(text = signal.title, style = EchoTypography.Display.copy(fontSize = 22.sp))
    Text(text = "${signal.artist} • ${signal.sourceApp}", style = EchoTypography.Body, color = EchoTokens.TextSecondary)

    Spacer(modifier = Modifier.height(12.dp))

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TechnicalTag(label = "BPM", value = "${signal.bpm}")
        TechnicalTag(label = "КЛЮЧ", value = signal.camelotKey, accentColor = EchoTokens.VinylAmber)
        TechnicalTag(label = "ДИСТ", value = signal.distanceLabel, accentColor = EchoTokens.TextPrimary)
    }

    Spacer(modifier = Modifier.height(16.dp))

    // Поэтическая персона и наблюдение
    EchoCard(borderColor = EchoTokens.Divider, backgroundColor = EchoTokens.Surface) {
        Text(text = signal.poeticName.uppercase(), style = EchoTypography.Label, color = EchoTokens.VinylAmber)
        Spacer(modifier = Modifier.height(4.dp))
        Text(text = signal.resonanceStory, style = EchoTypography.Body, color = EchoTokens.TextPrimary)
        if (signal.aiObservation.isNotBlank()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = "«${signal.aiObservation}»", style = EchoTypography.Secondary)
        }
    }

    Spacer(modifier = Modifier.height(20.dp))

    // Тактильные действия
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        TactileButton(
            text = "СИНХРОН ИГЛЫ",
            onClick = onNeedleSync,
            accentColor = EchoTokens.SignalCyan,
            isPrimary = true,
            modifier = Modifier.weight(1f)
        )
        TactileButton(
            text = "КАМЕНЬ В ВОДУ",
            onClick = onWaveImpulse,
            accentColor = EchoTokens.VinylAmber,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun LagrangeDetailContent(info: LagrangePointInfo) {
    Text(text = "ТОЧКА ЛАГРАНЖА / АКУСТИЧЕСКОЕ ЗАТМЕНИЕ", style = EchoTypography.Label, color = EchoTokens.Gold)
    Spacer(modifier = Modifier.height(8.dp))
    Text(text = info.secretTrack, style = EchoTypography.TrackTitle)
    Spacer(modifier = Modifier.height(8.dp))
    Text(text = info.secretMessage, style = EchoTypography.Body)
    Spacer(modifier = Modifier.height(12.dp))
    Text(text = "Участников резонанса: ${info.participants.size}", style = EchoTypography.Secondary)
}

@Composable
private fun CapsuleDetailContent(capsule: EchoCapsule) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = if (capsule.isVenue) "ТАЙНИК ЗАВЕДЕНИЯ" else "МУЗЫКАЛЬНАЯ КАПСУЛА",
            style = EchoTypography.Label,
            color = if (capsule.isVenue) EchoTokens.Gold else EchoTokens.SignalCyan
        )
        ResonanceBadge(score = capsule.compatibility)
    }

    Spacer(modifier = Modifier.height(10.dp))
    Text(text = capsule.title, style = EchoTypography.Display.copy(fontSize = 22.sp))
    Text(text = capsule.artist, style = EchoTypography.Body)

    Spacer(modifier = Modifier.height(14.dp))

    EchoCard {
        Text(text = "СООБЩЕНИЕ ИЗ ТАЙНИКА", style = EchoTypography.Label, color = EchoTokens.TextSecondary)
        Spacer(modifier = Modifier.height(4.dp))
        Text(text = "«${capsule.secretMessage}»", style = EchoTypography.Body, color = EchoTokens.TextPrimary)

        if (capsule.isVenue && capsule.secretPerk.isNotBlank()) {
            Spacer(modifier = Modifier.height(10.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(EchoShapes.Small)
                    .background(EchoTokens.Gold.copy(alpha = 0.12f))
                    .border(0.8.dp, EchoTokens.Gold, EchoShapes.Small)
                    .padding(10.dp)
            ) {
                Column {
                    Text(text = "ПРИВИЛЕГИЯ ЗАВЕДЕНИЯ (PROOF-OF-MUSIC)", style = EchoTypography.Label, color = EchoTokens.Gold)
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(text = capsule.secretPerk, style = EchoTypography.Body, color = EchoTokens.TextPrimary)
                }
            }
        }
    }
}

@Composable
private fun CuratorStoryContent(title: String, text: String, persona: String) {
    Text(text = "ИСТОРИЯ ИИ-КУРАТОРА", style = EchoTypography.Label, color = EchoTokens.VinylAmber)
    Spacer(modifier = Modifier.height(8.dp))
    Text(text = title, style = EchoTypography.Display.copy(fontSize = 20.sp))
    Spacer(modifier = Modifier.height(4.dp))
    Text(text = persona, style = EchoTypography.Secondary)
    Spacer(modifier = Modifier.height(14.dp))
    EchoCard {
        Text(text = text, style = EchoTypography.Body, color = EchoTokens.TextPrimary)
    }
}

@Composable
private fun DropCapsuleContent(
    onSubmit: (artist: String, title: String, message: String, zone: String) -> Unit,
    onCancel: () -> Unit
) {
    var artist by remember { mutableStateOf("") }
    var title by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    var zone by remember { mutableStateOf("Вечерняя улица") }

    Text(text = "ОСТАВИТЬ МУЗЫКАЛЬНЫЙ ТАЙНИК", style = EchoTypography.Label, color = EchoTokens.SignalCyan)
    Spacer(modifier = Modifier.height(12.dp))

    EchoTextField(value = artist, onValueChange = { artist = it }, label = "ИСПОЛНИТЕЛЬ")
    Spacer(modifier = Modifier.height(8.dp))
    EchoTextField(value = title, onValueChange = { title = it }, label = "НАЗВАНИЕ ТРЕКА")
    Spacer(modifier = Modifier.height(8.dp))
    EchoTextField(value = message, onValueChange = { message = it }, label = "ТАЙНОЕ ПОСЛАНИЕ", singleLine = false)
    Spacer(modifier = Modifier.height(8.dp))
    EchoTextField(value = zone, onValueChange = { zone = it }, label = "ЗОНА / КВАРТАЛ")

    Spacer(modifier = Modifier.height(18.dp))

    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        TactileButton(
            text = "ОСТАВИТЬ В ЭФИРЕ",
            onClick = {
                if (title.isNotBlank()) {
                    onSubmit(artist.ifBlank { "Неизвестный автор" }, title, message, zone)
                }
            },
            isPrimary = true,
            modifier = Modifier.weight(1f)
        )
        TactileButton(
            text = "ОТМЕНА",
            onClick = onCancel,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun ServerSettingsContent(
    onSubmit: (host: String, token: String) -> Unit,
    onCancel: () -> Unit
) {
    var host by remember { mutableStateOf("http://192.168.0.214:8080") }
    var token by remember { mutableStateOf("1c13ee8697755fdc4518013ea2fd6996") }

    Text(text = "КОНФИГУРАЦИЯ СЕРВЕРА ЭХО", style = EchoTypography.Label, color = EchoTokens.SignalCyan)
    Spacer(modifier = Modifier.height(12.dp))

    EchoTextField(value = host, onValueChange = { host = it }, label = "IP-АДРЕС СЕРВЕРА (HTTP)")
    Spacer(modifier = Modifier.height(8.dp))
    EchoTextField(value = token, onValueChange = { token = it }, label = "ТОКЕН ДОСТУПА")

    Spacer(modifier = Modifier.height(18.dp))

    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        TactileButton(
            text = "СОХРАНИТЬ",
            onClick = { onSubmit(host, token) },
            isPrimary = true,
            modifier = Modifier.weight(1f)
        )
        TactileButton(
            text = "ЗАКРЫТЬ",
            onClick = onCancel,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun EchoTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    singleLine: Boolean = true
) {
    Column {
        Text(text = label, style = EchoTypography.Label, color = EchoTokens.TextSecondary)
        Spacer(modifier = Modifier.height(3.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(EchoShapes.Small)
                .background(EchoTokens.Surface)
                .border(0.8.dp, EchoTokens.Divider, EchoShapes.Small)
                .padding(horizontal = 10.dp, vertical = 8.dp)
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = singleLine,
                textStyle = EchoTypography.Body.copy(color = EchoTokens.TextPrimary),
                cursorBrush = SolidColor(EchoTokens.SignalCyan),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
