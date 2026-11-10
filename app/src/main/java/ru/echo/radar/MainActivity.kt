package ru.echo.radar

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        EchoStateRepository.init(this)
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    background = Color(0xFF04060A),
                    surface = Color(0xFF0B101B),
                    primary = Color(0xFF818CF8),
                    secondary = Color(0xFF38BDF8)
                )
            ) {
                EchoRadarAppScreen()
            }
        }
    }
}

@Composable
fun EchoRadarAppScreen() {
    val context = LocalContext.current
    val currentTrack by EchoStateRepository.currentTrack.collectAsState()
    val presenceActive by EchoStateRepository.presenceActive.collectAsState()
    val connectionStatus by EchoStateRepository.connectionStatus.collectAsState()
    val nearbySignalsFlow by EchoStateRepository.nearbySignals.collectAsState()

    var selectedSignal by remember { mutableStateOf<NearbyEchoSignal?>(null) }
    var sentSymbolMessage by remember { mutableStateOf("") }
    var showManualDialog by remember { mutableStateOf(false) }
    var showServerDialog by remember { mutableStateOf(false) }
    var serverHostInput by remember { mutableStateOf(EchoStateRepository.serverHost.value) }
    var serverTokenInput by remember { mutableStateOf(EchoStateRepository.echoToken.value) }

    // Анимация волны и кругового сканирования радара
    val infiniteTransition = rememberInfiniteTransition(label = "echo_pulse")
    val wavePhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 6.28318f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 6000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wave"
    )

    val radarSweepAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 4000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "radar_sweep"
    )

    val beaconPulse by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.35f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "beacon_pulse"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF04060A))
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // ── ВЕРХНЯЯ ПАНЕЛЬ С ЛОГОТИПОМ И РЕЖИМОМ ПРИСУТСТВИЯ ──
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Э Х О",
                    color = Color(0xFFF8FAFC),
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 8.sp
                )
                Text(
                    text = "Сонар музыкального присутствия",
                    color = Color(0xFF94A3B8),
                    fontSize = 11.sp
                )
            }

            Button(
                onClick = { EchoStateRepository.togglePresence() },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (presenceActive) Color(0xFF072740) else Color(0xFF1E293B)
                ),
                shape = RoundedCornerShape(20.dp),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    if (presenceActive) Color(0xFF0284C7) else Color(0xFF334155)
                ),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
            ) {
                Text(
                    text = if (presenceActive) "● В эфире" else "○ Невидимка",
                    color = if (presenceActive) Color(0xFF38BDF8) else Color(0xFF94A3B8),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        // ── СТАТУС ПОДКЛЮЧЕНИЯ И СЕРВЕР ──
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF0B101D))
                .padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                val isOnline = connectionStatus.startsWith("●")
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(if (isOnline) Color(0xFF34D399) else Color(0xFFEF4444))
                )
                Text(
                    text = connectionStatus,
                    color = if (isOnline) Color(0xFFCBD5E1) else Color(0xFFF87171),
                    fontSize = 11.sp
                )
            }
            TextButton(
                onClick = { showServerDialog = true },
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
            ) {
                Text("⚙ Настроить сервер", color = Color(0xFF818CF8), fontSize = 11.sp)
            }
        }

        // ── КАРТОЧКА ТЕКУЩЕГО ТРЕКА В НАУШНИКАХ ──
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color(0xFF0F172A), Color(0xFF090E1A))
                    )
                )
                .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(22.dp))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                GenerativeAvatarCanvas(
                    phase = wavePhase,
                    seed = currentTrack.artist.length + 5,
                    color = Color(0xFF38BDF8),
                    modifier = Modifier.size(52.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        EqualizerBars(isActive = presenceActive && currentTrack.artist != "Ожидание музыки...")
                        Text(
                            text = "В НАУШНИКАХ · ${currentTrack.sourceApp.uppercase()}",
                            color = Color(0xFF818CF8),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                    }
                    Text(
                        text = "${currentTrack.artist} — ${currentTrack.title}",
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "ИИ-образ: ${currentTrack.poeticPersona}",
                        color = Color(0xFF94A3B8),
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFCBD5E1)),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF334155)),
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    Text("🎧 Доступ к плеерам", fontSize = 11.sp)
                }

                OutlinedButton(
                    onClick = { showManualDialog = true },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF818CF8)),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF4338CA)),
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    Text("✍ Ввести трек", fontSize = 11.sp)
                }
            }
        }

        // ── ИНТЕРАКТИВНЫЙ РАДАР СОНАРА ──
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(26.dp))
                .background(Color(0xFF05070D))
                .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(26.dp))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "СОНАР ЭФИРА",
                    color = Color(0xFF64748B),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp
                )
                Text(
                    text = "Радиус: ~200 м",
                    color = Color(0xFF475569),
                    fontSize = 10.sp
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(280.dp),
                contentAlignment = Alignment.Center
            ) {
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(nearbySignalsFlow) {
                            detectTapGestures { tapOffset ->
                                val center = Offset(size.width / 2f, size.height / 2f)
                                val maxR = size.minDimension * 0.44f
                                val hit = nearbySignalsFlow.minByOrNull { sig ->
                                    val rad = Math.toRadians(sig.angleDeg.toDouble())
                                    val r = maxR * sig.ringFactor.coerceIn(0.2f, 0.95f)
                                    val bx = center.x + (r * cos(rad)).toFloat()
                                    val by = center.y + (r * sin(rad)).toFloat()
                                    val dx = tapOffset.x - bx
                                    val dy = tapOffset.y - by
                                    dx * dx + dy * dy
                                }
                                if (hit != null) {
                                    val rad = Math.toRadians(hit.angleDeg.toDouble())
                                    val r = maxR * hit.ringFactor.coerceIn(0.2f, 0.95f)
                                    val bx = center.x + (r * cos(rad)).toFloat()
                                    val by = center.y + (r * sin(rad)).toFloat()
                                    val dist = sqrt((tapOffset.x - bx) * (tapOffset.x - bx) + (tapOffset.y - by) * (tapOffset.y - by))
                                    if (dist < 32.dp.toPx()) {
                                        selectedSignal = hit
                                        sentSymbolMessage = ""
                                    }
                                }
                            }
                        }
                ) {
                    val center = Offset(size.width / 2f, size.height / 2f)
                    val maxR = size.minDimension * 0.44f

                    // Фоновая сетка концентрических колец сонара
                    listOf(0.35f, 0.65f, 0.95f).forEach { factor ->
                        drawCircle(
                            color = Color(0xFF141E33),
                            radius = maxR * factor,
                            center = center,
                            style = Stroke(width = 1.2f)
                        )
                    }

                    // Перекрестие осей
                    drawLine(
                        color = Color(0xFF0F1A2E),
                        start = Offset(center.x, center.y - maxR),
                        end = Offset(center.x, center.y + maxR),
                        strokeWidth = 1f
                    )
                    drawLine(
                        color = Color(0xFF0F1A2E),
                        start = Offset(center.x - maxR, center.y),
                        end = Offset(center.x + maxR, center.y),
                        strokeWidth = 1f
                    )

                    // Вращающийся сканирующий луч сонара
                    if (presenceActive) {
                        val sweepRad = Math.toRadians(radarSweepAngle.toDouble())
                        val endX = center.x + (maxR * cos(sweepRad)).toFloat()
                        val endY = center.y + (maxR * sin(sweepRad)).toFloat()

                        drawLine(
                            brush = Brush.linearGradient(
                                colors = listOf(Color(0xFF38BDF8).copy(alpha = 0.8f), Color.Transparent),
                                start = center,
                                end = Offset(endX, endY)
                            ),
                            start = center,
                            end = Offset(endX, endY),
                            strokeWidth = 2.5f,
                            cap = StrokeCap.Round
                        )
                    }

                    // Отрисовка обнаруженных сигналов как светящихся маяков на радаре
                    nearbySignalsFlow.forEach { sig ->
                        val rad = Math.toRadians(sig.angleDeg.toDouble())
                        val r = maxR * sig.ringFactor.coerceIn(0.2f, 0.95f)
                        val bx = center.x + (r * cos(rad)).toFloat()
                        val by = center.y + (r * sin(rad)).toFloat()
                        val bOffset = Offset(bx, by)

                        val sigColor = Color(sig.colorHex.toInt()).copy(alpha = if (sig.isGhost) 0.5f else 1.0f)
                        val isSelected = sig.id == selectedSignal?.id

                        drawCircle(
                            color = sigColor.copy(alpha = if (isSelected) 0.35f else 0.15f),
                            radius = if (isSelected) 14.dp.toPx() * beaconPulse else 10.dp.toPx(),
                            center = bOffset
                        )

                        drawCircle(
                            color = sigColor,
                            radius = if (isSelected) 6.dp.toPx() else 4.5.dp.toPx(),
                            center = bOffset
                        )

                        if (isSelected) {
                            drawCircle(
                                color = Color.White,
                                radius = 16.dp.toPx(),
                                center = bOffset,
                                style = Stroke(width = 1.5f)
                            )
                        }
                    }

                    drawCircle(
                        color = if (presenceActive) Color(0xFF38BDF8) else Color(0xFF475569),
                        radius = 6.dp.toPx(),
                        center = center
                    )
                    if (presenceActive) {
                        drawCircle(
                            color = Color(0xFF38BDF8).copy(alpha = 0.25f),
                            radius = 12.dp.toPx() * beaconPulse,
                            center = center
                        )
                    }
                }

                if (nearbySignalsFlow.isEmpty()) {
                    Text(
                        text = "Сонар слушает эфир в радиусе тишины...\nОставайтесь в приложении, чтобы поймать волны.",
                        color = Color(0xFF475569),
                        fontSize = 11.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        // ── КАРТОЧКА ВЫБРАННОГО СЛУШАТЕЛЯ ──
        selectedSignal?.let { sig ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color(0xFF0B111E))
                    .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(24.dp))
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    GenerativeAvatarCanvas(
                        phase = wavePhase,
                        seed = sig.artist.length + 7,
                        color = Color(sig.colorHex.toInt()),
                        modifier = Modifier.size(56.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "МУЗЫКАЛЬНЫЙ ОБРАЗ (ИИ-КУРАТОР)",
                            color = Color(0xFF64748B),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                        Text(
                            text = sig.poeticName,
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "✦ ${sig.compatibility}% совпадение",
                                color = Color(0xFF38BDF8),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(text = "·", color = Color(0xFF475569))
                            Text(
                                text = sig.sourceApp,
                                color = Color(0xFF94A3B8),
                                fontSize = 11.sp
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF131D31))
                        .padding(10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "♫ ${sig.artist} — ${sig.title}",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (sig.isGhost) {
                            Text(
                                text = "Призрачный след (звучал ранее)",
                                color = Color(0xFFA78BFA),
                                fontSize = 10.sp
                            )
                        }
                    }
                    Text(
                        text = sig.distanceLabel,
                        color = Color(0xFF94A3B8),
                        fontSize = 11.sp
                    )
                }

                if (sig.resonanceDays != null) {
                    Text(
                        text = "✦ ${sig.resonanceDays}: совпала ваша музыкальная фаза жизни",
                        color = Color(0xFFC7D2FE),
                        fontSize = 11.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFF1E1B4B))
                            .padding(8.dp)
                    )
                }

                Text(
                    text = "«${sig.aiObservation}»",
                    color = Color(0xFFCBD5E1),
                    fontSize = 12.sp,
                    fontStyle = FontStyle.Italic
                )

                // ── ТИХИЕ ЗНАКИ (РЕАКЦИИ) ──
                Text(
                    text = "Отправить музыкальное настроение (без лайков и оценок):",
                    color = Color(0xFF94A3B8),
                    fontSize = 11.sp
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    listOf("🦇", "🌑", "🌊", "⚔", "🌲", "🎼", "☁").forEach { symbol ->
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF162138))
                                .border(1.dp, Color(0xFF283548), CircleShape)
                                .clickable {
                                    sentSymbolMessage = "Отправка знака $symbol..."
                                    EchoStateRepository.sendReaction(sig.id, symbol) { success ->
                                        sentSymbolMessage = if (success) {
                                            "Тихий знак $symbol доставлен для «${sig.poeticName}»"
                                        } else {
                                            "Знак $symbol не удалось отправить (проверьте сеть)"
                                        }
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(text = symbol, fontSize = 18.sp)
                        }
                    }
                }

                AnimatedVisibility(
                    visible = sentSymbolMessage.isNotEmpty(),
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    Text(
                        text = sentSymbolMessage,
                        color = Color(0xFF818CF8),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        // ── СПИСОК ОБНАРУЖЕННЫХ ВОЛН (КАРТОЧКИ) ──
        if (nearbySignalsFlow.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "ОБНАРУЖЕННЫЕ ВОЛНЫ (${nearbySignalsFlow.size})",
                    color = Color(0xFF64748B),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp
                )

                nearbySignalsFlow.forEach { sig ->
                    val isSelected = sig.id == selectedSignal?.id
                    val cardBorder by animateColorAsState(
                        targetValue = if (isSelected) Color(0xFF818CF8) else Color(0xFF1E293B),
                        label = "card_border"
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (isSelected) Color(0xFF151C2E) else Color(0xFF0B101D))
                            .border(1.dp, cardBorder, RoundedCornerShape(16.dp))
                            .clickable {
                                selectedSignal = sig
                                sentSymbolMessage = ""
                            }
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Цветовая полоса настроения
                        Box(
                            modifier = Modifier
                                .width(4.dp)
                                .height(38.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(Color(sig.colorHex.toInt()))
                        )

                        Spacer(modifier = Modifier.width(10.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "♫ ${sig.artist} — ${sig.title}",
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${sig.poeticName} · ${sig.sourceApp}",
                                color = Color(0xFF94A3B8),
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = sig.distanceLabel,
                                color = Color(0xFF38BDF8),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "${sig.compatibility}% вкус",
                                color = Color(0xFF64748B),
                                fontSize = 10.sp
                            )
                        }
                    }
                }
            }
        }
    }

    // ── ДИАЛОГ РУЧНОГО ВВОДА ТРЕКА ──
    if (showManualDialog) {
        ManualTrackDialog(
            onDismiss = { showManualDialog = false },
            onSubmit = { artist, title ->
                EchoStateRepository.updateDetectedMusic(artist, title, "Ручной ввод")
                showManualDialog = false
            }
        )
    }

    // ── ДИАЛОГ НАСТРОЙКИ СЕРВЕРА И ТОКЕНА ──
    if (showServerDialog) {
        AlertDialog(
            onDismissRequest = { showServerDialog = false },
            title = {
                Text(
                    text = "Подключение к серверу ЭХО",
                    color = Color.White,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Введите адрес сервера из echo_tunnel.py (например: abc.trycloudflare.com) или локальный IP (192.168.x.x:8080):",
                        color = Color(0xFF94A3B8),
                        fontSize = 12.sp
                    )
                    OutlinedTextField(
                        value = serverHostInput,
                        onValueChange = { serverHostInput = it },
                        placeholder = { Text("abc123.trycloudflare.com", fontSize = 12.sp) },
                        label = { Text("Адрес сервера", fontSize = 11.sp) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color(0xFFCBD5E1),
                            focusedBorderColor = Color(0xFF38BDF8),
                            unfocusedBorderColor = Color(0xFF334155)
                        )
                    )
                    OutlinedTextField(
                        value = serverTokenInput,
                        onValueChange = { serverTokenInput = it },
                        placeholder = { Text("Токен из консоли сервера", fontSize = 12.sp) },
                        label = { Text("🔑 Токен доступа (X-Echo-Token)", fontSize = 11.sp) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color(0xFFCBD5E1),
                            focusedBorderColor = Color(0xFF818CF8),
                            unfocusedBorderColor = Color(0xFF334155)
                        )
                    )
                    Text(
                        text = "💡 Токен генерируется автоматически при запуске echo_server.py и выводится в консоли.",
                        color = Color(0xFF64748B),
                        fontSize = 11.sp
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        EchoStateRepository.setServerHost(serverHostInput, serverTokenInput)
                        showServerDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7))
                ) {
                    Text("Подключить", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showServerDialog = false }) {
                    Text("Отмена", color = Color(0xFF94A3B8))
                }
            },
            containerColor = Color(0xFF0B111E),
            shape = RoundedCornerShape(20.dp)
        )
    }
}

// ── АНИМИРОВАННЫЙ ЭКВАЛАЙЗЕР (3 полоски) ──
@Composable
fun EqualizerBars(isActive: Boolean) {
    val infiniteTransition = rememberInfiniteTransition(label = "eq")
    val h1 by infiniteTransition.animateFloat(
        initialValue = 4f, targetValue = 12f,
        animationSpec = infiniteRepeatable(tween(450, easing = LinearEasing), RepeatMode.Reverse),
        label = "eq1"
    )
    val h2 by infiniteTransition.animateFloat(
        initialValue = 11f, targetValue = 5f,
        animationSpec = infiniteRepeatable(tween(350, easing = LinearEasing), RepeatMode.Reverse),
        label = "eq2"
    )
    val h3 by infiniteTransition.animateFloat(
        initialValue = 6f, targetValue = 13f,
        animationSpec = infiniteRepeatable(tween(550, easing = LinearEasing), RepeatMode.Reverse),
        label = "eq3"
    )

    Row(
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.Bottom,
        modifier = Modifier.height(13.dp)
    ) {
        val color = if (isActive) Color(0xFF38BDF8) else Color(0xFF475569)
        Box(modifier = Modifier.width(2.5.dp).height(if (isActive) h1.dp else 4.dp).clip(RoundedCornerShape(1.dp)).background(color))
        Box(modifier = Modifier.width(2.5.dp).height(if (isActive) h2.dp else 6.dp).clip(RoundedCornerShape(1.dp)).background(color))
        Box(modifier = Modifier.width(2.5.dp).height(if (isActive) h3.dp else 4.dp).clip(RoundedCornerShape(1.dp)).background(color))
    }
}

// ── ГЕНЕРАТИВНЫЙ АВАТАР ВОЛНЫ ──
@Composable
fun GenerativeAvatarCanvas(
    phase: Float,
    seed: Int,
    color: Color = Color(0xFF818CF8),
    modifier: Modifier = Modifier
) {
    Canvas(
        modifier = modifier
            .clip(CircleShape)
            .background(Color(0xFF030712))
            .border(1.dp, Color(0xFF1E293B), CircleShape)
    ) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val baseR = (size.minDimension * 0.28f).coerceAtLeast(2f)
        val petals = 4 + (seed % 5)

        val path = Path()
        val steps = 54
        for (i in 0..steps) {
            val theta = (i.toFloat() / steps) * 6.28318f
            val wave = sin(theta * petals + phase) * (baseR * 0.25f)
            val r = (baseR + wave).coerceAtLeast(1f)
            val x = cx + cos(theta) * r
            val y = cy + sin(theta) * r
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()

        drawPath(
            path = path,
            brush = Brush.radialGradient(
                colors = listOf(color, color.copy(alpha = 0.4f), Color.Transparent)
            )
        )
        drawPath(
            path = path,
            color = color.copy(alpha = 0.9f),
            style = Stroke(width = 1.8f)
        )
    }
}

@Composable
fun ManualTrackDialog(onDismiss: () -> Unit, onSubmit: (String, String) -> Unit) {
    var artist by remember { mutableStateOf("Bohren & der Club of Gore") }
    var title by remember { mutableStateOf("Prowler") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF0F172A),
        shape = RoundedCornerShape(20.dp),
        title = {
            Text("Поделиться треком вручную", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = artist,
                    onValueChange = { artist = it },
                    label = { Text("Исполнитель") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color(0xFFCBD5E1)
                    )
                )
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Название трека") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color(0xFFCBD5E1)
                    )
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSubmit(artist, title) },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4F46E5))
            ) {
                Text("Транслировать в ЭХО", color = Color.White)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Отмена", color = Color(0xFF94A3B8))
            }
        }
    )
}
