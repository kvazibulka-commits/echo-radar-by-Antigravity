package ru.echo.radar

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
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
import kotlinx.coroutines.launch
import java.net.URLEncoder
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

class MainActivity : ComponentActivity() {
    private val hasPermissionState = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        EchoStateRepository.init(this)
        hasPermissionState.value = isNotificationListenerEnabled(this)
        setContent {
            EchoRadarApp(hasPermission = hasPermissionState.value)
        }
    }

    override fun onResume() {
        super.onResume()
        hasPermissionState.value = isNotificationListenerEnabled(this)
    }
}

fun isNotificationListenerEnabled(context: Context): Boolean {
    val packageName = context.packageName
    val flat = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
    return flat != null && flat.contains(packageName)
}

/**
 * Мгновенное открытие трека в плеере (Яндекс Музыка / системный аудиоплеер).
 * Воплощает бестпрактику Shazam: мгновенное действие в 1 тап.
 */
fun openTrackInMusicPlayer(context: Context, artist: String, title: String) {
    val query = "$artist $title".trim()
    if (query.isBlank()) return

    val encoded = URLEncoder.encode(query, "UTF-8")
    // 1. Яндекс Музыка
    val yandexUri = Uri.parse("https://music.yandex.ru/search?text=$encoded")
    val yandexIntent = Intent(Intent.ACTION_VIEW, yandexUri).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    try {
        context.startActivity(yandexIntent)
        return
    } catch (_: Exception) {}

    // 2. Системный медиа-интент
    val mediaIntent = Intent(MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH).apply {
        putExtra(MediaStore.EXTRA_MEDIA_FOCUS, "vnd.android.cursor.item/*")
        putExtra("query", query)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    try {
        context.startActivity(mediaIntent)
        return
    } catch (_: Exception) {}

    // 3. Веб-поиск
    val webUri = Uri.parse("https://www.google.com/search?q=" + URLEncoder.encode("$query слушать", "UTF-8"))
    context.startActivity(Intent(Intent.ACTION_VIEW, webUri).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) })
}

@Composable
fun EchoRadarApp(hasPermission: Boolean) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()

    val prefs = remember { context.getSharedPreferences("echo_prefs", Context.MODE_PRIVATE) }
    var hasNotificationPermission by remember(hasPermission) { mutableStateOf(hasPermission) }
    var showPermissionGuideDialog by remember { mutableStateOf(false) }
    var showManifestDialog by remember { mutableStateOf(!prefs.getBoolean("has_seen_manifest", false)) }

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
    var activeTab by remember { mutableStateOf(0) } // 0 = Радар, 1 = Тайники, 2 = Следы

    // Анимация бегущей световой волны отклика
    val impulseProgress = remember { Animatable(0f) }
    var impulseTargetOffset by remember { mutableStateOf<Offset?>(null) }

    var serverHostInput by remember(serverHost) { mutableStateOf(if (serverHost.isBlank()) "http://192.168.0.214:8080" else serverHost) }
    var serverTokenInput by remember(echoToken) { mutableStateOf(if (echoToken.isBlank()) "1c13ee8697755fdc4518013ea2fd6996" else echoToken) }

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
            animation = tween(3200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wave_phase"
    )

    // Автоматическая тактильная отдача при получении входящего импульса
    LaunchedEffect(incomingReaction?.id) {
        val reaction = incomingReaction
        if (reaction != null) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            delay(5500L)
            EchoStateRepository.dismissIncomingReaction()
        }
    }

    // ── ГЛАВНЫЙ ЭКРАН (БЕЗ ВЕРТИКАЛЬНОГО СКРОЛЛА!) ──
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0B0E14)) // Deep Obsidian Dark
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 10.dp, start = 14.dp, end = 14.dp, bottom = 12.dp)
        ) {
            // ── 1. ВЕРХНЯЯ СТАТУСНАЯ ПАНЕЛЬ (TOP BAR, 56dp, GLASSMORPHISM) ──
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .clip(RoundedCornerShape(20.dp)),
                color = Color(0xFF151A24).copy(alpha = 0.88f),
                border = BorderStroke(1.dp, Color(0xFF1E2638))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Слева: пульсирующая точка + бегущая строка своего трека
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { showManualDialog = true },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(9.dp)
                                .clip(CircleShape)
                                .background(if (presenceActive) Color(0xFF10B981) else Color(0xFF64748B))
                        )
                        Column {
                            Text(
                                text = if (currentTrack.artist != "Ожидание музыки...") "♫ ${currentTrack.artist} — ${currentTrack.title}" else "♫ ЭХО · Тихий вечерний город",
                                color = Color(0xFFF8FAFC),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            val isConnected = connectionStatus.startsWith("●")
                            Text(
                                text = if (isConnected) "● В эфире · Вечерняя улица" else "⚠️ Не подключено к серверу",
                                color = if (isConnected) Color(0xFF10B981) else Color(0xFFEF4444),
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    // Справа: кнопки управления
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // Кнопка манифеста
                        Surface(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { showManifestDialog = true },
                            color = Color(0xFF1E1B4B),
                            border = BorderStroke(1.dp, Color(0xFF4338CA))
                        ) {
                            Text(
                                text = "✦",
                                color = Color(0xFF818CF8),
                                fontSize = 13.sp,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                            )
                        }

                        // Шестеренка сервера
                        Surface(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { showServerDialog = true },
                            color = Color(0xFF1A2130),
                            border = BorderStroke(1.dp, Color(0xFF263246))
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text("⚙", color = Color(0xFF94A3B8), fontSize = 15.sp)
                            }
                        }

                        // Тумблер приватности «В эфире / Невидимка» (48x48dp зона клика)
                        Surface(
                            modifier = Modifier
                                .height(34.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .clickable { EchoStateRepository.togglePresence() },
                            color = if (presenceActive) Color(0xFF042F2E) else Color(0xFF1E2638),
                            border = BorderStroke(1.dp, if (presenceActive) Color(0xFF10B981) else Color(0xFF475569))
                        ) {
                            Box(
                                modifier = Modifier.padding(horizontal = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = if (presenceActive) "В эфире" else "Невидимка",
                                    color = if (presenceActive) Color(0xFF10B981) else Color(0xFF94A3B8),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // ── 2. ЦЕНТРАЛЬНОЕ ПОЛЕ — СОНАР МУЗЫКАЛЬНОГО ПРИСУТСТВИЯ (70% ЭКРАНА) ──
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(28.dp))
                    .background(Color(0xFF06090F))
                    .border(1.dp, Color(0xFF151D2A), RoundedCornerShape(28.dp)),
                contentAlignment = Alignment.Center
            ) {
                // Если выбран таб «Радар»
                if (activeTab == 0) {
                    Canvas(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(nearbySignalsFlow, capsulesFlow) {
                                detectTapGestures { tapOffset ->
                                    val center = Offset(size.width / 2f, size.height / 2f)
                                    val minDim = minOf(size.width, size.height).toFloat()
                                    val maxR = minDim * 0.44f

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

                                    val touchRadiusPx = 40.dp.toPx()
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

                        // Концентрические кольца дистанции (100м, 250м, 500м)
                        listOf(0.35f, 0.65f, 0.95f).forEach { factor ->
                            drawCircle(
                                color = Color(0xFF141C2B),
                                radius = maxR * factor,
                                center = center,
                                style = Stroke(width = 1.2f)
                            )
                        }

                        // Оси радара
                        drawLine(
                            color = Color(0xFF0F1724),
                            start = Offset(center.x, center.y - maxR),
                            end = Offset(center.x, center.y + maxR),
                            strokeWidth = 1f
                        )
                        drawLine(
                            color = Color(0xFF0F1724),
                            start = Offset(center.x - maxR, center.y),
                            end = Offset(center.x + maxR, center.y),
                            strokeWidth = 1f
                        )

                        // Сканирующий вращающийся луч сонара
                        if (presenceActive) {
                            val sweepRad = Math.toRadians(radarSweepAngle.toDouble())
                            val endX = center.x + (maxR * cos(sweepRad)).toFloat()
                            val endY = center.y + (maxR * sin(sweepRad)).toFloat()

                            drawLine(
                                brush = Brush.linearGradient(
                                    colors = listOf(Color(0xFF00F0FF).copy(alpha = 0.85f), Color.Transparent),
                                    start = center,
                                    end = Offset(endX, endY)
                                ),
                                start = center,
                                end = Offset(endX, endY),
                                strokeWidth = 2.5f,
                                cap = StrokeCap.Round
                            )
                        }

                        // ── ЛАЗЕРНЫЕ СТРУНЫ РЕЗОНАНСА (LASER RESONANCE CHORDS) ──
                        nearbySignalsFlow.forEach { sig ->
                            val rad = Math.toRadians(sig.angleDeg.toDouble())
                            val r = maxR * sig.ringFactor.coerceIn(0.2f, 0.95f)
                            val bx = center.x + (r.toDouble() * cos(rad)).toFloat()
                            val by = center.y + (r.toDouble() * sin(rad)).toFloat()
                            val bOffset = Offset(bx, by)
                            val isSelected = sig.id == selectedSignal?.id

                            if (sig.compatibility >= 70 || isSelected) {
                                val beamColor = if (isSelected) Color(0xFF00F0FF) else Color(sig.colorHex)

                                // 1. Внешний мягкий неоновый ореол (Glow Bloom)
                                drawLine(
                                    color = beamColor.copy(alpha = if (isSelected) 0.35f else 0.15f),
                                    start = center,
                                    end = bOffset,
                                    strokeWidth = if (isSelected) 10.dp.toPx() else 6.dp.toPx(),
                                    cap = StrokeCap.Round
                                )

                                // 2. Плотный сердечник луча
                                drawLine(
                                    brush = Brush.linearGradient(
                                        colors = listOf(Color(0xFF00F0FF), beamColor),
                                        start = center,
                                        end = bOffset
                                    ),
                                    start = center,
                                    end = bOffset,
                                    strokeWidth = if (isSelected) 3.5.dp.toPx() else 2.0.dp.toPx(),
                                    cap = StrokeCap.Round
                                )

                                // 3. Бегущие световые частицы (кванты музыки) вдоль луча
                                val p1 = ((wavePhase / 6.28318f) + (sig.angleDeg / 360f)) % 1f
                                val sparkX = center.x + (bOffset.x - center.x) * p1
                                val sparkY = center.y + (bOffset.y - center.y) * p1
                                drawCircle(
                                    color = Color.White,
                                    radius = if (isSelected) 3.5.dp.toPx() else 2.5.dp.toPx(),
                                    center = Offset(sparkX, sparkY)
                                )
                            }
                        }

                        // Анимация световой волны отклика (когда нажата кнопка реакции)
                        impulseTargetOffset?.let { target ->
                            val p = impulseProgress.value
                            if (p > 0f && p < 1f) {
                                val currX = center.x + (target.x - center.x) * p
                                val currY = center.y + (target.y - center.y) * p
                                drawCircle(
                                    color = Color(0xFF00F0FF).copy(alpha = 1f - p),
                                    radius = 10.dp.toPx() + (p * 22.dp.toPx()),
                                    center = Offset(currX, currY),
                                    style = Stroke(width = 3.dp.toPx())
                                )
                            }
                        }

                        // Тайники (янтарные кристаллы #FFB800)
                        capsulesFlow.forEach { cap ->
                            val rad = Math.toRadians(cap.angleDeg.toDouble())
                            val r = maxR * cap.ringFactor.coerceIn(0.2f, 0.95f)
                            val cx = center.x + (r.toDouble() * cos(rad)).toFloat()
                            val cy = center.y + (r.toDouble() * sin(rad)).toFloat()
                            val cOffset = Offset(cx, cy)
                            val isSelected = cap.id == selectedCapsule?.id

                            drawCircle(
                                color = Color(0xFFFFB800).copy(alpha = if (isSelected) 0.35f else 0.15f),
                                radius = if (isSelected) 16.dp.toPx() * beaconPulse else 12.dp.toPx(),
                                center = cOffset
                            )
                            drawCircle(
                                color = Color(0xFFFFB800),
                                radius = if (isSelected) 7.dp.toPx() else 5.dp.toPx(),
                                center = cOffset
                            )
                            if (isSelected) {
                                drawCircle(
                                    color = Color.White,
                                    radius = 18.dp.toPx(),
                                    center = cOffset,
                                    style = Stroke(width = 2.dp.toPx())
                                )
                            }
                        }

                        // Маяки слушателей
                        nearbySignalsFlow.forEach { sig ->
                            val rad = Math.toRadians(sig.angleDeg.toDouble())
                            val r = maxR * sig.ringFactor.coerceIn(0.2f, 0.95f)
                            val bx = center.x + (r.toDouble() * cos(rad)).toFloat()
                            val by = center.y + (r.toDouble() * sin(rad)).toFloat()
                            val bOffset = Offset(bx, by)

                            val sigColor = Color(sig.colorHex).copy(alpha = if (sig.isGhost) 0.45f else 1.0f)
                            val isSelected = sig.id == selectedSignal?.id

                            // Внешний ореол
                            drawCircle(
                                color = sigColor.copy(alpha = if (isSelected) 0.45f else 0.2f),
                                radius = if (isSelected) 16.dp.toPx() * beaconPulse else 11.dp.toPx(),
                                center = bOffset
                            )
                            // Ядро маяка
                            drawCircle(
                                color = sigColor,
                                radius = if (isSelected) 7.5.dp.toPx() else 5.5.dp.toPx(),
                                center = bOffset
                            )
                            if (isSelected) {
                                drawCircle(
                                    color = Color.White,
                                    radius = 18.dp.toPx(),
                                    center = bOffset,
                                    style = Stroke(width = 2.dp.toPx())
                                )
                            }
                        }

                        // Центр радара (Слушатель)
                        drawCircle(
                            color = if (presenceActive) Color(0xFF00F0FF) else Color(0xFF475569),
                            radius = 7.dp.toPx(),
                            center = center
                        )
                        drawCircle(
                            color = Color(0xFF00F0FF).copy(alpha = 0.25f),
                            radius = 14.dp.toPx() * beaconPulse,
                            center = center
                        )
                    }

                    // Подписи треков прямо на радаре (до 5 ближайших, чтобы не было каши)
                    nearbySignalsFlow.take(5).forEach { sig ->
                        val rad = Math.toRadians(sig.angleDeg.toDouble())
                        val rFactor = sig.ringFactor.coerceIn(0.25f, 0.90f)
                        val alignBiasX = (rFactor * cos(rad)).toFloat()
                        val alignBiasY = (rFactor * sin(rad)).toFloat()

                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(24.dp),
                            contentAlignment = Alignment(alignBiasX, alignBiasY)
                        ) {
                            Surface(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable {
                                        selectedSignal = sig
                                        selectedCapsule = null
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    },
                                color = Color(0xFF0B0E14).copy(alpha = 0.82f),
                                border = BorderStroke(0.8.dp, if (selectedSignal?.id == sig.id) Color(0xFF00F0FF) else Color(0xFF1E2638))
                            ) {
                                Text(
                                    text = "♫ ${sig.artist} — ${sig.title}",
                                    color = if (selectedSignal?.id == sig.id) Color(0xFF00F0FF) else Color(0xFFCBD5E1),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }

                    // Состояние пустого эфира (Empty State)
                    if (nearbySignalsFlow.isEmpty()) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "Тихий час в этом квартале",
                                color = Color(0xFF94A3B8),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Включите трек на ПК или телефоне, чтобы зажечь первый маяк",
                                color = Color(0xFF64748B),
                                fontSize = 11.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                } else if (activeTab == 1) {
                    // Таб «Тайники»
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("⏳ МУЗЫКАЛЬНЫЕ ТАЙНИКИ", color = Color(0xFFFFB800), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Button(
                                onClick = { showDropCapsuleDialog = true },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD97706)),
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Text("+ Оставить", fontSize = 11.sp, color = Color.White)
                            }
                        }

                        if (capsulesFlow.isEmpty()) {
                            Text("В этом районе пока нет тайников. Оставьте свой первый след!", color = Color(0xFF64748B), fontSize = 11.sp, fontStyle = FontStyle.Italic)
                        } else {
                            capsulesFlow.forEach { cap ->
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(14.dp))
                                        .clickable {
                                            selectedCapsule = cap
                                            selectedSignal = null
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        },
                                    color = Color(0xFF151A24),
                                    border = BorderStroke(1.dp, Color(0xFFFFB800).copy(alpha = 0.5f))
                                ) {
                                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text("♫ ${cap.artist} — ${cap.title}", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                        Text("«${cap.secretMessage}»", color = Color(0xFFFDE68A), fontSize = 11.sp, fontStyle = FontStyle.Italic)
                                        Text("${cap.zoneName} · ${cap.distanceLabel} · ${cap.compatibility}% резонанс", color = Color(0xFF94A3B8), fontSize = 10.sp)
                                    }
                                }
                            }
                        }
                    }
                } else {
                    // Таб «Следы» (Дневник пересечений за день)
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text("📜 АНОНИМНЫЙ ДНЕВНИК ПЕРЕСЕЧЕНИЙ", color = Color(0xFF38BDF8), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Text("Музыка, звучавшая вокруг вас в течение сегодняшнего вечера:", color = Color(0xFF94A3B8), fontSize = 11.sp)

                        nearbySignalsFlow.forEach { sig ->
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp)),
                                color = Color(0xFF151A24),
                                border = BorderStroke(1.dp, Color(0xFF1E2638))
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(Color(sig.colorHex)))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("${sig.artist} — ${sig.title}", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                        Text("${sig.poeticName} · ${sig.distanceLabel}", color = Color(0xFF94A3B8), fontSize = 10.sp)
                                    }
                                    TextButton(onClick = { openTrackInMusicPlayer(context, sig.artist, sig.title) }) {
                                        Text("Слушать ▶", color = Color(0xFF38BDF8), fontSize = 10.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // ── 3. НИЖНЯЯ ЗОНА УПРАВЛЕНИЯ (THUMB ZONE, ПЛАВАЮЩАЯ КАПСУЛА РЕЖИМОВ) ──
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .clip(RoundedCornerShape(27.dp)),
                color = Color(0xFF151A24).copy(alpha = 0.94f),
                border = BorderStroke(1.dp, Color(0xFF1E2638))
            ) {
                Row(
                    modifier = Modifier.fillMaxSize().padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    listOf("● Радар", "⏳ Тайники", "📜 Следы").forEachIndexed { idx, label ->
                        val isSelected = activeTab == idx
                        Button(
                            onClick = { activeTab = idx },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isSelected) Color(0xFF00F0FF).copy(alpha = 0.18f) else Color.Transparent
                            ),
                            shape = RoundedCornerShape(24.dp),
                            border = if (isSelected) BorderStroke(1.dp, Color(0xFF00F0FF)) else null,
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Text(
                                text = label,
                                color = if (isSelected) Color(0xFF00F0FF) else Color(0xFF94A3B8),
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }

        // ── 4. МОДАЛЬНАЯ ШТОРКА: «МОСТ СОЗВУЧИЯ» (SPOTIFY BLEND + SHAZAM HERO ACTION) ──
        // Выдвигается снизу при нажатии на маяк (занимает ~58% экрана, Glassmorphism #151A24)
        AnimatedVisibility(
            visible = selectedSignal != null,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            selectedSignal?.let { sig ->
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)),
                    color = Color(0xFF151A24).copy(alpha = 0.97f),
                    border = BorderStroke(1.5.dp, Color(0xFF00F0FF).copy(alpha = 0.5f)),
                    shadowElevation = 24.dp
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Drag Handle
                        Box(
                            modifier = Modifier
                                .width(40.dp)
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(Color(0xFF334155))
                                .align(Alignment.CenterHorizontally)
                        )

                        // Шапка карточки: Генеративный винил + Архетип
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                GenerativeAvatarCanvas(
                                    phase = wavePhase,
                                    seed = sig.artist.length + 7,
                                    color = Color(sig.colorHex),
                                    modifier = Modifier.size(52.dp)
                                )
                                Column {
                                    Text(
                                        text = sig.poeticName,
                                        color = Color.White,
                                        fontSize = 17.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "${sig.distanceLabel} • ${sig.encountersCount}-я встреча за неделю",
                                        color = Color(0xFF00F0FF),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }

                            IconButton(
                                onClick = { selectedSignal = null },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Text("✕", color = Color(0xFF94A3B8), fontSize = 16.sp)
                            }
                        }

                        // ── БЛОК «МОСТ СОЗВУЧИЯ» (TASTE OVERLAP STORY) ──
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = Color(0xFF0B0E14),
                            border = BorderStroke(1.dp, Color(0xFF1E2638)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("🎧 В ваших ушах:", color = Color(0xFF64748B), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    Text(currentTrack.artist + " — " + currentTrack.title, color = Color(0xFFCBD5E1), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("📡 В ушах рядом:", color = Color(0xFF00F0FF), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    Text(sig.artist + " — " + sig.title, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }

                                if (sig.resonanceStory.isNotBlank()) {
                                    Text(
                                        text = "✦ ${sig.resonanceStory}",
                                        color = Color(0xFFC7D2FE),
                                        fontSize = 12.sp,
                                        lineHeight = 16.sp,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(Color(0xFF1E1B4B).copy(alpha = 0.5f))
                                            .padding(8.dp)
                                    )
                                }
                            }
                        }

                        // ── HERO ACTION (SHAZAM PATTERN): МОМЕНТАЛЬНО НАДЕТЬ НАУШНИКИ В 1 ТАП ──
                        Button(
                            onClick = {
                                openTrackInMusicPlayer(context, sig.artist, sig.title)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(54.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE11D48)),
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Text(
                                text = "▶ Надеть наушники незнакомца (Яндекс Музыка)",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }

                        // ── ПАНЕЛЬ НЕВЕРБАЛЬНЫХ МИКРО-ИМПУЛЬСОВ (LOCKET PATTERN) ──
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
                                        .size(50.dp)
                                        .clip(RoundedCornerShape(14.dp))
                                        .clickable {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            EchoStateRepository.sendReaction(sig.id, symbol) { ok ->
                                                sentSymbolMessage = if (ok) "Импульс $symbol отправлен в эфир" else "Ошибка отправки"
                                            }

                                            // Запуск бегущей световой волны от центра к маяку
                                            coroutineScope.launch {
                                                impulseProgress.snapTo(0f)
                                                impulseProgress.animateTo(
                                                    targetValue = 1f,
                                                    animationSpec = tween(650, easing = FastOutSlowInEasing)
                                                )
                                            }
                                        },
                                    color = Color(0xFF0B0E14),
                                    border = BorderStroke(1.dp, Color(0xFF1E2638))
                                ) {
                                    Column(
                                        modifier = Modifier.fillMaxSize(),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        Text(text = symbol, fontSize = 18.sp)
                                        Text(text = name, color = Color(0xFF94A3B8), fontSize = 8.sp)
                                    }
                                }
                            }
                        }

                        if (sentSymbolMessage.isNotBlank()) {
                            Text(
                                text = "✓ $sentSymbolMessage",
                                color = Color(0xFF00F0FF),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }
        }

        // ── ШТОРКА ТАЙНИКА ──
        AnimatedVisibility(
            visible = selectedCapsule != null,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            selectedCapsule?.let { cap ->
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)),
                    color = Color(0xFF1C1304).copy(alpha = 0.98f),
                    border = BorderStroke(1.5.dp, Color(0xFFFFB800)),
                    shadowElevation = 24.dp
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("⏳ МУЗЫКАЛЬНЫЙ ТАЙНИК", color = Color(0xFFFFB800), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            IconButton(onClick = { selectedCapsule = null }, modifier = Modifier.size(24.dp)) {
                                Text("✕", color = Color(0xFF94A3B8), fontSize = 14.sp)
                            }
                        }

                        Text("♫ ${cap.artist} — ${cap.title}", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Text("Послание автора: «${cap.secretMessage}»", color = Color(0xFFFDE68A), fontSize = 12.sp, fontStyle = FontStyle.Italic)
                        Text("Локация: ${cap.zoneName} · ${cap.distanceLabel} · ${cap.compatibility}% резонанс", color = Color(0xFF94A3B8), fontSize = 10.sp)

                        Button(
                            onClick = { openTrackInMusicPlayer(context, cap.artist, cap.title) },
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD97706))
                        ) {
                            Text("▶ Слушать трек в Яндекс Музыке", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        // ── ДИАЛОГИ (МАНИФЕСТ, СЕРВЕР, ТАЙНИК, РУЧНОЙ ВВОД) ──
        if (showManifestDialog) {
            AlertDialog(
                onDismissRequest = {
                    showManifestDialog = false
                    prefs.edit().putBoolean("has_seen_manifest", true).apply()
                },
                containerColor = Color(0xFF090E1A),
                shape = RoundedCornerShape(24.dp),
                title = {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("✦ МАНИФЕСТ ЭХО", color = Color(0xFF818CF8), fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                        Text("Какая музыка живет вокруг вас?", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    }
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Surface(shape = RoundedCornerShape(12.dp), color = Color(0xFF131D31)) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text("1. Город в наушниках", color = Color(0xFF00F0FF), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                Text("Вы видите не профили людей. Вы видите музыку, которая звучит в ушах тех, кто идет по этой же улице в радиусе 200 метров.", color = Color(0xFFCBD5E1), fontSize = 11.sp)
                            }
                        }
                        Surface(shape = RoundedCornerShape(12.dp), color = Color(0xFF131D31)) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text("2. Это НЕ соцсеть", color = Color(0xFFA78BFA), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                Text("Здесь нет лайков, счетчиков, фотографий и чатов. Только чистая музыка и тихие импульсы присутствия.", color = Color(0xFFCBD5E1), fontSize = 11.sp)
                            }
                        }
                        Surface(shape = RoundedCornerShape(12.dp), color = Color(0xFF131D31)) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text("3. Надевайте чужие наушники", color = Color(0xFFFFB800), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                Text("Тапните на любой маяк на сонаре и слушайте найденный трек в Яндекс Музыке в 1 клик.", color = Color(0xFFCBD5E1), fontSize = 11.sp)
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            showManifestDialog = false
                            prefs.edit().putBoolean("has_seen_manifest", true).apply()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4F46E5)),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Войти в акустический эфир", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            )
        }

        if (showServerDialog) {
            AlertDialog(
                onDismissRequest = { showServerDialog = false },
                title = { Text("Подключение к серверу ЭХО", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Введите локальный IP компьютера (192.168.x.x:8080) или адрес Cloudflare:", color = Color(0xFF94A3B8), fontSize = 12.sp)
                        OutlinedTextField(
                            value = serverHostInput,
                            onValueChange = { serverHostInput = it },
                            label = { Text("Адрес сервера", fontSize = 11.sp) },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color(0xFFCBD5E1),
                                focusedBorderColor = Color(0xFF00F0FF),
                                unfocusedBorderColor = Color(0xFF334155)
                            )
                        )
                        OutlinedTextField(
                            value = serverTokenInput,
                            onValueChange = { serverTokenInput = it },
                            label = { Text("🔑 Токен (X-Echo-Token)", fontSize = 11.sp) },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color(0xFFCBD5E1),
                                focusedBorderColor = Color(0xFF818CF8),
                                unfocusedBorderColor = Color(0xFF334155)
                            )
                        )
                        OutlinedButton(
                            onClick = {
                                serverHostInput = "http://192.168.0.214:8080"
                                serverTokenInput = "1c13ee8697755fdc4518013ea2fd6996"
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF00F0FF)),
                            border = BorderStroke(1.dp, Color(0xFF00F0FF))
                        ) {
                            Text("Вставить локальный IP (192.168.0.214:8080)", fontSize = 11.sp)
                        }
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

        if (showDropCapsuleDialog) {
            var secretMessage by remember { mutableStateOf("") }
            var isSubmitting by remember { mutableStateOf(false) }

            AlertDialog(
                onDismissRequest = { if (!isSubmitting) showDropCapsuleDialog = false },
                containerColor = Color(0xFF1C1304),
                shape = RoundedCornerShape(22.dp),
                title = { Text("Оставить тайник в городе", color = Color(0xFFFFB800), fontSize = 17.sp, fontWeight = FontWeight.Bold) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Трек: ♫ ${currentTrack.artist} — ${currentTrack.title}", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Text("Напишите мысль, привязанную к этой точке улицы:", color = Color(0xFFD4D4D8), fontSize = 12.sp)
                        OutlinedTextField(
                            value = secretMessage,
                            onValueChange = { secretMessage = it },
                            placeholder = { Text("Здесь шел теплый дождь...", fontSize = 12.sp) },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color(0xFFCBD5E1),
                                focusedBorderColor = Color(0xFFFFB800),
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

        if (showManualDialog) {
            ManualTrackDialog(
                onDismiss = { showManualDialog = false },
                onSubmit = { artist, title ->
                    EchoStateRepository.updateDetectedMusic(artist, title, "Ручной ввод")
                    showManualDialog = false
                }
            )
        }

        if (showPermissionGuideDialog) {
            AlertDialog(
                onDismissRequest = { showPermissionGuideDialog = false },
                containerColor = Color(0xFF0F172A),
                shape = RoundedCornerShape(22.dp),
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("🎧", fontSize = 22.sp)
                        Text("Как включить чтение музыки", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    }
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("В настройках Android разрешите доступ к уведомлениям для «ЭХО»:", color = Color(0xFFCBD5E1), fontSize = 12.sp)
                        Text("💡 Если тумблер серый («Ограниченная настройка»): на главном экране зажмите иконку ЭХО ➔ «О приложении» ➔ 3 точки (⋮) ➔ «Разрешить ограниченные настройки».", color = Color(0xFFFFB800), fontSize = 11.sp)
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            showPermissionGuideDialog = false
                            context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4F46E5))
                    ) {
                        Text("Открыть настройки Android", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showPermissionGuideDialog = false }) {
                        Text("Понятно", color = Color(0xFF94A3B8))
                    }
                }
            )
        }
    }
}

// ── ГЕНЕРАТИВНЫЙ АВАТАР ──
@Composable
fun GenerativeAvatarCanvas(
    phase: Float,
    seed: Int,
    color: Color = Color(0xFF00F0FF),
    modifier: Modifier = Modifier
) {
    Canvas(
        modifier = modifier
            .clip(CircleShape)
            .background(Color(0xFF06090F))
            .border(1.dp, Color(0xFF1E2638), CircleShape)
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
        title = { Text("Поделиться треком вручную", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold) },
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
