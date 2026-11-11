package ru.echo.radar

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
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
import androidx.compose.ui.graphics.PathEffect
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
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.net.URLEncoder
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

// =============================================================
// DESIGN TOKENS (Раздел 9 ТЗ: Цветовая палитра и дизайн-токены)
// =============================================================
object EchoTokens {
    val Background = Color(0xFF07090E)
    val Surface = Color(0xFF0D121B)
    val SurfaceElevated = Color(0xFF151E2A)
    val SignalCyan = Color(0xFF00F0FF)
    val VinylAmber = Color(0xFFFFB800)
    val Gold = Color(0xFFFFD700)
    val TextPrimary = Color(0xFFF2F5F7)
    val TextSecondary = Color(0xFF85909E)
    val Divider = Color(0xFF26313E)
    val PurpleResonance = Color(0xFFA855F7)
    val SkyBlue = Color(0xFF38BDF8)
    val WarningRed = Color(0xFFFF5533)
}

// =============================================================
// NAVIGATION DESTINATIONS (Раздел 3 и 4 ТЗ)
// =============================================================
enum class EchoScreen(val label: String, val iconSymbol: String) {
    ETHER("Эфир", "∿"),
    DECK("Дека", "💽"),
    ECHOES("Отзвуки", "📜"),
    CACHES("Тайники", "⏳")
}

// =============================================================
// ЕДИНЫЙ BOTTOM SHEET HOST STATE (Раздел 8 ТЗ)
// =============================================================
sealed interface EchoSheetState {
    object None : EchoSheetState
    data class CuratorStory(val track: VinylTrack) : EchoSheetState
    data class LagrangeDetail(val info: LagrangePointInfo) : EchoSheetState
    data class CoverZoom(val coverUrl: String, val title: String, val artist: String) : EchoSheetState
    data class CapsuleDetail(val track: VinylTrack) : EchoSheetState
    object DropCapsule : EchoSheetState
    object ServerSettings : EchoSheetState
}

// =============================================================
// DATA MODELS & MAPPERS
// =============================================================
data class TrackMediaInfo(
    val coverUrl: String? = null,
    val previewAudioUrl: String? = null
)

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
    val isUnlocked: Boolean = true,
    val bpm: Int = 120,
    val camelotKey: String = "8A",
    val genre: String = "indie"
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
    isSelf = true,
    bpm = 120,
    camelotKey = "8A",
    genre = "indie"
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
    coverUrl = coverUrl,
    bpm = bpm,
    camelotKey = camelotKey,
    genre = genre
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

// =============================================================
// MAIN ACTIVITY
// =============================================================
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        EchoStateRepository.init(this)
        EchoSoundManager.init(this)
        EchoChronicleManager.init(this)
        EchoBleManager.init(this)
        EchoBleManager.startScanning(this)
        EchoBleManager.startAdvertising(this, "echo_device_${System.currentTimeMillis() % 10000}", 120, 8, true)

        setContent {
            EchoRadarTheme {
                EchoAppRoot()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        EchoBleManager.stopScanning()
        EchoBleManager.stopAdvertising()
        EchoAudioPlayer.stop()
    }
}

@Composable
fun EchoRadarTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            background = EchoTokens.Background,
            surface = EchoTokens.Surface,
            primary = EchoTokens.SignalCyan,
            secondary = EchoTokens.PurpleResonance,
            tertiary = EchoTokens.VinylAmber
        ),
        content = content
    )
}

// =============================================================
// ROOT APPLICATION COMPOSABLE (АРХИТЕКТУРА И НАВИГАЦИЯ)
// =============================================================
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun EchoAppRoot() {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current

    // Общие глобальные источники данных (Single Source of Truth)
    val currentPlayingTrack by EchoStateRepository.currentTrack.collectAsState()
    val nearbySignals by EchoStateRepository.nearbySignals.collectAsState()
    val capsules by EchoStateRepository.capsules.collectAsState()
    val isPresenceActive by EchoStateRepository.presenceActive.collectAsState()
    val connectionStatusStr by EchoStateRepository.connectionStatus.collectAsState()
    val incomingReaction by EchoStateRepository.incomingReaction.collectAsState()
    val serverHost by EchoStateRepository.serverHost.collectAsState()

    // Сенсорный купол ночи (Аргон & ChatGPT)
    val thermalPatches by EchoStateRepository.thermalPatches.collectAsState()
    val needleSyncedTargets by EchoStateRepository.needleSyncedTargets.collectAsState()
    val lagrangePoint by EchoStateRepository.lagrangePoint.collectAsState()
    val isDawnMode by EchoStateRepository.isDawnMode.collectAsState()

    // Аудиоплеер
    val isAudioPlaying by EchoAudioPlayer.isPlaying.collectAsState()
    val isAudioLoading by EchoAudioPlayer.isLoading.collectAsState()

    // Текущий выбранный экран (ETHER, DECK, ECHOES, CACHES)
    var currentScreen by remember { mutableStateOf(EchoScreen.ETHER) }

    // Единый BottomSheet стейт
    var sheetState by remember { mutableStateOf<EchoSheetState>(EchoSheetState.None) }

    // Все доступные пластинки в эфире
    val allVinyls = remember(currentPlayingTrack, nearbySignals, capsules) {
        val list = mutableListOf<VinylTrack>()
        nearbySignals.forEach { list.add(it.toVinylTrack()) }
        capsules.forEach { list.add(it.toVinylTrack()) }
        list
    }

    // Выбранная пластинка на деке
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
    val isNeedleSynced = needleSyncedTargets.contains(activeTrack.id)

    // Загрузка обложки трека и превью
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

    // Анимация пульса неона (GPU)
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val neonPulse by infiniteTransition.animateFloat(
        initialValue = 0.88f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(2400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "neonPulse"
    )

    // Фотонный луч сонара (1 оборот за 6 секунд, ТЗ раздел 3)
    val laserPhotonProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(6000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "laserPhoton"
    )

    // Непрерывный угол вращения винила (ТЗ раздел 7)
    var platterAngle by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(isAudioPlaying) {
        while (true) {
            platterAngle = VinylRotationEngine.getContinuousAngle(isAudioPlaying || currentScreen == EchoScreen.DECK)
            delay(16) // ~60 FPS
        }
    }

    // Обработка кнопки Back
    BackHandler {
        if (sheetState !is EchoSheetState.None) {
            sheetState = EchoSheetState.None
        } else if (currentScreen != EchoScreen.ETHER) {
            currentScreen = EchoScreen.ETHER
        }
    }

    // Всплывающие уведомления
    var feedbackMessage by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(feedbackMessage) {
        if (feedbackMessage != null) {
            delay(3200)
            feedbackMessage = null
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(EchoTokens.Background)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Контент текущего экрана (с плавным fade + vertical slide переходом 200ms)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                AnimatedContent(
                    targetState = currentScreen,
                    transitionSpec = {
                        (fadeIn(animationSpec = tween(180)) + slideInVertically(animationSpec = tween(180)) { 24 })
                            .togetherWith(fadeOut(animationSpec = tween(140)))
                    },
                    label = "screen_transition"
                ) { screen ->
                    when (screen) {
                        EchoScreen.ETHER -> {
                            EtherScreen(
                                allVinyls = allVinyls,
                                selectedVinyl = selectedVinyl,
                                thermalPatches = thermalPatches,
                                needleSyncedTargets = needleSyncedTargets,
                                lagrangePoint = lagrangePoint,
                                isDawnMode = isDawnMode,
                                isPresenceActive = isPresenceActive,
                                nearbySignalsCount = nearbySignals.size,
                                connectionStatusStr = connectionStatusStr,
                                neonPulse = neonPulse,
                                laserPhotonProgress = laserPhotonProgress,
                                onSelectVinyl = { vinyl ->
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    EchoSoundManager.playNeedleDrop()
                                    selectedVinyl = vinyl
                                },
                                onVinylLongClicked = { vinyl ->
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    EchoSoundManager.triggerHeartbeatVibration(context)
                                    EchoSoundManager.playNeedleDrop()
                                    selectedVinyl = vinyl
                                    EchoStateRepository.syncNeedle(vinyl.id)
                                    EchoAudioPlayer.toggle(vinyl.artist, vinyl.title, trackMediaInfo.previewAudioUrl)
                                    feedbackMessage = "🪡 Синхронизация иглы: Вы слушаете вместе ✨"
                                },
                                onMapLongPressed = { nx, ny ->
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    EchoSoundManager.triggerHeartbeatVibration(context)
                                    EchoSoundManager.playNeedleDrop()
                                    EchoStateRepository.sendWarmth(selectedVinyl?.id ?: "", nx, ny)
                                    feedbackMessage = "♨️ Призрачное тепло отправлено на радар ✨"
                                },
                                onTogglePresence = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    EchoStateRepository.togglePresence()
                                },
                                onLagrangeClicked = {
                                    if (lagrangePoint != null) {
                                        sheetState = EchoSheetState.LagrangeDetail(lagrangePoint!!)
                                    }
                                },
                                onOpenDeck = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    currentScreen = EchoScreen.DECK
                                },
                                onOpenServerSettings = {
                                    sheetState = EchoSheetState.ServerSettings
                                }
                            )
                        }

                        EchoScreen.DECK -> {
                            DeckScreen(
                                activeTrack = activeTrack,
                                mediaInfo = trackMediaInfo,
                                isSpinning = true,
                                rotationAngle = { platterAngle },
                                isAudioPlaying = isAudioPlaying,
                                isAudioLoading = isAudioLoading,
                                isNeedleSynced = isNeedleSynced,
                                onToggleAudioPreview = {
                                    EchoAudioPlayer.toggle(activeTrack.artist, activeTrack.title, trackMediaInfo.previewAudioUrl)
                                },
                                onOpenCoverZoom = {
                                    val url = activeTrack.coverUrl ?: trackMediaInfo.coverUrl
                                    if (!url.isNullOrBlank()) {
                                        sheetState = EchoSheetState.CoverZoom(url, activeTrack.title, activeTrack.artist)
                                    }
                                },
                                onOpenInMusicApp = {
                                    openMusicApp(context, activeTrack.artist, activeTrack.title, activeTrack.sourceApp)
                                },
                                onSyncNeedle = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    EchoSoundManager.triggerHeartbeatVibration(context)
                                    EchoSoundManager.playNeedleDrop()
                                    EchoStateRepository.syncNeedle(activeTrack.id)
                                    feedbackMessage = "🪡 Игла установлена в унисон"
                                },
                                onSendWaveImpulse = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    EchoSoundManager.playWavePulse()
                                    EchoStateRepository.sendWaveImpulse(activeTrack.id)
                                    feedbackMessage = "🌊 Волновой импульс отправлен слушателю"
                                },
                                onSendThankYou = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    EchoSoundManager.playTick()
                                    EchoStateRepository.sendWaveImpulse(activeTrack.id)
                                    feedbackMessage = "∿ Волна созвучия ушла в эфир ✨"
                                },
                                onSaveToChronicle = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    EchoSoundManager.playUnlockChime()
                                    EchoChronicleManager.saveTrack(
                                        ChronicleTrackRecord(
                                            id = activeTrack.id,
                                            artist = activeTrack.artist,
                                            title = activeTrack.title,
                                            coverUrl = activeTrack.coverUrl ?: trackMediaInfo.coverUrl,
                                            distanceLabel = activeTrack.distanceLabel,
                                            poeticPersona = activeTrack.poeticPersona,
                                            resonancePercent = activeTrack.compatibility,
                                            timestampMillis = System.currentTimeMillis(),
                                            sourceApp = activeTrack.sourceApp
                                        )
                                    )
                                    feedbackMessage = "📜 Сохранено в ночную летопись"
                                },
                                onOpenCuratorStory = {
                                    sheetState = EchoSheetState.CuratorStory(activeTrack)
                                },
                                onSelectSelf = {
                                    selectedVinyl = currentPlayingTrack.toVinylTrack()
                                }
                            )
                        }

                        EchoScreen.ECHOES -> {
                            EchoesScreen(
                                activeTrackMediaInfo = trackMediaInfo,
                                onPlayTrack = { track ->
                                    EchoAudioPlayer.toggle(track.artist, track.title, track.coverUrl)
                                },
                                onOpenInMusicApp = { track ->
                                    openMusicApp(context, track.artist, track.title, track.sourceApp)
                                },
                                onOpenCuratorStory = { storyTrack ->
                                    sheetState = EchoSheetState.CuratorStory(storyTrack)
                                }
                            )
                        }

                        EchoScreen.CACHES -> {
                            CachesScreen(
                                capsules = capsules,
                                onSelectCapsule = { capsule ->
                                    val vinyl = capsule.toVinylTrack()
                                    selectedVinyl = vinyl
                                    sheetState = EchoSheetState.CapsuleDetail(vinyl)
                                },
                                onDropCapsuleClicked = {
                                    sheetState = EchoSheetState.DropCapsule
                                }
                            )
                        }
                    }
                }
            }

            // Компактный Mini Player (ТЗ раздел 5: скрыт на экране DECK, отображается на остальных)
            if (currentScreen != EchoScreen.DECK) {
                MiniPlayer(
                    activeTrack = activeTrack,
                    coverUrl = activeTrack.coverUrl ?: trackMediaInfo.coverUrl,
                    isAudioPlaying = isAudioPlaying,
                    rotationAngle = { platterAngle },
                    onTogglePlay = {
                        EchoAudioPlayer.toggle(activeTrack.artist, activeTrack.title, trackMediaInfo.previewAudioUrl)
                    },
                    onOpenDeck = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        currentScreen = EchoScreen.DECK
                    }
                )
            }

            // Постоянный Navigation Dock (ТЗ раздел 4: 4 пункта, высота 64 dp)
            NavigationDock(
                currentScreen = currentScreen,
                onScreenSelected = { screen ->
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    EchoSoundManager.playTick()
                    currentScreen = screen
                }
            )
        }

        // Всплывающая плашка обратной связи (Toast)
        feedbackMessage?.let { msg ->
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = EchoTokens.SurfaceElevated.copy(alpha = 0.95f),
                border = BorderStroke(1.dp, EchoTokens.SignalCyan.copy(alpha = 0.7f)),
                shadowElevation = 8.dp,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 44.dp)
            ) {
                Text(
                    text = msg,
                    color = EchoTokens.SignalCyan,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp)
                )
            }
        }

        // Единый BottomSheet Host
        EchoBottomSheetHost(
            sheetState = sheetState,
            onDismiss = { sheetState = EchoSheetState.None },
            onLoadToDeck = { track ->
                selectedVinyl = track
                sheetState = EchoSheetState.None
                currentScreen = EchoScreen.DECK
            }
        )
    }
}

// =============================================================
// SCREEN 01 — ETHER / ЭФИР (ПОЛНОЭКРАННЫЙ СОНАР И КАРТА ПРИСУТСТВИЯ)
// =============================================================
@Composable
fun EtherScreen(
    allVinyls: List<VinylTrack>,
    selectedVinyl: VinylTrack?,
    thermalPatches: List<ThermalPatch>,
    needleSyncedTargets: Set<String>,
    lagrangePoint: LagrangePointInfo?,
    isDawnMode: Boolean,
    isPresenceActive: Boolean,
    nearbySignalsCount: Int,
    connectionStatusStr: String,
    neonPulse: Float,
    laserPhotonProgress: Float,
    onSelectVinyl: (VinylTrack) -> Unit,
    onVinylLongClicked: (VinylTrack) -> Unit,
    onMapLongPressed: (Float, Float) -> Unit,
    onTogglePresence: () -> Unit,
    onLagrangeClicked: () -> Unit,
    onOpenDeck: () -> Unit,
    onOpenServerSettings: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // Header (ТЗ раздел 3: Логотип, NIGHT / PRESENCE, статус эфира)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Э Х О",
                        color = EchoTokens.TextPrimary,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 2.5.sp
                    )
                    Text(
                        text = "NIGHT / PRESENCE",
                        color = EchoTokens.SignalCyan.copy(alpha = 0.8f),
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 1.2.sp
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(if (isPresenceActive) EchoTokens.SignalCyan else Color(0xFF64748B))
                    )
                    Text(
                        text = if (nearbySignalsCount > 0) "$nearbySignalsCount в квартале" else "Ночной покой",
                        color = EchoTokens.TextSecondary,
                        fontSize = 11.sp
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Тумблер: «В эфире» / «Невидимка»
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = if (isPresenceActive) EchoTokens.SignalCyan.copy(alpha = 0.12f) else EchoTokens.Surface,
                    border = BorderStroke(1.dp, if (isPresenceActive) EchoTokens.SignalCyan.copy(alpha = 0.6f) else EchoTokens.Divider),
                    modifier = Modifier.clickable { onTogglePresence() }
                ) {
                    Text(
                        text = if (isPresenceActive) "● В эфире" else "○ Невидимка",
                        color = if (isPresenceActive) EchoTokens.SignalCyan else EchoTokens.TextSecondary,
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }

                // Индикатор сервера / настройки
                Surface(
                    shape = CircleShape,
                    color = EchoTokens.Surface,
                    border = BorderStroke(1.dp, EchoTokens.Divider),
                    modifier = Modifier
                        .size(32.dp)
                        .clickable { onOpenServerSettings() }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(text = "⚙", color = EchoTokens.TextSecondary, fontSize = 13.sp)
                    }
                }
            }
        }

        // Полноэкранный холст сонара (ТЗ: SonarCanvas занимает максимум доступного пространства)
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            VinylCityMapCanvas(
                allVinyls = allVinyls,
                selectedVinyl = selectedVinyl,
                thermalPatches = thermalPatches,
                needleSyncedTargets = needleSyncedTargets,
                lagrangePoint = lagrangePoint,
                isDawnMode = isDawnMode,
                neonPulse = neonPulse,
                laserPhotonProgress = laserPhotonProgress,
                onVinylSelected = onSelectVinyl,
                onVinylLongClicked = onVinylLongClicked,
                onMapLongPressed = onMapLongPressed,
                onCenterUserClicked = {
                    onSelectVinyl(allVinyls.find { it.isSelf } ?: allVinyls.firstOrNull() ?: VinylTrack("self", "Ночной эфир", "Вы", "ЭХО", "0 м", "Пилигрим", "", "", 100, 0f, 0f, 0xFF00F0FFL, isSelf = true))
                },
                onLagrangeClicked = onLagrangeClicked
            )
        }

        // Нижняя полоса статуса резонанса (ТЗ раздел 3: ResonanceStatusPanel)
        Surface(
            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
            color = EchoTokens.Surface.copy(alpha = 0.95f),
            border = BorderStroke(1.dp, EchoTokens.Divider),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                if (selectedVinyl != null && !selectedVinyl.isSelf) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(EchoTokens.SignalCyan)
                            )
                            Text(
                                text = "${selectedVinyl.compatibility}% РЕЗОНАНС • ${selectedVinyl.distanceLabel}",
                                color = EchoTokens.SignalCyan,
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.8.sp
                            )
                        }
                        Text(
                            text = "${selectedVinyl.title} — ${selectedVinyl.artist}",
                            color = EchoTokens.TextPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = EchoTokens.SignalCyan.copy(alpha = 0.16f),
                        border = BorderStroke(1.dp, EchoTokens.SignalCyan),
                        modifier = Modifier.clickable { onOpenDeck() }
                    ) {
                        Text(
                            text = "РАСКРЫТЬ ВОЛНУ ↗",
                            color = EchoTokens.SignalCyan,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                        )
                    }
                } else {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(text = "✦", color = EchoTokens.SignalCyan, fontSize = 12.sp)
                        Text(
                            text = "Ночной эфир чист • Коснитесь винила на радаре для фокусировки",
                            color = EchoTokens.TextSecondary,
                            fontSize = 11.5.sp
                        )
                    }
                }
            }
        }
    }
}

// =============================================================
// SCREEN 02 — DECK / ДЕКА (МУЗЫКАЛЬНЫЙ ИНТЕРФЕЙС И ПРОИГРЫВАТЕЛЬ)
// =============================================================
@Composable
fun DeckScreen(
    activeTrack: VinylTrack,
    mediaInfo: TrackMediaInfo,
    isSpinning: Boolean,
    rotationAngle: () -> Float,
    isAudioPlaying: Boolean,
    isAudioLoading: Boolean,
    isNeedleSynced: Boolean,
    onToggleAudioPreview: () -> Unit,
    onOpenCoverZoom: () -> Unit,
    onOpenInMusicApp: () -> Unit,
    onSyncNeedle: () -> Unit,
    onSendWaveImpulse: () -> Unit,
    onSendThankYou: () -> Unit,
    onSaveToChronicle: () -> Unit,
    onOpenCuratorStory: () -> Unit,
    onSelectSelf: () -> Unit
) {
    var isSignalDetailsExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Header DECK / PLAYBACK
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = "DECK / PLAYBACK",
                    color = EchoTokens.TextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.2.sp
                )
                Text(
                    text = if (activeTrack.isSelf) "Ваш винил звучит в ночи" else "${activeTrack.compatibility}% резонанс • ${activeTrack.distanceLabel}",
                    color = EchoTokens.SkyBlue,
                    fontSize = 11.5.sp
                )
            }

            if (!activeTrack.isSelf) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = EchoTokens.Surface,
                    border = BorderStroke(1.dp, EchoTokens.Divider),
                    modifier = Modifier.clickable { onSelectSelf() }
                ) {
                    Text(
                        text = "Мой винил ♫",
                        color = EchoTokens.SignalCyan,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }
            }
        }

        // Большой виниловый диск с тонармом (ТЗ раздел 3: максимум около 65% ширины)
        Box(
            modifier = Modifier
                .size(230.dp)
                .clickable {
                    if (!mediaInfo.previewAudioUrl.isNullOrBlank()) {
                        onToggleAudioPreview()
                    } else {
                        onOpenCoverZoom()
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            // Колодец диска
            Box(
                modifier = Modifier
                    .size(228.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF07090E))
            )

            // Вращающийся винил
            Canvas(
                modifier = Modifier
                    .size(220.dp)
                    .graphicsLayer { rotationZ = rotationAngle() }
            ) {
                val radius = size.minDimension / 2f
                val center = Offset(size.width / 2f, size.height / 2f)

                drawCircle(color = Color(0xFF13171F), radius = radius, center = center)

                for (g in 1..7) {
                    drawCircle(
                        color = Color(0xFF222B3A).copy(alpha = 0.7f),
                        radius = radius * (0.35f + g * 0.08f),
                        center = center,
                        style = Stroke(width = 0.9.dp.toPx())
                    )
                }

                drawCircle(
                    brush = Brush.sweepGradient(
                        listOf(
                            Color.Transparent,
                            Color.White.copy(alpha = 0.09f),
                            Color.Transparent,
                            Color.White.copy(alpha = 0.09f),
                            Color.Transparent
                        ),
                        center = center
                    ),
                    radius = radius,
                    center = center
                )
            }

            // Центральный лейбл с обложкой
            Box(
                modifier = Modifier
                    .size(92.dp)
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
                        contentDescription = "Cover Zoom",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(text = "33⅓", color = Color.Black.copy(alpha = 0.7f), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        Text(text = "ЭХО", color = Color.Black, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
                    }
                }

                // Шпиндель
                Box(
                    modifier = Modifier
                        .size(14.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFE2E8F0))
                        .border(1.5.dp, Color(0xFF0F1420), CircleShape)
                )
            }

            // Тонарм
            TonearmOverlay(isSpinning = isSpinning)
        }

        // Метаданные трека (Крупные, читаемые, чистые)
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = activeTrack.title,
                color = EchoTokens.TextPrimary,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Text(
                text = activeTrack.artist,
                color = EchoTokens.SkyBlue,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            // Акустические метрики трека (BPM / Camelot / Genre)
            if (activeTrack.bpm > 0) {
                Text(
                    text = "♩ ${activeTrack.bpm} BPM • Camelot ${activeTrack.camelotKey} • ${activeTrack.genre}",
                    color = EchoTokens.TextSecondary,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 0.8.sp
                )
            }

            if (isNeedleSynced) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = EchoTokens.Gold.copy(alpha = 0.16f),
                    border = BorderStroke(1.dp, EchoTokens.Gold.copy(alpha = 0.7f))
                ) {
                    Text(
                        text = "🪡 СИНХРОН ИГЛЫ АКТИВЕН",
                        color = EchoTokens.Gold,
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            // Поэтическая персона куратора
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = EchoTokens.Surface,
                border = BorderStroke(0.8.dp, EchoTokens.Divider),
                modifier = Modifier.clickable { onOpenCuratorStory() }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(text = "✦", color = EchoTokens.SignalCyan, fontSize = 11.sp)
                    Text(
                        text = activeTrack.poeticPersona,
                        color = EchoTokens.TextPrimary,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        // Если это тайник: карточка Proof-of-Music
        if (activeTrack.isCapsule) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = if (activeTrack.isUnlocked) Color(0xFF241C0F) else Color(0xFF221316),
                border = BorderStroke(1.dp, if (activeTrack.isUnlocked) EchoTokens.VinylAmber else EchoTokens.WarningRed.copy(alpha = 0.85f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(text = if (activeTrack.isUnlocked) "🔓 ✦" else "🔒", fontSize = 13.sp)
                        Text(
                            text = if (activeTrack.isVenue) {
                                if (activeTrack.isUnlocked) "АКУСТИЧЕСКИЙ ПАРОЛЬ СОВПАЛ" else "ЗВУКОВОЙ ЗАМОК ЗАПЕРТ"
                            } else "ВИНИЛОВЫЙ ТАЙНИК КВАРТАЛА",
                            color = if (activeTrack.isUnlocked) EchoTokens.VinylAmber else EchoTokens.WarningRed,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    if (activeTrack.secretPerk.isNotBlank()) {
                        Text(
                            text = "🎁 ${activeTrack.secretPerk}",
                            color = Color(0xFFFFE082),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }

        // Невербальный этикет (Синхрон иглы & Камень в воду)
        if (!activeTrack.isSelf) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (isNeedleSynced) EchoTokens.Gold.copy(alpha = 0.18f) else EchoTokens.Surface,
                    border = BorderStroke(1.dp, if (isNeedleSynced) EchoTokens.Gold else EchoTokens.Gold.copy(alpha = 0.45f)),
                    modifier = Modifier
                        .weight(1f)
                        .height(38.dp)
                        .clickable { onSyncNeedle() }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = if (isNeedleSynced) "🪡 Игла в унисоне" else "🪡 Синхрон иглы",
                            color = EchoTokens.Gold,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = EchoTokens.Surface,
                    border = BorderStroke(1.dp, EchoTokens.SignalCyan.copy(alpha = 0.45f)),
                    modifier = Modifier
                        .weight(1f)
                        .height(38.dp)
                        .clickable { onSendWaveImpulse() }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = "🌊 Камень в воду",
                            color = EchoTokens.SignalCyan,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // Компактные кнопки управления аудио (Transport Controls)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (!mediaInfo.previewAudioUrl.isNullOrBlank()) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (isAudioPlaying) EchoTokens.SignalCyan.copy(alpha = 0.16f) else EchoTokens.Surface,
                    border = BorderStroke(1.dp, if (isAudioPlaying) EchoTokens.SignalCyan else EchoTokens.Divider),
                    modifier = Modifier
                        .weight(1f)
                        .height(40.dp)
                        .clickable { onToggleAudioPreview() }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = if (isAudioLoading) "⏳ Загрузка..." else if (isAudioPlaying) "⏸ 30 сек" else "♫ Превью 30с",
                            color = if (isAudioPlaying) EchoTokens.SignalCyan else EchoTokens.TextPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = EchoTokens.Surface,
                border = BorderStroke(1.dp, EchoTokens.Divider),
                modifier = Modifier
                    .then(if (!mediaInfo.previewAudioUrl.isNullOrBlank()) Modifier.weight(1f) else Modifier.fillMaxWidth())
                    .height(40.dp)
                    .clickable { onOpenInMusicApp() }
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = "↗ ${activeTrack.sourceApp}",
                        color = EchoTokens.SkyBlue,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        // Тактильная пара отклика («Спасибо за звук» + «В летопись ночи»)
        if (!activeTrack.isSelf) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = EchoTokens.Surface,
                    border = BorderStroke(1.dp, EchoTokens.PurpleResonance.copy(alpha = 0.65f)),
                    modifier = Modifier
                        .weight(1f)
                        .height(46.dp)
                        .clickable { onSendThankYou() }
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(text = "∿ Спасибо за звук", color = EchoTokens.TextPrimary, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
                    }
                }

                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = EchoTokens.Surface,
                    border = BorderStroke(1.dp, EchoTokens.VinylAmber.copy(alpha = 0.65f)),
                    modifier = Modifier
                        .weight(1f)
                        .height(46.dp)
                        .clickable { onSaveToChronicle() }
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(text = "📜 В летопись ночи", color = EchoTokens.VinylAmber, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }

        // Раскрываемая секция Signal Details (ТЗ раздел 3)
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = EchoTokens.Surface,
            border = BorderStroke(0.8.dp, EchoTokens.Divider),
            modifier = Modifier
                .fillMaxWidth()
                .clickable { isSignalDetailsExpanded = !isSignalDetailsExpanded }
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "SIGNAL DETAILS / АКУСТИЧЕСКИЙ ПАСПОРТ",
                        color = EchoTokens.TextSecondary,
                        fontSize = 10.5.sp,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = if (isSignalDetailsExpanded) "▲" else "▼",
                        color = EchoTokens.TextSecondary,
                        fontSize = 11.sp
                    )
                }

                if (isSignalDetailsExpanded) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "• Тональность: Camelot ${activeTrack.camelotKey}\n• Темп: ${activeTrack.bpm} BPM\n• Жанр: ${activeTrack.genre}\n• Дистанция: ${activeTrack.distanceLabel}\n• Источник: ${activeTrack.sourceApp}",
                        color = EchoTokens.TextPrimary,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        lineHeight = 16.sp
                    )
                    if (activeTrack.observation.isNotBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "«${activeTrack.observation}»",
                            color = EchoTokens.SkyBlue,
                            fontSize = 11.sp,
                            fontStyle = FontStyle.Italic
                        )
                    }
                }
            }
        }
    }
}

// =============================================================
// SCREEN 03 — ECHOES / ОТЗВУКИ (АРХИВ НОЧНЫХ СОБЫТИЙ)
// =============================================================
@Composable
fun EchoesScreen(
    activeTrackMediaInfo: TrackMediaInfo,
    onPlayTrack: (ChronicleTrackRecord) -> Unit,
    onOpenInMusicApp: (ChronicleTrackRecord) -> Unit,
    onOpenCuratorStory: (VinylTrack) -> Unit
) {
    var selectedFilter by remember { mutableIntStateOf(0) } // 0: Созвучия, 1: Пойманные волны, 2: Истории куратора
    val savedTracks = EchoChronicleManager.savedTracks
    val incomingEchoes = EchoChronicleManager.incomingEchoes

    LaunchedEffect(Unit) {
        EchoChronicleManager.markAllRead()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Header
        Column {
            Text(
                text = "ECHOES / АРХИВ НОЧИ",
                color = EchoTokens.TextPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.2.sp
            )
            Text(
                text = "Эхо ночных встреч, пойманных импульсов и созвучий",
                color = EchoTokens.TextSecondary,
                fontSize = 11.5.sp
            )
        }

        // Фильтры (ТЗ раздел 3: Созвучия, Пойманные волны, Истории)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(EchoTokens.Surface)
                .padding(3.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            listOf("Созвучия (${savedTracks.size})", "Волны (${incomingEchoes.size})", "Истории").forEachIndexed { index, title ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(11.dp))
                        .background(if (selectedFilter == index) EchoTokens.SignalCyan.copy(alpha = 0.2f) else Color.Transparent)
                        .clickable { selectedFilter = index }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = title,
                        color = if (selectedFilter == index) EchoTokens.SignalCyan else EchoTokens.TextSecondary,
                        fontSize = 11.sp,
                        fontWeight = if (selectedFilter == index) FontWeight.Bold else FontWeight.Medium
                    )
                }
            }
        }

        // Списки событий
        when (selectedFilter) {
            0 -> {
                // Вкладка 1: Созвучия (сохраненные треки)
                if (savedTracks.isEmpty()) {
                    Box(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(text = "📻", fontSize = 32.sp)
                            Text(text = "Летопись созвучий пуста", color = EchoTokens.TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            Text(
                                text = "Нажмите «В летопись ночи» на пластинке любого слушателя на деке, чтобы сохранить находку.",
                                color = EchoTokens.TextSecondary,
                                fontSize = 11.5.sp,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 24.dp)
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
                                color = EchoTokens.Surface,
                                border = BorderStroke(0.8.dp, EchoTokens.Divider),
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
                                            modifier = Modifier.size(44.dp).clip(RoundedCornerShape(8.dp)),
                                            contentScale = ContentScale.Crop
                                        )
                                    } else {
                                        Box(
                                            modifier = Modifier.size(44.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFF1E283C)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(text = "♫", color = EchoTokens.VinylAmber, fontSize = 18.sp)
                                        }
                                    }

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(text = track.title, color = EchoTokens.TextPrimary, fontSize = 13.5.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(text = track.artist, color = EchoTokens.SkyBlue, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(text = "${track.distanceLabel} • ${track.resonancePercent}% резонанс", color = EchoTokens.TextSecondary, fontSize = 10.sp)
                                    }

                                    IconButton(onClick = { onOpenInMusicApp(track) }, modifier = Modifier.size(36.dp)) {
                                        Text(text = "↗", color = EchoTokens.SignalCyan, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                    }

                                    IconButton(onClick = { EchoChronicleManager.removeTrack(track.id) }, modifier = Modifier.size(30.dp)) {
                                        Text(text = "✕", color = EchoTokens.TextSecondary, fontSize = 13.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            1 -> {
                // Вкладка 2: Пойманные волны (взаимные отклики)
                if (incomingEchoes.isEmpty()) {
                    Box(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(text = "🌌", fontSize = 32.sp)
                            Text(text = "В эфире пока тихо", color = EchoTokens.TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            Text(
                                text = "Когда кто-то в квартале поймает вашу волну и отправит импульс созвучия, здесь появятся отклики.",
                                color = EchoTokens.TextSecondary,
                                fontSize = 11.5.sp,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 24.dp)
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
                                color = EchoTokens.Surface,
                                border = BorderStroke(0.8.dp, EchoTokens.Divider),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Box(
                                        modifier = Modifier.size(36.dp).clip(CircleShape).background(EchoTokens.SignalCyan.copy(alpha = 0.15f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(text = echo.symbol, color = EchoTokens.SignalCyan, fontSize = 16.sp)
                                    }
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(text = echo.message, color = EchoTokens.TextPrimary, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
                                        val timeAgo = calculateTimeAgo(echo.timeSec)
                                        Text(text = "От: ${echo.senderId.take(12)} • $timeAgo", color = EchoTokens.TextSecondary, fontSize = 10.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            2 -> {
                // Вкладка 3: Истории ИИ-куратора
                val sampleCuratorTracks = listOf(
                    VinylTrack("story_1", "Судно", "Молчат Дома", "VK Музыка", "~75 м", "Пилигрим неоновых панелек", "Холодный пост-панк на перекрёстке ночного спального района.", "Резонанс панельных высоток и синти-попа.", 88, 45f, 0.5f, 0xFF00F0FFL),
                    VinylTrack("story_2", "The Darkest Hours", "E-Mantra", "Яндекс.Музыка", "~120 м", "Ловец низких частот", "Глубокий эмбиент, пульсирующий в такт ночной реке.", "Гармонический мост через колесо Камелота 8A.", 92, 120f, 0.7f, 0xFFFFB800L),
                    VinylTrack("story_3", "Midnight City", "M83", "Spotify", "~190 м", "Хранитель бессонницы", "Звёздный свет отражается в мокром асфальте набережной.", "Октавный резонанс на темпе 105 BPM.", 84, 210f, 0.8f, 0xFFA855F7L)
                )

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(sampleCuratorTracks) { curatorTrack ->
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = EchoTokens.Surface,
                            border = BorderStroke(0.8.dp, EchoTokens.Divider),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onOpenCuratorStory(curatorTrack) }
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(text = "✦", color = EchoTokens.SignalCyan, fontSize = 12.sp)
                                    Text(text = curatorTrack.poeticPersona, color = EchoTokens.SignalCyan, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                                }
                                Text(text = "«${curatorTrack.observation}»", color = EchoTokens.TextPrimary, fontSize = 12.5.sp, fontStyle = FontStyle.Italic)
                                Text(text = "Саундтрек: ${curatorTrack.artist} — ${curatorTrack.title}", color = EchoTokens.TextSecondary, fontSize = 10.5.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

// =============================================================
// SCREEN 04 — CACHE / ТАЙНИКИ (ГОРОДСКИЕ КАПСУЛЫ И АКУСТИЧЕСКИЕ ТОЧКИ)
// =============================================================
@Composable
fun CachesScreen(
    capsules: List<EchoCapsule>,
    onSelectCapsule: (EchoCapsule) -> Unit,
    onDropCapsuleClicked: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Header CITY FREQUENCIES
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = "CITY FREQUENCIES / ТАЙНИКИ",
                    color = EchoTokens.TextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.2.sp
                )
                Text(
                    text = "Звуковые тайники заведений и ночные капсулы",
                    color = EchoTokens.TextSecondary,
                    fontSize = 11.5.sp
                )
            }

            Surface(
                shape = RoundedCornerShape(14.dp),
                color = EchoTokens.VinylAmber.copy(alpha = 0.16f),
                border = BorderStroke(1.dp, EchoTokens.VinylAmber),
                modifier = Modifier.clickable { onDropCapsuleClicked() }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(text = "⏳", fontSize = 12.sp)
                    Text(text = "Оставить", color = EchoTokens.VinylAmber, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        // Акустическая мини-карта (радар тайников)
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = EchoTokens.Surface,
            border = BorderStroke(1.dp, EchoTokens.Divider),
            modifier = Modifier
                .fillMaxWidth()
                .height(130.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val center = Offset(size.width / 2f, size.height / 2f)
                    val maxR = size.height * 0.42f

                    // Кольца локатора тайников
                    drawCircle(color = EchoTokens.Divider.copy(alpha = 0.5f), radius = maxR, center = center, style = Stroke(1.dp.toPx()))
                    drawCircle(color = EchoTokens.Divider.copy(alpha = 0.3f), radius = maxR * 0.6f, center = center, style = Stroke(1.dp.toPx()))

                    // Центр пользователя
                    drawCircle(color = EchoTokens.SignalCyan, radius = 4.dp.toPx(), center = center)

                    // Точки капсул на радаре
                    capsules.forEach { cap ->
                        val rad = Math.toRadians(cap.angleDeg.toDouble())
                        val r = maxR * cap.ringFactor.coerceIn(0.2f, 0.95f)
                        val px = center.x + (r * cos(rad)).toFloat()
                        val py = center.y + (r * sin(rad)).toFloat()
                        val color = if (cap.isVenue) EchoTokens.VinylAmber else EchoTokens.SkyBlue
                        drawCircle(color = color.copy(alpha = 0.35f), radius = 7.dp.toPx(), center = Offset(px, py))
                        drawCircle(color = color, radius = 3.dp.toPx(), center = Offset(px, py))
                    }
                }

                Text(
                    text = "РАДИУС СКАНИРОВАНИЯ: 1 КМ",
                    color = EchoTokens.TextSecondary.copy(alpha = 0.6f),
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 6.dp)
                )
            }
        }

        // Список капсул и заведений
        if (capsules.isEmpty()) {
            Box(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(text = "⏳", fontSize = 32.sp)
                    Text(text = "В этом секторе пока нет тайников", color = EchoTokens.TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        text = "Вы можете оставить свой первый музыкальный тайник для полуночников в кнопке сверху.",
                        color = EchoTokens.TextSecondary,
                        fontSize = 11.5.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 24.dp)
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(capsules) { capsule ->
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = EchoTokens.Surface,
                        border = BorderStroke(1.dp, if (capsule.isUnlocked) EchoTokens.VinylAmber else EchoTokens.Divider),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelectCapsule(capsule) }
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(if (capsule.isUnlocked) EchoTokens.VinylAmber.copy(alpha = 0.16f) else Color(0xFF221316)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = if (capsule.isUnlocked) "🔓" else "🔒",
                                    fontSize = 16.sp
                                )
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (capsule.isVenue) capsule.venueName else capsule.title,
                                    color = EchoTokens.TextPrimary,
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${capsule.artist} — ${capsule.title}",
                                    color = EchoTokens.SkyBlue,
                                    fontSize = 11.5.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (capsule.isVenue && capsule.secretPerk.isNotBlank()) {
                                    Text(
                                        text = "🎁 ${capsule.secretPerk}",
                                        color = EchoTokens.VinylAmber,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                } else {
                                    Text(
                                        text = "${capsule.zoneName} • ${capsule.distanceLabel}",
                                        color = EchoTokens.TextSecondary,
                                        fontSize = 10.sp
                                    )
                                }
                            }

                            Text(
                                text = "Детали ↗",
                                color = EchoTokens.SignalCyan,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }
    }
}

// =============================================================
// MINI PLAYER (ТЗ раздел 5: Компактный плеер над Navigation Dock)
// =============================================================
@Composable
fun MiniPlayer(
    activeTrack: VinylTrack,
    coverUrl: String?,
    isAudioPlaying: Boolean,
    rotationAngle: () -> Float,
    onTogglePlay: () -> Unit,
    onOpenDeck: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        color = EchoTokens.SurfaceElevated,
        border = BorderStroke(1.dp, EchoTokens.Divider),
        modifier = Modifier
            .fillMaxWidth()
            .height(58.dp)
            .clickable { onOpenDeck() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Вращающийся мини-винил
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .graphicsLayer { rotationZ = rotationAngle() }
                    .clip(CircleShape)
                    .background(Color(activeTrack.colorHex)),
                contentAlignment = Alignment.Center
            ) {
                if (!coverUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = coverUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Text(text = "♫", color = Color.Black, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFE2E8F0))
                )
            }

            // Метаданные
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = activeTrack.title,
                    color = EchoTokens.TextPrimary,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${activeTrack.artist} • ${activeTrack.bpm} BPM",
                    color = EchoTokens.SkyBlue,
                    fontSize = 10.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Play / Pause превью кнопка
            IconButton(
                onClick = onTogglePlay,
                modifier = Modifier.size(38.dp)
            ) {
                Text(
                    text = if (isAudioPlaying) "⏸" else "▶",
                    color = EchoTokens.SignalCyan,
                    fontSize = 16.sp
                )
            }
        }
    }
}

// =============================================================
// NAVIGATION DOCK (ТЗ раздел 4: Постоянный нижний док)
// =============================================================
@Composable
fun NavigationDock(
    currentScreen: EchoScreen,
    onScreenSelected: (EchoScreen) -> Unit
) {
    val hasUnreadChronicle by EchoChronicleManager.hasUnread

    Surface(
        color = EchoTokens.Surface,
        border = BorderStroke(1.dp, EchoTokens.Divider),
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceAround
        ) {
            EchoScreen.values().forEach { screen ->
                val isSelected = currentScreen == screen
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable { onScreenSelected(screen) },
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Box {
                            Text(
                                text = screen.iconSymbol,
                                color = if (isSelected) EchoTokens.SignalCyan else EchoTokens.TextSecondary,
                                fontSize = 17.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                            if (screen == EchoScreen.ECHOES && hasUnreadChronicle) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .align(Alignment.TopEnd)
                                        .clip(CircleShape)
                                        .background(EchoTokens.SignalCyan)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = screen.label,
                            color = if (isSelected) EchoTokens.SignalCyan else EchoTokens.TextSecondary,
                            fontSize = 10.5.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}

// =============================================================
// ЕДИНЫЙ BOTTOM SHEET HOST (Раздел 8 ТЗ)
// =============================================================
@Composable
fun EchoBottomSheetHost(
    sheetState: EchoSheetState,
    onDismiss: () -> Unit,
    onLoadToDeck: (VinylTrack) -> Unit
) {
    if (sheetState is EchoSheetState.None) return

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = EchoTokens.SurfaceElevated,
            border = BorderStroke(1.2.dp, EchoTokens.SignalCyan.copy(alpha = 0.65f)),
            shadowElevation = 16.dp,
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Header модалки с крестиком
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = when (sheetState) {
                            is EchoSheetState.CuratorStory -> "✦ ИСТОРИЯ КУРАТОРА"
                            is EchoSheetState.LagrangeDetail -> "✦ ТОЧКА ЛАГРАНЖА"
                            is EchoSheetState.CoverZoom -> "ОБЛОЖКА РЕЛИЗА"
                            is EchoSheetState.CapsuleDetail -> "ТАЙНИК ЗАВЕДЕНИЯ"
                            is EchoSheetState.DropCapsule -> "ОСТАВИТЬ ТАЙНИК"
                            is EchoSheetState.ServerSettings -> "НАСТРОЙКИ СЕТИ"
                            else -> ""
                        },
                        color = EchoTokens.SignalCyan,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 1.sp
                    )

                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Text(text = "✕", color = EchoTokens.TextSecondary, fontSize = 15.sp)
                    }
                }

                // Содержимое модалки
                when (sheetState) {
                    is EchoSheetState.CuratorStory -> {
                        val track = sheetState.track
                        Text(text = track.poeticPersona, color = EchoTokens.TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                        Text(text = "«${track.observation}»", color = EchoTokens.SkyBlue, fontSize = 13.5.sp, fontStyle = FontStyle.Italic)
                        Text(text = track.story, color = EchoTokens.TextSecondary, fontSize = 12.sp, lineHeight = 17.sp)

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = EchoTokens.SignalCyan.copy(alpha = 0.16f),
                            border = BorderStroke(1.dp, EchoTokens.SignalCyan),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onLoadToDeck(track) }
                        ) {
                            Box(modifier = Modifier.padding(12.dp), contentAlignment = Alignment.Center) {
                                Text(text = "Загрузить на деку ♫", color = EchoTokens.SignalCyan, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    is EchoSheetState.LagrangeDetail -> {
                        val info = sheetState.info
                        Text(text = "Акустическое затмение квартала", color = EchoTokens.Gold, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Text(
                            text = "3 или более созвучных слушателей образовали гравитационную точку равновесия. Ночь открывает тайный трек эфира:",
                            color = EchoTokens.TextSecondary,
                            fontSize = 12.sp
                        )
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = EchoTokens.Surface,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                        ) {
                            Text(text = info.secretTrack, color = EchoTokens.TextPrimary, fontSize = 13.5.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(10.dp))
                        }
                        Text(text = "«${info.secretMessage}»", color = EchoTokens.SkyBlue, fontSize = 12.sp, fontStyle = FontStyle.Italic)
                    }

                    is EchoSheetState.CoverZoom -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(260.dp)
                                .clip(RoundedCornerShape(16.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            AsyncImage(
                                model = sheetState.coverUrl,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        Text(text = "${sheetState.title} — ${sheetState.artist}", color = EchoTokens.TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                    }

                    is EchoSheetState.CapsuleDetail -> {
                        val track = sheetState.track
                        Text(text = track.title, color = EchoTokens.TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                        Text(text = if (track.isUnlocked) "🔓 АКУСТИЧЕСКИЙ ПАРОЛЬ СОВПАЛ" else "🔒 ЗВУКОВОЙ ЗАМОК ЗАПЕРТ", color = if (track.isUnlocked) EchoTokens.VinylAmber else EchoTokens.WarningRed, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        if (track.secretPerk.isNotBlank()) {
                            Text(text = "🎁 Награда: ${track.secretPerk}", color = Color(0xFFFFE082), fontSize = 13.sp)
                        }
                        Text(text = "«${track.observation}»", color = EchoTokens.TextSecondary, fontSize = 12.sp)

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = EchoTokens.VinylAmber.copy(alpha = 0.16f),
                            border = BorderStroke(1.dp, EchoTokens.VinylAmber),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onLoadToDeck(track) }
                        ) {
                            Box(modifier = Modifier.padding(12.dp), contentAlignment = Alignment.Center) {
                                Text(text = "Загрузить капсулу на деку ♫", color = EchoTokens.VinylAmber, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    is EchoSheetState.DropCapsule -> {
                        var artistInput by remember { mutableStateOf("") }
                        var titleInput by remember { mutableStateOf("") }
                        var messageInput by remember { mutableStateOf("") }

                        Text(text = "Оставить звуковой тайник", color = EchoTokens.TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        OutlinedTextField(
                            value = artistInput,
                            onValueChange = { artistInput = it },
                            label = { Text("Исполнитель", color = EchoTokens.TextSecondary) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = titleInput,
                            onValueChange = { titleInput = it },
                            label = { Text("Название трека", color = EchoTokens.TextSecondary) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = messageInput,
                            onValueChange = { messageInput = it },
                            label = { Text("Послание полуночникам", color = EchoTokens.TextSecondary) },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = EchoTokens.VinylAmber,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (artistInput.isNotBlank() && titleInput.isNotBlank()) {
                                        EchoStateRepository.dropCapsule(
                                            artist = artistInput.trim(),
                                            title = titleInput.trim(),
                                            moodLabel = "Меланхолия",
                                            zoneName = "Ночной сектор",
                                            secretMessage = messageInput.trim().ifBlank { "Музыкальный след ночи." }
                                        )
                                        onDismiss()
                                    }
                                }
                        ) {
                            Box(modifier = Modifier.padding(12.dp), contentAlignment = Alignment.Center) {
                                Text(text = "Запечатать тайник ⏳", color = Color.Black, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    is EchoSheetState.ServerSettings -> {
                        var hostInput by remember { mutableStateOf(EchoStateRepository.serverHost.value) }
                        Text(text = "Адрес сервера ЭХО", color = EchoTokens.TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        OutlinedTextField(
                            value = hostInput,
                            onValueChange = { hostInput = it },
                            label = { Text("IP / Домен сервера", color = EchoTokens.TextSecondary) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = EchoTokens.SignalCyan,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    EchoStateRepository.updateServerHost(hostInput.trim())
                                    onDismiss()
                                }
                        ) {
                            Box(modifier = Modifier.padding(12.dp), contentAlignment = Alignment.Center) {
                                Text(text = "Сохранить и подключиться", color = Color.Black, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    else -> {}
                }
            }
        }
    }
}

// =============================================================
// ПРОЦЕДУРНЫЙ СОНАР И КАРТА НОЧНОГО ГОРОДА (VinylCityMapCanvas)
// =============================================================
@Composable
fun VinylCityMapCanvas(
    allVinyls: List<VinylTrack>,
    selectedVinyl: VinylTrack?,
    thermalPatches: List<ThermalPatch> = emptyList(),
    needleSyncedTargets: Set<String> = emptySet(),
    lagrangePoint: LagrangePointInfo? = null,
    isDawnMode: Boolean = false,
    neonPulse: Float,
    laserPhotonProgress: Float,
    onVinylSelected: (VinylTrack) -> Unit,
    onVinylLongClicked: (VinylTrack) -> Unit,
    onMapLongPressed: (Float, Float) -> Unit,
    onCenterUserClicked: () -> Unit,
    onLagrangeClicked: () -> Unit = {}
) {
    Box(modifier = Modifier.fillMaxSize()) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(allVinyls, selectedVinyl, lagrangePoint) {
                    detectTapGestures(
                        onTap = { tapOffset ->
                            val center = Offset(size.width / 2f, size.height / 2f)
                            val maxR = minOf(size.width, size.height) * 0.44f

                            // 1. Проверка тапа по Точке Лагранжа
                            if (lagrangePoint != null && lagrangePoint.active) {
                                val radL = Math.toRadians(lagrangePoint.angleDegrees.toDouble())
                                val rL = maxR * lagrangePoint.distanceRatio.coerceIn(0.24f, 0.92f)
                                val lx = center.x + (rL * cos(radL)).toFloat()
                                val ly = center.y + (rL * sin(radL)).toFloat()
                                val distL = sqrt((tapOffset.x - lx) * (tapOffset.x - lx) + (tapOffset.y - ly) * (tapOffset.y - ly))
                                if (distL < 38.dp.toPx()) {
                                    onLagrangeClicked()
                                    return@detectTapGestures
                                }
                            }

                            // 2. Проверка тапа по центру (свой винил)
                            val distCenter = sqrt(
                                (tapOffset.x - center.x) * (tapOffset.x - center.x) +
                                (tapOffset.y - center.y) * (tapOffset.y - center.y)
                            )
                            if (distCenter < 38.dp.toPx()) {
                                onCenterUserClicked()
                                return@detectTapGestures
                            }

                            // 3. Проверка тапа по винилам
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
                        },
                        onLongPress = { tapOffset ->
                            val center = Offset(size.width / 2f, size.height / 2f)
                            val maxR = minOf(size.width, size.height) * 0.44f

                            var longClickedVinyl: VinylTrack? = null
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
                                    longClickedVinyl = vinyl
                                }
                            }

                            val targetVinyl = longClickedVinyl
                            if (targetVinyl != null) {
                                onVinylLongClicked(targetVinyl)
                            } else {
                                val nx = (tapOffset.x / size.width).coerceIn(0f, 1f)
                                val ny = (tapOffset.y / size.height).coerceIn(0f, 1f)
                                onMapLongPressed(nx, ny)
                            }
                        }
                    )
                }
        ) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val maxR = minOf(size.width, size.height) * 0.44f

            // A. ПРОЦЕДУРНАЯ КАРТА НОЧНОГО ГОРОДА
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
                color = Color(0xFF0D2538).copy(alpha = 0.55f),
                style = Stroke(width = 38.dp.toPx(), cap = StrokeCap.Round)
            )

            // Мосты через реку
            val bridge1 = Offset(size.width * 0.38f, size.height * 0.17f)
            drawLine(color = Color(0xFF263346), start = Offset(bridge1.x - 16.dp.toPx(), bridge1.y - 20.dp.toPx()), end = Offset(bridge1.x + 16.dp.toPx(), bridge1.y + 20.dp.toPx()), strokeWidth = 3.dp.toPx())

            // Радиальная сетка улиц
            for (ang in listOf(30.0, 75.0, 140.0, 210.0, 260.0, 315.0)) {
                val rRad = Math.toRadians(ang)
                val ex = center.x + (maxR * 1.15f * cos(rRad)).toFloat()
                val ey = center.y + (maxR * 1.15f * sin(rRad)).toFloat()
                drawLine(
                    color = Color(0xFF141A28).copy(alpha = 0.7f),
                    start = center,
                    end = Offset(ex, ey),
                    strokeWidth = 1.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 10f), 0f)
                )
            }

            // B. КОНЦЕНТРИЧЕСКИЕ КОЛЬЦА СОНАРА
            val ringCount = 4
            for (i in 1..ringCount) {
                val r = maxR * (i.toFloat() / ringCount)
                drawCircle(
                    color = Color(0xFF00F0FF).copy(alpha = 0.08f),
                    radius = r,
                    center = center,
                    style = Stroke(width = 1.dp.toPx())
                )
            }

            // C. СКАНИРУЮЩИЙ ЛУЧ СОНАРА (RADAR SWEEP)
            val sweepRad = Math.toRadians((laserPhotonProgress * 360f).toDouble())
            val sweepEnd = Offset(center.x + (maxR * cos(sweepRad)).toFloat(), center.y + (maxR * sin(sweepRad)).toFloat())
            drawLine(
                brush = Brush.linearGradient(
                    colors = listOf(Color(0xFF00F0FF).copy(alpha = 0.55f), Color.Transparent),
                    start = center,
                    end = sweepEnd
                ),
                start = center,
                end = sweepEnd,
                strokeWidth = 2.dp.toPx()
            )

            // D. ТЕПЛОВЫЕ ПЯТНА ДЫХАНИЯ (ThermalBreathLayer)
            thermalPatches.forEach { patch ->
                val px = patch.x * size.width
                val py = patch.y * size.height
                val alpha = (1.0f - (patch.ageSec / 15f)).coerceIn(0f, 1f)
                if (alpha > 0f) {
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(Color(0xFFFF9988).copy(alpha = 0.35f * alpha), Color(0xFFFFB800).copy(alpha = 0.15f * alpha), Color.Transparent),
                            center = Offset(px, py),
                            radius = 48.dp.toPx()
                        ),
                        radius = 48.dp.toPx(),
                        center = Offset(px, py)
                    )
                }
            }

            // E. ТОЧКА ЛАГРАНЖА (LagrangePoint)
            if (lagrangePoint != null && lagrangePoint.active) {
                val radL = Math.toRadians(lagrangePoint.angleDegrees.toDouble())
                val rL = maxR * lagrangePoint.distanceRatio.coerceIn(0.24f, 0.92f)
                val lx = center.x + (rL * cos(radL)).toFloat()
                val ly = center.y + (rL * sin(radL)).toFloat()
                val lPos = Offset(lx, ly)

                drawCircle(
                    color = EchoTokens.Gold.copy(alpha = 0.25f * neonPulse),
                    radius = 24.dp.toPx() * neonPulse,
                    center = lPos
                )
                drawCircle(color = EchoTokens.Gold, radius = 5.dp.toPx(), center = lPos)
                drawCircle(color = Color(0xFF07090E), radius = 2.dp.toPx(), center = lPos)
            }

            // F. ВИНИЛОВЫЕ ПЛАСТИНКИ В ЭФИРЕ
            allVinyls.forEach { vinyl ->
                val isPc = vinyl.id == "pc_studio_node" || vinyl.sourceApp.contains("ПК") || vinyl.sourceApp.contains("Windows")
                val isFocused = selectedVinyl?.id == vinyl.id
                val jitter = (vinyl.id.hashCode() % 25 - 12) * 0.35f
                val rad = Math.toRadians((vinyl.angleDeg + jitter).toDouble())
                val r = maxR * vinyl.ringFactor.coerceIn(0.24f, 0.92f)
                val vx = center.x + (r * cos(rad)).toFloat()
                val vy = center.y + (r * sin(rad)).toFloat()
                val pos = Offset(vx, vy)

                val isSynced = needleSyncedTargets.contains(vinyl.id)
                val vinylBaseColor = if (isPc) EchoTokens.SignalCyan else Color(vinyl.colorHex)
                val ghostAlpha = if (vinyl.isGhost) 0.42f else 1.0f

                // Резонансная хорда к выбранному винилу
                if (isFocused) {
                    val chordResonance = vinyl.compatibility
                    if (chordResonance >= 80) {
                        // Звуковая синусоида осциллографа при резонансе >= 80% (Гравитационный фазовый сдвиг)
                        val chordPath = Path().apply {
                            moveTo(center.x, center.y)
                            val segments = 24
                            val dx = (pos.x - center.x) / segments
                            val dy = (pos.y - center.y) / segments
                            val nx = -dy * 0.18f
                            val ny = dx * 0.18f
                            for (s in 1..segments) {
                                val wave = sin(s * 0.9f + laserPhotonProgress * 12f).toFloat() * 10.dp.toPx()
                                lineTo(center.x + dx * s + nx * wave, center.y + dy * s + ny * wave)
                            }
                        }
                        drawPath(path = chordPath, color = EchoTokens.SignalCyan.copy(alpha = 0.75f), style = Stroke(width = 1.8.dp.toPx()))
                    } else {
                        drawLine(
                            brush = Brush.linearGradient(
                                colors = listOf(EchoTokens.SignalCyan.copy(alpha = 0.65f), vinylBaseColor.copy(alpha = 0.85f)),
                                start = center,
                                end = pos
                            ),
                            start = center,
                            end = pos,
                            strokeWidth = 1.5.dp.toPx()
                        )
                    }
                }

                // Свечение ореола
                drawCircle(
                    color = vinylBaseColor.copy(alpha = (if (isFocused) 0.45f else if (isPc) 0.35f else 0.15f) * ghostAlpha),
                    radius = (if (isFocused) 26.dp else if (isPc) 22.dp else 18.dp).toPx() * neonPulse,
                    center = pos
                )

                // Золотая вторая орбита при Needle Sync (Синхронизация иглы)
                if (isSynced) {
                    drawCircle(
                        color = EchoTokens.Gold.copy(alpha = 0.85f),
                        radius = 28.dp.toPx() * neonPulse,
                        center = pos,
                        style = Stroke(width = 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f), 0f))
                    )
                }

                // Тело пластинки
                val discRadius = (if (isFocused) 16.dp else if (isPc) 14.dp else 12.dp).toPx()
                drawCircle(color = Color(0xFF141720).copy(alpha = ghostAlpha), radius = discRadius, center = pos)
                drawCircle(
                    color = if (isFocused) vinylBaseColor.copy(alpha = ghostAlpha) else Color(0xFF2D3748).copy(alpha = ghostAlpha),
                    radius = discRadius,
                    center = pos,
                    style = Stroke(width = if (isFocused) 1.8.dp.toPx() else 1.dp.toPx())
                )
                drawCircle(color = vinylBaseColor.copy(alpha = ghostAlpha), radius = discRadius * 0.38f, center = pos)
                drawCircle(color = Color.Black.copy(alpha = ghostAlpha), radius = discRadius * 0.12f, center = pos)

                // Маркер тайника / заведения
                if (vinyl.isCapsule) {
                    drawCircle(
                        color = EchoTokens.VinylAmber.copy(alpha = 0.45f * neonPulse),
                        radius = discRadius * 1.5f,
                        center = pos,
                        style = Stroke(width = 1.2.dp.toPx())
                    )
                }
            }

            // G. ШУМ РАССВЕТА
            if (isDawnMode) {
                for (i in 0 until 30) {
                    val rx = ((i * 73 + (neonPulse * 120).toInt()) % size.width.toInt()).toFloat()
                    val ry = ((i * 127 + (neonPulse * 90).toInt()) % size.height.toInt()).toFloat()
                    drawCircle(color = Color(0xFFFDE68A).copy(alpha = 0.08f), radius = 1.4.dp.toPx(), center = Offset(rx, ry))
                }
            }

            // H. ЦЕНТР: МАЯК СЛУШАТЕЛЯ
            drawCircle(color = EchoTokens.SignalCyan.copy(alpha = 0.18f), radius = 28.dp.toPx() * neonPulse, center = center)
            drawCircle(color = Color(0xFF0E1420), radius = 16.dp.toPx(), center = center)
            drawCircle(color = EchoTokens.SignalCyan, radius = 16.dp.toPx(), center = center, style = Stroke(width = 2.dp.toPx()))
            drawCircle(color = EchoTokens.SignalCyan, radius = 6.dp.toPx(), center = center)
        }
    }
}

// =============================================================
// TONEARM OVERLAY
// =============================================================
@Composable
fun TonearmOverlay(isSpinning: Boolean) {
    val animatedArmAngle by animateFloatAsState(
        targetValue = if (isSpinning) 28f else 10f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "tonearm"
    )

    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.88f, 0.12f)
                rotationZ = animatedArmAngle
            }
    ) {
        val pivot = Offset(size.width * 0.88f, size.height * 0.12f)
        val bend = Offset(size.width * 0.72f, size.height * 0.48f)
        val headshell = Offset(size.width * 0.52f, size.height * 0.65f)

        // Подшипник тонарма
        drawCircle(color = Color(0xFF1E283C), radius = 8.dp.toPx(), center = pivot)
        drawCircle(color = Color(0xFFE2E8F0), radius = 4.dp.toPx(), center = pivot)

        // Трубка тонарма
        val armPath = Path().apply {
            moveTo(pivot.x, pivot.y)
            lineTo(bend.x, bend.y)
            lineTo(headshell.x, headshell.y)
        }
        drawPath(path = armPath, color = Color(0xFFCBD5E1), style = Stroke(width = 2.2.dp.toPx(), cap = StrokeCap.Round))

        // Головка звукоснимателя
        drawCircle(color = Color(0xFF0F1420), radius = 4.dp.toPx(), center = headshell)
        drawCircle(color = if (isSpinning) EchoTokens.SignalCyan else Color(0xFF64748B), radius = 2.5.dp.toPx(), center = headshell)
    }
}

// =============================================================
// ВСПОМОГАТЕЛЬНЫЕ ФУНКЦИИ
// =============================================================
fun openMusicApp(context: Context, artist: String, title: String, sourceApp: String) {
    try {
        val query = "$artist $title"
        val intent = when {
            sourceApp.contains("VK", ignoreCase = true) -> {
                val uri = Uri.parse("vk://search?q=${URLEncoder.encode(query, "UTF-8")}")
                Intent(Intent.ACTION_VIEW, uri).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            }
            sourceApp.contains("Яндекс", ignoreCase = true) || sourceApp.contains("Yandex", ignoreCase = true) -> {
                val uri = Uri.parse("yandexmusic://search?text=${URLEncoder.encode(query, "UTF-8")}")
                Intent(Intent.ACTION_VIEW, uri).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            }
            sourceApp.contains("Spotify", ignoreCase = true) -> {
                val uri = Uri.parse("spotify:search:${URLEncoder.encode(query, "UTF-8")}")
                Intent(Intent.ACTION_VIEW, uri).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            }
            else -> {
                val uri = Uri.parse("https://music.yandex.ru/search?text=${URLEncoder.encode(query, "UTF-8")}")
                Intent(Intent.ACTION_VIEW, uri).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            }
        }
        context.startActivity(intent)
    } catch (_: Exception) {
        val webUri = Uri.parse("https://yandex.ru/search/?text=${URLEncoder.encode("$artist $title слушать", "UTF-8")}")
        context.startActivity(Intent(Intent.ACTION_VIEW, webUri).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) })
    }
}

fun calculateTimeAgo(timestampSec: Long): String {
    val now = System.currentTimeMillis() / 1000
    val diff = now - timestampSec
    return when {
        diff < 60 -> "только что"
        diff < 3600 -> "${diff / 60} мин назад"
        diff < 86400 -> "${diff / 3600} ч назад"
        else -> "${diff / 86400} дн назад"
    }
}
