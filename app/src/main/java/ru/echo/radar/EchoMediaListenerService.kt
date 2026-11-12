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

    companion object {
        var instance: EchoMediaListenerService? = null
            private set
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        instance = this
        Log.i(tag, "EchoMediaListenerService подключен к системному сервису уведомлений")
        try {
            mediaSessionManager = getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
            val componentName = ComponentName(this, EchoMediaListenerService::class.java)
            mediaSessionManager?.addOnActiveSessionsChangedListener(sessionsChangedListener, componentName)
        } catch (e: Exception) {
            Log.e(tag, "Ошибка при подключении MediaSessionManager: ${e.message}")
        }
        scanActiveMusic()
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        instance = null
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

    fun scanActiveMusic() {
        // 1. Проверяем активные MediaSession controllers
        try {
            val componentName = ComponentName(this, EchoMediaListenerService::class.java)
            val controllers = mediaSessionManager?.getActiveSessions(componentName)
            if (controllers != null && controllers.isNotEmpty()) {
                inspectControllers(controllers)
            }
        } catch (e: Exception) {
            Log.d(tag, "getActiveSessions skipped: ${e.message}")
        }

        // 2. Проверяем активные уведомления в статус-баре
        try {
            val activeNotifs = activeNotifications
            if (activeNotifs != null) {
                for (sbn in activeNotifs) {
                    processNotification(sbn)
                }
            }
        } catch (e: Exception) {
            Log.d(tag, "activeNotifications scan error: ${e.message}")
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        if (sbn == null) return
        processNotification(sbn)
    }

    private fun processNotification(sbn: StatusBarNotification) {
        val extras = sbn.notification?.extras ?: return
        val pkg = sbn.packageName.lowercase()

        // Проверяем сигнатуры медиа-уведомления
        val hasMediaSession = extras.get("android.mediaSession") != null
        val template = extras.getString(Notification.EXTRA_TEMPLATE) ?: ""
        val isMediaStyle = template.contains("MediaStyle")
        val isMusicApp = hasMediaSession || isMediaStyle ||
                pkg.contains("music") || pkg.contains("audio") || pkg.contains("player") ||
                pkg.contains("yandex") || pkg.contains("spotify") || pkg.contains("zvuk") ||
                pkg.contains("vkontakte") || pkg.contains("vk.music") || pkg.contains("sound") ||
                pkg.contains("aimp") || pkg.contains("poweramp")

        if (isMusicApp) {
            var title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim() ?: ""
            var artist = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.trim()
                ?: extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()?.trim()
                ?: extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString()?.trim()
                ?: extras.getCharSequence(Notification.EXTRA_INFO_TEXT)?.toString()?.trim()
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
