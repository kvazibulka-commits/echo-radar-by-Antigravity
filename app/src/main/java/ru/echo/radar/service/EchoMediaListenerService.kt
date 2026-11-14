package ru.echo.radar.service

import android.app.Notification
import android.content.Intent
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import ru.echo.radar.domain.model.SignalSource

class EchoMediaListenerService : NotificationListenerService() {

    companion object {
        const val ACTION_TRACK_CHANGED = "ru.echo.radar.ACTION_TRACK_CHANGED"
        const val EXTRA_ARTIST = "extra_artist"
        const val EXTRA_TRACK = "extra_track"
        const val EXTRA_ALBUM = "extra_album"
        const val EXTRA_SOURCE = "extra_source"
        const val EXTRA_IS_PLAYING = "extra_is_playing"

        private val SUPPORTED_PACKAGES = mapOf(
            "ru.yandex.music" to SignalSource.YANDEX_MUSIC,
            "com.vkontakte.android" to SignalSource.VK_MUSIC,
            "com.vk.music" to SignalSource.VK_MUSIC,
            "com.spotify.music" to SignalSource.SPOTIFY,
            "com.sber.zvuk" to SignalSource.ZVUK,
            "com.google.android.apps.youtube.music" to SignalSource.YANDEX_MUSIC
        )
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        sbn ?: return

        val pkg = sbn.packageName
        val source = SUPPORTED_PACKAGES[pkg] ?: return
        val extras = sbn.notification.extras ?: return

        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.trim()

        if (!title.isNullOrEmpty() && !text.isNullOrEmpty()) {
            val (artist, track) = parseArtistTrack(title, text)
            broadcastTrack(artist, track, "", source, true)
        }
    }

    private fun parseArtistTrack(title: String, text: String): Pair<String, String> {
        return if (title.contains(" — ") || title.contains(" - ")) {
            val parts = title.split(Regex(" [—-] "), 2)
            parts[0].trim() to parts[1].trim()
        } else {
            text to title
        }
    }

    private fun broadcastTrack(artist: String, track: String, album: String, source: SignalSource, isPlaying: Boolean) {
        val intent = Intent(ACTION_TRACK_CHANGED).apply {
            putExtra(EXTRA_ARTIST, artist)
            putExtra(EXTRA_TRACK, track)
            putExtra(EXTRA_ALBUM, album)
            putExtra(EXTRA_SOURCE, source.name)
            putExtra(EXTRA_IS_PLAYING, isPlaying)
            setPackage(packageName)
        }
        sendBroadcast(intent)
    }
}
