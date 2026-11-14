package ru.echo.radar

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.launch
import ru.echo.radar.data.local.EchoPreferences
import ru.echo.radar.data.local.LocalChronicleStorage
import ru.echo.radar.domain.model.*
import ru.echo.radar.service.EchoMediaListenerService
import ru.echo.radar.service.EchoSoundManager
import ru.echo.radar.ui.beacons.BeaconsScreen
import ru.echo.radar.ui.chronicle.ChronicleScreen
import ru.echo.radar.ui.components.IndustrialBadge
import ru.echo.radar.ui.components.IndustrialButton
import ru.echo.radar.ui.deck.DeckScreen
import ru.echo.radar.ui.radar.RadarScreen
import ru.echo.radar.ui.theme.EchoIndustrialTheme
import ru.echo.radar.ui.theme.LocalEchoColors

enum class EchoScreen(val label: String, val icon: String) {
    ETHER("ЭФИР", "📡"),
    DECK("ДЕКА", "💿"),
    CHRONICLE("ХРОНИКА", "📜"),
    BEACONS("ТАЙНИКИ", "📍")
}

class MainActivity : ComponentActivity() {

    private lateinit var soundManager: EchoSoundManager
    private lateinit var preferences: EchoPreferences
    private val chronicleStorage = LocalChronicleStorage()

    // Active track state
    private val activeTrackState = mutableStateOf<EchoSignal?>(null)

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
                isPlaying = isPlaying
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
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        soundManager = EchoSoundManager(this)
        preferences = EchoPreferences(this)

        val filter = IntentFilter(EchoMediaListenerService.ACTION_TRACK_CHANGED)
        registerReceiver(trackReceiver, filter, RECEIVER_EXPORTED)

        setContent {
            val scope = rememberCoroutineScope()
            var isEveningMode by remember { mutableStateOf(false) }
            var currentScreen by remember { mutableStateOf(EchoScreen.ETHER) }

            // Default signal seeds for realistic radar experience
            var signals by remember {
                mutableStateOf(
                    listOf(
                        EchoSignal(
                            id = "sig-1",
                            artist = "Дайте танк (!)",
                            track = "Смешно",
                            album = "Человеко-часы (2020)",
                            source = SignalSource.YANDEX_MUSIC,
                            distanceMeters = 65,
                            angleDegrees = 45f,
                            normalizedRadius = 0.35f,
                            resonancePercent = 88,
                            camelotKey = "8A",
                            bpm = 128,
                            genre = "Инди-рок"
                        ),
                        EchoSignal(
                            id = "sig-2",
                            artist = "Сироткин",
                            track = "Выше домов",
                            album = "Эхолот (2020)",
                            source = SignalSource.SPOTIFY,
                            distanceMeters = 120,
                            angleDegrees = 155f,
                            normalizedRadius = 0.55f,
                            resonancePercent = 94,
                            camelotKey = "11B",
                            bpm = 118,
                            genre = "Инди-фолк"
                        ),
                        EchoSignal(
                            id = "sig-3",
                            artist = "Shortparis",
                            track = "Страшно",
                            album = "Так закалялась сталь",
                            source = SignalSource.VK_MUSIC,
                            distanceMeters = 210,
                            angleDegrees = 285f,
                            normalizedRadius = 0.72f,
                            resonancePercent = 71,
                            camelotKey = "5A",
                            bpm = 134,
                            genre = "Пост-панк"
                        ),
                        EchoSignal(
                            id = "sig-4",
                            artist = "Хадн Дадн",
                            track = "Мы сегодня дома",
                            source = SignalSource.BEACON_VENUE,
                            distanceMeters = 85,
                            angleDegrees = 105f,
                            normalizedRadius = 0.42f,
                            resonancePercent = 82,
                            camelotKey = "7B",
                            bpm = 120,
                            genre = "Инди-поп"
                        ),
                        EchoSignal(
                            id = "sig-5",
                            artist = "Дельфин",
                            track = "Июнь",
                            source = SignalSource.TIME_CAPSULE,
                            distanceMeters = 310,
                            angleDegrees = 220f,
                            normalizedRadius = 0.84f,
                            resonancePercent = 78,
                            camelotKey = "4A",
                            bpm = 96,
                            genre = "Альтернатива"
                        )
                    )
                )
            }

            var selectedSignal by remember { mutableStateOf<EchoSignal?>(signals.first()) }
            var isSyncedWithSignal by remember { mutableStateOf(false) }

            // Venue Beacons
            val venueBeacons by remember {
                mutableStateOf(
                    listOf(
                        VenueBeacon(
                            id = "venue-1",
                            name = "Кофейня «Фонотека & Зерно»",
                            venueType = VenueType.COFFEE,
                            currentTrack = "Мы сегодня дома",
                            currentArtist = "Хадн Дадн",
                            streamingSource = "Звук",
                            distanceMeters = 85,
                            minResonancePercent = 75,
                            proofCode = "ЭХО-82",
                            discountDescription = "Скидка 20% на спешлти фильтр-кофе бариста"
                        ),
                        VenueBeacon(
                            id = "venue-2",
                            name = "Винный бар & Джаз «Пластинка»",
                            venueType = VenueType.BAR,
                            currentTrack = "Blue in Green",
                            currentArtist = "Miles Davis",
                            streamingSource = "Яндекс Музыка",
                            distanceMeters = 240,
                            minResonancePercent = 75,
                            proofCode = "JAZZ-90",
                            discountDescription = "Бокал авторского сидра при созвучии"
                        )
                    )
                )
            }

            // Time Capsules
            val timeCapsules by remember {
                mutableStateOf(
                    listOf(
                        TimeCapsule(
                            id = "cap-1",
                            artist = "Дельфин",
                            track = "Июнь",
                            message = "Здесь в ноябре первый снег ложился на мокрый асфальт, и этот трек играл на повторе...",
                            distanceMeters = 310,
                            locationName = "Сквер у Патриарших"
                        )
                    )
                )
            }

            val encounters by chronicleStorage.encounters.collectAsState()

            // Modals
            var activeBaristaModalVenue by remember { mutableStateOf<VenueBeacon?>(null) }
            var showCreateCapsuleModal by remember { mutableStateOf(false) }
            var showVenueClaimModal by remember { mutableStateOf(false) }

            EchoIndustrialTheme(isEvening = isEveningMode) {
                val colors = LocalEchoColors.current

                Scaffold(
                    bottomBar = {
                        EchoBottomBar(
                            currentScreen = currentScreen,
                            onScreenChange = { screen ->
                                soundManager.playTactileClick()
                                currentScreen = screen
                            }
                        )
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
                                    selectedSignal = selectedSignal,
                                    onSignalSelect = { sig ->
                                        soundManager.playSonarPing()
                                        selectedSignal = sig
                                    },
                                    onOpenDeck = { sig ->
                                        selectedSignal = sig
                                        soundManager.playTactileClick()
                                        currentScreen = EchoScreen.DECK
                                    }
                                )
                            }
                            EchoScreen.DECK -> {
                                DeckScreen(
                                    signal = selectedSignal,
                                    isSynced = isSyncedWithSignal,
                                    onToggleSync = {
                                        isSyncedWithSignal = !isSyncedWithSignal
                                        if (isSyncedWithSignal) soundManager.playNeedleDrop() else soundManager.playTactileClick()
                                    },
                                    onSendPulse = {
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
                                    userResonancePercent = selectedSignal?.resonancePercent ?: 82,
                                    onShowBaristaCode = { venue ->
                                        soundManager.playSonarPing()
                                        activeBaristaModalVenue = venue
                                    },
                                    onCreateCapsule = {
                                        soundManager.playTactileClick()
                                        showCreateCapsuleModal = true
                                    },
                                    onClaimVenueB2B = {
                                        soundManager.playTactileClick()
                                        showVenueClaimModal = true
                                    }
                                )
                            }
                        }

                        // Day / Evening floating quick switch
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(top = 16.dp, end = 16.dp)
                                .clickable {
                                    isEveningMode = !isEveningMode
                                    soundManager.playTactileClick()
                                }
                        ) {
                            IndustrialBadge(
                                text = if (isEveningMode) "🌙 ВЕЧЕР" else "☀️ ДЕНЬ",
                                highlight = false
                            )
                        }
                    }
                }

                // Modal: Barista Code Screen
                activeBaristaModalVenue?.let { venue ->
                    Dialog(onDismissRequest = { activeBaristaModalVenue = null }) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(colors.surface, RoundedCornerShape(12.dp))
                                .border(2.dp, colors.venueGold, RoundedCornerShape(12.dp))
                                .padding(20.dp)
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Text(
                                    text = "PROOF-OF-MUSIC // ПОДТВЕРЖДЕНО",
                                    color = colors.venueGold,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp
                                )
                                Text(
                                    text = venue.name,
                                    color = colors.textPrimary,
                                    fontWeight = FontWeight.Black,
                                    fontSize = 18.sp
                                )

                                Box(
                                    modifier = Modifier
                                        .background(colors.surfaceElevated, RoundedCornerShape(8.dp))
                                        .border(2.dp, colors.venueGold, RoundedCornerShape(8.dp))
                                        .padding(16.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(
                                            text = "КОД ДЛЯ БАРИСТА:",
                                            color = colors.textSecondary,
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 10.sp
                                        )
                                        Text(
                                            text = venue.proofCode,
                                            color = colors.accent,
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.Black,
                                            fontSize = 28.sp,
                                            letterSpacing = 2.sp
                                        )
                                        Text(
                                            text = venue.discountDescription,
                                            color = colors.textPrimary,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(top = 4.dp)
                                        )
                                    }
                                }

                                Text(
                                    text = "Покажите этот экран бариста. Заведение убедится в созвучии вашего вкуса.",
                                    color = colors.textSecondary,
                                    fontSize = 11.sp,
                                    lineHeight = 16.sp
                                )

                                IndustrialButton(
                                    text = "Закрыть",
                                    onClick = { activeBaristaModalVenue = null },
                                    isPrimary = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                }

                // Modal: Create Capsule
                if (showCreateCapsuleModal) {
                    Dialog(onDismissRequest = { showCreateCapsuleModal = false }) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(colors.surface, RoundedCornerShape(12.dp))
                                .border(1.5.dp, colors.border, RoundedCornerShape(12.dp))
                                .padding(20.dp)
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text(
                                    text = "Оставить звуковую капсулу",
                                    color = colors.textPrimary,
                                    fontWeight = FontWeight.Black,
                                    fontSize = 17.sp
                                )
                                Text(
                                    text = "Привяжите текущую песню и мысль к этой точке города на 7 дней.",
                                    color = colors.textSecondary,
                                    fontSize = 12.sp,
                                    lineHeight = 16.sp
                                )

                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(colors.surfaceElevated, RoundedCornerShape(6.dp))
                                        .border(1.dp, colors.border, RoundedCornerShape(6.dp))
                                        .padding(10.dp)
                                ) {
                                    Text(
                                        text = "ТРЕК: ${selectedSignal?.artist} — ${selectedSignal?.track}",
                                        color = colors.textPrimary,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                Text(
                                    text = "✓ 1 бесплатная капсула этой недели доступна",
                                    color = colors.capsuleEmerald,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    IndustrialButton(
                                        text = "Оставить след",
                                        onClick = {
                                            soundManager.playSonarPing()
                                            showCreateCapsuleModal = false
                                        },
                                        isPrimary = true,
                                        modifier = Modifier.weight(1f)
                                    )
                                    IndustrialButton(
                                        text = "Отмена",
                                        onClick = { showCreateCapsuleModal = false },
                                        isPrimary = false
                                    )
                                }
                            }
                        }
                    }
                }

                // Modal: Venue Claim B2B
                if (showVenueClaimModal) {
                    Dialog(onDismissRequest = { showVenueClaimModal = false }) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(colors.surface, RoundedCornerShape(12.dp))
                                .border(1.5.dp, colors.border, RoundedCornerShape(12.dp))
                                .padding(20.dp)
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text(
                                    text = "Звуковой маяк заведения",
                                    color = colors.textPrimary,
                                    fontWeight = FontWeight.Black,
                                    fontSize = 17.sp
                                )
                                Text(
                                    text = "Привлекайте людей своего культурного кода. ЭХО транслирует играющий в зале плейлист прохожим и дарит комплименты за музыкальное созвучие (Proof-of-Music).",
                                    color = colors.textSecondary,
                                    fontSize = 12.sp,
                                    lineHeight = 16.sp
                                )
                                Text(
                                    text = "● Статус звуковой точки квартала\n● Скидки только при совпадении вкуса\n● 1 490 ₽ / месяц (счет ИП/самозанятым через Т-Банк)",
                                    color = colors.textPrimary,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    lineHeight = 16.sp
                                )

                                IndustrialButton(
                                    text = "Закрыть",
                                    onClick = { showVenueClaimModal = false },
                                    isPrimary = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
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
    }
}

@Composable
fun EchoBottomBar(
    currentScreen: EchoScreen,
    onScreenChange: (EchoScreen) -> Unit
) {
    val colors = LocalEchoColors.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .background(colors.surface)
            .border(width = 1.dp, color = colors.border),
        horizontalArrangement = Arrangement.SpaceAround,
        verticalAlignment = Alignment.CenterVertically
    ) {
        EchoScreen.values().forEach { screen ->
            val isSelected = screen == currentScreen
            val contentColor = if (isSelected) colors.accent else colors.textSecondary

            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable { onScreenChange(screen) }
                    .padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = screen.icon,
                    fontSize = 16.sp
                )
                Text(
                    text = screen.label,
                    color = contentColor,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp,
                    letterSpacing = 0.5.sp
                )
            }
        }
    }
}
