package ru.echo.radar.state

import android.content.Context
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import ru.echo.radar.*

/**
 * AppSessionManager — единый координатор состояния ночного прибора (Single Source of Truth).
 * Объединяет потоки EchoStateRepository, EchoAudioPlayer, EchoBleManager, EchoSoundManager
 * и EchoChronicleManager в единое реактивное состояние AppSessionState.
 * Управляет непрерывным физическим углом винила и жизненным циклом сессии.
 */
object AppSessionManager {

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private val _state = MutableStateFlow(AppSessionState())
    val state: StateFlow<AppSessionState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<EchoSessionEvent>(extraBufferCapacity = 16)
    val events: SharedFlow<EchoSessionEvent> = _events.asSharedFlow()

    private var vinylAngle = 0f
    private var lastFrameTime = System.currentTimeMillis()
    private var vinylTickerJob: Job? = null
    private var appContext: Context? = null

    fun init(context: Context) {
        if (appContext != null) return
        appContext = context.applicationContext

        // Инициализируем аппаратные и сервисные компоненты через существующие точки
        EchoSoundManager.init(context)
        EchoAudioPlayer.init(context)
        EchoStateRepository.init(context)
        EchoChronicleManager.init(context)
        EchoBleManager.init(context)

        // Связываем потоки состояния репозиториев с единым AppSessionState
        observeRepositories()
        startVinylPhysicsLoop()
    }

    private fun observeRepositories() {
        scope.launch {
            EchoStateRepository.currentTrack.collect { track ->
                _state.update { it.copy(currentTrack = track) }
            }
        }
        scope.launch {
            EchoStateRepository.nearbySignals.collect { signals ->
                _state.update { it.copy(nearbySignals = signals) }
            }
        }
        scope.launch {
            EchoStateRepository.capsules.collect { caps ->
                _state.update { it.copy(capsules = caps) }
            }
        }
        scope.launch {
            EchoStateRepository.thermalPatches.collect { patches ->
                _state.update { it.copy(thermalPatches = patches) }
            }
        }
        scope.launch {
            EchoStateRepository.needleSyncedTargets.collect { targets ->
                _state.update { it.copy(needleSyncedTargets = targets) }
            }
        }
        scope.launch {
            EchoStateRepository.lagrangePoint.collect { lagrange ->
                _state.update { it.copy(lagrangePoint = lagrange) }
            }
        }
        scope.launch {
            EchoStateRepository.presenceActive.collect { active ->
                _state.update { it.copy(isPresenceActive = active) }
            }
        }
        scope.launch {
            EchoStateRepository.connectionStatus.collect { status ->
                _state.update { it.copy(connectionStatus = status) }
            }
        }
        scope.launch {
            EchoStateRepository.serverHost.collect { host ->
                _state.update { it.copy(serverHost = host) }
            }
        }
        scope.launch {
            EchoStateRepository.echoToken.collect { token ->
                _state.update { it.copy(echoToken = token) }
            }
        }
        scope.launch {
            EchoStateRepository.incomingReaction.collect { reaction ->
                _state.update { it.copy(incomingReaction = reaction) }
                if (reaction != null) {
                    EchoSoundManager.playSonarPing()
                }
            }
        }
        scope.launch {
            EchoAudioPlayer.isPlaying.collect { playing ->
                _state.update { it.copy(isPlaying = playing) }
            }
        }
    }

    /**
     * Физика винила: вращение диска строго привязано к реальному воспроизведению.
     * При 33⅓ об/мин полный оборот (360°) занимает 1.8 секунды (200° в секунду).
     * Состояние угла хранится непрерывно и не сбрасывается при переходе между экранами!
     */
    private fun startVinylPhysicsLoop() {
        vinylTickerJob?.cancel()
        vinylTickerJob = scope.launch(Dispatchers.Default) {
            lastFrameTime = System.currentTimeMillis()
            while (isActive) {
                val now = System.currentTimeMillis()
                val deltaMs = (now - lastFrameTime).coerceAtMost(100L)
                lastFrameTime = now

                if (_state.value.isPlaying) {
                    val deltaAngle = (deltaMs / 1000f) * 200f
                    vinylAngle = (vinylAngle + deltaAngle) % 360f
                    _state.update { it.copy(vinylRotationAngle = vinylAngle) }
                }
                delay(16) // ~60 fps
            }
        }
    }

    // --- Навигация и диалоги ---

    fun navigateTo(destination: EchoDestination) {
        if (_state.value.currentDestination != destination) {
            EchoSoundManager.playTick()
            _state.update { it.copy(currentDestination = destination) }
        }
    }

    fun openSheet(sheet: EchoModalSheet) {
        EchoSoundManager.playTick()
        _state.update { it.copy(activeBottomSheet = sheet) }
    }

    fun closeSheet() {
        _state.update { it.copy(activeBottomSheet = null) }
    }

    fun dismissIncomingReaction() {
        EchoStateRepository.dismissIncomingReaction()
    }

    // --- Интерактивные физические действия инструмента ---

    fun togglePlayPause() {
        val cur = _state.value.currentTrack
        val isNowPlaying = _state.value.isPlaying
        if (isNowPlaying) {
            EchoAudioPlayer.pause()
            EchoSoundManager.playTick()
        } else {
            scope.launch {
                val mediaInfo = AlbumArtResolver.getMediaInfo(cur.artist, cur.title, cur.coverUrl)
                val previewUrl = mediaInfo.previewAudioUrl
                if (!previewUrl.isNullOrBlank()) {
                    EchoSoundManager.playNeedleDrop()
                    EchoAudioPlayer.play("${cur.artist}::${cur.title}", previewUrl)
                } else {
                    // Тактильный щелчок иглы при локальном треке
                    EchoSoundManager.playNeedleDrop()
                    _events.emit(EchoSessionEvent.ToastNotice("Превью трека не найдено в каталоге"))
                }
            }
        }
    }

    fun togglePresence() {
        EchoSoundManager.playTick()
        EchoStateRepository.togglePresence()
    }

    /**
     * Needle Sync (Синхронизация иглы):
     * Воспроизводит тактильный щелчок иглы, двойной сердечный импульс вибрации
     * и регистрирует синхронизацию на сервере.
     */
    fun triggerNeedleSync(targetId: String, targetTitle: String) {
        appContext?.let { ctx ->
            EchoSoundManager.playNeedleDrop()
            EchoSoundManager.triggerHeartbeatVibration(ctx)
        }
        EchoStateRepository.syncNeedle(targetId) { success ->
            if (success) {
                scope.launch {
                    _events.emit(EchoSessionEvent.NeedleSyncSuccess(targetTitle))
                }
            }
        }
    }

    /**
     * Камень в воду (Wave Impulse):
     * Посылает расходящуюся акустическую волну по эфиру города.
     */
    fun triggerWaveImpulse(targetId: String) {
        EchoSoundManager.playWavePulse()
        EchoStateRepository.sendWaveImpulse(targetId) {
            scope.launch {
                _events.emit(EchoSessionEvent.WavePulseDispatched)
            }
        }
    }

    /**
     * Призрачное тепло / Дыхание на стекле (Thermal Breath)
     */
    fun sendThermalBreath(targetId: String, x: Float, y: Float) {
        EchoSoundManager.playTick(volume = 0.2f)
        EchoStateRepository.sendWarmth(targetId, x, y)
    }

    fun dropCapsule(
        artist: String,
        title: String,
        secretMessage: String,
        zoneName: String = "Вечерняя улица",
        isVenue: Boolean = false,
        venueName: String = "",
        minResonance: Int = 0,
        secretPerk: String = ""
    ) {
        EchoStateRepository.dropCapsule(
            artist = artist,
            title = title,
            secretMessage = secretMessage,
            zoneName = zoneName,
            isVenue = isVenue,
            venueName = venueName,
            minResonance = minResonance,
            secretPerk = secretPerk
        ) { success ->
            if (success) {
                EchoSoundManager.playUnlockChime()
                closeSheet()
                scope.launch {
                    _events.emit(EchoSessionEvent.ToastNotice("Тайник оставлен в эфире"))
                }
            } else {
                scope.launch {
                    _events.emit(EchoSessionEvent.ToastNotice("Ошибка отправки капсулы"))
                }
            }
        }
    }

    fun updateServerConfig(host: String, token: String) {
        EchoStateRepository.setServerHost(host, token)
        closeSheet()
        scope.launch {
            _events.emit(EchoSessionEvent.ToastNotice("Адрес сервера обновлен"))
        }
    }
}
