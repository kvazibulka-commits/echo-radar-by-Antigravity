package ru.echo.radar

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.service.notification.NotificationListenerService
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.tooling.preview.Preview
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URLEncoder
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.roundToInt

// =============================================================
// DESIGN TOKENS
// =============================================================
object EchoTokens {
    val Background = Color(0xFF04060A)
    val Surface = Color(0xFF0C1017)
    val SurfaceElevated = Color(0xFF151E2A)
    val SignalCyan = Color(0xFF00F0FF)
    val VinylAmber = Color(0xFFFFB800)
    val Gold = Color(0xFFFFD700)
    val AcousticGold = Color(0xFFFFD700)
    val TextPrimary = Color(0xFFF2F5F7)
    val TextSecondary = Color(0xFF85909E)
    val Divider = Color(0xFF26313E)
    val PurpleResonance = Color(0xFFA855F7)
    val SkyBlue = Color(0xFF38BDF8)
    val WarningRed = Color(0xFFFF5533)
    val WarningOrange = Color(0xFFF97316)
    val SlateMuted = Color(0xFF64748B)
    val SlateBorder = Color(0xFF334155)
}

// =============================================================
// NAVIGATION DESTINATIONS
// =============================================================
enum class EchoScreen(val label: String) {
    ETHER("ЭФИР"),
    DECK("ДЕКА"),
    ECHOES("ОТЗВУКИ"),
    CACHES("ТАЙНИКИ")
}

enum class EtherViewMode(val label: String, val icon: String) {
    SONAR("Сонар", "📡"),
    TUNER("Тюнер", "📻")
}

// =============================================================
// BOTTOM SHEET HOST STATE
// =============================================================
sealed interface EchoSheetState {
    object None : EchoSheetState
    object SonarLegend : EchoSheetState
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

fun PlayingTrack.toVinylTrack(): VinylTrack {
    val effectiveGenre = if (genre.isNotBlank() && genre != "indie" && !genre.startsWith("Анализ")) {
        genre
    } else {
        AlbumArtResolver.detectLocalGenre(artist, title)
    }
    val (pTitle, pObs) = AlbumArtResolver.generatePoeticArchetype(artist, title, effectiveGenre)
    val finalPersona = if (poeticPersona.isNotBlank() && !poeticPersona.startsWith("Генерируется") && !poeticPersona.startsWith("Архивариус ночных шорохов")) {
        poeticPersona
    } else pTitle

    return VinylTrack(
        id = "self_user_vinyl",
        title = title,
        artist = artist,
        sourceApp = sourceApp,
        distanceLabel = "Вы • В эфире",
        poeticPersona = finalPersona,
        observation = pObs,
        story = "Ваш личный звуковой маяк транслирует эту мелодию в эфир.",
        compatibility = 100,
        angleDeg = 0f,
        ringFactor = 0f,
        colorHex = 0xFF00F0FFL,
        isSelf = true,
        bpm = bpm,
        camelotKey = camelotKey,
        genre = effectiveGenre
    )
}

fun NearbyEchoSignal.toVinylTrack(): VinylTrack = VinylTrack(
    id = id,
    title = title,
    artist = artist,
    sourceApp = sourceApp,
    distanceLabel = distanceLabel,
    poeticPersona = poeticName,
    observation = aiObservation.ifBlank { "Слушает музыку в городском потоке." },
    story = resonanceStory.ifBlank { "Тёплый акустический резонанс в квартале." },
    compatibility = compatibility,
    angleDeg = angleDeg,
    ringFactor = ringFactor,
    colorHex = colorHex,
    isGhost = isGhost,
    coverUrl = coverUrl ?: AlbumArtResolver.getCachedCover(artist, title),
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
    companion object {
        const val EXTRA_OPEN_DECK = "extra_open_deck"
    }

    private val musicBroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent == null) return
            val artist = intent.getStringExtra("artist") ?: ""
            val track = intent.getStringExtra("track") ?: intent.getStringExtra("title") ?: ""
            val isPlaying = intent.getBooleanExtra("playing", true)
            if (track.isNotBlank()) {
                EchoStateRepository.updateDetectedMusic(
                    artist = artist,
                    title = track,
                    packageName = intent.component?.packageName ?: "music_player",
                    isPlaying = isPlaying
                )
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        EchoStateRepository.init(this)
        EchoSoundManager.init(this)
        EchoAudioPlayer.init(this)
        EchoChronicleManager.init(this)
        EchoBleManager.init(this)
        EchoBleManager.startScanning(this)
        EchoBleManager.startAdvertising(this, "echo_device_${System.currentTimeMillis() % 10000}", 120, 8, true)

        val filter = IntentFilter().apply {
            addAction("com.android.music.metachanged")
            addAction("com.android.music.playstatechanged")
            addAction("com.android.music.playbackcomplete")
            addAction("com.htc.music.metachanged")
            addAction("fm.last.android.metachanged")
            addAction("com.sec.android.app.music.metachanged")
            addAction("com.nullsoft.winamp.metachanged")
            addAction("com.amazon.mp3.metachanged")
            addAction("com.miui.player.metachanged")
            addAction("com.real.IMP.metachanged")
            addAction("com.sonyericsson.music.playbackcontrol.ACTION_TRACK_STARTED")
            addAction("com.yandex.music.action.PLAY")
            addAction("com.yandex.music.action.TRACK_CHANGED")
            addAction("com.spotify.music.metadatachanged")
            addAction("com.spotify.music.playbackstatechanged")
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(musicBroadcastReceiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            registerReceiver(musicBroadcastReceiver, filter)
        }

        setContent {
            EchoRadarTheme {
                EchoAppRoot()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        rebindNotificationListener()
        EchoMediaListenerService.instance?.scanActiveMusic()
    }

    private fun rebindNotificationListener() {
        if (EchoStateRepository.isNotificationAccessGranted(this)) {
            try {
                val componentName = ComponentName(this, EchoMediaListenerService::class.java)
                val pm = packageManager
                pm.setComponentEnabledSetting(
                    componentName,
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.DONT_KILL_APP
                )
                pm.setComponentEnabledSetting(
                    componentName,
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                    PackageManager.DONT_KILL_APP
                )
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    NotificationListenerService.requestRebind(componentName)
                }
            } catch (_: Exception) {}
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try { unregisterReceiver(musicBroadcastReceiver) } catch (_: Exception) {}
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
// SCREEN 00 — ONBOARDING (ТОЛЬКО ПЕРВЫЙ ЗАПУСК)
// =============================================================
@Composable
fun OnboardingScreen(
    hasNotificationPermission: Boolean,
    hasLocationPermission: Boolean,
    onRequestNotificationPermission: () -> Unit,
    onRequestLocationPermission: () -> Unit,
    onEnterEther: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(EchoTokens.Background)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "Э Х О",
                    color = EchoTokens.TextPrimary,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 4.sp
                )
                Text(
                    text = "ГОРОДСКОЙ МУЗЫКАЛЬНЫЙ СОНАР",
                    color = EchoTokens.SignalCyan,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 2.sp
                )
            }

            Surface(
                shape = RoundedCornerShape(16.dp),
                color = EchoTokens.Surface,
                border = BorderStroke(1.dp, EchoTokens.Divider),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "«Ты видишь не людей вокруг.\nТы видишь музыку, которая сейчас живёт вокруг тебя.»",
                        color = EchoTokens.TextPrimary,
                        fontSize = 13.5.sp,
                        fontStyle = FontStyle.Italic,
                        textAlign = TextAlign.Center,
                        lineHeight = 20.sp
                    )
                    Text(
                        text = "Здесь нет профилей, подписчиков и лайков. Только следы звука, расстояние и созвучие.",
                        color = EchoTokens.TextSecondary,
                        fontSize = 11.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }

            // Карточка 1: Доступ к плеерам
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = if (hasNotificationPermission) EchoTokens.SurfaceElevated else EchoTokens.WarningOrange.copy(alpha = 0.12f),
                border = BorderStroke(1.dp, if (hasNotificationPermission) EchoTokens.Divider else EchoTokens.WarningOrange),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(text = "🔔", fontSize = 18.sp)
                        Column {
                            Text(
                                text = "Доступ к плеерам",
                                color = EchoTokens.TextPrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "ЭХО услышит, что играет, но не узнает, кто вы.",
                                color = EchoTokens.TextSecondary,
                                fontSize = 10.5.sp
                            )
                        }
                    }
                    if (hasNotificationPermission) {
                        Text(text = "✓ Включено", color = EchoTokens.SignalCyan, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                    } else {
                        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = EchoTokens.WarningOrange,
                                modifier = Modifier.clickable { onRequestNotificationPermission() }
                            ) {
                                Text(
                                    text = "Разрешить",
                                    color = Color.Black,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                )
                            }
                            Text(
                                text = "Инфо о приложении ⚙",
                                color = EchoTokens.TextSecondary,
                                fontSize = 9.5.sp,
                                modifier = Modifier.clickable { onRequestLocationPermission() }
                            )
                        }
                    }
                }
            }

            if (!hasNotificationPermission) {
                Text(
                    text = "💡 Android 13+: если переключатель серый — откройте Настройки → Приложения → ЭХО → 3 точки вверху → «Разрешить ограниченные настройки»",
                    color = EchoTokens.WarningOrange.copy(alpha = 0.85f),
                    fontSize = 10.sp,
                    textAlign = TextAlign.Center,
                    lineHeight = 14.sp,
                    modifier = Modifier.padding(horizontal = 12.dp)
                )
            }

            // Карточка 2: Геолокация
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = if (hasLocationPermission) EchoTokens.SurfaceElevated else EchoTokens.SkyBlue.copy(alpha = 0.12f),
                border = BorderStroke(1.dp, if (hasLocationPermission) EchoTokens.Divider else EchoTokens.SkyBlue),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(text = "📍", fontSize = 18.sp)
                        Column {
                            Text(
                                text = "Геолокация рядом",
                                color = EchoTokens.TextPrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Показать музыку рядом, а не везде.",
                                color = EchoTokens.TextSecondary,
                                fontSize = 10.5.sp
                            )
                        }
                    }
                    if (hasLocationPermission) {
                        Text(text = "✓ Включено", color = EchoTokens.SignalCyan, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                    } else {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = EchoTokens.SkyBlue,
                            modifier = Modifier.clickable { onRequestLocationPermission() }
                        ) {
                            Text(
                                text = "Разрешить",
                                color = Color.Black,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Surface(
                shape = RoundedCornerShape(16.dp),
                color = EchoTokens.SignalCyan,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .clickable { onEnterEther() }
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = "ВОЙТИ В ЭФИР ↗",
                        color = Color.Black,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.sp
                    )
                }
            }
        }
    }
}

// =============================================================
// ROOT APPLICATION COMPOSABLE
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

    // Сенсорный купол эфира
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

    // Все доступные пластинки в эфире (с фильтрацией технического мусора)
    val allVinyls = remember(currentPlayingTrack, nearbySignals, capsules) {
        val list = mutableListOf<VinylTrack>()
        nearbySignals.forEach { sig ->
            val tLow = sig.title.lowercase()
            val aLow = sig.artist.lowercase()
            val isGarbage = tLow.contains("chrome-extension") || tLow.contains("http://") || tLow.contains("https://") || tLow.contains(".html") || aLow.contains("chrome-extension")
            if (!isGarbage) {
                list.add(sig.toVinylTrack())
            }
        }
        capsules.forEach { list.add(it.toVinylTrack()) }
        list
    }

    // Выбранная пластинка на деке
    var selectedVinyl by remember { mutableStateOf<VinylTrack?>(null) }

    // Автоматический выбор самого созвучного трека при первом запуске
    LaunchedEffect(allVinyls, currentPlayingTrack) {
        if (selectedVinyl == null) {
            val validSignals = allVinyls.filter { !it.isSelf && !it.title.lowercase().contains("chrome-extension") }
            selectedVinyl = validSignals.maxByOrNull { it.compatibility } ?: (if (allVinyls.isNotEmpty()) allVinyls.first() else currentPlayingTrack.toVinylTrack())
        } else if (selectedVinyl?.isSelf == true) {
            selectedVinyl = currentPlayingTrack.toVinylTrack()
        }
    }

    // Синхронизация изменений (например, появление обложки с ПК или смена трека)
    LaunchedEffect(allVinyls) {
        selectedVinyl?.let { current ->
            val updated = allVinyls.find { it.id == current.id }
            if (updated != null && (updated.coverUrl != current.coverUrl || updated.title != current.title)) {
                selectedVinyl = updated
            }
        }
        val tracksToFetch = allVinyls.filter { !it.isSelf && it.coverUrl.isNullOrBlank() }.map { it.artist to it.title }
        if (tracksToFetch.isNotEmpty()) {
            withContext(Dispatchers.IO) {
                AlbumArtResolver.prefetchCovers(tracksToFetch)
            }
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

    // Анимация пульса неона
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val neonPulse by infiniteTransition.animateFloat(
        initialValue = 0.90f,
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(
            animation = tween(2400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "neonPulse"
    )

    // Тонкий сканирующий луч сонара
    val laserPhotonProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(6000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "laserPhoton"
    )

    // Непрерывный угол вращения винила (вращается при воспроизведении превью ИЛИ когда играет музыка на телефоне/ПК)
    val isVinylActive = isAudioPlaying || (currentPlayingTrack.isPlaying) || (currentScreen == EchoScreen.DECK && !currentPlayingTrack.artist.startsWith("Ожидание"))
    var platterAngle by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(isVinylActive) {
        while (true) {
            platterAngle = VinylRotationEngine.getContinuousAngle(isVinylActive)
            delay(16)
        }
    }

    // Проверка системных разрешений с динамическим обновлением при возврате из настроек
    fun checkLocPermission(): Boolean {
        return context.checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                context.checkSelfPermission(android.Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }

    fun checkNotifPermission(): Boolean {
        return EchoStateRepository.isNotificationAccessGranted(context)
    }

    var hasLocationPermission by remember { mutableStateOf(checkLocPermission()) }
    var hasNotificationPermission by remember { mutableStateOf(checkNotifPermission()) }

    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                hasLocationPermission = checkLocPermission()
                hasNotificationPermission = checkNotifPermission()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val onRequestNotificationPermission = {
        try {
            val intent = android.content.Intent(android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
            intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (_: Exception) {
            val intent = android.content.Intent(android.provider.Settings.ACTION_SETTINGS)
            intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
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

    // Всплывающее уведомление
    var feedbackMessage by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(feedbackMessage) {
        if (feedbackMessage != null) {
            delay(3500)
            feedbackMessage = null
        }
    }

    // Слушатель входящих реакций и откликов из эфира / с ПК
    val incomingReaction by EchoStateRepository.incomingReaction.collectAsState()
    LaunchedEffect(incomingReaction) {
        incomingReaction?.let { reaction ->
            EchoSoundManager.playTick()
            EchoSoundManager.playWavePulse()
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            feedbackMessage = "${reaction.symbol} ${reaction.message}"
            delay(4500)
            EchoStateRepository.dismissIncomingReaction()
        }
    }

    val prefs = remember { context.getSharedPreferences("echo_prefs", Context.MODE_PRIVATE) }
    var hasSeenOnboarding by remember { mutableStateOf(prefs.getBoolean("has_seen_onboarding", false)) }

    if (!hasSeenOnboarding) {
        OnboardingScreen(
            hasNotificationPermission = hasNotificationPermission,
            hasLocationPermission = hasLocationPermission,
            onRequestNotificationPermission = onRequestNotificationPermission,
            onRequestLocationPermission = {
                try {
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.fromParts("package", context.packageName, null)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                } catch (_: Exception) {}
            },
            onEnterEther = {
                prefs.edit().putBoolean("has_seen_onboarding", true).apply()
                hasSeenOnboarding = true
            }
        )
        return
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(EchoTokens.Background)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Контент текущего экрана
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                AnimatedContent(
                    targetState = currentScreen,
                    transitionSpec = {
                        (fadeIn(animationSpec = tween(180)) + slideInVertically(animationSpec = tween(180)) { 20 })
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
                                hasLocationPermission = hasLocationPermission,
                                hasNotificationPermission = hasNotificationPermission,
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
                                    feedbackMessage = "🪡 Слушаете вместе с ${vinyl.artist} ✨"
                                },
                                onMapLongPressed = { nx, ny ->
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    EchoSoundManager.triggerHeartbeatVibration(context)
                                    EchoSoundManager.playNeedleDrop()
                                    EchoStateRepository.sendWarmth(selectedVinyl?.id ?: "", nx, ny)
                                    feedbackMessage = "♨️ Тепловой импульс отправлен на радар ✨"
                                },
                                onTogglePresence = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    EchoStateRepository.togglePresence()
                                },
                                onOpenLegend = {
                                    sheetState = EchoSheetState.SonarLegend
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
                                },
                                onRequestPermission = {
                                    try {
                                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                            data = Uri.fromParts("package", context.packageName, null)
                                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        }
                                        context.startActivity(intent)
                                    } catch (_: Exception) {}
                                },
                                onRequestNotificationPermission = onRequestNotificationPermission
                            )
                        }

                        EchoScreen.DECK -> {
                            DeckScreen(
                                activeTrack = activeTrack,
                                mediaInfo = trackMediaInfo,
                                isSpinning = isVinylActive,
                                rotationAngle = { platterAngle },
                                isAudioPlaying = isAudioPlaying,
                                isAudioLoading = isAudioLoading,
                                isNeedleSynced = isNeedleSynced,
                                onSendReaction = { symbol ->
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    EchoSoundManager.playTick()
                                    EchoStateRepository.sendReaction(activeTrack.id, symbol)
                                    feedbackMessage = "$symbol Импульс созвучия отправлен ✨"
                                },
                                onMainPlayPauseToggle = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    EchoSoundManager.playNeedleDrop()
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
                                    feedbackMessage = "🪡 Слушаете вместе: трек синхронизирован"
                                },
                                onSendWaveImpulse = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    EchoSoundManager.playWavePulse()
                                    EchoStateRepository.sendWaveImpulse(activeTrack.id)
                                    feedbackMessage = "🌊 Световой импульс отправлен в эфир"
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
                                    EchoChronicleManager.saveTrack(activeTrack)
                                    feedbackMessage = "📜 Сохранено в вашу летопись"
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
                                allVinyls = allVinyls,
                                onSelectVinyl = { vinyl ->
                                    selectedVinyl = vinyl
                                    currentScreen = EchoScreen.DECK
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

            // Компактный Mini Player (скрыт на DECK)
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

            // Постоянный Navigation Dock с векторными иконками
            NavigationDock(
                currentScreen = currentScreen,
                onScreenSelected = { screen ->
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    EchoSoundManager.playTick()
                    currentScreen = screen
                }
            )
        }

        // Всплывающее уведомление (Toast)
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
// SCREEN 01 — ETHER / ЭФИР (СОНАР И КАРТА ПРИСУТСТВИЯ)
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
    hasLocationPermission: Boolean,
    hasNotificationPermission: Boolean,
    neonPulse: Float,
    laserPhotonProgress: Float,
    onSelectVinyl: (VinylTrack) -> Unit,
    onVinylLongClicked: (VinylTrack) -> Unit,
    onMapLongPressed: (Float, Float) -> Unit,
    onTogglePresence: () -> Unit,
    onOpenLegend: () -> Unit,
    onLagrangeClicked: () -> Unit,
    onOpenDeck: () -> Unit,
    onOpenServerSettings: () -> Unit,
    onRequestPermission: () -> Unit,
    onRequestNotificationPermission: () -> Unit
) {
    var showCachesOnRadar by remember { mutableStateOf(true) }

    Column(modifier = Modifier.fillMaxSize()) {
        // Header (Чистый, атмосферный, с фильтром тайников и статусом эфира)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "Э Х О",
                        color = EchoTokens.TextPrimary,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 2.sp
                    )
                    Text(
                        text = "СОЗВЕЗДИЯ",
                        color = EchoTokens.Gold,
                        fontSize = 8.5.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 1.sp
                    )
                }
                Text(
                    text = if (nearbySignalsCount > 0) "Басманный • $nearbySignalsCount сигнала" else "Басманный • Эфир спокоен",
                    color = EchoTokens.TextSecondary,
                    fontSize = 10.5.sp
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Восстановленный фильтр тайников [ 💎 Тайники ] / [ 🎧 Без тайников ]
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = if (showCachesOnRadar) EchoTokens.Gold.copy(alpha = 0.15f) else Color(0x18FFFFFF),
                    border = BorderStroke(1.dp, if (showCachesOnRadar) EchoTokens.Gold.copy(alpha = 0.5f) else Color(0x22FFFFFF)),
                    modifier = Modifier.clickable {
                        showCachesOnRadar = !showCachesOnRadar
                        EchoSoundManager.playTick()
                    }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = if (showCachesOnRadar) "💎 Тайники" else "🎧 Без тайников",
                            color = if (showCachesOnRadar) EchoTokens.Gold else EchoTokens.TextSecondary,
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Чистый статус «В эфире»
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = if (isPresenceActive) EchoTokens.SignalCyan.copy(alpha = 0.15f) else Color(0x18FFFFFF),
                    border = BorderStroke(1.dp, if (isPresenceActive) EchoTokens.SignalCyan.copy(alpha = 0.6f) else Color(0x22FFFFFF)),
                    modifier = Modifier.clickable { onTogglePresence() }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(if (isPresenceActive) EchoTokens.SignalCyan else Color(0xFF64748B))
                        )
                        Text(
                            text = if (isPresenceActive) "В эфире" else "Тишина",
                            color = if (isPresenceActive) EchoTokens.SignalCyan else EchoTokens.TextSecondary,
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Кнопка настроек и легенды сонара
                Surface(
                    shape = CircleShape,
                    color = Color(0x15FFFFFF),
                    border = BorderStroke(1.dp, Color(0x22FFFFFF)),
                    modifier = Modifier
                        .size(30.dp)
                        .clickable { onOpenServerSettings() }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(text = "⚙", color = EchoTokens.TextSecondary, fontSize = 12.sp)
                    }
                }
            }
        }

        // Подсказка о разрешениях (если не выданы)
        val audioManager = LocalContext.current.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        val isDeviceMusicActive = audioManager?.isMusicActive == true

        if (!hasNotificationPermission) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = EchoTokens.WarningOrange.copy(alpha = 0.14f),
                border = BorderStroke(1.dp, EchoTokens.WarningOrange.copy(alpha = 0.8f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 3.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(text = "🎵", fontSize = 16.sp)
                            Column {
                                Text(
                                    text = if (isDeviceMusicActive) "⚡ Музыка играет, но трек не перехвачен!" else "Для чтения музыки включите доступ к уведомлениям",
                                    color = EchoTokens.TextPrimary,
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "ЭХО слушает Яндекс Музыку, VK, Spotify",
                                    color = EchoTokens.TextSecondary,
                                    fontSize = 9.5.sp
                                )
                            }
                        }
                    }

                    Text(
                        text = "💡 Android 13+: если тумблер серый — откройте Настройки → Приложения → ЭХО → 3 точки вверху → «Разрешить ограниченные настройки»",
                        color = EchoTokens.WarningOrange.copy(alpha = 0.9f),
                        fontSize = 9.5.sp,
                        lineHeight = 13.sp
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Инфо о приложении ⚙",
                            color = EchoTokens.TextSecondary,
                            fontSize = 10.5.sp,
                            modifier = Modifier
                                .clickable { onRequestPermission() }
                                .padding(end = 12.dp)
                        )
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = EchoTokens.WarningOrange,
                            modifier = Modifier.clickable { onRequestNotificationPermission() }
                        ) {
                            Text(
                                text = "Включить доступ ↗",
                                color = Color.Black,
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                            )
                        }
                    }
                }
            }
        }

        if (!hasLocationPermission) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = EchoTokens.SurfaceElevated,
                border = BorderStroke(1.dp, EchoTokens.Divider),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 3.dp)
                    .clickable { onRequestPermission() }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(text = "📍", fontSize = 14.sp)
                        Text(
                            text = "Для поиска музыки рядом разрешите геолокацию",
                            color = EchoTokens.TextPrimary,
                            fontSize = 11.5.sp
                        )
                    }
                    Text(
                        text = "Включить ↗",
                        color = EchoTokens.SkyBlue,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Полноэкранный холст сонара или аналогового тюнера
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            val displayVinyls = if (showCachesOnRadar) allVinyls else allVinyls.filter { !it.isCapsule }
            VinylCityMapCanvas(
                allVinyls = displayVinyls,
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
                    onSelectVinyl(allVinyls.find { it.isSelf } ?: allVinyls.firstOrNull() ?: VinylTrack("self", "Ваш эфир", "Вы", "ЭХО", "0 м", "Слушатель", "", "", 100, 0f, 0f, 0xFF00F0FFL, isSelf = true))
                },
                onLagrangeClicked = onLagrangeClicked
            )
        }
    }
}

// =============================================================
// SCREEN 02 — DECK / ДЕКА (ПРОИГРЫВАТЕЛЬ)
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
    onMainPlayPauseToggle: () -> Unit,
    onOpenCoverZoom: () -> Unit,
    onOpenInMusicApp: () -> Unit,
    onSyncNeedle: () -> Unit,
    onSendWaveImpulse: () -> Unit,
    onSendThankYou: () -> Unit,
    onSaveToChronicle: () -> Unit,
    onOpenCuratorStory: () -> Unit,
    onSelectSelf: () -> Unit,
    onSendReaction: (String) -> Unit = {}
) {
    var isSignalDetailsExpanded by remember { mutableStateOf(false) }
    val effectiveGenre = (mediaInfo.genre?.takeIf { it.isNotBlank() }
        ?: activeTrack.genre.takeIf { it.isNotBlank() && it != "indie" && !it.startsWith("Анализ") }
        ?: AlbumArtResolver.detectLocalGenre(activeTrack.artist, activeTrack.title))

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // A. Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = "ДЕКА",
                    color = EchoTokens.TextPrimary,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.5.sp
                )
                Text(
                    text = "ПРОИГРЫВАТЕЛЬ ЭФИРА",
                    color = EchoTokens.TextSecondary,
                    fontSize = 9.5.sp,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 1.sp
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
                        text = "Мой трек ♫",
                        color = EchoTokens.SignalCyan,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }
            }
        }

        // B. Большой виниловый диск с тонармом
        Box(
            modifier = Modifier
                .size(236.dp)
                .clickable { onMainPlayPauseToggle() },
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(234.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF07090E))
            )

            // Вращающийся винил
            Canvas(
                modifier = Modifier
                    .size(226.dp)
                    .graphicsLayer { rotationZ = rotationAngle() }
            ) {
                val radius = size.minDimension / 2f
                val center = Offset(size.width / 2f, size.height / 2f)

                drawCircle(color = Color(0xFF13171F), radius = radius, center = center)

                for (g in 1..8) {
                    drawCircle(
                        color = Color(0xFF222B3A).copy(alpha = 0.7f),
                        radius = radius * (0.35f + g * 0.075f),
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
                    .size(94.dp)
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

        // C. Метаданные трека + Архетип + Жанр
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (activeTrack.poeticPersona.isNotBlank()) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = EchoTokens.SignalCyan.copy(alpha = 0.12f),
                    border = BorderStroke(1.dp, EchoTokens.SignalCyan.copy(alpha = 0.40f)),
                    modifier = Modifier.padding(bottom = 2.dp)
                ) {
                    Text(
                        text = "✦ ${activeTrack.poeticPersona.uppercase()}",
                        color = EchoTokens.Gold,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                    )
                }
            }

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

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(top = 2.dp)
            ) {
                Text(
                    text = "♫  ${effectiveGenre.uppercase()} · ${activeTrack.sourceApp}",
                    color = EchoTokens.SignalCyan.copy(alpha = 0.85f),
                    fontSize = 11.5.sp,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 0.8.sp
                )
                if (!activeTrack.isSelf) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = EchoTokens.Gold.copy(alpha = 0.15f),
                        border = BorderStroke(0.8.dp, EchoTokens.Gold.copy(alpha = 0.45f))
                    ) {
                        Text(
                            text = "${activeTrack.compatibility}% СОЗВУЧИЕ",
                            color = EchoTokens.Gold,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                if (isNeedleSynced) {
                    Text(
                        text = "• 🪡 В унисоне",
                        color = EchoTokens.Gold,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // D. ДЕЙСТВИЯ ПО ТЗ: Строгая иерархия без лишних цветных заливок и без смайликов
        val specificAppText = when {
            activeTrack.sourceApp.contains("VK", ignoreCase = true) -> "Слушать в VK Музыке ↗"
            activeTrack.sourceApp.contains("Яндекс", ignoreCase = true) || activeTrack.sourceApp.contains("Yandex", ignoreCase = true) -> "Слушать в Яндекс Музыке ↗"
            activeTrack.sourceApp.contains("Spotify", ignoreCase = true) -> "Слушать в Spotify ↗"
            else -> "Слушать в плеере ↗"
        }

        if (!activeTrack.isSelf) {
            // PRIMARY ACTION (Gold #FBBF24): «Слушать вместе» / «В унисоне»
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = if (isNeedleSynced) EchoTokens.Gold.copy(alpha = 0.18f) else EchoTokens.Gold,
                border = if (isNeedleSynced) BorderStroke(1.5.dp, EchoTokens.Gold) else null,
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .height(48.dp)
                    .clickable { onSyncNeedle() }
            ) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(text = if (isNeedleSynced) "🪡" else "⏳", fontSize = 15.sp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isNeedleSynced) "В унисоне · Игла синхронизирована" else "Слушать вместе",
                        color = if (isNeedleSynced) EchoTokens.Gold else Color(0xFF0A0A0F),
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.4.sp
                    )
                }
            }

            // SECONDARY ACTION (Cyan outline #38BDF8): «Импульс в эфир»
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = EchoTokens.SignalCyan.copy(alpha = 0.10f),
                border = BorderStroke(1.2.dp, EchoTokens.SignalCyan),
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .height(44.dp)
                    .clickable { onSendWaveImpulse() }
            ) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(text = "📡", fontSize = 14.sp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Импульс в эфир",
                        color = EchoTokens.SignalCyan,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            // TERTIARY ACTIONS (текстовые ссылки с ↗, без визуального шума)
            Row(
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = specificAppText,
                    color = EchoTokens.SkyBlue,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.clickable { onOpenInMusicApp() }
                )

                Text(
                    text = "🛒 В сохранённое ↗",
                    color = EchoTokens.TextSecondary,
                    fontSize = 12.sp,
                    modifier = Modifier.clickable { onSaveToChronicle() }
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .padding(bottom = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "♫ Спасибо за звук",
                    color = EchoTokens.TextSecondary,
                    fontSize = 11.5.sp,
                    modifier = Modifier.clickable { onSendThankYou() }
                )

                Text(
                    text = if (isAudioLoading) "✦ Ищу превью..." else if (isAudioPlaying) "⏸ Пауза превью" else "▶ Превью (30 сек) ↗",
                    color = if (isAudioPlaying) EchoTokens.SignalCyan else EchoTokens.TextSecondary,
                    fontSize = 11.5.sp,
                    modifier = Modifier.clickable { onMainPlayPauseToggle() }
                )
            }
        } else {
            // Для собственного трека
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = EchoTokens.SkyBlue.copy(alpha = 0.16f),
                border = BorderStroke(1.2.dp, EchoTokens.SkyBlue),
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .height(46.dp)
                    .clickable { onOpenInMusicApp() }
            ) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(text = "🎧", fontSize = 15.sp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = specificAppText,
                        color = EchoTokens.SkyBlue,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                }
            }
        }

        // G. Акустический паспорт (чистый минималистичный аккордеон без лишних коробок)
        HorizontalDivider(color = EchoTokens.Divider.copy(alpha = 0.6f), thickness = 0.8.dp, modifier = Modifier.padding(top = 8.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { isSignalDetailsExpanded = !isSignalDetailsExpanded }
                .padding(vertical = 10.dp, horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "АКУСТИЧЕСКИЙ ПАСПОРТ ТРЕКА",
                color = EchoTokens.TextSecondary,
                fontSize = 10.5.sp,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 1.sp
            )
            Text(
                text = if (isSignalDetailsExpanded) "▲ Свернуть" else "▼ Детали ↗",
                color = EchoTokens.SkyBlue,
                fontSize = 11.sp
            )
        }

        if (isSignalDetailsExpanded) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                val albumDisplay = mediaInfo.albumName?.takeIf { it.isNotBlank() } ?: "Сингл / Релиз"
                Text(
                    text = "• Трек: ${activeTrack.title}\n• Артист: ${activeTrack.artist}\n• Альбом: $albumDisplay\n• Жанр: $effectiveGenre\n• Источник: ${activeTrack.sourceApp}   • Дистанция: ${activeTrack.distanceLabel}",
                    color = EchoTokens.TextPrimary,
                    fontSize = 11.5.sp,
                    fontFamily = FontFamily.Monospace,
                    lineHeight = 17.sp
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

// =============================================================
// SCREEN 03 — ECHOES / ОТЗВУКИ (АРХИВ СОЗВУЧИЙ + ЭФИР ВОКРУГ МЕНЯ)
// =============================================================
@Composable
fun EchoesScreen(
    allVinyls: List<VinylTrack>,
    onSelectVinyl: (VinylTrack) -> Unit,
    onOpenInMusicApp: (ChronicleTrackRecord) -> Unit,
    onOpenCuratorStory: (VinylTrack) -> Unit
) {
    var selectedFilter by remember { mutableIntStateOf(0) } // 0: Эфир вокруг, 1: Сохранено, 2: Волны, 3: Истории
    val savedTracks = EchoChronicleManager.savedTracks
    val incomingEchoes = EchoChronicleManager.incomingEchoes

    val sampleCuratorStories = remember {
        listOf(
            VinylTrack("cur_1", "Судно", "Молчат Дома", "VK Музыка", "~75 м", "Пилигрим неоновых панелек", "Холодный пост-панк на перекрёстке спального района.", "Резонанс панельных высоток и синти-попа.", 88, 45f, 0.5f, 0xFF00F0FFL),
            VinylTrack("cur_2", "The Darkest Hours", "E-Mantra", "Яндекс.Музыка", "~120 м", "Ловец низких частот", "Глубокий эмбиент, пульсирующий в такт городской реке.", "Гармонический мост через колесо Камелота 8A.", 92, 120f, 0.7f, 0xFFFFB800L),
            VinylTrack("cur_3", "Midnight City", "M83", "Spotify", "~190 м", "Хранитель бессонницы", "Свет фонарей отражается в мокром асфальте набережной.", "Октавный резонанс на темпе 105 BPM.", 84, 210f, 0.8f, 0xFFA855F7L)
        )
    }

    LaunchedEffect(Unit) {
        EchoChronicleManager.markAllRead()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Header
        Column {
            Text(
                text = "ОТЗВУКИ",
                color = EchoTokens.TextPrimary,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.5.sp
            )
            Text(
                text = "АРХИВ СОЗВУЧИЙ И ЭФИР ВОКРУГ",
                color = EchoTokens.TextSecondary,
                fontSize = 9.5.sp,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 1.sp
            )
        }

        // 4 фильтра (включая «Эфир вокруг» построчно!)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(EchoTokens.Surface)
                .padding(3.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            listOf("Эфир (${allVinyls.size})", "Сохранено (${savedTracks.size})", "Волны (${incomingEchoes.size})", "Истории").forEachIndexed { index, title ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(11.dp))
                        .background(if (selectedFilter == index) EchoTokens.SignalCyan.copy(alpha = 0.2f) else Color.Transparent)
                        .clickable { selectedFilter = index }
                        .padding(vertical = 7.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = title,
                        color = if (selectedFilter == index) EchoTokens.SignalCyan else EchoTokens.TextSecondary,
                        fontSize = 10.5.sp,
                        fontWeight = if (selectedFilter == index) FontWeight.Bold else FontWeight.Medium
                    )
                }
            }
        }

        // Списки
        when (selectedFilter) {
            0 -> {
                // Вкладка 0: ЭФИР ВОКРУГ МЕНЯ (построчно то, что на сонаре!)
                if (allVinyls.isEmpty()) {
                    Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text(text = "В эфире пока тихо", color = EchoTokens.TextSecondary, fontSize = 13.sp)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(allVinyls) { vinyl ->
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = EchoTokens.Surface,
                                border = BorderStroke(0.8.dp, if (vinyl.isSelf) EchoTokens.SignalCyan.copy(alpha = 0.5f) else EchoTokens.Divider),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onSelectVinyl(vinyl) }
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    // Вертикальная полоска жанра/настроения
                                    Box(
                                        modifier = Modifier
                                            .width(3.5.dp)
                                            .height(44.dp)
                                            .clip(RoundedCornerShape(2.dp))
                                            .background(Color(vinyl.colorHex))
                                    )

                                    if (!vinyl.coverUrl.isNullOrBlank()) {
                                        AsyncImage(
                                            model = vinyl.coverUrl,
                                            contentDescription = null,
                                            modifier = Modifier.size(44.dp).clip(RoundedCornerShape(8.dp)),
                                            contentScale = ContentScale.Crop
                                        )
                                    } else {
                                        Box(
                                            modifier = Modifier
                                                .size(44.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(Color(vinyl.colorHex).copy(alpha = 0.22f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = if (vinyl.isSelf) "✦" else "♫",
                                                color = Color(vinyl.colorHex),
                                                fontSize = 18.sp
                                            )
                                        }
                                    }

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(text = vinyl.title, color = EchoTokens.TextPrimary, fontSize = 13.5.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(text = "${vinyl.artist} · ${vinyl.genre}", color = EchoTokens.SkyBlue, fontSize = 11.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(
                                            text = if (vinyl.isSelf) "Ваш личный маяк" else "${vinyl.distanceLabel} · ${vinyl.compatibility}% резонанс",
                                            color = EchoTokens.TextSecondary,
                                            fontSize = 10.sp
                                        )
                                    }

                                    // Кнопка перехода на Деку (тач-таргет 44x44 dp)
                                    Box(
                                        modifier = Modifier.size(44.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Surface(
                                            shape = CircleShape,
                                            color = EchoTokens.SurfaceElevated,
                                            border = BorderStroke(1.dp, EchoTokens.Divider),
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Text(text = "💽", fontSize = 13.sp)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            1 -> {
                // Вкладка 1: Сохранено (персональная летопись)
                if (savedTracks.isEmpty()) {
                    Box(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(text = "∿", color = EchoTokens.SignalCyan.copy(alpha = 0.5f), fontSize = 36.sp)
                            Text(text = "ПОКА ТИШИНА", color = EchoTokens.TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                            Text(
                                text = "Здесь появятся созвучия, которые вы сохраните клавишей «Сохранить трек» на экране Деки.",
                                color = EchoTokens.TextSecondary,
                                fontSize = 11.5.sp,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 28.dp)
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

            2 -> {
                // Вкладка 2: Волны (взаимные импульсы)
                if (incomingEchoes.isEmpty()) {
                    Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text(text = "В эфире пока тихо", color = EchoTokens.TextSecondary, fontSize = 13.sp)
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

            3 -> {
                // Вкладка 3: Истории куратора
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(sampleCuratorStories) { curatorTrack ->
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = EchoTokens.Surface,
                            border = BorderStroke(0.8.dp, EchoTokens.Divider),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onOpenCuratorStory(curatorTrack) }
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = curatorTrack.poeticPersona.uppercase(),
                                        color = EchoTokens.SignalCyan,
                                        fontSize = 10.5.sp,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 0.8.sp
                                    )
                                    Text(text = "В ЭФИРЕ", color = EchoTokens.TextSecondary, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                                }

                                Text(
                                    text = "«${curatorTrack.observation}»",
                                    color = EchoTokens.TextPrimary,
                                    fontSize = 13.5.sp,
                                    fontStyle = FontStyle.Italic,
                                    lineHeight = 18.sp
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "♫ ${curatorTrack.artist} — ${curatorTrack.title}",
                                        color = EchoTokens.SkyBlue,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        text = "Читать ↗",
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
    }
}

// =============================================================
// SCREEN 04 — CACHE / ТАЙНИКИ
// =============================================================
@Composable
fun CachesScreen(
    capsules: List<EchoCapsule>,
    onSelectCapsule: (EchoCapsule) -> Unit,
    onDropCapsuleClicked: () -> Unit
) {
    var selectedRadiusIndex by remember { mutableIntStateOf(2) } // 0: 250м, 1: 500м, 2: 1000м

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Header
        Column {
            Text(
                text = "ТАЙНИКИ",
                color = EchoTokens.TextPrimary,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.5.sp
            )
            Text(
                text = "МУЗЫКАЛЬНЫЕ КАПСУЛЫ И МЕСТА ГОРОДА",
                color = EchoTokens.TextSecondary,
                fontSize = 9.5.sp,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 1.sp
            )
        }

        // Полноразмерная аккуратная кнопка создания капсулы в одну строку
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = EchoTokens.VinylAmber.copy(alpha = 0.16f),
            border = BorderStroke(1.2.dp, EchoTokens.VinylAmber),
            modifier = Modifier
                .fillMaxWidth()
                .height(42.dp)
                .clickable { onDropCapsuleClicked() }
        ) {
            Row(
                modifier = Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Text(text = "⏳", fontSize = 13.sp)
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "+ ОСТАВИТЬ МУЗЫКАЛЬНУЮ КАПСУЛУ",
                    color = EchoTokens.VinylAmber,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp
                )
            }
        }

        // Радиус поиска
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

                    drawCircle(color = EchoTokens.Divider.copy(alpha = 0.6f), radius = maxR, center = center, style = Stroke(1.dp.toPx()))
                    drawCircle(color = EchoTokens.Divider.copy(alpha = 0.35f), radius = maxR * 0.5f, center = center, style = Stroke(1.dp.toPx()))
                    drawLine(color = EchoTokens.Divider.copy(alpha = 0.3f), start = Offset(center.x - maxR, center.y), end = Offset(center.x + maxR, center.y), strokeWidth = 1.dp.toPx())
                    drawLine(color = EchoTokens.Divider.copy(alpha = 0.3f), start = Offset(center.x, center.y - maxR), end = Offset(center.x, center.y + maxR), strokeWidth = 1.dp.toPx())
                    drawCircle(color = EchoTokens.SignalCyan, radius = 4.dp.toPx(), center = center)

                    capsules.forEach { cap ->
                        val rad = Math.toRadians(cap.angleDeg.toDouble())
                        val r = maxR * cap.ringFactor.coerceIn(0.2f, 0.95f)
                        val px = center.x + (r * cos(rad)).toFloat()
                        val py = center.y + (r * sin(rad)).toFloat()
                        val color = if (cap.isUnlocked) EchoTokens.VinylAmber else EchoTokens.SkyBlue
                        drawCircle(color = color.copy(alpha = 0.35f), radius = 7.dp.toPx(), center = Offset(px, py))
                        drawCircle(color = color, radius = 3.dp.toPx(), center = Offset(px, py))
                    }
                }

                Row(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    listOf("250м", "500м", "1000м").forEachIndexed { idx, radiusText ->
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (selectedRadiusIndex == idx) EchoTokens.VinylAmber.copy(alpha = 0.22f) else EchoTokens.SurfaceElevated,
                            border = BorderStroke(1.dp, if (selectedRadiusIndex == idx) EchoTokens.VinylAmber else EchoTokens.Divider),
                            modifier = Modifier.clickable { selectedRadiusIndex = idx }
                        ) {
                            Text(
                                text = "РАДИУС: $radiusText",
                                color = if (selectedRadiusIndex == idx) EchoTokens.VinylAmber else EchoTokens.TextSecondary,
                                fontSize = 9.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = if (selectedRadiusIndex == idx) FontWeight.Bold else FontWeight.Normal,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }
                }
            }
        }

        // Список капсул
        if (capsules.isEmpty()) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(text = "В этом секторе пока нет тайников", color = EchoTokens.TextSecondary, fontSize = 13.sp)
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(capsules) { capsule ->
                    val isUnlocked = capsule.isUnlocked
                    val isVenue = capsule.isVenue

                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = EchoTokens.Surface,
                        border = BorderStroke(1.dp, if (isUnlocked) EchoTokens.VinylAmber else EchoTokens.Divider),
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
                                    .background(if (isUnlocked) EchoTokens.VinylAmber.copy(alpha = 0.16f) else Color(0xFF221316)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(text = if (isUnlocked) "🔓" else "🔒", fontSize = 16.sp)
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                if (isVenue) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = EchoTokens.VinylAmber.copy(alpha = 0.16f),
                                            border = BorderStroke(0.8.dp, EchoTokens.VinylAmber)
                                        ) {
                                            Text(
                                                text = "☕ МЕСТО ГОРОДА",
                                                color = EchoTokens.VinylAmber,
                                                fontSize = 8.5.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                            )
                                        }
                                        Text(
                                            text = capsule.venueName.ifBlank { "Заведение" },
                                            color = EchoTokens.TextPrimary,
                                            fontSize = 13.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                } else {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = EchoTokens.SurfaceElevated,
                                            border = BorderStroke(0.8.dp, EchoTokens.Divider)
                                        ) {
                                            Text(
                                                text = "✉ КАПСУЛА",
                                                color = EchoTokens.TextSecondary,
                                                fontSize = 8.5.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                            )
                                        }
                                        Text(
                                            text = capsule.title,
                                            color = EchoTokens.TextPrimary,
                                            fontSize = 13.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }

                                Text(
                                    text = "♫ ${capsule.artist} — ${capsule.title}",
                                    color = EchoTokens.SkyBlue,
                                    fontSize = 11.5.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (isUnlocked && capsule.secretPerk.isNotBlank()) {
                                    Text(
                                        text = "🎁 Награда: ${capsule.secretPerk}",
                                        color = EchoTokens.VinylAmber,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                } else if (!isUnlocked && capsule.minResonance > 0) {
                                    Text(
                                        text = "🔒 Заперто · Требуется резонанс ≥ ${capsule.minResonance}%",
                                        color = EchoTokens.TextSecondary,
                                        fontSize = 10.sp
                                    )
                                } else {
                                    Text(
                                        text = "${capsule.zoneName} · ${capsule.distanceLabel}",
                                        color = EchoTokens.TextSecondary,
                                        fontSize = 10.sp
                                    )
                                }
                            }

                            // Кнопка деталей (тач-таргет 44x44 dp)
                            Box(
                                modifier = Modifier.size(44.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = EchoTokens.SurfaceElevated,
                                    border = BorderStroke(1.dp, EchoTokens.Divider),
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(text = "↗", color = EchoTokens.SkyBlue, fontSize = 13.sp, fontWeight = FontWeight.Bold)
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

// =============================================================
// MINI PLAYER
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
            .height(62.dp)
            .clickable { onOpenDeck() }
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
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
                    Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(Color(0xFFE2E8F0)))
                }

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = activeTrack.title,
                            color = EchoTokens.TextPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (activeTrack.compatibility > 0 && !activeTrack.isSelf) {
                            val isHighRes = activeTrack.compatibility >= 80
                            val badgeColor = if (isHighRes) EchoTokens.Gold else EchoTokens.SignalCyan
                            Text(
                                text = "${activeTrack.compatibility}%",
                                color = badgeColor,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier
                                    .background(badgeColor.copy(alpha = 0.18f), RoundedCornerShape(6.dp))
                                    .border(0.8.dp, badgeColor.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                                    .padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                    }
                    Text(
                        text = if (activeTrack.poeticPersona.isNotBlank()) "«${activeTrack.poeticPersona}»" else "${activeTrack.artist} • ${activeTrack.distanceLabel}",
                        color = EchoTokens.SkyBlue,
                        fontSize = 10.5.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                IconButton(onClick = onTogglePlay, modifier = Modifier.size(38.dp)) {
                    Text(
                        text = if (isAudioPlaying) "⏸" else "▶",
                        color = EchoTokens.SignalCyan,
                        fontSize = 16.sp
                    )
                }
            }
        }
    }
}

// =============================================================
// NAVIGATION DOCK (Векторные иконки)
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
                val iconColor = if (isSelected) EchoTokens.SignalCyan else Color(0xFF788695)

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
                            when (screen) {
                                EchoScreen.ETHER -> NavEtherIcon(color = iconColor)
                                EchoScreen.DECK -> NavDeckIcon(color = iconColor)
                                EchoScreen.ECHOES -> NavEchoesIcon(color = iconColor)
                                EchoScreen.CACHES -> NavCachesIcon(color = iconColor)
                            }

                            if (screen == EchoScreen.ECHOES && hasUnreadChronicle) {
                                Box(
                                    modifier = Modifier
                                        .size(5.dp)
                                        .align(Alignment.TopEnd)
                                        .clip(CircleShape)
                                        .background(EchoTokens.SignalCyan)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = screen.label,
                            color = iconColor,
                            fontSize = 10.5.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun NavEtherIcon(color: Color) {
    Canvas(modifier = Modifier.size(22.dp)) {
        val center = Offset(size.width / 2f, size.height * 0.7f)
        drawCircle(color = color, radius = 2.5.dp.toPx(), center = center)
        drawArc(
            color = color,
            startAngle = 205f,
            sweepAngle = 130f,
            useCenter = false,
            topLeft = Offset(center.x - 7.dp.toPx(), center.y - 7.dp.toPx()),
            size = Size(14.dp.toPx(), 14.dp.toPx()),
            style = Stroke(width = 1.6.dp.toPx(), cap = StrokeCap.Round)
        )
        drawArc(
            color = color.copy(alpha = 0.8f),
            startAngle = 210f,
            sweepAngle = 120f,
            useCenter = false,
            topLeft = Offset(center.x - 12.dp.toPx(), center.y - 12.dp.toPx()),
            size = Size(24.dp.toPx(), 24.dp.toPx()),
            style = Stroke(width = 1.6.dp.toPx(), cap = StrokeCap.Round)
        )
    }
}

@Composable
fun NavDeckIcon(color: Color) {
    Canvas(modifier = Modifier.size(22.dp)) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val radius = size.minDimension / 2f - 1.dp.toPx()
        drawCircle(color = color, radius = radius, center = center, style = Stroke(width = 1.6.dp.toPx()))
        drawCircle(color = color.copy(alpha = 0.5f), radius = radius * 0.6f, center = center, style = Stroke(width = 1.dp.toPx()))
        drawCircle(color = color, radius = 2.dp.toPx(), center = center)
    }
}

@Composable
fun NavEchoesIcon(color: Color) {
    Canvas(modifier = Modifier.size(22.dp)) {
        val barWidth = 2.dp.toPx()
        val spacing = 3.5.dp.toPx()
        val heights = floatArrayOf(0.4f, 0.85f, 0.55f, 0.95f, 0.35f)
        val startX = (size.width - (heights.size * barWidth + (heights.size - 1) * spacing)) / 2f
        for (i in heights.indices) {
            val h = size.height * heights[i]
            val x = startX + i * (barWidth + spacing)
            val y = (size.height - h) / 2f
            drawRoundRect(
                color = color,
                topLeft = Offset(x, y),
                size = Size(barWidth, h),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(1.dp.toPx())
            )
        }
    }
}

@Composable
fun NavCachesIcon(color: Color) {
    Canvas(modifier = Modifier.size(22.dp)) {
        val path = Path().apply {
            moveTo(size.width / 2f, 2.dp.toPx())
            lineTo(size.width - 3.dp.toPx(), size.height / 2f)
            lineTo(size.width / 2f, size.height - 2.dp.toPx())
            lineTo(3.dp.toPx(), size.height / 2f)
            close()
        }
        drawPath(path = path, color = color, style = Stroke(width = 1.6.dp.toPx(), cap = StrokeCap.Round))
        drawCircle(color = color, radius = 2.2.dp.toPx(), center = Offset(size.width / 2f, size.height / 2f))
    }
}

// =============================================================
// BOTTOM SHEET HOST
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
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = when (sheetState) {
                            is EchoSheetState.SonarLegend -> "ЛЕГЕНДА СИГНАЛОВ СОНАРА"
                            is EchoSheetState.CuratorStory -> "✦ ИСТОРИЯ ЭФИРА"
                            is EchoSheetState.LagrangeDetail -> "✦ ТОЧКА РАВНОВЕСИЯ"
                            is EchoSheetState.CoverZoom -> "ОБЛОЖКА РЕЛИЗА"
                            is EchoSheetState.CapsuleDetail -> "ТАЙНИК ГОРОДА"
                            is EchoSheetState.DropCapsule -> "ОСТАВИТЬ КАПСУЛУ"
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

                when (sheetState) {
                    is EchoSheetState.SonarLegend -> {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            LegendRow(color = EchoTokens.SignalCyan, title = "Циановый узел", desc = "Ваш звуковой маяк или студийный узел ПК в эфире")
                            LegendRow(color = EchoTokens.Gold, title = "Золотая орбита", desc = "Слушатели в унисоне (совместное прослушивание)")
                            LegendRow(color = EchoTokens.VinylAmber, title = "Янтарный маркер", desc = "Тайники города и звуковые капсулы заведений")
                            LegendRow(color = EchoTokens.PurpleResonance, title = "Пурпурный след", desc = "Слушатели поблизости в городском эфире")
                            LegendRow(color = EchoTokens.SkyBlue, title = "Голубая волна", desc = "Гармонический резонанс по тональности")
                        }
                    }

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
                            text = "3 или более созвучных слушателей образовали точку равновесия. Эфир открывает скрытый трек:",
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

                        Text(text = "Оставить звуковую капсулу", color = EchoTokens.TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
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
                            label = { Text("Послание слушателям", color = EchoTokens.TextSecondary) },
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
                                            secretMessage = messageInput.trim().ifBlank { "Музыкальный след города." },
                                            zoneName = "Городской сектор"
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
                                    EchoStateRepository.setServerHost(hostInput.trim())
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

@Composable
fun LegendRow(color: Color, title: String, desc: String) {
    Row(
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .padding(top = 4.dp)
                .size(10.dp)
                .clip(CircleShape)
                .background(color)
        )
        Column {
            Text(text = title, color = EchoTokens.TextPrimary, fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
            Text(text = desc, color = EchoTokens.TextSecondary, fontSize = 11.sp, lineHeight = 15.sp)
        }
    }
}

// =============================================================
// ВЫЧИСЛЕНИЕ АНТИ-КОЛЛИЗИОННЫХ ПОЗИЦИЙ ДИСКОВ НА СОНАРЕ
// =============================================================
fun calculateVinylMapPositions(
    allVinyls: List<VinylTrack>,
    center: Offset,
    maxR: Float,
    minDistancePx: Float
): Map<String, Offset> {
    if (allVinyls.isEmpty()) return emptyMap()

    // 1. Начальное распределение с учетом естественного угла и гарантированного гармоничного разброса
    val polarList = allVinyls.mapIndexed { index, vinyl ->
        val hashAngle = ((vinyl.id.hashCode() and 0x7FFFFFFF) % 360).toFloat()
        val effectiveAngle = if (vinyl.angleDeg in 5f..355f) {
            // Если угол задан сервером, добавляем мягкую фазовую девиацию
            vinyl.angleDeg + (((vinyl.id.hashCode() % 31) - 15) * 1.5f)
        } else {
            // Равномерно распределяем по небесной сфере ночного города
            (index * (360f / allVinyls.size.coerceAtLeast(1)) + hashAngle % 30f) % 360f
        }

        // Радиус от центра: держим в комфортном диапазоне 0.32f .. 0.88f (центр свободен для маяка слушателя)
        val rawRatio = if (vinyl.ringFactor > 0.1f) vinyl.ringFactor else (0.35f + (index % 4) * 0.16f)
        val effectiveRatio = rawRatio.coerceIn(0.32f, 0.88f)
        val r = maxR * effectiveRatio
        val rad = Math.toRadians(effectiveAngle.toDouble())

        vinyl to Offset(
            center.x + (r * cos(rad)).toFloat(),
            center.y + (r * sin(rad)).toFloat()
        )
    }.toMutableList()

    // 2. 8 проходов физической релаксации (Spring / Repulsion) для 100% исключения наложений
    for (pass in 0 until 8) {
        var shifted = false
        for (i in polarList.indices) {
            for (j in (i + 1) until polarList.size) {
                val (v1, p1) = polarList[i]
                val (v2, p2) = polarList[j]
                val dx = p2.x - p1.x
                val dy = p2.y - p1.y
                val dist = sqrt(dx * dx + dy * dy)
                if (dist < minDistancePx && dist > 0.001f) {
                    val overlap = (minDistancePx - dist) / 2f
                    val nx = dx / dist
                    val ny = dy / dist

                    var newP1 = Offset(p1.x - nx * overlap, p1.y - ny * overlap)
                    var newP2 = Offset(p2.x + nx * overlap, p2.y + ny * overlap)

                    // Ограничиваем дистанцию до центра (маяка пользователя)
                    val r1 = sqrt((newP1.x - center.x) * (newP1.x - center.x) + (newP1.y - center.y) * (newP1.y - center.y))
                    if (r1 < maxR * 0.30f) {
                        val scale = (maxR * 0.30f) / r1
                        newP1 = Offset(center.x + (newP1.x - center.x) * scale, center.y + (newP1.y - center.y) * scale)
                    } else if (r1 > maxR * 0.90f) {
                        val scale = (maxR * 0.90f) / r1
                        newP1 = Offset(center.x + (newP1.x - center.x) * scale, center.y + (newP1.y - center.y) * scale)
                    }

                    val r2 = sqrt((newP2.x - center.x) * (newP2.x - center.x) + (newP2.y - center.y) * (newP2.y - center.y))
                    if (r2 < maxR * 0.30f) {
                        val scale = (maxR * 0.30f) / r2
                        newP2 = Offset(center.x + (newP2.x - center.x) * scale, center.y + (newP2.y - center.y) * scale)
                    } else if (r2 > maxR * 0.90f) {
                        val scale = (maxR * 0.90f) / r2
                        newP2 = Offset(center.x + (newP2.x - center.x) * scale, center.y + (newP2.y - center.y) * scale)
                    }

                    polarList[i] = v1 to newP1
                    polarList[j] = v2 to newP2
                    shifted = true
                }
            }
        }
        if (!shifted) break
    }

    val map = mutableMapOf<String, Offset>()
    polarList.forEach { (vinyl, pos) ->
        map[vinyl.id] = pos
    }
    return map
}

// =============================================================
// СВЕТЯЩИЙСЯ ВИНИЛОВЫЙ ОРБ (Pure Slow Tech — Без нагромождения карточек!)
// =============================================================
@Composable
fun FloatingMusicIsland(
    vinyl: VinylTrack,
    isSelected: Boolean,
    pos: Offset,
    neonPulse: Float,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val accentColor = when {
        vinyl.compatibility >= 90 -> EchoTokens.Gold
        vinyl.compatibility >= 75 -> EchoTokens.VinylAmber
        vinyl.compatibility >= 55 -> EchoTokens.SignalCyan
        else -> Color(0xFF64748B)
    }

    val discSize = if (isSelected) 44.dp else 36.dp

    Box(
        modifier = Modifier
            .offset {
                IntOffset(
                    (pos.x - 24.dp.toPx()).roundToInt(),
                    (pos.y - 24.dp.toPx()).roundToInt()
                )
            }
            .size(48.dp)
            .pointerInput(vinyl.id) {
                detectTapGestures(
                    onTap = { onClick() },
                    onLongPress = { onLongClick() }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        // Мягкое атмосферное гало (Aura)
        if (vinyl.compatibility >= 75 || isSelected) {
            Box(
                modifier = Modifier
                    .size(if (isSelected) 48.dp else 40.dp)
                    .clip(CircleShape)
                    .background(accentColor.copy(alpha = if (isSelected) 0.32f * neonPulse else 0.14f * neonPulse))
            )
        }

        // Тело винилового диска
        Box(
            modifier = Modifier
                .size(discSize)
                .clip(CircleShape)
                .background(Color(0xFF090D14))
                .border(
                    width = if (isSelected) 2.dp else 1.2.dp,
                    color = if (isSelected) accentColor else accentColor.copy(alpha = 0.70f),
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            if (!vinyl.coverUrl.isNullOrBlank()) {
                AsyncImage(
                    model = vinyl.coverUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().clip(CircleShape)
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(14.dp)
                        .clip(CircleShape)
                        .background(Color(vinyl.colorHex))
                )

                // Живой мини-эквалайзер внутри яблока диска (только если нет обложки)
                Row(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .background(Color.Black.copy(alpha = 0.55f), shape = RoundedCornerShape(3.dp))
                        .padding(horizontal = 2.dp, vertical = 1.5.dp),
                    horizontalArrangement = Arrangement.spacedBy(1.dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    val eq1 = ((neonPulse * 10).toInt() % 6 + 2).dp
                    val eq2 = (((neonPulse * 16).toInt() + 2) % 8 + 3).dp
                    val eq3 = (((neonPulse * 12).toInt() + 3) % 5 + 2).dp
                    Box(modifier = Modifier.width(1.8.dp).height(eq1).background(accentColor, CircleShape))
                    Box(modifier = Modifier.width(1.8.dp).height(eq2).background(accentColor, CircleShape))
                    Box(modifier = Modifier.width(1.8.dp).height(eq3).background(accentColor, CircleShape))
                }
            }
        }
    }
}

// =============================================================
// ЕДИНЫЙ ПЛАВАЮЩИЙ БЕЙДЖ ТОЛЬКО ДЛЯ ВЫБРАННОГО ТРЕКА
// =============================================================
@Composable
fun SelectedTrackCallout(
    vinyl: VinylTrack,
    pos: Offset
) {
    val accentColor = when {
        vinyl.compatibility >= 90 -> EchoTokens.Gold
        vinyl.compatibility >= 75 -> EchoTokens.VinylAmber
        vinyl.compatibility >= 55 -> EchoTokens.SignalCyan
        else -> Color(0xFF64748B)
    }

    Box(
        modifier = Modifier
            .offset {
                IntOffset(
                    (pos.x - 85.dp.toPx()).roundToInt(),
                    (pos.y - 48.dp.toPx()).roundToInt()
                )
            }
            .width(170.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = Color(0xF2080C16),
            border = BorderStroke(1.dp, accentColor),
            shadowElevation = 8.dp
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = vinyl.title,
                    color = Color.White,
                    fontSize = 9.5.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(text = "·", color = accentColor, fontSize = 9.sp)
                Text(
                    text = "${vinyl.compatibility}%",
                    color = accentColor,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}

data class AcousticConstellationEdge(
    val idA: String,
    val idB: String,
    val posA: Offset,
    val posB: Offset,
    val resonance: Int,
    val isHarmonic: Boolean,
    val isConnectedToSelected: Boolean
)

// =============================================================
// ПРОЦЕДУРНЫЙ СОНАР СОЗВЕЗДИЙ (VinylCityMapCanvas)
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
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val widthPx = constraints.maxWidth.toFloat()
        val heightPx = constraints.maxHeight.toFloat()
        val center = Offset(widthPx / 2f, heightPx / 2f)
        val maxR = minOf(widthPx, heightPx) * 0.44f

        val density = LocalDensity.current
        val minDistancePx = remember(density) { with(density) { 54.dp.toPx() } }
        val displayVinyls = remember(allVinyls) { allVinyls.filter { !it.isSelf } }
        val positions = remember(displayVinyls, center, maxR, minDistancePx) {
            calculateVinylMapPositions(displayVinyls, center, maxR, minDistancePx)
        }

        // 1. ЖЕСТЫ МАСШТАБИРОВАНИЯ (PINCH-TO-ZOOM) И ПАНОРАМИРОВАНИЯ (PAN)
        var zoom by remember { mutableStateOf(1f) }
        var panOffset by remember { mutableStateOf(Offset.Zero) }

        val animatedZoom by animateFloatAsState(
            targetValue = zoom,
            animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow),
            label = "sonarZoom"
        )
        val animatedPanX by animateFloatAsState(
            targetValue = panOffset.x,
            animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow),
            label = "sonarPanX"
        )
        val animatedPanY by animateFloatAsState(
            targetValue = panOffset.y,
            animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow),
            label = "sonarPanY"
        )

        // 2. РАСЧЕТ МУЗЫКАЛЬНЫХ СОЗВЕЗДИЙ (ACOUSTIC CONSTELLATIONS)
        val constellationEdges = remember(displayVinyls, positions, selectedVinyl) {
            val edges = mutableListOf<AcousticConstellationEdge>()
            val visited = mutableSetOf<String>()
            val qualified = displayVinyls.filter { positions.containsKey(it.id) }

            qualified.forEach { v1 ->
                val p1 = positions[v1.id] ?: return@forEach
                val candidates = qualified
                    .filter { it.id != v1.id }
                    .map { v2 ->
                        val p2 = positions[v2.id]!!
                        val dist = sqrt((p1.x - p2.x) * (p1.x - p2.x) + (p1.y - p2.y) * (p1.y - p2.y))
                        val isGenreMatch = v1.genre.isNotBlank() && v1.genre.equals(v2.genre, ignoreCase = true)
                        val isHighRes = (v1.compatibility >= 70 && v2.compatibility >= 70)
                        Triple(v2, dist, isGenreMatch || isHighRes)
                    }
                    .filter { (_, dist, isMatch) -> isMatch && dist < maxR * 1.35f }
                    .sortedBy { it.second }
                    .take(2)

                candidates.forEach { (v2, _, isHarmonic) ->
                    val pairKey = if (v1.id < v2.id) "${v1.id}__${v2.id}" else "${v2.id}__${v1.id}"
                    if (!visited.contains(pairKey)) {
                        visited.add(pairKey)
                        val p2 = positions[v2.id]!!
                        val avgRes = (v1.compatibility + v2.compatibility) / 2
                        val isConnected = selectedVinyl != null && (selectedVinyl.id == v1.id || selectedVinyl.id == v2.id)
                        edges.add(AcousticConstellationEdge(v1.id, v2.id, p1, p2, avgRes, isHarmonic, isConnected))
                    }
                }
            }
            edges
        }

        // Интерактивный видовой экран с трансформациями масштаба и сдвига
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTransformGestures(panZoomLock = false) { _, pan, gestureZoom, _ ->
                        zoom = (zoom * gestureZoom).coerceIn(0.65f, 3.5f)
                        val maxPan = 450f * zoom
                        panOffset = Offset(
                            (panOffset.x + pan.x).coerceIn(-maxPan, maxPan),
                            (panOffset.y + pan.y).coerceIn(-maxPan, maxPan)
                        )
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures(
                        onDoubleTap = {
                            zoom = 1f
                            panOffset = Offset.Zero
                            EchoSoundManager.playTick()
                        },
                        onTap = {
                            onVinylSelected(VinylTrack("none", "", "", "", "", "", "", "", 0, 0f, 0f, 0L))
                        }
                    )
                }
                .graphicsLayer {
                    scaleX = animatedZoom
                    scaleY = animatedZoom
                    translationX = animatedPanX
                    translationY = animatedPanY
                }
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(allVinyls, selectedVinyl, lagrangePoint) {
                        detectTapGestures(
                            onTap = { tapOffset ->
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

                                val distCenter = sqrt(
                                    (tapOffset.x - center.x) * (tapOffset.x - center.x) +
                                    (tapOffset.y - center.y) * (tapOffset.y - center.y)
                                )
                                if (distCenter < 36.dp.toPx()) {
                                    onCenterUserClicked()
                                    return@detectTapGestures
                                }
                            }
                        )
                    }
            ) {
            // 0. Мягкие рассеянные фоновые свечения (Ambient Mesh)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFF4F46E5).copy(alpha = 0.15f), Color.Transparent),
                    center = Offset(0f, 0f),
                    radius = size.width * 0.75f
                ),
                radius = size.width * 0.75f,
                center = Offset(0f, 0f)
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFF06B6D4).copy(alpha = 0.12f), Color.Transparent),
                    center = Offset(size.width, size.height * 0.45f),
                    radius = size.width * 0.70f
                ),
                radius = size.width * 0.70f,
                center = Offset(size.width, size.height * 0.45f)
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFFF59E0B).copy(alpha = 0.08f), Color.Transparent),
                    center = Offset(size.width * 0.2f, size.height),
                    radius = size.width * 0.65f
                ),
                radius = size.width * 0.65f,
                center = Offset(size.width * 0.2f, size.height)
            )

            // 1. Светящееся неоновое русло Яузы (органика вместо прямых линий)
            val riverPath = Path().apply {
                moveTo(-30f, size.height * 0.70f)
                cubicTo(
                    size.width * 0.35f, size.height * 0.62f,
                    size.width * 0.65f, size.height * 0.78f,
                    size.width + 40f, size.height * 0.68f
                )
            }
            // Мягкое неоновое свечение реки
            drawPath(
                path = riverPath,
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        Color(0xFF0284C7).copy(alpha = 0.18f),
                        Color(0xFF0369A1).copy(alpha = 0.35f),
                        Color(0xFF00F0FF).copy(alpha = 0.15f)
                    )
                ),
                style = Stroke(width = 24.dp.toPx(), cap = StrokeCap.Round)
            )
            // Тонкая водная нить реки
            drawPath(
                path = riverPath,
                color = Color(0xFF38BDF8).copy(alpha = 0.40f),
                style = Stroke(width = 1.6.dp.toPx(), cap = StrokeCap.Round)
            )

            // 2. Органические мягкие траектории городских переулков
            val street1 = Path().apply {
                moveTo(size.width * 0.18f, 0f)
                cubicTo(size.width * 0.28f, size.height * 0.35f, size.width * 0.36f, size.height * 0.65f, size.width * 0.46f, size.height)
            }
            drawPath(path = street1, color = Color.White.copy(alpha = 0.035f), style = Stroke(width = 1.8.dp.toPx(), cap = StrokeCap.Round))

            val street2 = Path().apply {
                moveTo(size.width * 0.82f, 0f)
                cubicTo(size.width * 0.72f, size.height * 0.35f, size.width * 0.62f, size.height * 0.65f, size.width * 0.56f, size.height)
            }
            drawPath(path = street2, color = Color.White.copy(alpha = 0.03f), style = Stroke(width = 1.5.dp.toPx(), cap = StrokeCap.Round))

            // Тонкие атмосферные названия улиц прямо на карте
            val streetPaint = android.graphics.Paint().apply {
                color = android.graphics.Color.argb(75, 148, 163, 184)
                textSize = 28f
                isAntiAlias = true
                letterSpacing = 0.15f
                typeface = android.graphics.Typeface.MONOSPACE
            }
            drawContext.canvas.nativeCanvas.drawText("ПОКРОВКА", size.width * 0.10f, size.height * 0.15f, streetPaint)
            drawContext.canvas.nativeCanvas.drawText("ЧИСТЫЕ ПРУДЫ", size.width * 0.55f, size.height * 0.22f, streetPaint)
            drawContext.canvas.nativeCanvas.drawText("р. ЯУЗА • НАБЕРЕЖНАЯ", size.width * 0.08f, size.height * 0.74f, streetPaint)

            // 3. Дышащие органические изолинии звука
            val topoDrift = sin(laserPhotonProgress * 2f * Math.PI.toFloat()) * 5.dp.toPx()

            val topoWave1 = Path().apply {
                moveTo(-20f, size.height * 0.42f + topoDrift)
                cubicTo(
                    size.width * 0.25f, size.height * 0.34f + topoDrift,
                    size.width * 0.60f, size.height * 0.48f + topoDrift,
                    size.width + 30f, size.height * 0.38f + topoDrift
                )
            }
            drawPath(
                path = topoWave1,
                brush = Brush.horizontalGradient(
                    colors = listOf(Color.Transparent, Color(0xFF38BDF8).copy(alpha = 0.20f), Color.Transparent)
                ),
                style = Stroke(width = 1.4.dp.toPx(), cap = StrokeCap.Round)
            )

            val topoWave2 = Path().apply {
                moveTo(-20f, size.height * 0.46f - topoDrift * 0.8f)
                cubicTo(
                    size.width * 0.28f, size.height * 0.38f - topoDrift * 0.8f,
                    size.width * 0.65f, size.height * 0.52f - topoDrift * 0.8f,
                    size.width + 30f, size.height * 0.42f - topoDrift * 0.8f
                )
            }
            drawPath(
                path = topoWave2,
                brush = Brush.horizontalGradient(
                    colors = listOf(Color.Transparent, Color(0xFF38BDF8).copy(alpha = 0.14f), Color.Transparent)
                ),
                style = Stroke(width = 1.0.dp.toPx(), cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 8f), 0f))
            )

            val topoWave3 = Path().apply {
                moveTo(-20f, size.height * 0.36f + topoDrift * 0.6f)
                cubicTo(
                    size.width * 0.22f, size.height * 0.28f + topoDrift * 0.6f,
                    size.width * 0.55f, size.height * 0.40f + topoDrift * 0.6f,
                    size.width + 30f, size.height * 0.32f + topoDrift * 0.6f
                )
            }
            drawPath(
                path = topoWave3,
                brush = Brush.horizontalGradient(
                    colors = listOf(Color.Transparent, Color(0xFFF59E0B).copy(alpha = 0.15f), Color.Transparent)
                ),
                style = Stroke(width = 1.2.dp.toPx(), cap = StrokeCap.Round)
            )

            // 4. Тепловые импульсы дыхания
            thermalPatches.forEach { patch ->
                val px = patch.x * size.width
                val py = patch.y * size.height
                val alpha = (1.0f - (patch.ageSec / 15f)).coerceIn(0f, 1f)
                if (alpha > 0f) {
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(Color(0xFFFF9988).copy(alpha = 0.30f * alpha), Color(0xFFFFB800).copy(alpha = 0.12f * alpha), Color.Transparent),
                            center = Offset(px, py),
                            radius = 44.dp.toPx()
                        ),
                        radius = 44.dp.toPx(),
                        center = Offset(px, py)
                    )
                }
            }

            // 5. Точка Лагранжа
            if (lagrangePoint != null && lagrangePoint.active) {
                val radL = Math.toRadians(lagrangePoint.angleDegrees.toDouble())
                val rL = maxR * lagrangePoint.distanceRatio.coerceIn(0.24f, 0.92f)
                val lx = center.x + (rL * cos(radL)).toFloat()
                val ly = center.y + (rL * sin(radL)).toFloat()
                val lPos = Offset(lx, ly)

                drawCircle(color = EchoTokens.Gold.copy(alpha = 0.25f * neonPulse), radius = 22.dp.toPx() * neonPulse, center = lPos)
                drawCircle(color = EchoTokens.Gold, radius = 5.dp.toPx(), center = lPos)
                drawCircle(color = Color(0xFF07090E), radius = 2.dp.toPx(), center = lPos)
            }

            // 5.5 АКУСТИЧЕСКИЕ СОЗВЕЗДИЯ (ACOUSTIC CONSTELLATIONS)
            constellationEdges.forEach { edge ->
                val isLit = edge.isConnectedToSelected
                val baseAlpha = if (selectedVinyl != null && selectedVinyl.id != "none") {
                    if (isLit) 0.85f else 0.07f
                } else {
                    if (edge.resonance >= 85) 0.32f else 0.18f
                }

                val edgeColor = when {
                    edge.resonance >= 90 -> EchoTokens.Gold
                    edge.resonance >= 75 -> EchoTokens.VinylAmber
                    else -> EchoTokens.SignalCyan
                }

                // Внешнее свечение нити созвездия
                if (isLit) {
                    drawLine(
                        color = edgeColor.copy(alpha = 0.25f * neonPulse),
                        start = edge.posA,
                        end = edge.posB,
                        strokeWidth = 4.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                }

                // Тончайшая небесная нить
                drawLine(
                    color = edgeColor.copy(alpha = baseAlpha * (if (isLit) neonPulse else 0.8f)),
                    start = edge.posA,
                    end = edge.posB,
                    strokeWidth = if (isLit) 2.0.dp.toPx() else 0.9.dp.toPx(),
                    cap = StrokeCap.Round,
                    pathEffect = if (!isLit) PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f) else null
                )

                // Блуждающие кванты звездной пыли вдоль созвездий
                val hash = Math.abs((edge.idA + edge.idB).hashCode()) % 100
                val phase = (laserPhotonProgress * 0.7f + (hash / 100f)) % 1f
                val starX = edge.posA.x + (edge.posB.x - edge.posA.x) * phase
                val starY = edge.posA.y + (edge.posB.y - edge.posA.y) * phase
                val starPos = Offset(starX, starY)

                drawCircle(
                    color = edgeColor.copy(alpha = baseAlpha * 0.6f),
                    radius = if (isLit) 3.5.dp.toPx() else 2.0.dp.toPx(),
                    center = starPos
                )
                drawCircle(
                    color = Color.White.copy(alpha = baseAlpha),
                    radius = if (isLit) 1.8.dp.toPx() else 1.0.dp.toPx(),
                    center = starPos
                )
            }

            // 6. Акустический луч и водные волны к пластинкам
            displayVinyls.forEach { vinyl ->
                val isFocused = selectedVinyl?.id == vinyl.id
                val pos = positions[vinyl.id] ?: center
                val isHighRes = vinyl.compatibility >= 80
                val isLowRes = vinyl.compatibility < 50

                // ВОЛНЫ НА ВОДЕ (Вариант 3): Нежные расходящиеся круги для резонансных точек (>= 80%)
                if (isHighRes && !vinyl.isGhost) {
                    val phaseOffset = (Math.abs(vinyl.id.hashCode()) % 100) / 100f
                    val ripplePhase = (laserPhotonProgress * 1.3f + phaseOffset) % 1f
                    val rippleRadius = 22.dp.toPx() + (22.dp.toPx() * ripplePhase)
                    val rippleAlpha = (1f - ripplePhase) * 0.22f
                    drawCircle(
                        color = EchoTokens.Gold.copy(alpha = rippleAlpha),
                        radius = rippleRadius,
                        center = pos,
                        style = Stroke(width = 0.9.dp.toPx())
                    )
                }

                // Элегантный неоновый луч созвучия к выбранному треку (без извилистых червячков!)
                if (isFocused) {
                    if (isLowRes) {
                        drawLine(
                            color = Color(0xFF64748B).copy(alpha = 0.45f),
                            start = center,
                            end = pos,
                            strokeWidth = 1.2.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f)
                        )
                    } else {
                        val beamColor = if (isHighRes) EchoTokens.Gold else EchoTokens.SignalCyan
                        // Мягкое широкое свечение луча
                        drawLine(
                            color = beamColor.copy(alpha = 0.20f),
                            start = center,
                            end = pos,
                            strokeWidth = 5.dp.toPx(),
                            cap = StrokeCap.Round
                        )
                        // Яркое тонкое ядро луча
                        drawLine(
                            brush = Brush.linearGradient(
                                colors = listOf(EchoTokens.SignalCyan.copy(alpha = 0.4f), beamColor),
                                start = center,
                                end = pos
                            ),
                            start = center,
                            end = pos,
                            strokeWidth = 1.6.dp.toPx(),
                            cap = StrokeCap.Round,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f), 0f)
                        )
                        // Светящийся квант звука, плавно скользящий по лучу
                        val photonX = center.x + (pos.x - center.x) * laserPhotonProgress
                        val photonY = center.y + (pos.y - center.y) * laserPhotonProgress
                        drawCircle(color = beamColor.copy(alpha = 0.45f), radius = 3.5.dp.toPx(), center = Offset(photonX, photonY))
                        drawCircle(color = Color.White, radius = 1.5.dp.toPx(), center = Offset(photonX, photonY))
                    }
                }
            }

            // 7. Рассветный режим
            if (isDawnMode) {
                for (i in 0 until 30) {
                    val rx = ((i * 73 + (neonPulse * 120).toInt()) % size.width.toInt()).toFloat()
                    val ry = ((i * 127 + (neonPulse * 90).toInt()) % size.height.toInt()).toFloat()
                    drawCircle(color = Color(0xFFFDE68A).copy(alpha = 0.08f), radius = 1.4.dp.toPx(), center = Offset(rx, ry))
                }
            }

            // 8. Центр: Органический световой маяк слушателя (без жестких кругов-мишеней)
            val userWaveR = 12.dp.toPx() + (32.dp.toPx() * ((laserPhotonProgress * 1.5f) % 1f))
            val userWaveAlpha = (1f - ((laserPhotonProgress * 1.5f) % 1f)) * 0.22f
            drawCircle(
                color = EchoTokens.SignalCyan.copy(alpha = userWaveAlpha),
                radius = userWaveR,
                center = center,
                style = Stroke(width = 1.dp.toPx())
            )

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(EchoTokens.SignalCyan.copy(alpha = 0.20f * neonPulse), Color.Transparent),
                    center = center,
                    radius = 32.dp.toPx() * neonPulse
                ),
                radius = 32.dp.toPx() * neonPulse,
                center = center
            )
            drawCircle(color = Color(0xFF04060A), radius = 12.dp.toPx(), center = center)
            drawCircle(
                brush = Brush.linearGradient(
                    colors = listOf(Color(0xFF0284C7), EchoTokens.SignalCyan),
                    start = Offset(center.x - 12.dp.toPx(), center.y - 12.dp.toPx()),
                    end = Offset(center.x + 12.dp.toPx(), center.y + 12.dp.toPx())
                ),
                radius = 12.dp.toPx(),
                center = center,
                style = Stroke(width = 1.6.dp.toPx())
            )
            drawCircle(color = EchoTokens.SignalCyan, radius = 4.dp.toPx(), center = center)
        }

        // 9. СВЕТЯЩИЕСЯ ВИНИЛОВЫЕ ОРБЫ НАД КАРТОЙ (Чистый Slow Tech, без нагромождения стикеров!)
        displayVinyls.forEach { vinyl ->
            val pos = positions[vinyl.id]
            if (pos != null) {
                FloatingMusicIsland(
                    vinyl = vinyl,
                    isSelected = selectedVinyl?.id == vinyl.id,
                    pos = pos,
                    neonPulse = neonPulse,
                    onClick = { onVinylSelected(vinyl) },
                    onLongClick = { onVinylLongClicked(vinyl) }
                )
            }
        }

        // 10. ЕДИНЫЙ ЭЛЕГАНТНЫЙ БЕЙДЖ ТОЛЬКО ДЛЯ ВЫБРАННОГО ТРЕКА
        selectedVinyl?.let { selected ->
            if (selected.id != "none" && selected.title.isNotBlank()) {
                val pos = positions[selected.id]
                if (pos != null) {
                    SelectedTrackCallout(
                        vinyl = selected,
                        pos = pos
                    )
                }
            }
        }
    } // Конец трансформируемого слоя холста (graphicsLayer)

    // 11. ПЛАВАЮЩАЯ КНОПКА ВОЗВРАТА [ ⌖ К себе (1.4x) ] ПРИ МАСШТАБИРОВАНИИ ИЛИ СДВИГЕ
    AnimatedVisibility(
        visible = zoom != 1f || panOffset != Offset.Zero,
        enter = fadeIn() + scaleIn(),
        exit = fadeOut() + scaleOut(),
        modifier = Modifier
            .align(Alignment.BottomEnd)
            .padding(end = 16.dp, bottom = 16.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = Color(0xF00D131F),
            border = BorderStroke(1.dp, EchoTokens.SignalCyan.copy(alpha = 0.6f)),
            shadowElevation = 8.dp,
            modifier = Modifier.clickable {
                zoom = 1f
                panOffset = Offset.Zero
                EchoSoundManager.playTick()
            }
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(text = "⌖", color = EchoTokens.SignalCyan, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Text(
                    text = "К себе ${String.format(java.util.Locale.US, "(%.1fx)", zoom)}",
                    color = Color.White,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }

    // 12. НЕБЕСНЫЙ ТИТР СОЗВЕЗДИЯ ПРИ ВЫБОРЕ ТРЕКА
    AnimatedVisibility(
        visible = selectedVinyl != null && selectedVinyl.id != "none" && selectedVinyl.title.isNotBlank(),
        enter = fadeIn() + slideInVertically { -it / 2 },
        exit = fadeOut() + slideOutVertically { -it / 2 },
        modifier = Modifier
            .align(Alignment.TopCenter)
            .padding(top = 10.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = Color(0xF2070A12),
            border = BorderStroke(0.8.dp, EchoTokens.Gold.copy(alpha = 0.5f)),
            shadowElevation = 6.dp
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(text = "✦", color = EchoTokens.Gold, fontSize = 11.sp)
                val genreText = selectedVinyl?.genre?.takeIf { it.isNotBlank() } ?: "Ночной эфир"
                Text(
                    text = "Созвездие «$genreText» · ${selectedVinyl?.compatibility}% созвучие",
                    color = Color.White,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
            }
        }
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

        drawCircle(color = Color(0xFF1E283C), radius = 8.dp.toPx(), center = pivot)
        drawCircle(color = Color(0xFFE2E8F0), radius = 4.dp.toPx(), center = pivot)

        val armPath = Path().apply {
            moveTo(pivot.x, pivot.y)
            lineTo(bend.x, bend.y)
            lineTo(headshell.x, headshell.y)
        }
        drawPath(path = armPath, color = Color(0xFFCBD5E1), style = Stroke(width = 2.2.dp.toPx(), cap = StrokeCap.Round))

        drawCircle(color = Color(0xFF0F1420), radius = 4.dp.toPx(), center = headshell)
        drawCircle(color = if (isSpinning) EchoTokens.SignalCyan else Color(0xFF64748B), radius = 2.5.dp.toPx(), center = headshell)
    }
}

// =============================================================
// КОНЦЕПТ B: АНАЛОГОВЫЙ HI-FI ТЮНЕР (ANALOG TUNER SCREEN)
// =============================================================
@Composable
fun AnalogTunerScreen(
    allVinyls: List<VinylTrack>,
    selectedVinyl: VinylTrack?,
    neonPulse: Float,
    onSelectVinyl: (VinylTrack) -> Unit,
    onOpenDeck: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val sortedVinyls = remember(allVinyls) {
        allVinyls.filter { !it.isSelf }.sortedBy { it.ringFactor }
    }
    val activeTrack = selectedVinyl ?: sortedVinyls.firstOrNull() ?: allVinyls.firstOrNull()
        ?: VinylTrack("self", "Ваш эфир", "Вы", "ЭХО", "0 м", "Слушатель", "", "", 100, 0f, 0f, 0xFF00F0FFL, isSelf = true)

    val targetFreq = remember(activeTrack) {
        88.0f + (activeTrack.ringFactor.coerceIn(0.1f, 0.95f) * 20.0f)
    }
    val animatedFreq by animateFloatAsState(
        targetValue = targetFreq,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "tunerFreq"
    )

    val infiniteTransition = rememberInfiniteTransition(label = "vinylSpinTuner")
    val platterAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "platterAngleTuner"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // 1. АНАЛОГОВАЯ СТЕКЛЯННАЯ ШКАЛА РАДИОЭФИРА (TUNER DIAL)
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = Color(0xF2070A10),
            border = BorderStroke(1.dp, EchoTokens.SignalCyan.copy(alpha = 0.35f)),
            shadowElevation = 8.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "88.0", color = Color(0xFF64748B), fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                    Text(text = "92.0", color = Color(0xFF64748B), fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                    Text(
                        text = String.format(java.util.Locale.US, "%.1f MHz", animatedFreq),
                        color = EchoTokens.SignalCyan,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(text = "102.0", color = Color(0xFF64748B), fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                    Text(text = "108.0", color = Color(0xFF64748B), fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                }

                Spacer(modifier = Modifier.height(5.dp))

                // Шкала с делениями, волной плотности радиоэфира и бегунком
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(36.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF04060A))
                        .border(0.8.dp, Color(0x22FFFFFF), RoundedCornerShape(8.dp))
                        .pointerInput(sortedVinyls) {
                            detectTapGestures { tapOffset ->
                                val ratio = (tapOffset.x / size.width).coerceIn(0f, 1f)
                                val tapFreq = 88.0f + ratio * 20.0f
                                val nearest = sortedVinyls.minByOrNull { v ->
                                    val f = 88.0f + (v.ringFactor.coerceIn(0.1f, 0.95f) * 20.0f)
                                    Math.abs(f - tapFreq)
                                }
                                if (nearest != null) {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    EchoSoundManager.playTick()
                                    onSelectVinyl(nearest)
                                }
                            }
                        }
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val w = size.width
                        val h = size.height

                        // Рисуем деления шкалы
                        for (i in 0..40) {
                            val x = (i / 40f) * w
                            val isMajor = i % 10 == 0
                            val tickH = if (isMajor) h * 0.55f else h * 0.30f
                            drawLine(
                                color = if (isMajor) Color(0x66FFFFFF) else Color(0x25FFFFFF),
                                start = Offset(x, h - tickH),
                                end = Offset(x, h),
                                strokeWidth = if (isMajor) 1.5.dp.toPx() else 0.8.dp.toPx()
                            )
                        }

                        // Рисуем волну плотности радиоэфира (RF wave)
                        val wavePath = Path().apply {
                            moveTo(0f, h * 0.85f)
                            for (xStep in 0..80) {
                                val x = (xStep / 80f) * w
                                val freqAtX = 88.0f + (x / w) * 20.0f
                                var amplitude = 0f
                                sortedVinyls.forEach { v ->
                                    val vFreq = 88.0f + (v.ringFactor.coerceIn(0.1f, 0.95f) * 20.0f)
                                    val diff = Math.abs(freqAtX - vFreq)
                                    if (diff < 1.5f) {
                                        amplitude += (1.5f - diff) / 1.5f
                                    }
                                }
                                val y = h * 0.85f - (amplitude * h * 0.65f).coerceAtMost(h * 0.75f)
                                lineTo(x, y)
                            }
                            lineTo(w, h * 0.85f)
                            close()
                        }
                        drawPath(
                            path = wavePath,
                            brush = Brush.verticalGradient(
                                colors = listOf(EchoTokens.SignalCyan.copy(alpha = 0.25f), Color.Transparent)
                            )
                        )

                        // Точки станций на шкале
                        sortedVinyls.forEach { v ->
                            val vRatio = ((88.0f + (v.ringFactor.coerceIn(0.1f, 0.95f) * 20.0f)) - 88.0f) / 20.0f
                            val px = vRatio * w
                            val isMatch = v.compatibility >= 80
                            drawCircle(
                                color = if (isMatch) EchoTokens.Gold else EchoTokens.SignalCyan,
                                radius = 2.5.dp.toPx(),
                                center = Offset(px, h * 0.35f)
                            )
                        }

                        // Красный аналоговый бегунок настройки
                        val cursorRatio = ((animatedFreq - 88.0f) / 20.0f).coerceIn(0f, 1f)
                        val cursorX = cursorRatio * w
                        drawLine(
                            color = EchoTokens.WarningRed,
                            start = Offset(cursorX, 0f),
                            end = Offset(cursorX, h),
                            strokeWidth = 2.dp.toPx(),
                            cap = StrokeCap.Round
                        )
                        drawCircle(color = EchoTokens.WarningRed, radius = 3.dp.toPx(), center = Offset(cursorX, h * 0.15f))
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "◄ НАЖИМАЙТЕ НА ШКАЛУ ДЛЯ НАСТРОЙКИ ►",
                        color = Color(0xFF64748B),
                        fontSize = 8.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = "${activeTrack.distanceLabel} · ${activeTrack.sourceApp}",
                        color = EchoTokens.VinylAmber,
                        fontSize = 8.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // 2. ЦЕНТРАЛЬНЫЙ ВИНИЛОВЫЙ СТОЛ 12" С ТОНАРМОМ
        Box(
            modifier = Modifier
                .size(220.dp)
                .clickable { onOpenDeck() },
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(218.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF07090E))
                    .border(2.dp, Color(0x22FFFFFF), CircleShape)
            )

            // Вращающийся винил с микро-дорожками
            Canvas(
                modifier = Modifier
                    .size(210.dp)
                    .graphicsLayer { rotationZ = platterAngle }
            ) {
                val radius = size.minDimension / 2f
                val c = Offset(size.width / 2f, size.height / 2f)

                drawCircle(color = Color(0xFF0F141E), radius = radius, center = c)

                for (g in 1..8) {
                    drawCircle(
                        color = Color(0xFF222B3A).copy(alpha = 0.65f),
                        radius = radius * (0.35f + g * 0.075f),
                        center = c,
                        style = Stroke(width = 0.8.dp.toPx())
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
                        center = c
                    ),
                    radius = radius,
                    center = c
                )
            }

            // Центральный лейбл с настоящей обложкой альбома
            Box(
                modifier = Modifier
                    .size(86.dp)
                    .graphicsLayer { rotationZ = platterAngle }
                    .clip(CircleShape)
                    .background(Color(activeTrack.colorHex)),
                contentAlignment = Alignment.Center
            ) {
                if (!activeTrack.coverUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = activeTrack.coverUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(text = "33⅓", color = Color.Black.copy(alpha = 0.7f), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        Text(text = "ЭХО", color = Color.Black, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
                    }
                }

                // Центральный латунный шпиндель
                Box(
                    modifier = Modifier
                        .size(13.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFE2E8F0))
                        .border(1.5.dp, Color(0xFF0F1420), CircleShape)
                )
            }

            // Физический тонарм
            TonearmOverlay(isSpinning = true)
        }

        // 3. АНАЛОГОВЫЕ СТРЕЛОЧНЫЕ VU-МЕТРЫ
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // VU 1: Резонанс созвучия
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color(0xF20B1019),
                border = BorderStroke(0.8.dp, Color(0x22FFFFFF)),
                modifier = Modifier.weight(1f)
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "РЕЗОНАНС СОЗВУЧИЯ",
                        color = Color(0xFF64748B),
                        fontSize = 8.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(26.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val w = size.width
                            val h = size.height
                            val arcCenter = Offset(w / 2f, h * 1.15f)
                            drawArc(
                                color = Color(0x33F59E0B),
                                startAngle = 180f,
                                sweepAngle = 180f,
                                useCenter = false,
                                style = Stroke(width = 1.dp.toPx())
                            )
                            val targetAngle = 180f + ((activeTrack.compatibility.toFloat() / 100f) * 180f)
                            val rad = Math.toRadians(targetAngle.toDouble())
                            val needleLen = h * 0.95f
                            val endX = arcCenter.x + (needleLen * cos(rad)).toFloat()
                            val endY = arcCenter.y + (needleLen * sin(rad)).toFloat()
                            drawLine(
                                color = EchoTokens.Gold,
                                start = arcCenter,
                                end = Offset(endX, endY),
                                strokeWidth = 1.5.dp.toPx(),
                                cap = StrokeCap.Round
                            )
                        }
                    }
                    Text(
                        text = "${activeTrack.compatibility}% СОЗВУЧИЕ",
                        color = EchoTokens.Gold,
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            // VU 2: Мощность сигнала
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color(0xF20B1019),
                border = BorderStroke(0.8.dp, Color(0x22FFFFFF)),
                modifier = Modifier.weight(1f)
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "МОЩНОСТЬ СИГНАЛА",
                        color = Color(0xFF64748B),
                        fontSize = 8.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(26.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val w = size.width
                            val h = size.height
                            val arcCenter = Offset(w / 2f, h * 1.15f)
                            drawArc(
                                color = Color(0x3300F0FF),
                                startAngle = 180f,
                                sweepAngle = 180f,
                                useCenter = false,
                                style = Stroke(width = 1.dp.toPx())
                            )
                            val rfPulse = (neonPulse * 15f)
                            val targetAngle = 220f + rfPulse
                            val rad = Math.toRadians(targetAngle.toDouble())
                            val needleLen = h * 0.95f
                            val endX = arcCenter.x + (needleLen * cos(rad)).toFloat()
                            val endY = arcCenter.y + (needleLen * sin(rad)).toFloat()
                            drawLine(
                                color = EchoTokens.SignalCyan,
                                start = arcCenter,
                                end = Offset(endX, endY),
                                strokeWidth = 1.5.dp.toPx(),
                                cap = StrokeCap.Round
                            )
                        }
                    }
                    Text(
                        text = "-42 dB · БЛИЗКО",
                        color = EchoTokens.SignalCyan,
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
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
                Intent(Intent.ACTION_VIEW, uri).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
            }
            sourceApp.contains("Яндекс", ignoreCase = true) || sourceApp.contains("Yandex", ignoreCase = true) -> {
                val uri = Uri.parse("yandexmusic://search?text=${URLEncoder.encode(query, "UTF-8")}")
                Intent(Intent.ACTION_VIEW, uri).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
            }
            sourceApp.contains("Spotify", ignoreCase = true) -> {
                val uri = Uri.parse("spotify:search:${URLEncoder.encode(query, "UTF-8")}")
                Intent(Intent.ACTION_VIEW, uri).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
            }
            else -> {
                val uri = Uri.parse("https://music.yandex.ru/search?text=${URLEncoder.encode(query, "UTF-8")}")
                Intent(Intent.ACTION_VIEW, uri).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
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

// =============================================================
// COMPOSE PREVIEWS (Для рендеринга и визуального контроля в Antigravity)
// =============================================================

@Preview(name = "Навигационный док", showBackground = true, backgroundColor = 0xFF08080C)
@Composable
fun NavigationDockPreview() {
    EchoRadarTheme {
        NavigationDock(
            currentScreen = EchoScreen.ETHER,
            onScreenSelected = {}
        )
    }
}

@Preview(name = "Онбординг (Первый запуск)", showBackground = true, backgroundColor = 0xFF08080C)
@Composable
fun OnboardingScreenPreview() {
    EchoRadarTheme {
        OnboardingScreen(
            hasNotificationPermission = false,
            hasLocationPermission = false,
            onRequestNotificationPermission = {},
            onRequestLocationPermission = {},
            onEnterEther = {}
        )
    }
}

@Preview(name = "Легенда сонара", showBackground = true, backgroundColor = 0xFF11161F)
@Composable
fun LegendRowPreview() {
    EchoRadarTheme {
        Column(modifier = Modifier.padding(16.dp)) {
            LegendRow(
                color = EchoTokens.AcousticGold,
                title = "Высокое созвучие (≥ 80%)",
                desc = "Аналоговые золотые волны резонанса"
            )
            Spacer(modifier = Modifier.height(8.dp))
            LegendRow(
                color = EchoTokens.SignalCyan,
                title = "Живой эфир",
                desc = "Прямой перехват музыки вокруг"
            )
        }
    }
}
