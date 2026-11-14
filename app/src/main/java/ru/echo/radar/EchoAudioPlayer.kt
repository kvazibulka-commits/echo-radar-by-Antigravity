package ru.echo.radar

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Build
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object EchoAudioPlayer {
    private var mediaPlayer: MediaPlayer? = null
    private var audioManager: AudioManager? = null
    private var focusRequest: AudioFocusRequest? = null

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _activeTrackKey = MutableStateFlow<String?>(null)
    val activeTrackKey: StateFlow<String?> = _activeTrackKey.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val afChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                pause()
            }
            AudioManager.AUDIOFOCUS_GAIN -> {
                // Фокус возвращен
            }
        }
    }

    fun init(context: Context) {
        audioManager = context.applicationContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    }

    private fun requestFocus(): Boolean {
        val am = audioManager ?: return true
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val playbackAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build()
            val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                .setAudioAttributes(playbackAttributes)
                .setAcceptsDelayedFocusGain(false)
                .setOnAudioFocusChangeListener(afChangeListener)
                .build()
            focusRequest = request
            am.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        } else {
            @Suppress("DEPRECATION")
            am.requestAudioFocus(
                afChangeListener,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT
            ) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        }
    }

    private fun abandonFocus() {
        val am = audioManager ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            focusRequest?.let { am.abandonAudioFocusRequest(it) }
            focusRequest = null
        } else {
            @Suppress("DEPRECATION")
            am.abandonAudioFocus(afChangeListener)
        }
    }

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
        try {
            stop()
            requestFocus() // Ставит на паузу Яндекс Музыку / VK / Spotify!
            _activeTrackKey.value = key
            _isLoading.value = true

            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .build()
                )
                setDataSource(url)
                setOnPreparedListener { mp ->
                    _isLoading.value = false
                    mp.start()
                    _isPlaying.value = true
                }
                setOnCompletionListener {
                    _isPlaying.value = false
                    _isLoading.value = false
                    _activeTrackKey.value = null
                    abandonFocus() // Возобновляет Яндекс Музыку
                }
                setOnErrorListener { _, what, extra ->
                    Log.w("EchoAudioPlayer", "Playback error: $what, $extra")
                    _isPlaying.value = false
                    _isLoading.value = false
                    _activeTrackKey.value = null
                    abandonFocus()
                    true
                }
                prepareAsync()
            }
        } catch (e: Exception) {
            Log.w("EchoAudioPlayer", "Play failed: ${e.message}")
            _isPlaying.value = false
            _isLoading.value = false
            _activeTrackKey.value = null
            abandonFocus()
        }
    }

    fun currentPositionMs(): Int = try { mediaPlayer?.currentPosition ?: 0 } catch (_: Exception) { 0 }

    fun pause() {
        try {
            mediaPlayer?.pause()
            _isPlaying.value = false
            abandonFocus()
        } catch (e: Exception) {}
    }

    fun stop() {
        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
            mediaPlayer = null
            _isPlaying.value = false
            _isLoading.value = false
            _activeTrackKey.value = null
            abandonFocus()
        } catch (e: Exception) {}
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
