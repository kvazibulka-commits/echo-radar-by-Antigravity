package ru.echo.radar.service

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.sin

class EchoSoundManager(private val context: Context) {
    private val scope = CoroutineScope(Dispatchers.Default)

    fun playTactileClick() {
        scope.launch {
            try {
                val sampleRate = 44100
                val durationMs = 35
                val numSamples = (sampleRate * (durationMs / 1000.0)).toInt()
                val buffer = ShortArray(numSamples)

                for (i in 0 until numSamples) {
                    val progress = i.toDouble() / numSamples
                    val freq = 140.0 * (1.0 - progress * 0.7)
                    val envelope = (1.0 - progress) * (1.0 - progress)
                    val sample = sin(2.0 * PI * freq * i / sampleRate) * envelope
                    buffer[i] = (sample * Short.MAX_VALUE * 0.25).toInt().toShort()
                }

                playBuffer(buffer, sampleRate)
            } catch (_: Exception) {}
        }
    }

    fun playSonarPing(frequency: Double = 780.0) {
        scope.launch {
            try {
                val sampleRate = 44100
                val durationMs = 180
                val numSamples = (sampleRate * (durationMs / 1000.0)).toInt()
                val buffer = ShortArray(numSamples)

                for (i in 0 until numSamples) {
                    val progress = i.toDouble() / numSamples
                    val envelope = (1.0 - progress) * (1.0 - progress) * (1.0 - progress)
                    val sample = sin(2.0 * PI * frequency * i / sampleRate) * envelope
                    buffer[i] = (sample * Short.MAX_VALUE * 0.30).toInt().toShort()
                }

                playBuffer(buffer, sampleRate)
            } catch (_: Exception) {}
        }
    }

    fun playNeedleDrop() {
        scope.launch {
            try {
                val sampleRate = 44100
                val durationMs = 90
                val numSamples = (sampleRate * (durationMs / 1000.0)).toInt()
                val buffer = ShortArray(numSamples)

                for (i in 0 until numSamples) {
                    val progress = i.toDouble() / numSamples
                    val noise = (Math.random() * 2.0 - 1.0)
                    val envelope = (1.0 - progress)
                    buffer[i] = (noise * envelope * Short.MAX_VALUE * 0.18).toInt().toShort()
                }

                playBuffer(buffer, sampleRate)
            } catch (_: Exception) {}
        }
    }

    private fun playBuffer(buffer: ShortArray, sampleRate: Int) {
        val audioTrack = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(buffer.size * 2)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()

        audioTrack.write(buffer, 0, buffer.size)
        audioTrack.play()
        audioTrack.release()
    }
}
