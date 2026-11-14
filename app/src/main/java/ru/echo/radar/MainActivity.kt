package ru.echo.radar

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import ru.echo.radar.components.EchoBottomSheetHost
import ru.echo.radar.components.MiniPlayer
import ru.echo.radar.components.NavigationDock
import ru.echo.radar.screens.CachesScreen
import ru.echo.radar.screens.DeckScreen
import ru.echo.radar.screens.EchoesScreen
import ru.echo.radar.screens.EtherScreen
import ru.echo.radar.state.AppSessionManager
import ru.echo.radar.state.EchoDestination
import ru.echo.radar.state.EchoSessionEvent
import ru.echo.radar.theme.EchoTheme
import ru.echo.radar.theme.EchoTokens

class MainActivity : ComponentActivity() {

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions.entries.all { it.value }
        if (!granted) {
            Toast.makeText(this, "ЭХО: Рекомендуется предоставить разрешения для BLE и геолокации", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Инициализация единого координатора сессии
        AppSessionManager.init(this)
        requestRequiredPermissions()

        setContent {
            EchoAppRoot()
        }
    }

    override fun onResume() {
        super.onResume()
        // Сканирование играющей в плеере музыки через NotificationListener
        EchoMediaListenerService.instance?.scanActiveMusic()
    }

    private fun requestRequiredPermissions() {
        val permissions = mutableListOf(
            android.Manifest.permission.ACCESS_FINE_LOCATION,
            android.Manifest.permission.ACCESS_COARSE_LOCATION
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions.add(android.Manifest.permission.BLUETOOTH_SCAN)
            permissions.add(android.Manifest.permission.BLUETOOTH_ADVERTISE)
            permissions.add(android.Manifest.permission.BLUETOOTH_CONNECT)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(android.Manifest.permission.POST_NOTIFICATIONS)
        }

        val needed = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (needed.isNotEmpty()) {
            permissionLauncher.launch(needed.toTypedArray())
        }

        // Проверка разрешения доступа к уведомлениям
        if (!isNotificationAccessGranted(this)) {
            try {
                startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            } catch (_: Exception) {}
        }
    }

    private fun isNotificationAccessGranted(context: Context): Boolean {
        return try {
            val pkgName = context.packageName
            val flat = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners") ?: ""
            flat.contains(pkgName)
        } catch (_: Exception) {
            false
        }
    }
}

/**
 * EchoAppRoot — корневой контейнер композиции.
 * Обеспечивает связывание Single Source of Truth (AppSessionState)
 * с NavigationDock, плавающим MiniPlayer и EchoBottomSheetHost.
 */
@Composable
fun EchoAppRoot() {
    val state by AppSessionManager.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    // Обработка одноразовых событий сессии
    LaunchedEffect(Unit) {
        AppSessionManager.events.collect { event ->
            when (event) {
                is EchoSessionEvent.NeedleSyncSuccess -> {
                    snackbarHostState.showSnackbar("Игла синхронизирована с «${event.targetTitle}»")
                }
                is EchoSessionEvent.WavePulseDispatched -> {
                    snackbarHostState.showSnackbar("Акустическая волна разошлась по эфиру города")
                }
                is EchoSessionEvent.ProofOfMusicUnlocked -> {
                    snackbarHostState.showSnackbar("Тайник разблокирован: ${event.perk}")
                }
                is EchoSessionEvent.ToastNotice -> {
                    snackbarHostState.showSnackbar(event.message)
                }
            }
        }
    }

    EchoTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(EchoTokens.Background)
        ) {
            Scaffold(
                containerColor = EchoTokens.Background,
                snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
                bottomBar = {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        // MiniPlayer плавает над NavigationDock на ETHER, ECHOES, CACHES. Скрыт на DECK.
                        if (state.currentDestination != EchoDestination.DECK) {
                            MiniPlayer(
                                state = state,
                                onNavigateToDeck = { AppSessionManager.navigateTo(EchoDestination.DECK) },
                                onTogglePlayPause = { AppSessionManager.togglePlayPause() }
                            )
                        }

                        // Постоянный док навигации
                        NavigationDock(
                            currentDestination = state.currentDestination,
                            onDestinationSelected = { dest -> AppSessionManager.navigateTo(dest) }
                        )
                    }
                }
            ) { paddingValues ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = paddingValues.calculateBottomPadding())
                ) {
                    // Анимированный плавный переход между 4 мирами (180-260 мс)
                    Crossfade(
                        targetState = state.currentDestination,
                        animationSpec = tween(durationMillis = 220),
                        label = "world_crossfade"
                    ) { destination ->
                        when (destination) {
                            EchoDestination.ETHER -> {
                                EtherScreen(
                                    state = state,
                                    onOpenSheet = { AppSessionManager.openSheet(it) },
                                    onTogglePresence = { AppSessionManager.togglePresence() },
                                    onSendWarmth = { targetId, x, y ->
                                        AppSessionManager.sendThermalBreath(targetId, x, y)
                                    }
                                )
                            }
                            EchoDestination.DECK -> {
                                DeckScreen(
                                    state = state,
                                    onTogglePlayPause = { AppSessionManager.togglePlayPause() },
                                    onNeedleSync = { targetId, title ->
                                        AppSessionManager.triggerNeedleSync(targetId, title)
                                    },
                                    onWaveImpulse = { targetId ->
                                        AppSessionManager.triggerWaveImpulse(targetId)
                                    }
                                )
                            }
                            EchoDestination.ECHOES -> {
                                EchoesScreen(
                                    state = state,
                                    onOpenSheet = { AppSessionManager.openSheet(it) },
                                    onNeedleSync = { targetId, title ->
                                        AppSessionManager.triggerNeedleSync(targetId, title)
                                    }
                                )
                            }
                            EchoDestination.CACHES -> {
                                CachesScreen(
                                    state = state,
                                    onOpenSheet = { AppSessionManager.openSheet(it) }
                                )
                            }
                        }
                    }
                }
            }

            // Единый хост модальных панелей (Bottom Sheet)
            EchoBottomSheetHost(
                activeSheet = state.activeBottomSheet,
                onDismiss = { AppSessionManager.closeSheet() },
                onNeedleSync = { targetId, title ->
                    AppSessionManager.triggerNeedleSync(targetId, title)
                },
                onWaveImpulse = { targetId ->
                    AppSessionManager.triggerWaveImpulse(targetId)
                },
                onDropCapsuleSubmit = { artist, title, msg, zone ->
                    AppSessionManager.dropCapsule(artist, title, msg, zone)
                },
                onServerConfigSubmit = { host, token ->
                    AppSessionManager.updateServerConfig(host, token)
                }
            )
        }
    }
}
