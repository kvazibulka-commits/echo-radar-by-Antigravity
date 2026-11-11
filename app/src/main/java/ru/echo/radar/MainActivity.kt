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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.window.Dialog
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
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
        EchoSoundManager.init(this)
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

    override fun onDestroy() {
        super.onDestroy()
        EchoAudioPlayer.stop()
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
// UNIFIED VINYL TRACK MODEL (SIGNALS & CAPSULES)
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
    val isSelf: Boolean = false,
    val coverUrl: String? = null,
    val isVenue: Boolean = false,
    val venueName: String = "",
    val minResonance: Int = 0,
    val secretPerk: String = "",
    val isUnlocked: Boolean = true
)

fun PlayingTrack.toVinylTrack(): VinylTrack = VinylTrack(
    id = "self_user_vinyl",
    title = title,
    artist = artist,
    sourceApp = sourceApp,
    distanceLabel = "Вы • В эфире",
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
    isGhost = isGhost,
    coverUrl = coverUrl
)

fun EchoCapsule.toVinylTrack(): VinylTrack = VinylTrack(
    id = id,
    title = title,
    artist = artist,
    sourceApp = if (isVenue && venueName.isNotBlank()) venueName else "Тайник",
    distanceLabel = distanceLabel,
    poeticPersona = personaTitle,
    observation = secretMessage,
    story = if (isVenue) "Звуковой паспорт заведения • $zoneName" else "Музыкальная капсула, оставленная в зоне: $zoneName",
    compatibility = compatibility,
    angleDeg = angleDeg,
    ringFactor = ringFactor,
    colorHex = if (isVenue) 0xFFFFB800L else 0xFFFF9900L,
    isCapsule = true,
    isVenue = isVenue,
    venueName = venueName,
    minResonance = minResonance,
    secretPerk = secretPerk,
    isUnlocked = isUnlocked
)

// -------------------------------------------------------------
// MAIN SCREEN: VINYL CITY MAP + TACTILE TURNTABLE DECK
// -------------------------------------------------------------
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun VinylCityMainScreen() {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current

    val currentPlayingTrack by EchoStateRepository.currentTrack.collectAsState()
    val nearbySignals by EchoStateRepository.nearbySignals.collectAsState()
    val capsules by EchoStateRepository.capsules.collectAsState()
    val isPresenceActive by EchoStateRepository.presenceActive.collectAsState()
    val connectionStatusStr by EchoStateRepository.connectionStatus.collectAsState()
    val incomingReaction by EchoStateRepository.incomingReaction.collectAsState()
    val serverHost by EchoStateRepository.serverHost.collectAsState()

    // Audio preview player states
    val isAudioPlaying by EchoAudioPlayer.isPlaying.collectAsState()
    val isAudioLoading by EchoAudioPlayer.isLoading.collectAsState()

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

    LaunchedEffect(allVinyls, currentPlayingTrack) {
        if (selectedVinyl == null) {
            val pcVinyl = allVinyls.find { it.id == "pc_studio_node" || it.sourceApp.contains("ПК") || it.sourceApp.contains("Windows") }
            selectedVinyl = pcVinyl ?: (if (allVinyls.isNotEmpty()) allVinyls.first() else currentPlayingTrack.toVinylTrack())
        } else if (selectedVinyl?.isSelf == true) {
            selectedVinyl = currentPlayingTrack.toVinylTrack()
        }
    }

    val activeTrack = selectedVinyl ?: currentPlayingTrack.toVinylTrack()

    // Active Album Artwork and Audio Preview URL loaded via iTunes Search API
    var trackMediaInfo by remember { mutableStateOf(TrackMediaInfo()) }

    LaunchedEffect(activeTrack.artist, activeTrack.title, activeTrack.coverUrl) {
        trackMediaInfo = if (!activeTrack.coverUrl.isNullOrBlank()) {
            TrackMediaInfo(coverUrl = activeTrack.coverUrl)
        } else {
            TrackMediaInfo()
        }
        if (!activeTrack.artist.startsWith("Ожидание") && activeTrack.artist.isNotBlank()) {
            val info = AlbumArtResolver.getMediaInfo(activeTrack.artist, activeTrack.title, activeTrack.coverUrl)
            trackMediaInfo = info
        }
    }

    // Manual platter spinning state or auto-spin when audio preview is active
    var manualSpinning by remember { mutableStateOf(true) }
    val isPlatterSpinning = manualSpinning || isAudioPlaying

    // Dialog states
    var showServerSettingsDialog by remember { mutableStateOf(false) }
    var showManualTrackDialog by remember { mutableStateOf(false) }
    var showDropCapsuleDialog by remember { mutableStateOf(false) }
    var showCuratorObservationDialog by remember { mutableStateOf(false) }
    var showCoverZoomDialog by remember { mutableStateOf(false) }
    var showChronicleDialog by remember { mutableStateOf(false) }
    var hasSentThankYouToTarget by remember { mutableStateOf<String?>(null) }

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

    // Battery-optimized infinite transitions:
    // Platter animation only updates when spinning, saving GPU/CPU when idle!
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
        initialValue = 0.85f,
        targetValue = 1.15f,
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
                Text(
                    text = "Э  Х  О",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 6.sp
                )

                // Presence / Status Pill (Continuous smooth border, no corner gaps!)
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = Color(0xFF111622).copy(alpha = 0.95f),
                    border = BorderStroke(1.dp, if (isConnected) Color(0xFF1E283C) else Color(0xFFE11D48)),
                    modifier = Modifier.combinedClickable(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            showManualTrackDialog = true
                        },
                        onLongClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            showServerSettingsDialog = true
                        }
                    )
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
                    shape = RoundedCornerShape(18.dp),
                    color = if (isPresenceActive) Color(0xFF00F0FF).copy(alpha = 0.12f) else Color(0xFF1E2430),
                    border = BorderStroke(
                        1.dp,
                        if (isPresenceActive) Color(0xFF00F0FF).copy(alpha = 0.5f) else Color(0xFF334155)
                    ),
                    modifier = Modifier.clickable {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        EchoStateRepository.togglePresence()
                    }
                ) {
                    Text(
                        text = if (isPresenceActive) "В эфире" else "Невидимка",
                        color = if (isPresenceActive) Color(0xFF00F0FF) else Color(0xFF94A3B8),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 11.dp, vertical = 5.dp)
                    )
                }
            }

            // =========================================================
            // 2. VINYL CITY MAP (56% Viewport Height)
            // =========================================================
            Box(
                modifier = Modifier
                    .weight(1.35f)
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
                        EchoSoundManager.playNeedleDrop()
                        if (vinyl.isCapsule && vinyl.isUnlocked) {
                            EchoSoundManager.playUnlockChime()
                        }
                        EchoAudioPlayer.stop()
                        selectedVinyl = vinyl
                        manualSpinning = true
                    },
                    onCenterUserClicked = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        EchoSoundManager.playNeedleDrop()
                        EchoAudioPlayer.stop()
                        selectedVinyl = currentPlayingTrack.toVinylTrack()
                        manualSpinning = true
                    }
                )

                // Floating Action: Chronicle / History of Echoes (Symmetric Left Bottom)
                val hasUnreadChronicle by EchoChronicleManager.hasUnread
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = Color(0xFF111622).copy(alpha = 0.95f),
                    border = BorderStroke(1.dp, if (hasUnreadChronicle) Color(0xFF00F0FF) else Color(0xFF00F0FF).copy(alpha = 0.65f)),
                    shadowElevation = 6.dp,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 16.dp, bottom = 12.dp)
                        .clickable {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            EchoSoundManager.playTick()
                            showChronicleDialog = true
                        }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(text = "📜", fontSize = 13.sp)
                        Text(
                            text = "Летопись",
                            color = Color(0xFF00F0FF),
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        if (hasUnreadChronicle) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF00F0FF))
                            )
                        }
                    }
                }

                // Floating Action: Drop Capsule (Continuous round border, no corner gaps!)
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = Color(0xFF111622).copy(alpha = 0.95f),
                    border = BorderStroke(1.dp, Color(0xFFFFB800).copy(alpha = 0.75f)),
                    shadowElevation = 6.dp,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 16.dp, bottom = 12.dp)
                        .clickable {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            EchoSoundManager.playTick()
                            showDropCapsuleDialog = true
                        }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(text = "⏳", fontSize = 13.sp)
                        Text(
                            text = "Оставить тайник",
                            color = Color(0xFFFFB800),
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            // =========================================================
            // 3. TACTILE TURNTABLE DECK (44% Viewport Height in Thumb Zone)
            // =========================================================
            Surface(
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                color = Color(0xFF0D121B),
                border = BorderStroke(1.dp, Color(0xFF1E283C)),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                TurntableDeckView(
                    activeTrack = activeTrack,
                    mediaInfo = trackMediaInfo,
                    isSpinning = isPlatterSpinning,
                    rotationAngle = { if (isPlatterSpinning) platterRotationAngle else 0f },
                    isAudioPlaying = isAudioPlaying,
                    isAudioLoading = isAudioLoading,
                    onToggleSpin = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        manualSpinning = !manualSpinning
                    },
                    onToggleAudioPreview = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        EchoAudioPlayer.toggle(activeTrack.artist, activeTrack.title, trackMediaInfo.previewAudioUrl)
                    },
                    onOpenCoverZoom = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        showCoverZoomDialog = true
                    },
                    onOpenInMusicApp = {
                        openMusicApp(context, activeTrack.artist, activeTrack.title, activeTrack.sourceApp)
                    },
                    isThanked = hasSentThankYouToTarget == activeTrack.id,
                    isSavedInChronicle = EchoChronicleManager.isTrackSaved(activeTrack.artist, activeTrack.title),
                    onSendThankYou = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        hasSentThankYouToTarget = activeTrack.id
                        sonarFocusTargetId = activeTrack.id
                        EchoStateRepository.sendReaction(activeTrack.id, "✦", "Спасибо за созвучие!") { ok ->
                            reactionSentFeedback = if (ok) "Волна ушла: «Спасибо за созвучие!» ✨" else "Ошибка отправки"
                        }
                    },
                    onSaveToChronicle = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        val added = EchoChronicleManager.saveTrack(activeTrack)
                        reactionSentFeedback = if (added) "Пластинка сохранена в летопись ночи 📜" else "Уже в вашей летописи"
                    },
                    onOpenCuratorStory = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        showCuratorObservationDialog = true
                    },
                    onSelectSelf = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        EchoAudioPlayer.stop()
                        selectedVinyl = currentPlayingTrack.toVinylTrack()
                        manualSpinning = true
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
                shape = RoundedCornerShape(18.dp),
                color = Color(0xFF141B29),
                border = BorderStroke(1.2.dp, Color(0xFF00F0FF)),
                shadowElevation = 12.dp,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 56.dp, start = 16.dp, end = 16.dp)
                    .clickable {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        EchoStateRepository.dismissIncomingReaction()
                    }
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

    // A. ENLARGED ALBUM COVER ZOOM MODAL (HIGH-RES VIEW & AUDIO PREVIEW)
    if (showCoverZoomDialog) {
        AlertDialog(
            onDismissRequest = { showCoverZoomDialog = false },
            containerColor = Color(0xFF0F1522),
            title = null,
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Big High-Res Album Cover with Vinyl Sleeve Aesthetics
                    Box(
                        modifier = Modifier
                            .size(240.dp)
                            .shadow(16.dp, RoundedCornerShape(16.dp))
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color(0xFF182030)),
                        contentAlignment = Alignment.Center
                    ) {
                        val effectiveCover = activeTrack.coverUrl ?: trackMediaInfo.coverUrl
                        if (!effectiveCover.isNullOrBlank()) {
                            AsyncImage(
                                model = effectiveCover,
                                contentDescription = "Full Album Cover",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Text(text = "♫", color = Color(activeTrack.colorHex), fontSize = 48.sp)
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "33⅓ RPM • ЭХО",
                                    color = Color(0xFF94A3B8),
                                    fontSize = 12.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }

                    // Album Name & Artist Metadata
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = activeTrack.title,
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = activeTrack.artist,
                            color = Color(0xFF38BDF8),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                            textAlign = TextAlign.Center
                        )
                        trackMediaInfo.albumName?.let { alb ->
                            Text(
                                text = "Альбом: $alb",
                                color = Color(0xFF64748B),
                                fontSize = 12.sp,
                                fontStyle = FontStyle.Italic,
                                textAlign = TextAlign.Center
                            )
                        }
                    }

                    // Online Audio Preview Button (Inside zoom dialog)
                    if (!trackMediaInfo.previewAudioUrl.isNullOrBlank()) {
                        Button(
                            onClick = {
                                EchoAudioPlayer.toggle(activeTrack.artist, activeTrack.title, trackMediaInfo.previewAudioUrl)
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isAudioPlaying) Color(0xFF00F0FF) else Color(0xFF1E293B)
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = if (isAudioLoading) "⏳ Загрузка..." else if (isAudioPlaying) "⏸ Пауза фрагмента" else "▶ Слушать онлайн фрагмент (30 сек)",
                                color = if (isAudioPlaying) Color.Black else Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        openMusicApp(context, activeTrack.artist, activeTrack.title, activeTrack.sourceApp)
                        showCoverZoomDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00F0FF)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("В плеер", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCoverZoomDialog = false }) {
                    Text("Закрыть", color = Color(0xFF94A3B8))
                }
            }
        )
    }

    // B. Curator Observation & Story Modal
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

    // Chronicle & Echo History Modal (Летопись находок и откликов)
    if (showChronicleDialog) {
        EchoChronicleDialog(
            onDismissRequest = { showChronicleDialog = false },
            onOpenInMusicApp = { record ->
                openMusicApp(context, record.artist, record.title, record.sourceApp)
            }
        )
    }

    // C. Manual Track Input Modal
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

    // D. Leave Musical Capsule Modal
    if (showDropCapsuleDialog) {
        var capArtist by remember { mutableStateOf(currentPlayingTrack.artist.takeIf { !it.startsWith("Ожидание") } ?: "") }
        var capTitle by remember { mutableStateOf(currentPlayingTrack.title.takeIf { !it.startsWith("Включите") } ?: "") }
        var capNote by remember { mutableStateOf("") }
        var isVenueMode by remember { mutableStateOf(false) }
        var venueNameInput by remember { mutableStateOf("") }
        var minResonanceInput by remember { mutableStateOf(75) }
        var secretPerkInput by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showDropCapsuleDialog = false },
            containerColor = Color(0xFF111622),
            title = {
                Text(
                    text = if (isVenueMode) "🏛️ Звуковой тайник заведения" else "⏳ Оставить виниловый тайник",
                    color = Color(0xFFFFB800),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = if (isVenueMode) "Тайник будет заперт акустическим паролем (Proof-of-Music) и откроется только при созвучии." else "Тайник останется на карте города для других ночных слушателей.",
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
                        maxLines = 2,
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Proof-of-Music / Venue Toggle
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { isVenueMode = !isVenueMode }
                            .padding(vertical = 4.dp)
                    ) {
                        Checkbox(
                            checked = isVenueMode,
                            onCheckedChange = { isVenueMode = it },
                            colors = CheckboxDefaults.colors(
                                checkedColor = Color(0xFFFFB800),
                                uncheckedColor = Color(0xFF64748B)
                            )
                        )
                        Text(
                            text = "Акустический пароль (замок локации)",
                            color = Color(0xFFE2E8F0),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    if (isVenueMode) {
                        OutlinedTextField(
                            value = venueNameInput,
                            onValueChange = { venueNameInput = it },
                            label = { Text("Имя локации / заведения", color = Color(0xFF94A3B8)) },
                            placeholder = { Text("Виниловый бар / Кофейня", color = Color(0xFF475569)) },
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
                            value = secretPerkInput,
                            onValueChange = { secretPerkInput = it },
                            label = { Text("Секретный подарок за созвучие", color = Color(0xFF94A3B8)) },
                            placeholder = { Text("Фильтр-кофе со скидкой 20%", color = Color(0xFF475569)) },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = Color(0xFFFFB800),
                                unfocusedBorderColor = Color(0xFF334155)
                            ),
                            maxLines = 2,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Порог резонанса для замка:", color = Color(0xFF94A3B8), fontSize = 11.5.sp)
                            Text("$minResonanceInput%", color = Color(0xFFFFB800), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                        Slider(
                            value = minResonanceInput.toFloat(),
                            onValueChange = { minResonanceInput = it.toInt() },
                            valueRange = 50f..95f,
                            steps = 8,
                            colors = SliderDefaults.colors(
                                thumbColor = Color(0xFFFFB800),
                                activeTrackColor = Color(0xFFFFB800),
                                inactiveTrackColor = Color(0xFF334155)
                            )
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (capArtist.isNotBlank() && capTitle.isNotBlank()) {
                            EchoSoundManager.playTick()
                            EchoStateRepository.dropCapsule(
                                artist = capArtist.trim(),
                                title = capTitle.trim(),
                                secretMessage = capNote.trim().ifBlank { "Музыкальный след ночного странника." },
                                isVenue = isVenueMode,
                                venueName = venueNameInput.trim(),
                                minResonance = if (isVenueMode) minResonanceInput else 0,
                                secretPerk = if (isVenueMode) secretPerkInput.trim() else ""
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

    // E. Server Host & Token Settings Modal
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

            // A. PROCEDURAL NIGHT CITY MAP
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

            // 2. City Street Grid (Avenues & Arteries)
            val streetColor = Color(0xFF141B28)
            val avenueColor = Color(0xFFFFB800).copy(alpha = 0.08f)

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
            ringRadii.forEach { radius ->
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
                    drawLine(
                        color = chordColor.copy(alpha = 0.22f),
                        start = center,
                        end = targetOffset,
                        strokeWidth = 6.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                    drawLine(
                        color = chordColor.copy(alpha = 0.85f),
                        start = center,
                        end = targetOffset,
                        strokeWidth = 1.8.dp.toPx(),
                        cap = StrokeCap.Round
                    )

                    val photonPos = Offset(
                        center.x + (targetOffset.x - center.x) * laserPhotonProgress,
                        center.y + (targetOffset.y - center.y) * laserPhotonProgress
                    )
                    drawCircle(color = Color.White, radius = 3.5.dp.toPx(), center = photonPos)
                    drawCircle(color = chordColor.copy(alpha = 0.5f), radius = 8.dp.toPx(), center = photonPos)
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
                val isPc = vinyl.id == "pc_studio_node" || vinyl.sourceApp.contains("ПК") || vinyl.sourceApp.contains("Windows")
                val vinylBaseColor = if (isPc) Color(0xFF00F0FF) else Color(vinyl.colorHex)

                // Neon puddle on asphalt (enhanced for PC)
                drawCircle(
                    color = vinylBaseColor.copy(alpha = if (isFocused) 0.45f else if (isPc) 0.35f else 0.15f),
                    radius = (if (isFocused) 26.dp else if (isPc) 22.dp else 18.dp).toPx() * neonPulse,
                    center = pos
                )

                if (isPc) {
                    drawCircle(
                        color = Color(0xFF00F0FF).copy(alpha = 0.55f),
                        radius = 20.dp.toPx() * neonPulse,
                        center = pos,
                        style = Stroke(width = 1.2.dp.toPx())
                    )
                }

                // Mini Vinyl Disc
                val discRadius = (if (isFocused) 16.dp else if (isPc) 14.dp else 12.dp).toPx()

                drawCircle(color = Color(0xFF141720), radius = discRadius, center = pos)
                drawCircle(
                    color = if (isFocused) vinylBaseColor else Color(0xFF2D3748),
                    radius = discRadius,
                    center = pos,
                    style = Stroke(width = if (isFocused) 1.8.dp.toPx() else 1.dp.toPx())
                )
                drawCircle(
                    color = Color(0xFF222B3D),
                    radius = discRadius * 0.65f,
                    center = pos,
                    style = Stroke(width = 0.8.dp.toPx())
                )
                drawCircle(color = vinylBaseColor, radius = discRadius * 0.38f, center = pos)
                drawCircle(color = Color.Black, radius = discRadius * 0.12f, center = pos)

                // Proof-of-Music Capsule & Venue Lock Indicators
                if (vinyl.isCapsule) {
                    val amberColor = Color(0xFFFFB800)
                    if (vinyl.isVenue) {
                        // Venue ring
                        drawCircle(
                            color = amberColor.copy(alpha = 0.45f * neonPulse),
                            radius = discRadius * 1.55f,
                            center = pos,
                            style = Stroke(width = 1.4.dp.toPx())
                        )
                        if (!vinyl.isUnlocked) {
                            // Locked venue indicator: crimson-amber lock badge
                            drawCircle(
                                color = Color(0xFFFF5533),
                                radius = 3.5.dp.toPx(),
                                center = Offset(pos.x + discRadius * 0.95f, pos.y - discRadius * 0.95f)
                            )
                        } else {
                            // Unlocked venue indicator: golden star badge
                            drawCircle(
                                color = Color(0xFFFFD700),
                                radius = 4.dp.toPx() * neonPulse,
                                center = Offset(pos.x + discRadius * 0.95f, pos.y - discRadius * 0.95f)
                            )
                        }
                    } else {
                        // Regular capsule: subtle golden halo
                        drawCircle(
                            color = amberColor.copy(alpha = 0.35f),
                            radius = discRadius * 1.35f,
                            center = pos,
                            style = Stroke(width = 1.dp.toPx())
                        )
                    }
                }
            }

            // E. USER CENTER POSITION (Core Turntable Beacon)
            val userCoreColor = Color(0xFF00F0FF)
            drawCircle(
                color = userCoreColor.copy(alpha = 0.18f),
                radius = 28.dp.toPx() * neonPulse,
                center = center
            )
            drawCircle(color = Color(0xFF0E1420), radius = 16.dp.toPx(), center = center)
            drawCircle(color = userCoreColor, radius = 16.dp.toPx(), center = center, style = Stroke(width = 2.dp.toPx()))
            drawCircle(color = userCoreColor, radius = 6.dp.toPx(), center = center)
        }
    }
}

// -------------------------------------------------------------
// TACTILE TURNTABLE DECK VIEW (THUMB ZONE)
// -------------------------------------------------------------
@Composable
fun TurntableDeckView(
    activeTrack: VinylTrack,
    mediaInfo: TrackMediaInfo,
    isSpinning: Boolean,
    rotationAngle: () -> Float,
    isAudioPlaying: Boolean,
    isAudioLoading: Boolean,
    isThanked: Boolean,
    isSavedInChronicle: Boolean,
    onToggleSpin: () -> Unit,
    onToggleAudioPreview: () -> Unit,
    onOpenCoverZoom: () -> Unit,
    onOpenInMusicApp: () -> Unit,
    onSendThankYou: () -> Unit,
    onSaveToChronicle: () -> Unit,
    onOpenCuratorStory: () -> Unit,
    onSelectSelf: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 10.dp),
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
            // 1. ROTATING VINYL PLATTER (GPU graphicsLayer rotation — Zero recomposition!)
            Box(
                modifier = Modifier
                    .size(136.dp)
                    .clickable {
                        if (!mediaInfo.previewAudioUrl.isNullOrBlank()) {
                            onToggleAudioPreview()
                        } else {
                            onOpenCoverZoom()
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                // Platter Well Shadow
                Box(
                    modifier = Modifier
                        .size(134.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF07090E))
                )

                // Spinning Vinyl Platter (GPU RenderNode transform)
                Canvas(
                    modifier = Modifier
                        .size(128.dp)
                        .graphicsLayer { rotationZ = rotationAngle() }
                ) {
                    val radius = size.minDimension / 2f
                    val center = Offset(size.width / 2f, size.height / 2f)

                    // Dark grooved body
                    drawCircle(color = Color(0xFF13171F), radius = radius, center = center)

                    for (g in 1..5) {
                        drawCircle(
                            color = Color(0xFF222B3A).copy(alpha = 0.7f),
                            radius = radius * (0.45f + g * 0.1f),
                            center = center,
                            style = Stroke(width = 0.9.dp.toPx())
                        )
                    }

                    // Specular light sheen gradient
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

                // Center Album Artwork Label (Tap to Zoom!)
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .graphicsLayer { rotationZ = rotationAngle() }
                        .clip(CircleShape)
                        .background(Color(activeTrack.colorHex))
                        .clickable { onOpenCoverZoom() },
                    contentAlignment = Alignment.Center
                ) {
                    val effectiveCover = activeTrack.coverUrl ?: mediaInfo.coverUrl
                    if (!effectiveCover.isNullOrBlank()) {
                        AsyncImage(
                            model = effectiveCover,
                            contentDescription = "Album Cover (Tap to Zoom)",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
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

                    // Metallic Center Spindle
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
                    fontSize = 17.5.sp,
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
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF161E2D),
                    border = BorderStroke(0.8.dp, Color(0xFF25334A)),
                    modifier = Modifier.clickable { onOpenCuratorStory() }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
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

        // Lower Deck Section: Audio Preview / 1-Click Launch / Proof-of-Music / Reactions
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // A0. PROOF-OF-MUSIC / VENUE CAPSULE CARD
            if (activeTrack.isCapsule) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = if (activeTrack.isUnlocked) Color(0xFF241C0F) else Color(0xFF221316),
                    border = BorderStroke(1.dp, if (activeTrack.isUnlocked) Color(0xFFFFB800) else Color(0xFFFF5533).copy(alpha = 0.85f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = if (activeTrack.isUnlocked) "🔓 ✦" else "🔒",
                                fontSize = 12.5.sp
                            )
                            Text(
                                text = if (activeTrack.isVenue) {
                                    if (activeTrack.isUnlocked) "АКУСТИЧЕСКИЙ ПАРОЛЬ СОВПАЛ" else "ЗВУКОВОЙ ЗАМОК ЗАПЕРТ"
                                } else {
                                    "ВИНИЛОВЫЙ ТАЙНИК КВАРТАЛА"
                                },
                                color = if (activeTrack.isUnlocked) Color(0xFFFFB800) else Color(0xFFFF7755),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.6.sp
                            )
                            if (activeTrack.minResonance > 0) {
                                Spacer(modifier = Modifier.weight(1f))
                                Text(
                                    text = "Порог: ${activeTrack.minResonance}%",
                                    color = Color(0xFF94A3B8),
                                    fontSize = 10.sp
                                )
                            }
                        }
                        if (activeTrack.isVenue && activeTrack.secretPerk.isNotBlank()) {
                            if (activeTrack.isUnlocked) {
                                Text(
                                    text = "🎁 ${activeTrack.secretPerk}",
                                    color = Color(0xFFFFE082),
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            } else {
                                Text(
                                    text = "Включите созвучный трек (резонанс ≥ ${activeTrack.minResonance}%), чтобы открыть замок и комплимент локации.",
                                    color = Color(0xFF94A3B8),
                                    fontSize = 11.sp
                                )
                            }
                        } else if (activeTrack.observation.isNotBlank()) {
                            Text(
                                text = "«${activeTrack.observation}»",
                                color = Color(0xFFE2E8F0),
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }

            // A. COMPACT UNIFIED ACTION BAR (Slim, elegant, non-intrusive)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // If 30-sec in-app preview is available: compact pill
                if (!mediaInfo.previewAudioUrl.isNullOrBlank()) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (isAudioPlaying) Color(0xFF00F0FF).copy(alpha = 0.16f) else Color(0xFF141926),
                        border = BorderStroke(1.dp, if (isAudioPlaying) Color(0xFF00F0FF) else Color(0xFF263346)),
                        modifier = Modifier
                            .weight(1f)
                            .height(36.dp)
                            .clickable { onToggleAudioPreview() }
                    ) {
                        Row(
                            modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = if (isAudioLoading) "⏳ Загрузка..." else if (isAudioPlaying) "⏸ 30 сек" else "♫ Превью 30с",
                                color = if (isAudioPlaying) Color(0xFF00F0FF) else Color(0xFFE2E8F0),
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                // Discreet link to external player (VK / Яндекс / Spotify)
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF141926),
                    border = BorderStroke(1.dp, Color(0xFF263346)),
                    modifier = Modifier
                        .then(if (!mediaInfo.previewAudioUrl.isNullOrBlank()) Modifier.weight(1f) else Modifier.fillMaxWidth())
                        .height(36.dp)
                        .clickable { onOpenInMusicApp() }
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "↗ ${activeTrack.sourceApp}",
                            color = Color(0xFF38BDF8),
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // B. TACTILE HARMONY PAIR (Тактильная пара клавиш: «Спасибо за созвучие» + «В летопись ночи»)
            if (!activeTrack.isSelf) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Клавиша 1: «Спасибо за созвучие» (Кивнуть сквозь темноту)
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = if (isThanked) Color(0xFF00F0FF).copy(alpha = 0.16f) else Color(0xFF141926),
                        border = BorderStroke(
                            1.dp,
                            if (isThanked) Color(0xFF00F0FF) else Color(0xFFA855F7).copy(alpha = 0.65f)
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp)
                            .clickable { onSendThankYou() }
                    ) {
                        Column(
                            modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp, vertical = 6.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Text(
                                    text = if (isThanked) "✓" else "∿",
                                    color = if (isThanked) Color(0xFF00F0FF) else Color(0xFFA855F7),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = if (isThanked) "Волна ушла ✨" else "Спасибо за звук",
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            Text(
                                text = if (isThanked) "Кивок сквозь ночь принят" else "Кивнуть сквозь темноту",
                                color = Color(0xFF94A3B8),
                                fontSize = 9.sp
                            )
                        }
                    }

                    // Клавиша 2: «В летопись ночи» (Сохранить находку)
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = if (isSavedInChronicle) Color(0xFFFFB800).copy(alpha = 0.16f) else Color(0xFF141926),
                        border = BorderStroke(
                            1.dp,
                            if (isSavedInChronicle) Color(0xFFFFB800) else Color(0xFF38BDF8).copy(alpha = 0.65f)
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp)
                            .clickable { onSaveToChronicle() }
                    ) {
                        Column(
                            modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp, vertical = 6.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Text(
                                    text = if (isSavedInChronicle) "✓" else "📜",
                                    color = if (isSavedInChronicle) Color(0xFFFFB800) else Color(0xFF38BDF8),
                                    fontSize = 12.sp
                                )
                                Text(
                                    text = if (isSavedInChronicle) "В летописи" else "В летопись ночи",
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            Text(
                                text = if (isSavedInChronicle) "Сохранено на память" else "Сохранить находку себе",
                                color = Color(0xFF94A3B8),
                                fontSize = 9.sp
                            )
                        }
                    }
                }
            } else {
                // Собственный винил пользователя
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF101622),
                    border = BorderStroke(0.8.dp, Color(0xFF1E283C)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "✦ Ваш винил транслируется в эфир квартала",
                            color = Color(0xFF00F0FF).copy(alpha = 0.85f),
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            // C. Quick Return to Own Track
            if (!activeTrack.isSelf) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    TextButton(
                        onClick = onSelectSelf,
                        contentPadding = PaddingValues(0.dp),
                        modifier = Modifier.height(28.dp)
                    ) {
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
            .graphicsLayer { rotationZ = tonearmAngle }
    ) {
        val pivot = Offset(size.width * 0.88f, size.height * 0.12f)
        val needleTip = Offset(size.width * 0.52f, size.height * 0.72f)

        // Tonearm Pivot Base
        drawCircle(color = Color(0xFF334155), radius = 7.dp.toPx(), center = pivot)
        drawCircle(color = Color(0xFF64748B), radius = 3.5.dp.toPx(), center = pivot)

        // Metallic Tonearm Tube
        drawLine(
            color = Color(0xFFCBD5E1),
            start = pivot,
            end = needleTip,
            strokeWidth = 2.4.dp.toPx(),
            cap = StrokeCap.Round
        )

        // Cartridge Head & Glowing Stylus Tip
        drawCircle(color = Color(0xFF1E293B), radius = 4.dp.toPx(), center = needleTip)
        drawCircle(color = Color(0xFF00F0FF), radius = 2.2.dp.toPx(), center = needleTip)
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
        val isVk = sourceApp.contains("vk", ignoreCase = true) || sourceApp.contains("вк", ignoreCase = true)

        val uri = when {
            isYandex -> Uri.parse("yandexmusic://search?text=$encodedQuery")
            isSpotify -> Uri.parse("spotify:search:$encodedQuery")
            isVk -> Uri.parse("vk://audio?q=$encodedQuery")
            else -> Uri.parse("https://music.yandex.ru/search?text=$encodedQuery")
        }

        val intent = Intent(Intent.ACTION_VIEW, uri).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    } catch (e: Exception) {
        try {
            val webUri = Uri.parse("https://www.google.com/search?q=" + URLEncoder.encode("$query слушать", "UTF-8"))
            val browserIntent = Intent(Intent.ACTION_VIEW, webUri).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(browserIntent)
        } catch (e: Exception) {}
    }
}


// -------------------------------------------------------------
// CHRONICLE & HISTORY DIALOG (ЛЕТОПИСЬ ЭФИРА И ОТКЛИКОВ)
// -------------------------------------------------------------
fun calculateTimeAgo(timestampSec: Long): String {
    val now = System.currentTimeMillis() / 1000
    val diff = now - timestampSec
    return when {
        diff < 60 -> "только что"
        diff < 3600 -> "${maxOf(1L, diff / 60)} мин назад"
        diff < 86400 -> "${diff / 3600} ч назад"
        else -> "${diff / 86400} дн назад"
    }
}

@Composable
fun EchoChronicleDialog(
    onDismissRequest: () -> Unit,
    onOpenInMusicApp: (ChronicleTrackRecord) -> Unit
) {
    var selectedTab by remember { mutableStateOf(0) }
    val savedTracks = EchoChronicleManager.savedTracks
    val incomingEchoes = EchoChronicleManager.incomingEchoes

    LaunchedEffect(Unit) {
        EchoChronicleManager.markAllRead()
    }

    Dialog(onDismissRequest = onDismissRequest) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = Color(0xFF0E1420),
            border = BorderStroke(1.2.dp, Color(0xFF00F0FF).copy(alpha = 0.65f)),
            shadowElevation = 16.dp,
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.78f)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(18.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(text = "📜", fontSize = 18.sp)
                        Column {
                            Text(
                                text = "Летопись эфира",
                                color = Color.White,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Эхо ночных встреч и находок",
                                color = Color(0xFF94A3B8),
                                fontSize = 11.sp
                            )
                        }
                    }
                    IconButton(onClick = onDismissRequest) {
                        Text(text = "✕", color = Color(0xFF94A3B8), fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Tabs Selector
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0xFF141C2B))
                        .padding(3.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(11.dp))
                            .background(if (selectedTab == 0) Color(0xFF00F0FF).copy(alpha = 0.2f) else Color.Transparent)
                            .clickable { selectedTab = 0 }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Отклики (${incomingEchoes.size})",
                            color = if (selectedTab == 0) Color(0xFF00F0FF) else Color(0xFF94A3B8),
                            fontSize = 12.sp,
                            fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Medium
                        )
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(11.dp))
                            .background(if (selectedTab == 1) Color(0xFFFFB800).copy(alpha = 0.2f) else Color.Transparent)
                            .clickable { selectedTab = 1 }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Мои находки (${savedTracks.size})",
                            color = if (selectedTab == 1) Color(0xFFFFB800) else Color(0xFF94A3B8),
                            fontSize = 12.sp,
                            fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Medium
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Tab Content
                if (selectedTab == 0) {
                    if (incomingEchoes.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(text = "🌌", fontSize = 32.sp)
                                Text(
                                    text = "В эфире пока тихо",
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "Когда кто-то в квартале поймает вашу волну и поблагодарит за музыку, здесь зажгутся отклики созвучия.",
                                    color = Color(0xFF94A3B8),
                                    fontSize = 11.5.sp,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(horizontal = 20.dp)
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(incomingEchoes) { echo ->
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = Color(0xFF141B28),
                                    border = BorderStroke(0.8.dp, Color(0xFF25334A)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(36.dp)
                                                .clip(CircleShape)
                                                .background(Color(0xFF00F0FF).copy(alpha = 0.15f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(text = echo.symbol, color = Color(0xFF00F0FF), fontSize = 16.sp)
                                        }
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = echo.message,
                                                color = Color.White,
                                                fontSize = 12.5.sp,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                            val timeAgo = calculateTimeAgo(echo.timeSec)
                                            Text(
                                                text = "От: ${echo.senderId.take(12)} • $timeAgo",
                                                color = Color(0xFF94A3B8),
                                                fontSize = 10.sp
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else {
                    if (savedTracks.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(text = "📻", fontSize = 32.sp)
                                Text(
                                    text = "Летопись пуста",
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "Нажмите «В летопись ночи» на пластинке любого слушателя, чтобы сохранить музыку в свой закрытый архив находок.",
                                    color = Color(0xFF94A3B8),
                                    fontSize = 11.5.sp,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(horizontal = 20.dp)
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(savedTracks) { track ->
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = Color(0xFF141B28),
                                    border = BorderStroke(0.8.dp, Color(0xFFFFB800).copy(alpha = 0.35f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        if (!track.coverUrl.isNullOrBlank()) {
                                            AsyncImage(
                                                model = track.coverUrl,
                                                contentDescription = null,
                                                modifier = Modifier
                                                    .size(42.dp)
                                                    .clip(RoundedCornerShape(8.dp)),
                                                contentScale = ContentScale.Crop
                                            )
                                        } else {
                                            Box(
                                                modifier = Modifier
                                                    .size(42.dp)
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background(Color(0xFF1E283C)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(text = "♫", color = Color(0xFFFFB800), fontSize = 16.sp)
                                            }
                                        }
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = track.title,
                                                color = Color.White,
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = track.artist,
                                                color = Color(0xFF38BDF8),
                                                fontSize = 11.5.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = "${track.distanceLabel} • ${track.resonancePercent}% резонанс",
                                                color = Color(0xFF94A3B8),
                                                fontSize = 9.5.sp
                                            )
                                        }
                                        IconButton(
                                            onClick = { onOpenInMusicApp(track) },
                                            modifier = Modifier.size(36.dp)
                                        ) {
                                            Text(text = "▶", color = Color(0xFF00F0FF), fontSize = 15.sp)
                                        }
                                        IconButton(
                                            onClick = { EchoChronicleManager.removeTrack(track.id) },
                                            modifier = Modifier.size(30.dp)
                                        ) {
                                            Text(text = "✕", color = Color(0xFF64748B), fontSize = 12.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
