package ru.echo.radar

import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object EchoAudioPlayer {
    private const val TAG = "EchoAudioPlayer"

    // Если prepareAsync не ответил за это время, снимаем «ЗАГРУЗКА...»
    private const val LOADING_TIMEOUT_MS = 12_000L

    private var mediaPlayer: MediaPlayer? = null
    private val handler = Handler(Looper.getMainLooper())
    private var loadGeneration = 0

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _activeTrackKey = MutableStateFlow<String?>(null)
    val activeTrackKey: StateFlow<String?> = _activeTrackKey.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    fun toggle(artist: String, title: String, previewUrl: String?) {
        if (previewUrl.isNullOrBlank()) return
        val key = "$artist::$title"
        if (_activeTrackKey.value == key && _isPlaying.value) {
            pause()
        } else {
            play(key, previewUrl)
        }
    }

    fun play(key: String, url: String) {
        stop()

        val generation = ++loadGeneration
        _activeTrackKey.value = key
        _isLoading.value = true

        try {
            val player = MediaPlayer()
            mediaPlayer = player

            player.setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .build()
            )
            player.setDataSource(url)
            player.setOnPreparedListener { mp ->
                // Плеер мог быть уже освобождён таймаутом или заменён новым треком
                if (mediaPlayer !== mp) return@setOnPreparedListener
                _isLoading.value = false
                try {
                    mp.start()
                    _isPlaying.value = true
                } catch (e: Exception) {
                    Log.w(TAG, "Start failed: ${e.message}")
                    resetState()
                }
            }
            player.setOnCompletionListener { mp ->
                if (mediaPlayer !== mp) return@setOnCompletionListener
                _isPlaying.value = false
                _isLoading.value = false
                _activeTrackKey.value = null
            }
            player.setOnErrorListener { mp, what, extra ->
                Log.w(TAG, "Playback error: $what, $extra")
                if (mediaPlayer === mp) resetState()
                true
            }
            player.prepareAsync()

            handler.postDelayed({
                if (generation == loadGeneration && _isLoading.value) {
                    Log.w(TAG, "Preview loading timeout, reset")
                    resetState()
                    releasePlayer()
                }
            }, LOADING_TIMEOUT_MS)
        } catch (e: Exception) {
            Log.w(TAG, "Play failed: ${e.message}")
            resetState()
        }
    }

    fun currentPositionMs(): Int = try { mediaPlayer?.currentPosition ?: 0 } catch (_: Exception) { 0 }

    fun pause() {
        try {
            mediaPlayer?.pause()
        } catch (e: Exception) {
            Log.w(TAG, "Pause failed: ${e.message}")
        }
        _isPlaying.value = false
        _isLoading.value = false
    }

    fun stop() {
        loadGeneration++
        releasePlayer()
        resetState()
    }

    private fun resetState() {
        _isPlaying.value = false
        _isLoading.value = false
        _activeTrackKey.value = null
    }

    private fun releasePlayer() {
        val player = mediaPlayer
        mediaPlayer = null
        try {
            player?.reset()
        } catch (_: Exception) {
        }
        try {
            player?.release()
        } catch (_: Exception) {
        }
    }
}

/**
 * VinylRotationEngine
 * Производный непрерывный расчет угла вращения винилового диска (раздел 7 ТЗ).
 * Не сбрасывается при навигации между экранами (ETHER <-> DECK <-> ECHOES <-> CACHES).
 * При паузе - сохраняет последнее положение.
 * При возобновлении - продолжает вращение без рывков (33⅓ RPM = 1.8с / 1800мс на оборот).
 */
object VinylRotationEngine {
    private var lastTimestamp = System.currentTimeMillis()
    private var currentAngle = 0f

    @Synchronized
    fun getContinuousAngle(isSpinning: Boolean): Float {
        val now = System.currentTimeMillis()
        val delta = (now - lastTimestamp).coerceIn(0L, 200L)
        lastTimestamp = now
        if (isSpinning) {
            currentAngle = (currentAngle + (delta / 1800f) * 360f) % 360f
        }
        return currentAngle
    }
}
