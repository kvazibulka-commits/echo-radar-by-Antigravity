package ru.echo.radar

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.Build
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/**
 * EchoSoundManager
 * Procedural tactile audio engine for Echo Radar according to the echo-sound-design skill.
 * Uses SoundPool with USAGE_ASSISTANCE_SONIFICATION (Zero Ducking, non-disruptive to active music players).
 */
object EchoSoundManager {

    private const val TAG = "EchoSoundManager"
    private const val SAMPLE_RATE = 44100

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var soundPool: SoundPool? = null
    private var needleSoundId = 0
    private var sonarSoundId = 0
    private var unlockSoundId = 0
    private var tickSoundId = 0
    private var waveSoundId = 0
    private var isLoaded = false

    fun init(context: Context) {
        if (soundPool != null) return

        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .setFlags(AudioAttributes.FLAG_AUDIBILITY_ENFORCED)
            .build()

        soundPool = SoundPool.Builder()
            .setMaxStreams(5)
            .setAudioAttributes(audioAttributes)
            .build()

        scope.launch {
            try {
                val soundDir = File(context.cacheDir, "echo_sounds").apply { mkdirs() }

                val needleFile = File(soundDir, "needle_drop.wav")
                if (!needleFile.exists()) {
                    writeWav(needleFile, generateNeedleDropPcm())
                }

                val sonarFile = File(soundDir, "sonar_ping.wav")
                if (!sonarFile.exists()) {
                    writeWav(sonarFile, generateSonarPingPcm())
                }

                val unlockFile = File(soundDir, "unlock_chime.wav")
                if (!unlockFile.exists()) {
                    writeWav(unlockFile, generateUnlockChimePcm())
                }

                val tickFile = File(soundDir, "tick.wav")
                if (!tickFile.exists()) {
                    writeWav(tickFile, generateTickPcm())
                }

                val waveFile = File(soundDir, "wave_pulse.wav")
                if (!waveFile.exists()) {
                    writeWav(waveFile, generateWavePulsePcm())
                }

                soundPool?.let { pool ->
                    needleSoundId = pool.load(needleFile.absolutePath, 1)
                    sonarSoundId = pool.load(sonarFile.absolutePath, 1)
                    unlockSoundId = pool.load(unlockFile.absolutePath, 1)
                    tickSoundId = pool.load(tickFile.absolutePath, 1)
                    waveSoundId = pool.load(waveFile.absolutePath, 1)
                    isLoaded = true
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to initialize procedural sounds: ${e.message}")
            }
        }
    }

    fun playNeedleDrop(volume: Float = 0.5f) {
        if (!isLoaded || soundPool == null) return
        soundPool?.play(needleSoundId, volume, volume, 1, 0, 1.0f)
    }

    fun playSonarPing(volume: Float = 0.45f) {
        if (!isLoaded || soundPool == null) return
        soundPool?.play(sonarSoundId, volume, volume, 1, 0, 1.0f)
    }

    fun playUnlockChime(volume: Float = 0.65f) {
        if (!isLoaded || soundPool == null) return
        soundPool?.play(unlockSoundId, volume, volume, 2, 0, 1.0f)
    }

    fun playTick(volume: Float = 0.3f) {
        if (!isLoaded || soundPool == null) return
        soundPool?.play(tickSoundId, volume, volume, 1, 0, 1.0f)
    }

    fun playWavePulse(volume: Float = 0.5f) {
        if (!isLoaded || soundPool == null) return
        soundPool?.play(waveSoundId, volume, volume, 1, 0, 1.0f)
    }

    /**
     * Тактильный пульс сердца (Heartbeat) по концепции Аргона:
     * Двойной импульс [80ms, пауза 100ms, 120ms] для Синхронизации иглы (Needle Sync)
     */
    fun triggerHeartbeatVibration(context: Context) {
        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? android.os.VibratorManager
                vm?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? android.os.Vibrator
            }

            val pattern = longArrayOf(0, 80, 100, 120)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val amplitudes = intArrayOf(0, 190, 0, 255)
                try {
                    vibrator?.vibrate(android.os.VibrationEffect.createWaveform(pattern, amplitudes, -1))
                } catch (_: Exception) {
                    vibrator?.vibrate(android.os.VibrationEffect.createWaveform(pattern, -1))
                }
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(pattern, -1)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Heartbeat vibration failed: ${e.message}")
        }
    }

    // --- PROCEDURAL AUDIO PCM GENERATORS ---

    // 1. Soft needle drop on vinyl groove (~45ms)
    private fun generateNeedleDropPcm(): ShortArray {
        val durationMs = 45
        val numSamples = (SAMPLE_RATE * durationMs) / 1000
        val pcm = ShortArray(numSamples)
        for (i in 0 until numSamples) {
            val t = i.toDouble() / SAMPLE_RATE
            val envelope = exp(-t * 90.0)
            // Low fundamental (140 Hz) + micro click noise
            val tone = sin(2.0 * PI * 140.0 * t) * 0.75 + sin(2.0 * PI * 280.0 * t) * 0.25
            val noise = (Math.random() * 2.0 - 1.0) * 0.20
            val sample = (tone + noise) * envelope * 22000.0
            pcm[i] = sample.coerceIn(-32767.0, 32767.0).toInt().toShort()
        }
        return pcm
    }

    // 2. Deep sub-bass sonar ping (~220ms, 90 Hz decaying sine)
    private fun generateSonarPingPcm(): ShortArray {
        val durationMs = 220
        val numSamples = (SAMPLE_RATE * durationMs) / 1000
        val pcm = ShortArray(numSamples)
        for (i in 0 until numSamples) {
            val t = i.toDouble() / SAMPLE_RATE
            val envelope = exp(-t * 14.0)
            val tone = sin(2.0 * PI * 92.0 * t) * 0.85 + sin(2.0 * PI * 184.0 * t) * 0.15
            val sample = tone * envelope * 24000.0
            pcm[i] = sample.coerceIn(-32767.0, 32767.0).toInt().toShort()
        }
        return pcm
    }

    // 3. Golden bell chime for Proof-of-Music unlock (~260ms, harmonic shimmer 880 + 1320 + 1760 Hz)
    private fun generateUnlockChimePcm(): ShortArray {
        val durationMs = 260
        val numSamples = (SAMPLE_RATE * durationMs) / 1000
        val pcm = ShortArray(numSamples)
        for (i in 0 until numSamples) {
            val t = i.toDouble() / SAMPLE_RATE
            val envelope = exp(-t * 11.0)
            val h1 = sin(2.0 * PI * 880.0 * t) * 0.50
            val h2 = sin(2.0 * PI * 1320.0 * t) * 0.32
            val h3 = sin(2.0 * PI * 1760.0 * t) * 0.18
            val sample = (h1 + h2 + h3) * envelope * 26000.0
            pcm[i] = sample.coerceIn(-32767.0, 32767.0).toInt().toShort()
        }
        return pcm
    }

    // 4. Subtle dry tick (~15ms)
    private fun generateTickPcm(): ShortArray {
        val durationMs = 15
        val numSamples = (SAMPLE_RATE * durationMs) / 1000
        val pcm = ShortArray(numSamples)
        for (i in 0 until numSamples) {
            val t = i.toDouble() / SAMPLE_RATE
            val envelope = exp(-t * 220.0)
            val tone = sin(2.0 * PI * 1200.0 * t) * envelope * 18000.0
            pcm[i] = tone.coerceIn(-32767.0, 32767.0).toInt().toShort()
        }
        return pcm
    }

    // 5. Resonant water droplet pulse for "Камень в воду" (~180ms)
    private fun generateWavePulsePcm(): ShortArray {
        val durationMs = 180
        val numSamples = (SAMPLE_RATE * durationMs) / 1000
        val pcm = ShortArray(numSamples)
        for (i in 0 until numSamples) {
            val t = i.toDouble() / SAMPLE_RATE
            val envelope = exp(-t * 22.0)
            val freq = 520.0 + (t / 0.18) * 260.0
            val tone = sin(2.0 * PI * freq * t) * 0.8 + sin(2.0 * PI * (freq * 2.0) * t) * 0.2
            val sample = tone * envelope * 24000.0
            pcm[i] = sample.coerceIn(-32767.0, 32767.0).toInt().toShort()
        }
        return pcm
    }

    // Standard 16-bit Mono WAV writer
    private fun writeWav(file: File, pcmData: ShortArray) {
        val byteData = ByteBuffer.allocate(pcmData.size * 2).order(ByteOrder.LITTLE_ENDIAN)
        for (s in pcmData) byteData.putShort(s)
        val audioBytes = byteData.array()

        val totalDataLen = audioBytes.size + 36
        val totalAudioLen = audioBytes.size.toLong()
        val channels = 1
        val byteRate = SAMPLE_RATE * 2 * channels

        val header = ByteArray(44)
        header[0] = 'R'.code.toByte()
        header[1] = 'I'.code.toByte()
        header[2] = 'F'.code.toByte()
        header[3] = 'F'.code.toByte()
        header[4] = (totalDataLen and 0xff).toByte()
        header[5] = ((totalDataLen shr 8) and 0xff).toByte()
        header[6] = ((totalDataLen shr 16) and 0xff).toByte()
        header[7] = ((totalDataLen shr 24) and 0xff).toByte()
        header[8] = 'W'.code.toByte()
        header[9] = 'A'.code.toByte()
        header[10] = 'V'.code.toByte()
        header[11] = 'E'.code.toByte()
        header[12] = 'f'.code.toByte()
        header[13] = 'm'.code.toByte()
        header[14] = 't'.code.toByte()
        header[15] = ' '.code.toByte()
        header[16] = 16
        header[17] = 0
        header[18] = 0
        header[19] = 0
        header[20] = 1 // PCM
        header[21] = 0
        header[22] = channels.toByte()
        header[23] = 0
        header[24] = (SAMPLE_RATE and 0xff).toByte()
        header[25] = ((SAMPLE_RATE shr 8) and 0xff).toByte()
        header[26] = ((SAMPLE_RATE shr 16) and 0xff).toByte()
        header[27] = ((SAMPLE_RATE shr 24) and 0xff).toByte()
        header[28] = (byteRate and 0xff).toByte()
        header[29] = ((byteRate shr 8) and 0xff).toByte()
        header[30] = ((byteRate shr 16) and 0xff).toByte()
        header[31] = ((byteRate shr 24) and 0xff).toByte()
        header[32] = (2 * channels).toByte()
        header[33] = 0
        header[34] = 16 // 16 bits per sample
        header[35] = 0
        header[36] = 'd'.code.toByte()
        header[37] = 'a'.code.toByte()
        header[38] = 't'.code.toByte()
        header[39] = 'a'.code.toByte()
        header[40] = (totalAudioLen and 0xff).toByte()
        header[41] = ((totalAudioLen shr 8) and 0xff).toByte()
        header[42] = ((totalAudioLen shr 16) and 0xff).toByte()
        header[43] = ((totalAudioLen shr 24) and 0xff).toByte()

        FileOutputStream(file).use { fos ->
            fos.write(header)
            fos.write(audioBytes)
        }
    }
}
