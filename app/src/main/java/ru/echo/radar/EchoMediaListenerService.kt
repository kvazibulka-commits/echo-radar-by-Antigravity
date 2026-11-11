package ru.echo.radar

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/**
 * EchoMediaListenerService
 *
 * Слушает медиа-сессии плееров (Яндекс Музыка, VK Музыка, Spotify, Звук, AIMP, ...)
 * и передаёт текущий трек в EchoStateRepository.
 *
 * Почему так:
 * 1. Яндекс Музыка не всегда обновляет уведомление при смене трека — поэтому раз в
 *    2.5 секунды опрашиваем активные медиа-сессии (дешёвый binder-вызов).
 * 2. На части прошивок metadata приходит только в уведомлении — читаем
 *    Notification.EXTRA_MEDIA_SESSION и текстовые поля как фоллбэк.
 * 3. Без доступа к уведомлениям сервис не получит сессии; статус доступа показывает
 *    экран ДЕКА и кнопка «Разрешить доступ».
 */
class EchoMediaListenerService : NotificationListenerService() {

    private companion object {
        const val POLL_INTERVAL_MS = 2500L
        val MUSIC_PACKAGES = listOf(
            "yandex.music", "vkontakte", "vk.music", "spotify", "zvuk",
            "aimp", "poweramp", "apple.music", "youtube", "deezer", "tidal",
            "google.android.music"
        )
    }

    private var mediaSessionManager: MediaSessionManager? = null
    private val handler = Handler(Looper.getMainLooper())
    private var polling = false

    private val pollRunnable = object : Runnable {
        override fun run() {
            if (!polling) return
            inspectActiveSessions()
            handler.postDelayed(this, POLL_INTERVAL_MS)
        }
    }

    private val sessionsChangedListener =
        MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
            inspectControllers(controllers)
        }

    override fun onListenerConnected() {
        super.onListenerConnected()
        try {
            mediaSessionManager = getSystemService(Context.MEDIA_SESSION_SERVICE) as? MediaSessionManager
            val componentName = ComponentName(this, EchoMediaListenerService::class.java)
            mediaSessionManager?.addOnActiveSessionsChangedListener(sessionsChangedListener, componentName)
            inspectActiveSessions()
            startPolling()
        } catch (_: SecurityException) {
            // Доступ к уведомлениям отозван — ждём повторного подключения
        } catch (_: Exception) {
        }
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        stopPolling()
        try {
            mediaSessionManager?.removeOnActiveSessionsChangedListener(sessionsChangedListener)
        } catch (_: Exception) {
        }
    }

    override fun onDestroy() {
        stopPolling()
        super.onDestroy()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        if (sbn == null) return
        if (sbn.packageName == packageName) return
        if (!isMusicPackage(sbn.packageName)) return

        val extras = sbn.notification?.extras ?: return

        // 1. Медиа-сессия прямо из уведомления (Яндекс Музыка / VK / Spotify)
        @Suppress("DEPRECATION")
        val token = extras.getParcelable(Notification.EXTRA_MEDIA_SESSION) as? MediaSession.Token
        if (token != null) {
            try {
                if (inspectController(MediaController(this, token), sbn.packageName, requirePlaying = false)) {
                    return
                }
            } catch (_: Exception) {
            }
        }

        // 2. Текстовый фоллбэк для плееров без MediaStyle
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim().orEmpty()
        val text = (
            extras.getCharSequence(Notification.EXTRA_TEXT)
                ?: extras.getCharSequence(Notification.EXTRA_BIG_TEXT)
            )?.toString()?.trim().orEmpty()
        val combined = listOf(title, text).filter { it.isNotBlank() }.joinToString(" — ")
        parseArtistTitle(combined)?.let { publish(it.first, it.second, sbn.packageName) }
    }

    private fun startPolling() {
        if (polling) return
        polling = true
        handler.postDelayed(pollRunnable, POLL_INTERVAL_MS)
    }

    private fun stopPolling() {
        polling = false
        handler.removeCallbacks(pollRunnable)
    }

    private fun inspectActiveSessions() {
        try {
            val manager = mediaSessionManager ?: return
            val componentName = ComponentName(this, EchoMediaListenerService::class.java)
            inspectControllers(manager.getActiveSessions(componentName))
        } catch (_: SecurityException) {
        } catch (_: Exception) {
        }
    }

    private fun inspectControllers(controllers: List<MediaController>?) {
        if (controllers.isNullOrEmpty()) return

        var fallback: MediaController? = null
        for (controller in controllers) {
            if (!isMusicPackage(controller.packageName)) continue
            val metadata = controller.metadata ?: continue
            if (!metadata.containsKey(MediaMetadata.METADATA_KEY_TITLE)) continue

            val state = controller.playbackState?.state
            if (state == PlaybackState.STATE_PLAYING || state == PlaybackState.STATE_BUFFERING) {
                if (inspectController(controller, controller.packageName, requirePlaying = true)) {
                    return
                }
            } else if (fallback == null) {
                fallback = controller
            }
        }
        // Ничего не играет прямо сейчас — показываем последний известный трек
        fallback?.let { inspectController(it, it.packageName, requirePlaying = false) }
    }

    private fun inspectController(
        controller: MediaController,
        packageName: String,
        requirePlaying: Boolean
    ): Boolean {
        val metadata = controller.metadata ?: return false
        if (requirePlaying) {
            val state = controller.playbackState?.state
            if (state != PlaybackState.STATE_PLAYING && state != PlaybackState.STATE_BUFFERING) {
                return false
            }
        }

        val title = sequenceOf(
            metadata.getString(MediaMetadata.METADATA_KEY_TITLE),
            metadata.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE)
        ).firstOrNull { !it.isNullOrBlank() }?.trim().orEmpty()

        val artist = sequenceOf(
            metadata.getString(MediaMetadata.METADATA_KEY_ARTIST),
            metadata.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST),
            metadata.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE)
        ).firstOrNull { !it.isNullOrBlank() }?.trim().orEmpty()

        if (title.isBlank()) return false

        if (artist.isBlank()) {
            val parsed = parseArtistTitle(title) ?: return false
            publish(parsed.first, parsed.second, packageName)
        } else {
            publish(artist, title, packageName)
        }
        return true
    }

    private fun publish(artist: String, title: String, packageName: String) {
        val cleanArtist = cleanPart(artist)
        val cleanTitle = cleanPart(title)
        if (cleanArtist.isBlank() || cleanTitle.isBlank()) return
        if (isJunk(cleanArtist, cleanTitle)) return
        if (cleanTitle.length > 200) return
        EchoStateRepository.updateDetectedMusic(cleanArtist, cleanTitle, packageName)
    }

    private fun parseArtistTitle(raw: String): Pair<String, String>? {
        val text = raw.replace(Regex("\\s+"), " ").trim()
        if (text.isBlank()) return null
        for (separator in listOf(" — ", " – ", " - ", "—", "–")) {
            val index = text.indexOf(separator)
            if (index <= 0) continue
            val artist = cleanPart(text.substring(0, index))
            val title = cleanPart(text.substring(index + separator.length))
            if (artist.isNotBlank() && title.isNotBlank()) {
                return artist.take(120) to title.take(160)
            }
        }
        return null
    }

    private fun cleanPart(value: String): String {
        return value
            .replace(Regex("\\s+"), " ")
            .trim()
            .trim('«', '»', '"', '“', '”', '\'', '`')
            .trim()
    }

    private fun isMusicPackage(packageName: String): Boolean {
        if (packageName.isBlank()) return false
        return MUSIC_PACKAGES.any { packageName.contains(it, ignoreCase = true) }
    }

    private fun isJunk(artist: String, title: String): Boolean {
        val blob = "$artist $title".lowercase()
        return blob.contains("://") ||
            blob.contains("offscreen.html") ||
            blob.contains("chrome-extension") ||
            blob.contains("about:blank")
    }
}
