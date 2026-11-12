package ru.echo.radar

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log

class EchoMediaListenerService : NotificationListenerService() {

    private val tag = "EchoMediaListener"
    private var mediaSessionManager: MediaSessionManager? = null
    private val controllerCallbacks = mutableMapOf<MediaController, MediaController.Callback>()

    private val sessionsChangedListener = MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
        inspectControllers(controllers)
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.i(tag, "EchoMediaListenerService подключен к системному сервису уведомлений")
        try {
            mediaSessionManager = getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
            val componentName = ComponentName(this, EchoMediaListenerService::class.java)
            val activeControllers = mediaSessionManager?.getActiveSessions(componentName)
            inspectControllers(activeControllers)
            mediaSessionManager?.addOnActiveSessionsChangedListener(sessionsChangedListener, componentName)
        } catch (e: Exception) {
            Log.e(tag, "Ошибка при подключении MediaSessionManager: ${e.message}")
        }
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        Log.i(tag, "EchoMediaListenerService отключен")
        cleanupCallbacks()
        try {
            mediaSessionManager?.removeOnActiveSessionsChangedListener(sessionsChangedListener)
        } catch (_: Exception) {}
    }

    private fun cleanupCallbacks() {
        controllerCallbacks.forEach { (ctrl, cb) ->
            try { ctrl.unregisterCallback(cb) } catch (_: Exception) {}
        }
        controllerCallbacks.clear()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        if (sbn == null) return

        val extras = sbn.notification?.extras ?: return
        val pkg = sbn.packageName.lowercase()

        // 1. Проверяем сигнатуры медиа-уведомления
        val hasMediaSession = extras.get("android.mediaSession") != null
        val template = extras.getString(Notification.EXTRA_TEMPLATE) ?: ""
        val isMediaStyle = template.contains("MediaStyle")
        val isMusicApp = hasMediaSession || isMediaStyle ||
                pkg.contains("music") || pkg.contains("audio") || pkg.contains("player") ||
                pkg.contains("yandex") || pkg.contains("spotify") || pkg.contains("zvuk") ||
                pkg.contains("vkontakte") || pkg.contains("vk.music") || pkg.contains("sound")

        if (isMusicApp) {
            val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim() ?: ""
            val artist = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.trim()
                ?: extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString()?.trim()
                ?: ""

            if (title.isNotBlank()) {
                Log.d(tag, "Медиа-уведомление от $pkg: $artist — $title")
                EchoStateRepository.updateDetectedMusic(
                    artist = artist,
                    title = title,
                    packageName = sbn.packageName,
                    isPlaying = true
                )
            }
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        super.onNotificationRemoved(sbn)
        // При скрытии медиа-уведомления перепроверяем активные сессии
        try {
            val componentName = ComponentName(this, EchoMediaListenerService::class.java)
            val activeControllers = mediaSessionManager?.getActiveSessions(componentName)
            inspectControllers(activeControllers)
        } catch (_: Exception) {}
    }

    private fun inspectControllers(controllers: List<MediaController>?) {
        cleanupCallbacks()
        if (controllers.isNullOrEmpty()) return

        for (controller in controllers) {
            // Регистрируем коллбек для отслеживания смены трека и паузы внутри сессии
            val callback = object : MediaController.Callback() {
                override fun onMetadataChanged(metadata: MediaMetadata?) {
                    handleControllerUpdate(controller)
                }

                override fun onPlaybackStateChanged(state: PlaybackState?) {
                    handleControllerUpdate(controller)
                }
            }
            try {
                controller.registerCallback(callback)
                controllerCallbacks[controller] = callback
            } catch (_: Exception) {}

            if (handleControllerUpdate(controller)) {
                break
            }
        }
    }

    private fun handleControllerUpdate(controller: MediaController): Boolean {
        val state = controller.playbackState?.state
        val metadata = controller.metadata
        val isPlaying = state == PlaybackState.STATE_PLAYING || state == PlaybackState.STATE_BUFFERING

        if (metadata != null) {
            val artist = metadata.getString(MediaMetadata.METADATA_KEY_ARTIST)
                ?: metadata.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
                ?: ""
            val title = metadata.getString(MediaMetadata.METADATA_KEY_TITLE) ?: ""

            if (title.isNotBlank()) {
                Log.d(tag, "MediaSession обновлен (${controller.packageName}): $artist — $title [playing=$isPlaying]")
                EchoStateRepository.updateDetectedMusic(
                    artist = artist,
                    title = title,
                    packageName = controller.packageName,
                    isPlaying = isPlaying
                )
                return isPlaying
            }
        }
        return false
    }
}
