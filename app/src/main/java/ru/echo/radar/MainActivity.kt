package ru.echo.radar

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        EchoStateRepository.init(this)
        setContent {
            EchoRadarApp()
        }
    }
}

@Composable
fun EchoRadarApp() {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current

    val currentTrack by EchoStateRepository.currentTrack.collectAsState()
    val presenceActive by EchoStateRepository.presenceActive.collectAsState()
    val nearbySignalsFlow by EchoStateRepository.nearbySignals.collectAsState()
    val capsulesFlow by EchoStateRepository.capsules.collectAsState()
    val incomingReaction by EchoStateRepository.incomingReaction.collectAsState()
    val connectionStatus by EchoStateRepository.connectionStatus.collectAsState()
    val serverHost by EchoStateRepository.serverHost.collectAsState()
    val echoToken by EchoStateRepository.echoToken.collectAsState()

    var selectedSignal by remember { mutableStateOf<NearbyEchoSignal?>(null) }
    var selectedCapsule by remember { mutableStateOf<EchoCapsule?>(null) }
    var sentSymbolMessage by remember { mutableStateOf("") }
    var showServerDialog by remember { mutableStateOf(false) }
    var showManualDialog by remember { mutableStateOf(false) }
    var showDropCapsuleDialog by remember { mutableStateOf(false) }
    var activeTab by remember { mutableStateOf(0) } // 0 = Волны, 1 = Тайники

    var serverHostInput by remember(serverHost) { mutableStateOf(serverHost) }
    var serverTokenInput by remember(echoToken) { mutableStateOf(echoToken) }

    val infiniteTransition = rememberInfiniteTransition(label = "radar_anim")
    val radarSweepAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 4200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "sweep_angle"
    )

    val beaconPulse by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.6f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "beacon_pulse"
    )

    val wavePhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 6.28318f,
        animationSpec = infiniteRepeatable(
            animation = tween(3800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wave_phase"
    )

    // Автоматическая тактильная отдача при получении входящего отклика
    LaunchedEffect(incomingReaction?.id) {
        val reaction = incomingReaction
        if (reaction != null) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            delay(5500L)
            EchoStateRepository.dismissIncomingReaction()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF030712))
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // ── ВСПЛЫВАЮЩИЙ БАННЕР ВХОДЯЩЕГО ОТКЛИКА ──
        AnimatedVisibility(
            visible = incomingReaction != null,
            enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut()
        ) {
            incomingReaction?.let { rx ->
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .clickable { EchoStateRepository.dismissIncomingReaction() },
                    color = Color(0xFF1E1B4B),
                    border = BorderStroke(1.5.dp, Color(0xFF818CF8)),
                    shadowElevation = 8.dp
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF312E81)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(text = rx.symbol, fontSize = 24.sp)
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "✨ ВАМ ПРИШЕЛ ОТКЛИК!",
                                color = Color(0xFF38BDF8),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                            Text(
                                text = rx.message,
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "От: ${rx.senderId}",
                                color = Color(0xFF94A3B8),
                                fontSize = 10.sp
                            )
                        }

                        TextButton(
                            onClick = { EchoStateRepository.dismissIncomingReaction() },
                            contentPadding = PaddingValues(horizontal = 6.dp)
                        ) {
                            Text("✕", color = Color(0xFF94A3B8), fontSize = 14.sp)
                        }
                    }
                }
            }
        }

        // ── ШАПКА ПРИЛОЖЕНИЯ ──
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Э  Х  О",
                    color = Color.White,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 4.sp
                )
                Text(
                    text = "Сонар музыкального присутствия",
                    color = Color(0xFF64748B),
                    fontSize = 11.sp
                )
            }

            // Переключатель видимости в эфире
            Surface(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .clickable { EchoStateRepository.togglePresence() },
                color = if (presenceActive) Color(0xFF0C243B) else Color(0xFF1E293B),
                border = BorderStroke(1.dp, if (presenceActive) Color(0xFF0284C7) else Color(0xFF475569))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(if (presenceActive) Color(0xFF38BDF8) else Color(0xFF64748B))
                    )
                    Text(
                        text = if (presenceActive) "В эфире" else "Тишина",
                        color = if (presenceActive) Color(0xFF38BDF8) else Color(0xFF94A3B8),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        // ── СТАТУС ПОДКЛЮЧЕНИЯ И КНОПКА НАСТРОЙКИ СЕРВЕРА ──
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val statusColor = when {
                connectionStatus.startsWith("●") -> Color(0xFF22C55E)
                connectionStatus.startsWith("✗") -> Color(0xFFEF4444)
                else -> Color(0xFF94A3B8)
            }
            Text(
                text = connectionStatus,
                color = statusColor,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium
            )

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

            // Кнопки управления источником и тайником
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFCBD5E1)),
                    border = BorderStroke(1.dp, Color(0xFF334155)),
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    Text("🎧 Плееры", fontSize = 11.sp)
                }

                OutlinedButton(
                    onClick = { showManualDialog = true },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF818CF8)),
                    border = BorderStroke(1.dp, Color(0xFF4338CA)),
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    Text("✍ Трек", fontSize = 11.sp)
                }

                Button(
                    onClick = { showDropCapsuleDialog = true },
                    modifier = Modifier.weight(1.2f),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD97706)),
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    Text("⏳ Тайник", fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.Bold)
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
                    text = "● ${nearbySignalsFlow.size} волн · ⏳ ${capsulesFlow.size} тайников",
                    color = Color(0xFF38BDF8),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(290.dp),
                contentAlignment = Alignment.Center
            ) {
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(nearbySignalsFlow, capsulesFlow) {
                            detectTapGestures { tapOffset ->
                                val center = Offset(size.width / 2f, size.height / 2f)
                                val minDim = minOf(size.width, size.height).toFloat()
                                val maxR = minDim * 0.44f

                                // Поиск ближайшего слушателя
                                var closestSignal: NearbyEchoSignal? = null
                                var minSignalDist = Float.MAX_VALUE
                                for (sig in nearbySignalsFlow) {
                                    val rad = Math.toRadians(sig.angleDeg.toDouble())
                                    val r = maxR * sig.ringFactor.coerceIn(0.2f, 0.95f)
                                    val bx = center.x + (r.toDouble() * cos(rad)).toFloat()
                                    val by = center.y + (r.toDouble() * sin(rad)).toFloat()
                                    val dx = tapOffset.x - bx
                                    val dy = tapOffset.y - by
                                    val dist = sqrt(dx * dx + dy * dy)
                                    if (dist < minSignalDist) {
                                        minSignalDist = dist
                                        closestSignal = sig
                                    }
                                }

                                // Поиск ближайшего тайника (капсулы)
                                var closestCap: EchoCapsule? = null
                                var minCapDist = Float.MAX_VALUE
                                for (cap in capsulesFlow) {
                                    val rad = Math.toRadians(cap.angleDeg.toDouble())
                                    val r = maxR * cap.ringFactor.coerceIn(0.2f, 0.95f)
                                    val bx = center.x + (r.toDouble() * cos(rad)).toFloat()
                                    val by = center.y + (r.toDouble() * sin(rad)).toFloat()
                                    val dx = tapOffset.x - bx
                                    val dy = tapOffset.y - by
                                    val dist = sqrt(dx * dx + dy * dy)
                                    if (dist < minCapDist) {
                                        minCapDist = dist
                                        closestCap = cap
                                    }
                                }

                                val touchRadiusPx = 36.dp.toPx()
                                if (minCapDist < minSignalDist && minCapDist < touchRadiusPx) {
                                    selectedCapsule = closestCap
                                    selectedSignal = null
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                } else if (minSignalDist < touchRadiusPx) {
                                    selectedSignal = closestSignal
                                    selectedCapsule = null
                                    sentSymbolMessage = ""
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
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

                    // 1. Отрисовка музыкальных тайников (капсул) — золотистые ромбы
                    capsulesFlow.forEach { cap ->
                        val rad = Math.toRadians(cap.angleDeg.toDouble())
                        val r = maxR * cap.ringFactor.coerceIn(0.2f, 0.95f)
                        val cx = center.x + (r.toDouble() * cos(rad)).toFloat()
                        val cy = center.y + (r.toDouble() * sin(rad)).toFloat()
                        val cOffset = Offset(cx, cy)
                        val isSelected = cap.id == selectedCapsule?.id

                        // Пульсирующий ореол
                        drawCircle(
                            color = Color(0xFFF59E0B).copy(alpha = if (isSelected) 0.35f else 0.15f),
                            radius = if (isSelected) 15.dp.toPx() * beaconPulse else 11.dp.toPx(),
                            center = cOffset
                        )

                        // Центральный маркер тайника
                        drawCircle(
                            color = Color(0xFFFBBF24),
                            radius = if (isSelected) 7.dp.toPx() else 5.dp.toPx(),
                            center = cOffset
                        )

                        if (isSelected) {
                            drawCircle(
                                color = Color.White,
                                radius = 17.dp.toPx(),
                                center = cOffset,
                                style = Stroke(width = 1.8f)
                            )
                        }
                    }

                    // 2. Отрисовка обнаруженных сигналов слушателей — неоновые маяки
                    nearbySignalsFlow.forEach { sig ->
                        val rad = Math.toRadians(sig.angleDeg.toDouble())
                        val r = maxR * sig.ringFactor.coerceIn(0.2f, 0.95f)
                        val bx = center.x + (r.toDouble() * cos(rad)).toFloat()
                        val by = center.y + (r.toDouble() * sin(rad)).toFloat()
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
                            radius = if (isSelected) 6.5.dp.toPx() else 4.5.dp.toPx(),
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

                    // Центр радара (позиция слушателя)
                    drawCircle(
                        color = if (presenceActive) Color(0xFF38BDF8) else Color(0xFF475569),
                        radius = 6.dp.toPx(),
                        center = center
                    )
                }
            }
        }

        // ── КАРТОЧКА ВЫБРАННОГО ТАЙНИКА (КАПСУЛЫ) ──
        selectedCapsule?.let { cap ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color(0xFF1C1304))
                    .border(1.5.dp, Color(0xFFF59E0B), RoundedCornerShape(24.dp))
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "⏳ МУЗЫКАЛЬНЫЙ ТАЙНИК",
                        color = Color(0xFFFBBF24),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                    IconButton(
                        onClick = { selectedCapsule = null },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Text("✕", color = Color(0xFF94A3B8), fontSize = 13.sp)
                    }
                }

                Text(
                    text = "♫ ${cap.artist} — ${cap.title}",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = "Послание автора: «${cap.secretMessage}»",
                    color = Color(0xFFFDE68A),
                    fontSize = 13.sp,
                    fontStyle = FontStyle.Italic
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Локация: ${cap.zoneName} · ${cap.distanceLabel}",
                        color = Color(0xFF94A3B8),
                        fontSize = 11.sp
                    )
                    Text(text = "·", color = Color(0xFF475569))
                    Text(
                        text = "${cap.compatibility}% резонанс",
                        color = Color(0xFF38BDF8),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
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
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
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
                            .background(Color(0xFF1E1B4B).copy(alpha = 0.5f))
                            .padding(8.dp)
                    )
                }

                if (sig.aiObservation.isNotBlank()) {
                    Text(
                        text = "«${sig.aiObservation}»",
                        color = Color(0xFF94A3B8),
                        fontSize = 12.sp,
                        fontStyle = FontStyle.Italic
                    )
                }

                // Кнопки отправки невербальных откликов
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "ОТПРАВИТЬ СИМВОЛ РЕЗОНАНСА",
                        color = Color(0xFF64748B),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        listOf(
                            "🌊" to "Волна",
                            "✨" to "Искра",
                            "🌙" to "Шёпот",
                            "🌲" to "Лес",
                            "🔥" to "Огонь"
                        ).forEach { (symbol, name) ->
                            Surface(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        EchoStateRepository.sendReaction(sig.id, symbol) { ok ->
                                            sentSymbolMessage = if (ok) "Импульс $symbol отправлен в эфир" else "Ошибка отправки"
                                        }
                                    },
                                color = Color(0xFF131D31),
                                border = BorderStroke(1.dp, Color(0xFF1E293B))
                            ) {
                                Column(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(text = symbol, fontSize = 20.sp)
                                    Text(text = name, color = Color(0xFF94A3B8), fontSize = 9.sp)
                                }
                            }
                        }
                    }
                }

                if (sentSymbolMessage.isNotBlank()) {
                    Text(
                        text = "✓ $sentSymbolMessage",
                        color = Color(0xFF38BDF8),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        // Подсказка, если ничего не выбрано
        if (selectedSignal == null && selectedCapsule == null) {
            val nearest = nearbySignalsFlow.firstOrNull()
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = Color(0xFF0F172A).copy(alpha = 0.5f),
                border = BorderStroke(1.dp, Color(0xFF1E293B))
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(text = "♫", fontSize = 18.sp, color = Color(0xFF38BDF8))
                    Column {
                        Text(
                            text = if (nearest != null) "Рядом звучит: ${nearest.artist} — ${nearest.title}" else "В радиусе тишины сканируются вечерние маяки...",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "Коснитесь любой точки на сонаре для подробностей и отклика",
                            color = Color(0xFF64748B),
                            fontSize = 10.sp
                        )
                    }
                }
            }
        }

        // ── СПИСКИ: ПЕРЕКЛЮЧАТЕЛЬ ТАБОВ (ВОЛНЫ / ТАЙНИКИ) ──
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = activeTab == 0,
                onClick = { activeTab = 0 },
                label = { Text("● Волны (${nearbySignalsFlow.size})") },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = Color(0xFF0284C7),
                    selectedLabelColor = Color.White
                )
            )
            FilterChip(
                selected = activeTab == 1,
                onClick = { activeTab = 1 },
                label = { Text("⏳ Тайники (${capsulesFlow.size})") },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = Color(0xFFD97706),
                    selectedLabelColor = Color.White
                )
            )
        }

        if (activeTab == 0) {
            // Список слушателей
            if (nearbySignalsFlow.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                                    selectedCapsule = null
                                    sentSymbolMessage = ""
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                }
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
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
            } else {
                Text(
                    text = "Пока нет активных сигналов рядом. Включите плеер на компьютере или телефоне.",
                    color = Color(0xFF64748B),
                    fontSize = 12.sp,
                    fontStyle = FontStyle.Italic
                )
            }
        } else {
            // Список капсул / тайников
            if (capsulesFlow.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    capsulesFlow.forEach { cap ->
                        val isSelected = cap.id == selectedCapsule?.id
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(if (isSelected) Color(0xFF261906) else Color(0xFF140F04))
                                .border(1.dp, if (isSelected) Color(0xFFF59E0B) else Color(0xFF38290B), RoundedCornerShape(16.dp))
                                .clickable {
                                    selectedCapsule = cap
                                    selectedSignal = null
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                }
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = "⏳", fontSize = 20.sp)
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "♫ ${cap.artist} — ${cap.title}",
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "«${cap.secretMessage}»",
                                    color = Color(0xFFFDE68A),
                                    fontSize = 11.sp,
                                    fontStyle = FontStyle.Italic,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Text(
                                text = cap.distanceLabel,
                                color = Color(0xFFFBBF24),
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            } else {
                Text(
                    text = "В этой зоне пока нет оставленных тайников. Нажмите «⏳ Тайник», чтобы оставить свой первый след!",
                    color = Color(0xFF64748B),
                    fontSize = 12.sp,
                    fontStyle = FontStyle.Italic
                )
            }
        }
    }

    // ── ДИАЛОГ ОСТАВИТЬ МУЗЫКАЛЬНЫЙ ТАЙНИК (КАПСУЛУ) ──
    if (showDropCapsuleDialog) {
        var secretMessage by remember { mutableStateOf("") }
        var isSubmitting by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { if (!isSubmitting) showDropCapsuleDialog = false },
            containerColor = Color(0xFF1C1304),
            shape = RoundedCornerShape(22.dp),
            title = {
                Text("Оставить тайник в городе", color = Color(0xFFFBBF24), fontSize = 17.sp, fontWeight = FontWeight.Bold)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Трек: ♫ ${currentTrack.artist} — ${currentTrack.title}",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Напишите воспоминание или мысль, которая останется привязана к этой точке города:",
                        color = Color(0xFFD4D4D8),
                        fontSize = 12.sp
                    )
                    OutlinedTextField(
                        value = secretMessage,
                        onValueChange = { secretMessage = it },
                        placeholder = { Text("Например: Здесь шел теплый осенний дождь...", fontSize = 12.sp) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color(0xFFCBD5E1),
                            focusedBorderColor = Color(0xFFF59E0B),
                            unfocusedBorderColor = Color(0xFF78350F)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        isSubmitting = true
                        EchoStateRepository.dropCapsule(
                            artist = currentTrack.artist,
                            title = currentTrack.title,
                            secretMessage = secretMessage.ifBlank { "Музыкальный маяк в тишине города." }
                        ) {
                            isSubmitting = false
                            showDropCapsuleDialog = false
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD97706)),
                    enabled = !isSubmitting
                ) {
                    Text("Оставить в эфире", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDropCapsuleDialog = false }) {
                    Text("Отмена", color = Color(0xFF94A3B8))
                }
            }
        )
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
    var artist by remember { mutableStateOf("Sirotkin") }
    var title by remember { mutableStateOf("Бейся сердце") }

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
