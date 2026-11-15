package ru.echo.radar.state

import ru.echo.radar.EchoCapsule
import ru.echo.radar.IncomingReaction
import ru.echo.radar.LagrangePointInfo
import ru.echo.radar.NearbyEchoSignal
import ru.echo.radar.PlayingTrack
import ru.echo.radar.ThermalPatch

enum class EchoDestination(val title: String, val subtitle: String) {
    ETHER("ЭФИР", "NIGHT / PRESENCE"),
    DECK("ДЕКА", "ANALOG INSTRUMENT"),
    ECHOES("ОТЗВУКИ", "MEMORY ARCHIVE"),
    CACHES("ТАЙНИКИ", "CITY FREQUENCIES")
}

sealed class EchoModalSheet {
    data class ResonanceDetail(val signal: NearbyEchoSignal) : EchoModalSheet()
    data class LagrangeDetail(val info: LagrangePointInfo) : EchoModalSheet()
    data class CapsuleDetail(val capsule: EchoCapsule) : EchoModalSheet()
    data class CuratorStory(val title: String, val text: String, val persona: String) : EchoModalSheet()
    data object DropCapsuleDialog : EchoModalSheet()
    data object ServerSettingsDialog : EchoModalSheet()
}

sealed class EchoSessionEvent {
    data class NeedleSyncSuccess(val targetTitle: String) : EchoSessionEvent()
    data object WavePulseDispatched : EchoSessionEvent()
    data class ProofOfMusicUnlocked(val venueName: String, val perk: String) : EchoSessionEvent()
    data class ToastNotice(val message: String) : EchoSessionEvent()
}

/**
 * AppSessionState — единое неизменяемое состояние сессии (Single Source of Truth).
 * Содержит полную картину работы всех аппаратных подсистем:
 * BLE, аудиодвижка, сонара, винила, карты тайников и модальных окон.
 */
data class AppSessionState(
    val currentDestination: EchoDestination = EchoDestination.ETHER,
    val currentTrack: PlayingTrack = PlayingTrack(
        artist = "Ожидание музыки...",
        title = "Включите трек в Яндекс Музыке / VK / плеере",
        sourceApp = "Системный перехват",
        poeticPersona = "Архивариус ночных шорохов",
        mood = "Ночная тишина",
        bpm = 120,
        camelotKey = "8A"
    ),
    val isPlaying: Boolean = false,
    val playbackPositionMs: Long = 0L,
    val vinylRotationAngle: Float = 0f,
    val isBleActive: Boolean = false,
    val isPresenceActive: Boolean = true,
    val connectionStatus: String = "Подключение...",
    val nearbySignals: List<NearbyEchoSignal> = emptyList(),
    val capsules: List<EchoCapsule> = emptyList(),
    val thermalPatches: List<ThermalPatch> = emptyList(),
    val needleSyncedTargets: Set<String> = emptySet(),
    val lagrangePoint: LagrangePointInfo? = null,
    val incomingReaction: IncomingReaction? = null,
    val activeBottomSheet: EchoModalSheet? = null,
    val serverHost: String = "http://192.168.0.214:8080",
    val echoToken: String = "1c13ee8697755fdc4518013ea2fd6996"
)
