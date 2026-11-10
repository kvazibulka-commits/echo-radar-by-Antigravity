package ru.echo.radar

import android.media.AudioAttributes
import android.media.MediaPlayer
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object EchoAudioPlayer {
    private var mediaPlayer: MediaPlayer? = null
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
        try {
            stop()
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
                }
                setOnErrorListener { _, what, extra ->
                    Log.w("EchoAudioPlayer", "Playback error: $what, $extra")
                    _isPlaying.value = false
                    _isLoading.value = false
                    _activeTrackKey.value = null
                    true
                }
                prepareAsync()
            }
        } catch (e: Exception) {
            Log.w("EchoAudioPlayer", "Play failed: ${e.message}")
            _isPlaying.value = false
            _isLoading.value = false
            _activeTrackKey.value = null
        }
    }

    fun pause() {
        try {
            mediaPlayer?.pause()
            _isPlaying.value = false
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
        } catch (e: Exception) {}
    }
}
