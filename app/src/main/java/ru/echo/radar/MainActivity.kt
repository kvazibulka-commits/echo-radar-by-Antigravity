package ru.echo.radar

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import ru.echo.radar.data.local.EchoPreferences
import ru.echo.radar.data.local.LocalChronicleStorage
import ru.echo.radar.data.remote.EchoApiClient
import ru.echo.radar.data.remote.SignalRequest
import ru.echo.radar.domain.model.*
import ru.echo.radar.service.EchoBleManager
import ru.echo.radar.service.EchoMediaListenerService
import ru.echo.radar.service.EchoSoundManager
import ru.echo.radar.ui.beacons.BeaconsScreen
import ru.echo.radar.ui.chronicle.ChronicleScreen
import ru.echo.radar.ui.components.GenArtCover
import ru.echo.radar.ui.components.IndustrialButton
import ru.echo.radar.ui.deck.DeckScreen
import ru.echo.radar.ui.radar.RadarScreen
import ru.echo.radar.ui.theme.EchoIndustrialTheme
import ru.echo.radar.ui.theme.LocalEchoColors
import java.util.UUID

enum class EchoScreen(val label: String, val icon: String) {
    ETHER("Эфир", "◎"),
    DECK("Дека", "◍"),
    CHRONICLE("Отзвуки", "∿"),
    BEACONS("Тайники", "◇")
}

class MainActivity : ComponentActivity() {

    private lateinit var soundManager: EchoSoundManager
    private lateinit var preferences: EchoPreferences
    private val chronicleStorage = LocalChronicleStorage()
    private val apiClient = EchoApiClient()

    private val activeTrackState = mutableStateOf<EchoSignal?>(null)
    private var deviceToken: String = ""

    private val trackReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            intent ?: return
            val artist = intent.getStringExtra(EchoMediaListenerService.EXTRA_ARTIST) ?: return
            val track = intent.getStringExtra(EchoMediaListenerService.EXTRA_TRACK) ?: return
            val album = intent.getStringExtra(EchoMediaListenerService.EXTRA_ALBUM) ?: ""
            val sourceStr = intent.getStringExtra(EchoMediaListenerService.EXTRA_SOURCE) ?: "YANDEX_MUSIC"
            val isPlaying = intent.getBooleanExtra(EchoMediaListenerService.EXTRA_IS_PLAYING, true)

            val source = try {
                SignalSource.valueOf(sourceStr)
            } catch (_: Exception) {
                SignalSource.YANDEX_MUSIC
            }

            val newSignal = EchoSignal(
                id = "self-track",
                artist = artist,
                track = track,
                album = album,
                source = source,
                distanceMeters = 0,
                angleDegrees = 0f,
                normalizedRadius = 0f,
                resonancePercent = 100,
                camelotKey = "8A",
                bpm = 120,
                genre = "Инди",
                isPlaying = isPlaying,
                persona = "Мой текущий сигнал",
                story = "Звучит прямо сейчас в твоих наушниках через ${source.displayName}."
            )

            activeTrackState.value = newSignal
            chronicleStorage.recordEncounter(
                LocalEncounter(
                    id = "enc-${System.currentTimeMillis()}",
                    timeLabel = "Сейчас",
                    periodLabel = "ЭФИР",
                    artist = artist,
                    track = track,
                    locationContext = "Текущий трек из ${source.displayName}",
                    resonancePercent = 100
                )
            )

            // Start BLE advertising for nearby presence
            if (isPlaying) {
                EchoBleManager.startAdvertising(this@MainActivity, deviceToken, 120, 8, true)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        soundManager = EchoSoundManager(this)
        preferences = EchoPreferences(this)
        EchoBleManager.init(this)

        val prefs = getSharedPreferences("echo_industrial_prefs", Context.MODE_PRIVATE)
        deviceToken = prefs.getString("device_token", null) ?: run {
            val gen = "echo_" + UUID.randomUUID().toString().replace("-", "").take(12)
            prefs.edit().putString("device_token", gen).apply()
            gen
        }

        val filter = IntentFilter(EchoMediaListenerService.ACTION_TRACK_CHANGED)
        registerReceiver(trackReceiver, filter, RECEIVER_EXPORTED)

        setContent {
            val coroutineScope = rememberCoroutineScope()
            val haptic = LocalHapticFeedback.current

            var isEveningMode by remember { mutableStateOf(false) }
            var currentScreen by remember { mutableStateOf(EchoScreen.ETHER) }

            // Server configuration
            var serverUrl by remember {
                mutableStateOf(prefs.getString("server_url", "http://10.0.2.2:8000") ?: "http://10.0.2.2:8000")
            }
            var isServerOnline by remember { mutableStateOf(false) }
            var showServerSettingsDialog by remember { mutableStateOf(false) }

            // Notification Access Check
            var hasNotificationAccess by remember {
                mutableStateOf(
                    NotificationManagerCompat.getEnabledListenerPackages(this@MainActivity)
                        .contains(packageName)
                )
            }

            // Runtime Permissions launcher
            val permissionLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestMultiplePermissions()
            ) { _ ->
                EchoBleManager.startScanning(this@MainActivity)
            }

            LaunchedEffect(Unit) {
                val perms = mutableListOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    perms.add(Manifest.permission.POST_NOTIFICATIONS)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    perms.add(Manifest.permission.BLUETOOTH_SCAN)
                    perms.add(Manifest.permission.BLUETOOTH_ADVERTISE)
                    perms.add(Manifest.permission.BLUETOOTH_CONNECT)
                }
                permissionLauncher.launch(perms.toTypedArray())
            }

            // Fallback rich seed signals matching Concept A
            val defaultSignals = remember {
                listOf(
                    EchoSignal(
                        id = "s1",
                        persona = "Пилигрим неоновых панелек",
                        artist = "Молчат Дома",
                        track = "Судно (Борис Рижий)",
                        genre = "Пост-панк",
                        source = SignalSource.YANDEX_MUSIC,
                        resonancePercent = 94,
                        bpm = 128,
                        camelotKey = "8A",
                        angleDegrees = 318f,
                        normalizedRadius = 0.62f,
                        label = "Судно · 94",
                        story = "Упругий бас пустых дворов и ровный стук драм-машины под мерцанием тусклых окон.",
                        hue = 212f
                    ),
                    EchoSignal(
                        id = "s2",
                        persona = "Архивариус забытых кассет",
                        artist = "Sirotkin",
                        track = "Август",
                        genre = "Инди-фолк",
                        source = SignalSource.YANDEX_MUSIC,
                        resonancePercent = 91,
                        bpm = 92,
                        camelotKey = "11B",
                        angleDegrees = 16f,
                        normalizedRadius = 0.82f,
                        story = "Тёплые гитарные реверберации превращают суету вечерней улицы в медленный ночной фильм.",
                        hue = 32f
                    ),
                    EchoSignal(
                        id = "s3",
                        persona = "Ловец ночных дождей",
                        artist = "Burial",
                        track = "Archangel",
                        genre = "Future garage",
                        source = SignalSource.SPOTIFY,
                        resonancePercent = 88,
                        bpm = 138,
                        camelotKey = "2A",
                        angleDegrees = 64f,
                        normalizedRadius = 0.55f,
                        story = "Шорохи винила и звуки зажигалки в пустом ночном автобусе Южного Лондона.",
                        hue = 268f
                    ),
                    EchoSignal(
                        id = "s4",
                        persona = "Хранитель полуночных костров",
                        artist = "КИНО",
                        track = "Звезда по имени Солнце",
                        genre = "Русский рок",
                        source = SignalSource.VK_MUSIC,
                        resonancePercent = 96,
                        bpm = 118,
                        camelotKey = "9A",
                        angleDegrees = 142f,
                        normalizedRadius = 0.78f,
                        label = "Звезда · 96",
                        story = "Лаконичный гитарный перебор звучит как пульс целого поколения.",
                        hue = 44f
                    ),
                    EchoSignal(
                        id = "s5",
                        persona = "Хранитель сонных созвездий",
                        artist = "Beach House",
                        track = "Space Song",
                        genre = "Dream pop",
                        source = SignalSource.YANDEX_MUSIC,
                        resonancePercent = 84,
                        bpm = 147,
                        camelotKey = "5B",
                        angleDegrees = 202f,
                        normalizedRadius = 0.60f,
                        story = "Тёплый органный аккорд и винтажная драм-машина убаюкивают вечер.",
                        hue = 310f
                    ),
                    EchoSignal(
                        id = "s6",
                        persona = "Мастер акустической тишины",
                        artist = "Nils Frahm",
                        track = "Says",
                        genre = "Неоклассика",
                        source = SignalSource.ZVUK,
                        resonancePercent = 79,
                        bpm = 68,
                        camelotKey = "4A",
                        angleDegrees = 250f,
                        normalizedRadius = 0.84f,
                        story = "Стук молоточков фортепиано и дыхание зала: механика инструмента становится живой.",
                        hue = 190f
                    ),
                    EchoSignal(
                        id = "s7",
                        persona = "Ловец виниловых волн",
                        artist = "Pompeya",
                        track = "90",
                        genre = "Dream groove",
                        source = SignalSource.YANDEX_MUSIC,
                        resonancePercent = 82,
                        bpm = 110,
                        camelotKey = "7B",
                        angleDegrees = 312f,
                        normalizedRadius = 0.50f,
                        story = "Мягкий фанковый бас и ностальгия калифорнийского заката в наушниках.",
                        hue = 24f
                    )
                )
            }

            var signals by remember { mutableStateOf(defaultSignals) }

            // Venue Beacons
            val defaultVenues = remember {
                listOf(
                    VenueBeacon(
                        id = "venue",
                        name = "Кофейня «Соль»",
                        venueType = VenueType.COFFEE,
                        currentTrack = "Судно (Борис Рижий)",
                        currentArtist = "Молчат Дома",
                        streamingSource = "Яндекс Музыка",
                        distanceMeters = 120,
                        minResonancePercent = 75,
                        proofCode = "СУДНО",
                        discountDescription = "Фильтр-кофе −20% за созвучие",
                        angleDegrees = 100f,
                        normalizedRadius = 0.44f,
                        label = "Соль · 120 м",
                        whisper = "Сегодня здесь варят кофе под пост-панк. Скажите бариста кодовое слово — и получите чашку с созвучием."
                    ),
                    VenueBeacon(
                        id = "book",
                        name = "Дом книги на Сретенке",
                        venueType = VenueType.BOOKSHOP,
                        currentTrack = "Август",
                        currentArtist = "Sirotkin",
                        streamingSource = "Яндекс Музыка",
                        distanceMeters = 240,
                        minResonancePercent = 60,
                        proofCode = "АВГУСТ",
                        discountDescription = "Закладка ручной работы внутри",
                        angleDegrees = 172f,
                        normalizedRadius = 0.92f,
                        label = "Дом книги · 240 м",
                        whisper = "Тот, кто листает страницы под акустику, уносит с собой целый вечер."
                    )
                )
            }
            var venueBeacons by remember { mutableStateOf(defaultVenues) }

            // Time Capsules
            val defaultCapsules = remember {
                listOf(
                    TimeCapsule(
                        id = "patriarch",
                        artist = "Nils Frahm",
                        track = "Says",
                        message = "Тот, кто слушает дождь, уже дома.",
                        distanceMeters = 410,
                        locationName = "Тайник у Патриарших",
                        angleDegrees = 286f,
                        normalizedRadius = 0.36f,
                        reward = "Шёпот · без замка"
                    ),
                    TimeCapsule(
                        id = "vinyl",
                        artist = "Burial",
                        track = "Archangel",
                        message = "Полка с электроникой. Игла уже опущена — осталось совпасть.",
                        distanceMeters = 620,
                        locationName = "Виниловая лавка",
                        minResonancePercent = 80,
                        isLocked = true,
                        reward = "−15% на пластинку"
                    )
                )
            }
            var timeCapsules by remember { mutableStateOf(defaultCapsules) }

            var selectedSignal by remember { mutableStateOf<EchoSignal?>(signals.firstOrNull()) }
            var isSyncedWithSignal by remember { mutableStateOf(false) }
            val encounters by chronicleStorage.encounters.collectAsState()

            // Active Modals
            var activePassportSignal by remember { mutableStateOf<EchoSignal?>(null) }
            var activeBaristaModalVenue by remember { mutableStateOf<VenueBeacon?>(null) }
            var showNightDropModal by remember { mutableStateOf(false) }

            // ═══════════════════════════════════════════════════════
            // LIVE SERVER SYNC COROUTINE (Every 3.5 seconds)
            // ═══════════════════════════════════════════════════════
            LaunchedEffect(serverUrl) {
                while (true) {
                    try {
                        val state = apiClient.fetchState(
                            baseUrl = serverUrl,
                            deviceToken = deviceToken,
                            lat = 55.760,
                            lon = 37.640
                        )

                        if (state != null && state.status == "ok") {
                            isServerOnline = true
                            if (state.signals.isNotEmpty()) {
                                signals = state.signals.mapIndexed { idx, netSig ->
                                    EchoSignal(
                                        id = netSig.id,
                                        artist = netSig.artist,
                                        track = netSig.track,
                                        album = netSig.album,
                                        source = SignalSource.values().firstOrNull { it.displayName == netSig.source } ?: SignalSource.YANDEX_MUSIC,
                                        distanceMeters = netSig.distanceMeters,
                                        angleDegrees = (idx * 51f) % 360f,
                                        normalizedRadius = 0.35f + (idx * 0.12f) % 0.55f,
                                        resonancePercent = netSig.resonancePercent,
                                        camelotKey = netSig.camelot,
                                        bpm = netSig.bpm,
                                        genre = netSig.genre,
                                        isPlaying = netSig.isPlaying,
                                        persona = "Слушатель на связи",
                                        story = "Сигнал транслируется в реальном времени с сервера ЭХО.",
                                        label = "${netSig.track.take(8)} · ${netSig.resonancePercent}",
                                        hue = (idx * 60f) % 360f
                                    )
                                }
                            }
                        } else {
                            isServerOnline = false
                        }
                    } catch (_: Exception) {
                        isServerOnline = false
                    }

                    // Push playing track to server if active
                    val currentTrack = activeTrackState.value
                    if (currentTrack != null && currentTrack.artist != "Ожидание музыки...") {
                        try {
                            apiClient.sendSignal(
                                baseUrl = serverUrl,
                                req = SignalRequest(
                                    deviceToken = deviceToken,
                                    artist = currentTrack.artist,
                                    track = currentTrack.track,
                                    album = currentTrack.album,
                                    source = currentTrack.source.displayName,
                                    latitude = 55.760,
                                    longitude = 37.640,
                                    bpm = currentTrack.bpm,
                                    camelot = currentTrack.camelotKey,
                                    genre = currentTrack.genre,
                                    isPlaying = currentTrack.isPlaying
                                )
                            )
                        } catch (_: Exception) {}
                    }

                    delay(3500)
                }
            }

            EchoIndustrialTheme(isEvening = isEveningMode) {
                val colors = LocalEchoColors.current

                Scaffold(
                    bottomBar = {
                        Column {
                            // Mini Player (52dp) with live audio level visualizer
                            selectedSignal?.let { sig ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(52.dp)
                                        .background(colors.surface)
                                        .border(width = 0.5.dp, color = colors.line)
                                        .clickable {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            soundManager.playTactileClick()
                                            currentScreen = EchoScreen.DECK
                                        }
                                        .padding(horizontal = 15.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(9.dp)
                                ) {
                                    GenArtCover(
                                        hue = sig.hue,
                                        modifier = Modifier.size(32.dp)
                                    )

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "СЕЙЧАС ИГРАЕТ · ${sig.source.displayName.uppercase()}",
                                            color = colors.faint,
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 6.5.sp,
                                            letterSpacing = 0.6.sp
                                        )
                                        Text(
                                            text = "${sig.artist} — ${sig.track}",
                                            color = colors.ink,
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 9.5.sp,
                                            maxLines = 1
                                        )
                                    }

                                    // Dancing VU meter indicator
                                    LiveMiniVisualizer(isPlaying = isSyncedWithSignal)

                                    Box(
                                        modifier = Modifier
                                            .size(28.dp)
                                            .clip(CircleShape)
                                            .border(1.dp, colors.line, CircleShape)
                                            .clickable {
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                isSyncedWithSignal = !isSyncedWithSignal
                                                if (isSyncedWithSignal) soundManager.playNeedleDrop() else soundManager.playTactileClick()
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = if (isSyncedWithSignal) "⏸" else "▶",
                                            color = colors.ink,
                                            fontSize = 10.sp
                                        )
                                    }
                                }
                            }

                            // Tabbar (58dp)
                            EchoAtlasTabBar(
                                currentScreen = currentScreen,
                                onScreenChange = { screen ->
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    soundManager.playTactileClick()
                                    currentScreen = screen
                                }
                            )
                        }
                    }
                ) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        when (currentScreen) {
                            EchoScreen.ETHER -> {
                                RadarScreen(
                                    signals = signals,
                                    venueBeacons = venueBeacons,
                                    selectedSignal = selectedSignal,
                                    isEveningMode = isEveningMode,
                                    isServerOnline = isServerOnline,
                                    serverUrl = serverUrl,
                                    hasNotificationAccess = hasNotificationAccess,
                                    onRequestNotificationAccess = {
                                        soundManager.playTactileClick()
                                        startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                                    },
                                    onOpenServerSettings = {
                                        soundManager.playTactileClick()
                                        showServerSettingsDialog = true
                                    },
                                    onToggleEveningMode = {
                                        isEveningMode = !isEveningMode
                                        soundManager.playTactileClick()
                                    },
                                    onSignalSelect = { sig ->
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        soundManager.playSonarPing()
                                        selectedSignal = sig
                                        activePassportSignal = sig
                                    },
                                    onBeaconSelect = { venue ->
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        soundManager.playSonarPing()
                                        activeBaristaModalVenue = venue
                                    },
                                    onOpenNightDrop = {
                                        soundManager.playTactileClick()
                                        showNightDropModal = true
                                    }
                                )
                            }
                            EchoScreen.DECK -> {
                                DeckScreen(
                                    signal = selectedSignal,
                                    isSynced = isSyncedWithSignal,
                                    onToggleSync = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        isSyncedWithSignal = !isSyncedWithSignal
                                        if (isSyncedWithSignal) soundManager.playNeedleDrop() else soundManager.playTactileClick()
                                    },
                                    onSendPulse = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        soundManager.playSonarPing(880.0)
                                    },
                                    onOpenStreaming = {},
                                    onOpenCustomization = {}
                                )
                            }
                            EchoScreen.CHRONICLE -> {
                                ChronicleScreen(encounters = encounters)
                            }
                            EchoScreen.BEACONS -> {
                                BeaconsScreen(
                                    venueBeacons = venueBeacons,
                                    timeCapsules = timeCapsules,
                                    userResonancePercent = selectedSignal?.resonancePercent ?: 94,
                                    onShowBaristaCode = { venue ->
                                        soundManager.playSonarPing()
                                        activeBaristaModalVenue = venue
                                    },
                                    onCreateCapsule = {
                                        soundManager.playTactileClick()
                                    },
                                    onClaimVenueB2B = {
                                        soundManager.playTactileClick()
                                    }
                                )
                            }
                        }
                    }
                }

                // Modal: Звуковой паспорт сигнала
                activePassportSignal?.let { sig ->
                    Dialog(onDismissRequest = { activePassportSignal = null }) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(colors.surface)
                                .border(1.dp, colors.line, RoundedCornerShape(12.dp))
                                .padding(18.dp)
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    GenArtCover(hue = sig.hue, modifier = Modifier.size(44.dp))
                                    Column {
                                        Text(
                                            text = "ЗВУКОВОЙ ПАСПОРТ",
                                            color = colors.faint,
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 7.5.sp,
                                            letterSpacing = 1.sp
                                        )
                                        Text(
                                            text = sig.track,
                                            color = colors.ink,
                                            fontFamily = FontFamily.Serif,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 16.sp
                                        )
                                        Text(
                                            text = "${sig.artist} · ${sig.genre}",
                                            color = colors.sub,
                                            fontSize = 9.sp
                                        )
                                    }
                                }

                                Text(
                                    text = "${sig.persona.uppercase()} · РЕЗОНАНС ${sig.resonancePercent}%",
                                    color = colors.accent,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 7.5.sp,
                                    letterSpacing = 0.8.sp
                                )

                                Text(
                                    text = sig.story,
                                    color = colors.sub,
                                    fontSize = 9.5.sp,
                                    lineHeight = 14.sp
                                )

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .border(0.5.dp, colors.line)
                                        .padding(vertical = 6.dp),
                                    horizontalArrangement = Arrangement.SpaceEvenly
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text("РЕЗОНАНС", color = colors.faint, fontFamily = FontFamily.Monospace, fontSize = 6.5.sp)
                                        Text("${sig.resonancePercent}%", color = colors.ink, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    }
                                    Box(modifier = Modifier.width(0.5.dp).height(24.dp).background(colors.line))
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text("ТЕМП", color = colors.faint, fontFamily = FontFamily.Monospace, fontSize = 6.5.sp)
                                        Text("${sig.bpm}", color = colors.ink, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    }
                                    Box(modifier = Modifier.width(0.5.dp).height(24.dp).background(colors.line))
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text("CAMELOT", color = colors.faint, fontFamily = FontFamily.Monospace, fontSize = 6.5.sp)
                                        Text(sig.camelotKey, color = colors.ink, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    }
                                }

                                IndustrialButton(
                                    text = "Слушать в ${sig.source.displayName}",
                                    onClick = { activePassportSignal = null },
                                    isPrimary = true
                                )
                            }
                        }
                    }
                }

                // Modal: Barista Code Screen
                activeBaristaModalVenue?.let { venue ->
                    Dialog(onDismissRequest = { activeBaristaModalVenue = null }) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(colors.surface)
                                .border(1.dp, colors.gold, RoundedCornerShape(12.dp))
                                .padding(18.dp)
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text(
                                    text = "PROOF-OF-MUSIC // ТАЙНИК",
                                    color = colors.gold,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 7.5.sp,
                                    letterSpacing = 1.sp
                                )
                                Text(
                                    text = venue.name,
                                    color = colors.ink,
                                    fontFamily = FontFamily.Serif,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 18.sp
                                )

                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(colors.surface2)
                                        .border(1.dp, colors.gold.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                                        .padding(14.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(
                                            text = "СЕКРЕТНЫЙ КОД БАРИСТА:",
                                            color = colors.faint,
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 7.5.sp
                                        )
                                        Text(
                                            text = venue.proofCode,
                                            color = colors.accent2,
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.Black,
                                            fontSize = 26.sp,
                                            letterSpacing = 2.sp
                                        )
                                        Text(
                                            text = venue.discountDescription,
                                            color = colors.ink,
                                            fontSize = 10.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(top = 4.dp)
                                        )
                                    }
                                }

                                Text(
                                    text = venue.whisper,
                                    color = colors.sub,
                                    fontSize = 9.sp,
                                    lineHeight = 13.sp
                                )

                                IndustrialButton(
                                    text = "Закрыть",
                                    onClick = { activeBaristaModalVenue = null },
                                    isPrimary = true
                                )
                            }
                        }
                    }
                }

                // Modal: Night Drop
                if (showNightDropModal) {
                    Dialog(onDismissRequest = { showNightDropModal = false }) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(colors.surface)
                                .border(1.dp, colors.line, RoundedCornerShape(12.dp))
                                .padding(18.dp)
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text(
                                    text = "НОЧНОЙ ДРОП · 00:00",
                                    color = colors.gold,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 7.5.sp,
                                    letterSpacing = 1.sp
                                )
                                Text(
                                    text = "Burial — Untrue",
                                    color = colors.ink,
                                    fontFamily = FontFamily.Serif,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 18.sp
                                )
                                Text(
                                    text = "Эксклюзивная премьера фрагмента откроется на набережной ровно в 00:00 — за 12 часов до мирового релиза. Никаких уведомлений: просто приходи в точку эфира.",
                                    color = colors.sub,
                                    fontSize = 9.5.sp,
                                    lineHeight = 14.sp
                                )
                                IndustrialButton(
                                    text = "Понятно",
                                    onClick = { showNightDropModal = false },
                                    isPrimary = true
                                )
                            }
                        }
                    }
                }

                // Modal: Server Settings
                if (showServerSettingsDialog) {
                    var editUrl by remember { mutableStateOf(serverUrl) }
                    Dialog(onDismissRequest = { showServerSettingsDialog = false }) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(colors.surface)
                                .border(1.dp, colors.line, RoundedCornerShape(12.dp))
                                .padding(18.dp)
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text(
                                    text = "НАСТРОЙКА СЕРВЕРА ЭХО",
                                    color = colors.ink,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                                Text(
                                    text = "Укажите адрес вашего Python-сервера (echo_server.py):",
                                    color = colors.sub,
                                    fontSize = 9.5.sp
                                )
                                OutlinedTextField(
                                    value = editUrl,
                                    onValueChange = { editUrl = it },
                                    label = { Text("URL сервера (например, http://192.168.0.214:8000)") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    IndustrialButton(
                                        text = "Сохранить",
                                        onClick = {
                                            serverUrl = editUrl.trim()
                                            prefs.edit().putString("server_url", serverUrl).apply()
                                            showServerSettingsDialog = false
                                        },
                                        isPrimary = true,
                                        modifier = Modifier.weight(1f)
                                    )
                                    IndustrialButton(
                                        text = "Отмена",
                                        onClick = { showServerSettingsDialog = false },
                                        isPrimary = false,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(trackReceiver)
        EchoBleManager.stopAdvertising()
        EchoBleManager.stopScanning()
    }
}

@Composable
fun LiveMiniVisualizer(isPlaying: Boolean) {
    val colors = LocalEchoColors.current
    val infiniteTransition = rememberInfiniteTransition(label = "vu_bars")

    val h1 by infiniteTransition.animateFloat(
        initialValue = 0.2f, targetValue = 0.9f,
        animationSpec = infiniteRepeatable(tween(450, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "h1"
    )
    val h2 by infiniteTransition.animateFloat(
        initialValue = 0.8f, targetValue = 0.3f,
        animationSpec = infiniteRepeatable(tween(550, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "h2"
    )
    val h3 by infiniteTransition.animateFloat(
        initialValue = 0.3f, targetValue = 1.0f,
        animationSpec = infiniteRepeatable(tween(400, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "h3"
    )

    Row(
        modifier = Modifier.height(14.dp).width(16.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        val factor1 = if (isPlaying) h1 else 0.2f
        val factor2 = if (isPlaying) h2 else 0.2f
        val factor3 = if (isPlaying) h3 else 0.2f

        Box(modifier = Modifier.width(2.5.dp).fillMaxHeight(factor1).background(colors.live, RoundedCornerShape(1.dp)))
        Box(modifier = Modifier.width(2.5.dp).fillMaxHeight(factor2).background(colors.live, RoundedCornerShape(1.dp)))
        Box(modifier = Modifier.width(2.5.dp).fillMaxHeight(factor3).background(colors.live, RoundedCornerShape(1.dp)))
    }
}

@Composable
fun EchoAtlasTabBar(
    currentScreen: EchoScreen,
    onScreenChange: (EchoScreen) -> Unit
) {
    val colors = LocalEchoColors.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(58.dp)
            .background(colors.background)
            .border(width = 0.5.dp, color = colors.line),
        horizontalArrangement = Arrangement.SpaceAround,
        verticalAlignment = Alignment.CenterVertically
    ) {
        EchoScreen.values().forEach { screen ->
            val isSelected = screen == currentScreen
            val contentColor = if (isSelected) colors.ink else colors.faint

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clickable { onScreenChange(screen) },
                contentAlignment = Alignment.Center
            ) {
                if (isSelected) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .width(18.dp)
                            .height(2.dp)
                            .background(colors.accent)
                    )
                }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = screen.icon,
                        color = contentColor,
                        fontSize = 14.sp
                    )
                    Text(
                        text = screen.label,
                        color = contentColor,
                        fontSize = 8.sp,
                        letterSpacing = 0.3.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }
    }
}
