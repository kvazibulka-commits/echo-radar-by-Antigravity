package ru.echo.radar

import android.content.Context
import android.content.Intent
import android.net.Uri
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.net.URLEncoder
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        EchoStateRepository.init(this)
        setContent {
            EchoRadarTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color(0xFF080B10)
                ) {
                    VinylCityMainScreen()
                }
            }
        }
    }
}

@Composable
fun EchoRadarTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            background = Color(0xFF080B10),
            surface = Color(0xFF0F1420),
            primary = Color(0xFF00F0FF),
            secondary = Color(0xFFA855F7),
            tertiary = Color(0xFFFFB800)
        ),
        content = content
    )
}

// -------------------------------------------------------------
// UNIFIED VINYL TRACK MODEL (COMBINING SIGNALS & CAPSULES)
// -------------------------------------------------------------
data class VinylTrack(
    val id: String,
    val title: String,
    val artist: String,
    val sourceApp: String,
    val distanceLabel: String,
    val poeticPersona: String,
    val observation: String,
    val story: String,
    val compatibility: Int,
    val angleDeg: Float,
    val ringFactor: Float,
    val colorHex: Long,
    val isCapsule: Boolean = false,
    val isGhost: Boolean = false,
    val isSelf: Boolean = false
)

fun PlayingTrack.toVinylTrack(): VinylTrack = VinylTrack(
    id = "self_user_vinyl",
    title = title,
    artist = artist,
    sourceApp = sourceApp,
    distanceLabel = "Вы • В центре эфира",
    poeticPersona = poeticPersona,
    observation = "Ваша текущая музыкальная аура звучит в ночном городе.",
    story = "Ваш личный звуковой маяк транслирует эту мелодию в эфир.",
    compatibility = 100,
    angleDeg = 0f,
    ringFactor = 0f,
    colorHex = 0xFF00F0FFL,
    isSelf = true
)

fun NearbyEchoSignal.toVinylTrack(): VinylTrack = VinylTrack(
    id = id,
    title = title,
    artist = artist,
    sourceApp = sourceApp,
    distanceLabel = distanceLabel,
    poeticPersona = poeticName,
    observation = aiObservation.ifBlank { "Слушает музыку в вечерней тишине." },
    story = resonanceStory.ifBlank { "Тёплый вечерний резонанс в ночном квартале." },
    compatibility = compatibility,
    angleDeg = angleDeg,
    ringFactor = ringFactor,
    colorHex = colorHex,
    isGhost = isGhost
)

fun EchoCapsule.toVinylTrack(): VinylTrack = VinylTrack(
    id = id,
    title = title,
    artist = artist,
    sourceApp = if (isVenue) venueName else "Тайник",
    distanceLabel = distanceLabel,
    poeticPersona = personaTitle,
    observation = secretMessage,
    story = "Музыкальная капсула, оставленная в зоне: $zoneName",
    compatibility = compatibility,
    angleDeg = angleDeg,
    ringFactor = ringFactor,
    colorHex = 0xFFFFB800L,
    isCapsule = true
)

// -------------------------------------------------------------
// MAIN SCREEN: VINYL CITY MAP + TACTILE TURNTABLE DECK
// -------------------------------------------------------------
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun VinylCityMainScreen() {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()

    val currentPlayingTrack by EchoStateRepository.currentTrack.collectAsState()
    val nearbySignals by EchoStateRepository.nearbySignals.collectAsState()
    val capsules by EchoStateRepository.capsules.collectAsState()
    val isPresenceActive by EchoStateRepository.presenceActive.collectAsState()
    val connectionStatusStr by EchoStateRepository.connectionStatus.collectAsState()
    val incomingReaction by EchoStateRepository.incomingReaction.collectAsState()
    val serverHost by EchoStateRepository.serverHost.collectAsState()

    val isConnected = !connectionStatusStr.contains("Не подключено") && !connectionStatusStr.contains("Сбой")

    // All available vinyl records in the vicinity
    val allVinyls = remember(currentPlayingTrack, nearbySignals, capsules) {
        val list = mutableListOf<VinylTrack>()
        nearbySignals.forEach { list.add(it.toVinylTrack()) }
        capsules.forEach { list.add(it.toVinylTrack()) }
        list
    }

    // Currently loaded vinyl on the turntable deck
    var selectedVinyl by remember { mutableStateOf<VinylTrack?>(null) }

    // When allVinyls or currentPlayingTrack changes, ensure a selected track
    LaunchedEffect(allVinyls, currentPlayingTrack) {
        if (selectedVinyl == null) {
            selectedVinyl = if (allVinyls.isNotEmpty()) allVinyls.first() else currentPlayingTrack.toVinylTrack()
        } else if (selectedVinyl?.isSelf == true) {
            selectedVinyl = currentPlayingTrack.toVinylTrack()
        }
    }

    // Turntable play & tonearm state
    var isPlatterSpinning by remember { mutableStateOf(true) }

    // Active Album Artwork URL loaded via iTunes Search API
    var activeCoverUrl by remember { mutableStateOf<String?>(null) }

    val activeTrack = selectedVinyl ?: currentPlayingTrack.toVinylTrack()

    LaunchedEffect(activeTrack.artist, activeTrack.title) {
        activeCoverUrl = null
        if (!activeTrack.artist.startsWith("Ожидание") && activeTrack.artist.isNotBlank()) {
            val url = AlbumArtResolver.getCoverUrl(activeTrack.artist, activeTrack.title)
            activeCoverUrl = url
        }
    }

    // Modal dialog states
    var showServerSettingsDialog by remember { mutableStateOf(false) }
    var showManualTrackDialog by remember { mutableStateOf(false) }
    var showDropCapsuleDialog by remember { mutableStateOf(false) }
    var showCuratorObservationDialog by remember { mutableStateOf(false) }

    // Feedback toast for reactions sent
    var reactionSentFeedback by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(reactionSentFeedback) {
        if (reactionSentFeedback != null) {
            delay(2400)
            reactionSentFeedback = null
        }
    }

    // Auto-dismiss and haptic vibration when an incoming reaction arrives
    LaunchedEffect(incomingReaction) {
        if (incomingReaction != null) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            delay(5500)
            EchoStateRepository.dismissIncomingReaction()
        }
    }

    // Continuous infinite transitions for vinyl rotation and neon pulsing
    val infiniteTransition = rememberInfiniteTransition(label = "VinylAnimations")
    val platterRotationAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "PlatterRotation"
    )

    val neonPulse by infiniteTransition.animateFloat(
        initialValue = 0.82f,
        targetValue = 1.18f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "NeonPulse"
    )

    val laserPhotonProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "LaserPhoton"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF080B10))
    ) {
        Column(modifier = Modifier.fillMaxSize()) {

            // =========================================================
            // 1. TOP STATUS & BRAND BAR (52dp)
            // =========================================================
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Typographic Brand Logo
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Э  Х  О",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 6.sp
                    )
                }

                // Presence / Status Pill
                Surface(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .combinedClickable(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                showManualTrackDialog = true
                            },
                            onLongClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                showServerSettingsDialog = true
                            }
                        ),
                    color = Color(0xFF111622).copy(alpha = 0.95f),
                    border = BorderStroke(0.8.dp, if (isConnected) Color(0xFF1E283C) else Color(0xFFE11D48))
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
                                .background(if (isConnected) Color(0xFF00F0FF) else Color(0xFFE11D48))
                        )
                        Text(
                            text = if (isConnected) "${allVinyls.size} в квартале" else "Оффлайн",
                            color = if (isConnected) Color(0xFF00F0FF) else Color(0xFF94A3B8),
                            fontSize = 11.5.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                // Stealth / On-Air Toggle Button
                Surface(
                    modifier = Modifier
                        .clip(RoundedCornerShape(18.dp))
                        .clickable {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            EchoStateRepository.togglePresence()
                        },
                    color = if (isPresenceActive) Color(0xFF00F0FF).copy(alpha = 0.12f) else Color(0xFF1E2430),
                    border = BorderStroke(
                        0.8.dp,
                        if (isPresenceActive) Color(0xFF00F0FF).copy(alpha = 0.5f) else Color(0xFF334155)
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Text(
                            text = if (isPresenceActive) "В эфире" else "Невидимка",
                            color = if (isPresenceActive) Color(0xFF00F0FF) else Color(0xFF94A3B8),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            // =========================================================
            // 2. VINYL CITY MAP (56% Viewport Height)
            // =========================================================
            Box(
                modifier = Modifier
                    .weight(1.25f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                VinylCityMapCanvas(
                    allVinyls = allVinyls,
                    selectedVinyl = selectedVinyl,
                    neonPulse = neonPulse,
                    laserPhotonProgress = laserPhotonProgress,
                    onVinylSelected = { vinyl ->
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        selectedVinyl = vinyl
                        isPlatterSpinning = true
                    },
                    onCenterUserClicked = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        selectedVinyl = currentPlayingTrack.toVinylTrack()
                        isPlatterSpinning = true
                    }
                )

                // Quick Floating Map Action: Drop Capsule
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 16.dp, bottom = 12.dp)
                        .clip(CircleShape)
                        .clickable {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            showDropCapsuleDialog = true
                        },
                    color = Color(0xFF111622).copy(alpha = 0.92f),
                    border = BorderStroke(1.dp, Color(0xFFFFB800).copy(alpha = 0.7f)),
                    shadowElevation = 6.dp
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(text = "⏳", fontSize = 13.sp)
                        Text(
                            text = "Оставить тайник",
                            color = Color(0xFFFFB800),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            // =========================================================
            // 3. TACTILE TURNTABLE DECK (44% Viewport Height in Thumb Zone)
            // =========================================================
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                color = Color(0xFF0D121B),
                border = BorderStroke(1.dp, Color(0xFF1E283C))
            ) {
                TurntableDeckView(
                    activeTrack = activeTrack,
                    coverUrl = activeCoverUrl,
                    isSpinning = isPlatterSpinning,
                    rotationAngle = if (isPlatterSpinning) platterRotationAngle else 0f,
                    onToggleSpin = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        isPlatterSpinning = !isPlatterSpinning
                    },
                    onOpenInMusicApp = {
                        openMusicApp(context, activeTrack.artist, activeTrack.title, activeTrack.sourceApp)
                    },
                    onSendReaction = { glyph ->
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        val targetId = if (activeTrack.isSelf) "broadcast" else activeTrack.id
                        EchoStateRepository.sendReaction(targetId, glyph) { ok ->
                            reactionSentFeedback = if (ok) "Импульс $glyph отправлен" else "Ошибка отправки"
                        }
                    },
                    onOpenCuratorStory = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        showCuratorObservationDialog = true
                    },
                    onSelectSelf = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        selectedVinyl = currentPlayingTrack.toVinylTrack()
                        isPlatterSpinning = true
                    }
                )
            }
        }

        // =========================================================
        // 4. FLOATING REACTION FEEDBACK NOTIFICATION
        // =========================================================
        AnimatedVisibility(
            visible = reactionSentFeedback != null,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 58.dp),
            enter = fadeIn() + slideInVertically(initialOffsetY = { -20 }),
            exit = fadeOut() + slideOutVertically(targetOffsetY = { -20 })
        ) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = Color(0xFF00F0FF).copy(alpha = 0.95f),
                shadowElevation = 8.dp
            ) {
                Text(
                    text = reactionSentFeedback ?: "",
                    color = Color.Black,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                )
            }
        }

        // =========================================================
        // 5. INCOMING ATMOSPHERIC REACTION BANNER
        // =========================================================
        incomingReaction?.let { inc ->
            Surface(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 56.dp, start = 16.dp, end = 16.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .clickable {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        EchoStateRepository.dismissIncomingReaction()
                    },
                color = Color(0xFF141B29),
                border = BorderStroke(1.2.dp, Color(0xFF00F0FF)),
                shadowElevation = 12.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(text = inc.symbol, fontSize = 22.sp)
                        Column {
                            Text(
                                text = "Импульс от ${inc.senderId}",
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = inc.message,
                                color = Color(0xFF94A3B8),
                                fontSize = 11.5.sp
                            )
                        }
                    }
                    Text(
                        text = "✕",
                        color = Color(0xFF64748B),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }

    // =========================================================
    // MODAL DIALOGS
    // =========================================================

    // A. Curator Observation & Story Modal
    if (showCuratorObservationDialog) {
        AlertDialog(
            onDismissRequest = { showCuratorObservationDialog = false },
            containerColor = Color(0xFF111622),
            title = {
                Text(
                    text = "✦ ${activeTrack.poeticPersona}",
                    color = Color(0xFF00F0FF),
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "\"${activeTrack.observation}\"",
                        color = Color(0xFFE2E8F0),
                        fontSize = 14.sp,
                        fontStyle = FontStyle.Italic,
                        lineHeight = 20.sp
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(0.8.dp)
                            .background(Color(0xFF1E283C))
                    )
                    Text(
                        text = activeTrack.story,
                        color = Color(0xFF94A3B8),
                        fontSize = 12.5.sp,
                        lineHeight = 18.sp
                    )
                    Text(
                        text = "Звучит через: ${activeTrack.sourceApp}",
                        color = Color(0xFF64748B),
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showCuratorObservationDialog = false }) {
                    Text("Закрыть", color = Color(0xFF00F0FF))
                }
            }
        )
    }

    // B. Manual Track Input Modal
    if (showManualTrackDialog) {
        var inputArtist by remember { mutableStateOf(currentPlayingTrack.artist.takeIf { !it.startsWith("Ожидание") } ?: "") }
        var inputTitle by remember { mutableStateOf(currentPlayingTrack.title.takeIf { !it.startsWith("Включите") } ?: "") }

        AlertDialog(
            onDismissRequest = { showManualTrackDialog = false },
            containerColor = Color(0xFF111622),
            title = {
                Text("Поставить свой винил в эфир", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = inputArtist,
                        onValueChange = { inputArtist = it },
                        label = { Text("Исполнитель", color = Color(0xFF94A3B8)) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFF00F0FF),
                            unfocusedBorderColor = Color(0xFF334155)
                        ),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = inputTitle,
                        onValueChange = { inputTitle = it },
                        label = { Text("Название трека", color = Color(0xFF94A3B8)) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFF00F0FF),
                            unfocusedBorderColor = Color(0xFF334155)
                        ),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        text = "Либо откройте Яндекс Музыку, VK или Spotify — перехват работает автоматически.",
                        color = Color(0xFF64748B),
                        fontSize = 11.sp
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (inputArtist.isNotBlank() && inputTitle.isNotBlank()) {
                            EchoStateRepository.updateDetectedMusic(inputArtist.trim(), inputTitle.trim(), "manual")
                            selectedVinyl = null
                        }
                        showManualTrackDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00F0FF))
                ) {
                    Text("В эфир", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                    context.startActivity(intent)
                }) {
                    Text("Настройки доступа", color = Color(0xFF94A3B8), fontSize = 12.sp)
                }
            }
        )
    }

    // C. Leave Musical Capsule Modal
    if (showDropCapsuleDialog) {
        var capArtist by remember { mutableStateOf(currentPlayingTrack.artist.takeIf { !it.startsWith("Ожидание") } ?: "") }
        var capTitle by remember { mutableStateOf(currentPlayingTrack.title.takeIf { !it.startsWith("Включите") } ?: "") }
        var capNote by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showDropCapsuleDialog = false },
            containerColor = Color(0xFF111622),
            title = {
                Text("Оставить виниловый тайник", color = Color(0xFFFFB800), fontSize = 16.sp, fontWeight = FontWeight.Bold)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Тайник останется на карте города для других ночных слушателей.",
                        color = Color(0xFF94A3B8),
                        fontSize = 12.sp
                    )
                    OutlinedTextField(
                        value = capArtist,
                        onValueChange = { capArtist = it },
                        label = { Text("Исполнитель", color = Color(0xFF94A3B8)) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFFFFB800),
                            unfocusedBorderColor = Color(0xFF334155)
                        ),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = capTitle,
                        onValueChange = { capTitle = it },
                        label = { Text("Название трека", color = Color(0xFF94A3B8)) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFFFFB800),
                            unfocusedBorderColor = Color(0xFF334155)
                        ),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = capNote,
                        onValueChange = { capNote = it },
                        label = { Text("Поэтическое послание", color = Color(0xFF94A3B8)) },
                        placeholder = { Text("Здесь звучал ночной джаз в тумане...", color = Color(0xFF475569)) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFFFFB800),
                            unfocusedBorderColor = Color(0xFF334155)
                        ),
                        maxLines = 3,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (capArtist.isNotBlank() && capTitle.isNotBlank()) {
                            EchoStateRepository.dropCapsule(
                                artist = capArtist.trim(),
                                title = capTitle.trim(),
                                secretMessage = capNote.trim().ifBlank { "Музыкальный след ночного странника." }
                            ) { ok ->
                                reactionSentFeedback = if (ok) "Тайник заложен на карте!" else "Ошибка сети"
                            }
                        }
                        showDropCapsuleDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFB800))
                ) {
                    Text("Заложить тайник", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDropCapsuleDialog = false }) {
                    Text("Отмена", color = Color(0xFF94A3B8))
                }
            }
        )
    }

    // D. Server Host & Token Settings Modal
    if (showServerSettingsDialog) {
        var inputHost by remember { mutableStateOf(serverHost) }
        var inputToken by remember { mutableStateOf(EchoStateRepository.echoToken.value) }

        AlertDialog(
            onDismissRequest = { showServerSettingsDialog = false },
            containerColor = Color(0xFF111622),
            title = {
                Text("Параметры соединения ЭХО", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Текущий статус: $connectionStatusStr",
                        color = if (isConnected) Color(0xFF00F0FF) else Color(0xFFE11D48),
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    OutlinedTextField(
                        value = inputHost,
                        onValueChange = { inputHost = it },
                        label = { Text("Адрес сервера (ПК или Cloudflare)", color = Color(0xFF94A3B8)) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFF00F0FF),
                            unfocusedBorderColor = Color(0xFF334155)
                        ),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = inputToken,
                        onValueChange = { inputToken = it },
                        label = { Text("Токен доступа (X-Echo-Token)", color = Color(0xFF94A3B8)) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFF00F0FF),
                            unfocusedBorderColor = Color(0xFF334155)
                        ),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        EchoStateRepository.setServerHost(inputHost.trim(), inputToken.trim())
                        showServerSettingsDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00F0FF))
                ) {
                    Text("Сохранить", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showServerSettingsDialog = false }) {
                    Text("Закрыть", color = Color(0xFF94A3B8))
                }
            }
        )
    }
}

// -------------------------------------------------------------
// VINYL CITY MAP CANVAS (ZENLY / SNAP MAP STYLE)
// -------------------------------------------------------------
@Composable
fun VinylCityMapCanvas(
    allVinyls: List<VinylTrack>,
    selectedVinyl: VinylTrack?,
    neonPulse: Float,
    laserPhotonProgress: Float,
    onVinylSelected: (VinylTrack) -> Unit,
    onCenterUserClicked: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(allVinyls, selectedVinyl) {
                    detectTapGestures { tapOffset ->
                        val center = Offset(size.width / 2f, size.height / 2f)
                        val maxR = minOf(size.width, size.height) * 0.44f

                        // Check tap on center user beacon
                        val distCenter = sqrt(
                            (tapOffset.x - center.x) * (tapOffset.x - center.x) +
                            (tapOffset.y - center.y) * (tapOffset.y - center.y)
                        )
                        if (distCenter < 38.dp.toPx()) {
                            onCenterUserClicked()
                            return@detectTapGestures
                        }

                        // Check tap on mini vinyls
                        var clickedVinyl: VinylTrack? = null
                        var minDistance = 44.dp.toPx()

                        allVinyls.forEach { vinyl ->
                            val jitter = (vinyl.id.hashCode() % 25 - 12) * 0.35f
                            val rad = Math.toRadians((vinyl.angleDeg + jitter).toDouble())
                            val r = maxR * vinyl.ringFactor.coerceIn(0.24f, 0.92f)
                            val vx = center.x + (r * cos(rad)).toFloat()
                            val vy = center.y + (r * sin(rad)).toFloat()

                            val d = sqrt((tapOffset.x - vx) * (tapOffset.x - vx) + (tapOffset.y - vy) * (tapOffset.y - vy))
                            if (d < minDistance) {
                                minDistance = d
                                clickedVinyl = vinyl
                            }
                        }

                        clickedVinyl?.let { onVinylSelected(it) }
                    }
                }
        ) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val maxR = minOf(size.width, size.height) * 0.44f

            // A. PROCEDURAL NIGHT CITY MAP (Streets, River, Blocks)
            // 1. River Waterway
            val riverPath = Path().apply {
                moveTo(-50f, size.height * 0.22f)
                cubicTo(
                    size.width * 0.35f, size.height * 0.12f,
                    size.width * 0.65f, size.height * 0.45f,
                    size.width + 60f, size.height * 0.38f
                )
            }
            drawPath(
                path = riverPath,
                color = Color(0xFF0A2234).copy(alpha = 0.85f),
                style = Stroke(width = 38.dp.toPx(), cap = StrokeCap.Round)
            )
            drawPath(
                path = riverPath,
                color = Color(0xFF00F0FF).copy(alpha = 0.15f),
                style = Stroke(width = 6.dp.toPx(), cap = StrokeCap.Round)
            )

            // 2. City Street Grid (Stylized Night Arteries)
            val streetColor = Color(0xFF141B28)
            val avenueColor = Color(0xFFFFB800).copy(alpha = 0.08f)

            // Horizontal & diagonal avenues
            drawLine(
                color = avenueColor,
                start = Offset(0f, size.height * 0.52f),
                end = Offset(size.width, size.height * 0.52f),
                strokeWidth = 14.dp.toPx()
            )
            drawLine(
                color = avenueColor,
                start = Offset(size.width * 0.15f, 0f),
                end = Offset(size.width * 0.85f, size.height),
                strokeWidth = 12.dp.toPx()
            )

            // Grid streets
            for (step in 1..4) {
                val y = size.height * (step / 5f)
                drawLine(
                    color = streetColor,
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = 2.dp.toPx()
                )
            }
            for (step in 1..4) {
                val x = size.width * (step / 5f)
                drawLine(
                    color = streetColor,
                    start = Offset(x, 0f),
                    end = Offset(x, size.height),
                    strokeWidth = 2.dp.toPx()
                )
            }

            // B. CONCENTRIC DISTANCE ACOUSTIC RINGS
            val ringRadii = listOf(maxR * 0.35f, maxR * 0.65f, maxR * 0.95f)
            ringRadii.forEachIndexed { idx, radius ->
                drawCircle(
                    color = Color(0xFF1E283C).copy(alpha = 0.6f),
                    radius = radius,
                    center = center,
                    style = Stroke(width = 1.dp.toPx())
                )
            }

            // C. RESONANCE LASER CHORD TO SELECTED VINYL
            selectedVinyl?.let { target ->
                if (!target.isSelf) {
                    val jitter = (target.id.hashCode() % 25 - 12) * 0.35f
                    val rad = Math.toRadians((target.angleDeg + jitter).toDouble())
                    val r = maxR * target.ringFactor.coerceIn(0.24f, 0.92f)
                    val targetOffset = Offset(
                        center.x + (r * cos(rad)).toFloat(),
                        center.y + (r * sin(rad)).toFloat()
                    )

                    val chordColor = Color(target.colorHex)
                    // Outer diffuse glow
                    drawLine(
                        color = chordColor.copy(alpha = 0.22f),
                        start = center,
                        end = targetOffset,
                        strokeWidth = 6.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                    // Bright core beam
                    drawLine(
                        color = chordColor.copy(alpha = 0.85f),
                        start = center,
                        end = targetOffset,
                        strokeWidth = 1.8.dp.toPx(),
                        cap = StrokeCap.Round
                    )

                    // Traveling photon packet
                    val photonPos = Offset(
                        center.x + (targetOffset.x - center.x) * laserPhotonProgress,
                        center.y + (targetOffset.y - center.y) * laserPhotonProgress
                    )
                    drawCircle(
                        color = Color.White,
                        radius = 3.5.dp.toPx(),
                        center = photonPos
                    )
                    drawCircle(
                        color = chordColor.copy(alpha = 0.5f),
                        radius = 8.dp.toPx(),
                        center = photonPos
                    )
                }
            }

            // D. FLOATING MINI VINYL RECORDS
            allVinyls.forEach { vinyl ->
                val jitter = (vinyl.id.hashCode() % 25 - 12) * 0.35f
                val rad = Math.toRadians((vinyl.angleDeg + jitter).toDouble())
                val r = maxR * vinyl.ringFactor.coerceIn(0.24f, 0.92f)
                val pos = Offset(
                    center.x + (r * cos(rad)).toFloat(),
                    center.y + (r * sin(rad)).toFloat()
                )

                val isFocused = selectedVinyl?.id == vinyl.id
                val vinylBaseColor = Color(vinyl.colorHex)

                // 1. Neon puddle reflection on ground
                drawCircle(
                    color = vinylBaseColor.copy(alpha = if (isFocused) 0.35f else 0.15f),
                    radius = (if (isFocused) 26.dp else 18.dp).toPx() * neonPulse,
                    center = pos
                )

                // 2. Mini Vinyl Disc (Grooved body)
                val discRadius = (if (isFocused) 16.dp else 12.dp).toPx()
                drawCircle(
                    color = Color(0xFF141720),
                    radius = discRadius,
                    center = pos
                )
                // Outer edge ring
                drawCircle(
                    color = if (isFocused) vinylBaseColor else Color(0xFF2D3748),
                    radius = discRadius,
                    center = pos,
                    style = Stroke(width = if (isFocused) 1.8.dp.toPx() else 1.dp.toPx())
                )
                // Micro groove line
                drawCircle(
                    color = Color(0xFF222B3D),
                    radius = discRadius * 0.65f,
                    center = pos,
                    style = Stroke(width = 0.8.dp.toPx())
                )
                // Center colored label
                drawCircle(
                    color = vinylBaseColor,
                    radius = discRadius * 0.38f,
                    center = pos
                )
                // Center spindle hole
                drawCircle(
                    color = Color.Black,
                    radius = discRadius * 0.12f,
                    center = pos
                )
            }

            // E. USER CENTER POSITION (Core Turntable Beacon)
            val userCoreColor = Color(0xFF00F0FF)
            // Breathing aura ring
            drawCircle(
                color = userCoreColor.copy(alpha = 0.18f),
                radius = 28.dp.toPx() * neonPulse,
                center = center
            )
            // Solid dark center player disc
            drawCircle(
                color = Color(0xFF0E1420),
                radius = 16.dp.toPx(),
                center = center
            )
            drawCircle(
                color = userCoreColor,
                radius = 16.dp.toPx(),
                center = center,
                style = Stroke(width = 2.dp.toPx())
            )
            // Inner cyan spindle core
            drawCircle(
                color = userCoreColor,
                radius = 6.dp.toPx(),
                center = center
            )
        }

        // F. FLOATING CALLOUT BADGES FOR MINI VINYLS
        allVinyls.forEach { vinyl ->
            val isFocused = selectedVinyl?.id == vinyl.id
            if (isFocused) {
                MiniVinylCalloutPill(
                    vinyl = vinyl,
                    modifier = Modifier.align(Alignment.TopCenter).padding(top = 8.dp)
                )
            }
        }
    }
}

@Composable
fun MiniVinylCalloutPill(vinyl: VinylTrack, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp)),
        color = Color(0xFF101624).copy(alpha = 0.95f),
        border = BorderStroke(1.dp, Color(vinyl.colorHex).copy(alpha = 0.7f)),
        shadowElevation = 8.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(text = "♫", color = Color(vinyl.colorHex), fontSize = 12.sp)
            Text(
                text = "${vinyl.artist} — ${vinyl.title}",
                color = Color.White,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "• ${vinyl.distanceLabel}",
                color = Color(0xFF94A3B8),
                fontSize = 10.5.sp
            )
        }
    }
}

// -------------------------------------------------------------
// TACTILE TURNTABLE DECK VIEW (THUMB ZONE)
// -------------------------------------------------------------
@Composable
fun TurntableDeckView(
    activeTrack: VinylTrack,
    coverUrl: String?,
    isSpinning: Boolean,
    rotationAngle: Float,
    onToggleSpin: () -> Unit,
    onOpenInMusicApp: () -> Unit,
    onSendReaction: (String) -> Unit,
    onOpenCuratorStory: () -> Unit,
    onSelectSelf: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Upper Deck Section: Platter (Left) + Big Legible Metadata (Right)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. ROTATING VINYL PLATTER WITH COVER ART & TONEARM
            Box(
                modifier = Modifier
                    .size(140.dp)
                    .clickable { onToggleSpin() },
                contentAlignment = Alignment.Center
            ) {
                // Platter Well / Shadow
                Box(
                    modifier = Modifier
                        .size(136.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF07090E))
                )

                // Spinning Vinyl Platter
                Canvas(
                    modifier = Modifier
                        .size(130.dp)
                        .rotate(rotationAngle)
                ) {
                    val radius = size.minDimension / 2f
                    val center = Offset(size.width / 2f, size.height / 2f)

                    // Vinyl Base
                    drawCircle(color = Color(0xFF13171F), radius = radius, center = center)

                    // Concentric Grooves
                    for (g in 1..5) {
                        drawCircle(
                            color = Color(0xFF222B3A).copy(alpha = 0.7f),
                            radius = radius * (0.45f + g * 0.1f),
                            center = center,
                            style = Stroke(width = 0.9.dp.toPx())
                        )
                    }

                    // Specular Sheen (Vinyl Light Reflection Gradient)
                    drawCircle(
                        brush = Brush.sweepGradient(
                            listOf(
                                Color.Transparent,
                                Color.White.copy(alpha = 0.08f),
                                Color.Transparent,
                                Color.White.copy(alpha = 0.08f),
                                Color.Transparent
                            ),
                            center = center
                        ),
                        radius = radius,
                        center = center
                    )
                }

                // Center Circular Album Artwork / Generative Label
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .rotate(rotationAngle)
                        .clip(CircleShape)
                        .background(Color(activeTrack.colorHex)),
                    contentAlignment = Alignment.Center
                ) {
                    if (!coverUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = coverUrl,
                            contentDescription = "Album Cover",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        // Generative Stylized Label
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = "33⅓",
                                color = Color.Black.copy(alpha = 0.75f),
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "ЭХО",
                                color = Color.Black,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                    }

                    // Brass Spindle Core
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFE2E8F0))
                            .border(1.5.dp, Color(0xFF0F1420), CircleShape)
                    )
                }

                // Stylized Tonearm & Glowing Needle Overlay
                TonearmOverlay(isSpinning = isSpinning)
            }

            // 2. TRACK METADATA & POETIC TAGS (Big, Readable, Clean)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.Center
            ) {
                // Poetic Tag / Resonance Badge
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(Color(activeTrack.colorHex))
                    )
                    Text(
                        text = if (activeTrack.isSelf) "ВАШ ВИНИЛ В ЭФИРЕ" else "${activeTrack.compatibility}% РЕЗОНАНС • ${activeTrack.distanceLabel}",
                        color = Color(activeTrack.colorHex),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp,
                        maxLines = 1
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Big Track Title
                Text(
                    text = activeTrack.title,
                    color = Color.White,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                // Artist Name
                Text(
                    text = activeTrack.artist,
                    color = Color(0xFF38BDF8),
                    fontSize = 14.5.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(6.dp))

                // Poetic Persona Button
                Surface(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onOpenCuratorStory() },
                    color = Color(0xFF161E2D),
                    border = BorderStroke(0.6.dp, Color(0xFF25334A))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(text = "✦", color = Color(0xFF00F0FF), fontSize = 11.sp)
                        Text(
                            text = activeTrack.poeticPersona,
                            color = Color(0xFFE2E8F0),
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        // Lower Deck Section: 1-Click Launch Button + 5 Vector Resonance Glyphs
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // A. Primary 1-Click Music Launch Bar
            Button(
                onClick = onOpenInMusicApp,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00F0FF)),
                shape = RoundedCornerShape(14.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(text = "▶", color = Color.Black, fontSize = 14.sp)
                    Text(
                        text = "Слушать в плеере (${activeTrack.sourceApp})",
                        color = Color.Black,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // B. 5 Vector Atmospheric Resonance Glyphs Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val glyphs = listOf(
                    Triple("✦", "Резонанс", Color(0xFF00F0FF)),
                    Triple("✨", "Созвучие", Color(0xFFA855F7)),
                    Triple("🌙", "Ночь", Color(0xFF38BDF8)),
                    Triple("🌲", "Туман", Color(0xFF10B981)),
                    Triple("⚡", "Искра", Color(0xFFFFB800))
                )

                glyphs.forEach { (glyph, label, glyphColor) ->
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 3.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onSendReaction(glyph) },
                        color = Color(0xFF121824),
                        border = BorderStroke(0.8.dp, glyphColor.copy(alpha = 0.35f))
                    ) {
                        Column(
                            modifier = Modifier.padding(vertical = 6.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(text = glyph, color = glyphColor, fontSize = 16.sp)
                            Text(text = label, color = Color(0xFF94A3B8), fontSize = 9.sp)
                        }
                    }
                }
            }

            // C. Quick Return to Own Track
            if (!activeTrack.isSelf) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    TextButton(onClick = onSelectSelf) {
                        Text(
                            text = "Вернуться к моему винилу ♫",
                            color = Color(0xFF94A3B8),
                            fontSize = 11.5.sp
                        )
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------
// ANALOG TONEARM WITH GLOWING NEEDLE COMPOSABLE
// -------------------------------------------------------------
@Composable
fun TonearmOverlay(isSpinning: Boolean) {
    val tonearmAngle by animateFloatAsState(
        targetValue = if (isSpinning) 28f else 0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "TonearmMovement"
    )

    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .rotate(tonearmAngle)
    ) {
        val pivot = Offset(size.width * 0.88f, size.height * 0.12f)
        val needleTip = Offset(size.width * 0.52f, size.height * 0.72f)

        // Tonearm Pivot Base
        drawCircle(
            color = Color(0xFF334155),
            radius = 7.dp.toPx(),
            center = pivot
        )
        drawCircle(
            color = Color(0xFF64748B),
            radius = 3.5.dp.toPx(),
            center = pivot
        )

        // Metallic Tonearm Tube
        drawLine(
            color = Color(0xFFCBD5E1),
            start = pivot,
            end = needleTip,
            strokeWidth = 2.4.dp.toPx(),
            cap = StrokeCap.Round
        )

        // Cartridge Head & Glowing Stylus Tip
        drawCircle(
            color = Color(0xFF1E293B),
            radius = 4.dp.toPx(),
            center = needleTip
        )
        drawCircle(
            color = Color(0xFF00F0FF),
            radius = 2.2.dp.toPx(),
            center = needleTip
        )
    }
}

// -------------------------------------------------------------
// INTENT HELPER: OPEN 1-CLICK IN MUSIC APPS
// -------------------------------------------------------------
fun openMusicApp(context: Context, artist: String, title: String, sourceApp: String) {
    val query = "$artist $title".trim()
    if (query.isBlank() || artist.startsWith("Ожидание")) return

    try {
        val encodedQuery = URLEncoder.encode(query, "UTF-8")
        val isYandex = sourceApp.contains("yandex", ignoreCase = true) || sourceApp.contains("яндекс", ignoreCase = true)
        val isSpotify = sourceApp.contains("spotify", ignoreCase = true)

        val uri = when {
            isYandex -> Uri.parse("yandexmusic://search?text=$encodedQuery")
            isSpotify -> Uri.parse("spotify:search:$encodedQuery")
            else -> Uri.parse("https://music.yandex.ru/search?text=$encodedQuery")
        }

        val intent = Intent(Intent.ACTION_VIEW, uri).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    } catch (e: Exception) {
        // Fallback to browser search
        try {
            val webUri = Uri.parse("https://www.google.com/search?q=" + URLEncoder.encode("$query слушать", "UTF-8"))
            val browserIntent = Intent(Intent.ACTION_VIEW, webUri).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(browserIntent)
        } catch (e: Exception) {}
    }
}
