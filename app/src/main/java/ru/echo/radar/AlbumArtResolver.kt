package ru.echo.radar

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

data class TrackMediaInfo(
    val coverUrl: String? = null,
    val previewAudioUrl: String? = null,
    val albumName: String? = null
)

/**
 * Cascading album art resolver: iTunes → Deezer → empty fallback.
 *
 * Both APIs are completely free, require zero API keys, and together
 * cover virtually all commercial music catalogs including Russian indie,
 * CIS releases, and niche genres that one provider alone might miss.
 *
 * Results are cached in-memory by "artist::title" key to avoid redundant
 * network calls on every recomposition or scroll.
 */
object AlbumArtResolver {
    private const val TAG = "AlbumArtResolver"
    private val cache = ConcurrentHashMap<String, TrackMediaInfo>()
    private val client = OkHttpClient.Builder()
        .connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    /**
     * Main entry point. Returns cover URL, 30-sec audio preview URL,
     * and album name. All fields may be null if the track is not found
     * in any catalog.
     */
    suspend fun getMediaInfo(artist: String, title: String, directCoverUrl: String? = null): TrackMediaInfo = withContext(Dispatchers.IO) {
        val cleanArtist = artist.trim()
        val cleanTitle = title.trim()
        if (cleanArtist.isBlank() || cleanTitle.isBlank() ||
            cleanArtist.startsWith("Ожидание") || cleanTitle.startsWith("Включите")) {
            return@withContext if (!directCoverUrl.isNullOrBlank()) TrackMediaInfo(coverUrl = directCoverUrl) else TrackMediaInfo()
        }
        val key = "$cleanArtist::$cleanTitle".lowercase()
        cache[key]?.let { cached ->
            return@withContext if (!directCoverUrl.isNullOrBlank()) cached.copy(coverUrl = directCoverUrl) else cached
        }

        var info: TrackMediaInfo? = null
        if (!directCoverUrl.isNullOrBlank()) {
            // Direct cover from local PC server / SMTC stream
            val previewInfo = tryItunes(cleanArtist, cleanTitle) ?: tryDeezer(cleanArtist, cleanTitle)
            info = TrackMediaInfo(
                coverUrl = directCoverUrl,
                previewAudioUrl = previewInfo?.previewAudioUrl,
                albumName = previewInfo?.albumName
            )
        } else {
            // Cascade: try iTunes first, then Deezer as fallback (covers Russian/indie/Yandex catalogs)
            info = tryItunes(cleanArtist, cleanTitle)
            if (info == null || info.coverUrl.isNullOrBlank()) {
                val deezerInfo = tryDeezer(cleanArtist, cleanTitle)
                if (deezerInfo != null && !deezerInfo.coverUrl.isNullOrBlank()) {
                    info = TrackMediaInfo(
                        coverUrl = deezerInfo.coverUrl,
                        previewAudioUrl = deezerInfo.previewAudioUrl ?: info?.previewAudioUrl,
                        albumName = deezerInfo.albumName ?: info?.albumName
                    )
                }
            }
        }

        val result = info ?: TrackMediaInfo()
        cache[key] = result
        return@withContext result
    }

    /**
     * iTunes Search API — best coverage for Western and K-Pop catalogs,
     * also has most Russian commercial releases. Returns 600x600 artwork.
     */
    private fun tryItunes(artist: String, title: String): TrackMediaInfo? {
        return try {
            val query = URLEncoder.encode("$artist $title", "UTF-8")
            val url = "https://itunes.apple.com/search?term=$query&entity=song&limit=1"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "EchoRadar/2.0")
                .build()

            client.newCall(request).execute().use { resp ->
                if (resp.isSuccessful) {
                    val body = resp.body?.string() ?: return null
                    val json = JSONObject(body)
                    if (json.optInt("resultCount", 0) > 0) {
                        val results = json.optJSONArray("results")
                        if (results != null && results.length() > 0) {
                            val item = results.getJSONObject(0)
                            val art100 = item.optString("artworkUrl100", "")
                            val art600 = art100.replace("100x100bb.jpg", "600x600bb.jpg")
                                .takeIf { it.isNotBlank() }
                            val preview = item.optString("previewUrl", "")
                                .takeIf { it.isNotBlank() }
                            val album = item.optString("collectionName", "")
                                .takeIf { it.isNotBlank() }
                            return TrackMediaInfo(
                                coverUrl = art600,
                                previewAudioUrl = preview,
                                albumName = album
                            )
                        }
                    }
                }
                null
            }
        } catch (e: Exception) {
            Log.d(TAG, "iTunes lookup failed for '$artist - $title': ${e.message}")
            null
        }
    }

    /**
     * Deezer Search API — excellent coverage for European, Russian indie,
     * and CIS-exclusive releases. No API key required. Returns 1000x1000 artwork.
     * Rate limit: 50 requests / 5 seconds per IP (generous for our use case).
     */
    private fun tryDeezer(artist: String, title: String): TrackMediaInfo? {
        return try {
            val query = URLEncoder.encode("$artist $title", "UTF-8")
            val url = "https://api.deezer.com/search?q=$query&limit=1"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "EchoRadar/2.0")
                .build()

            client.newCall(request).execute().use { resp ->
                if (resp.isSuccessful) {
                    val body = resp.body?.string() ?: return null
                    val json = JSONObject(body)
                    val data = json.optJSONArray("data")
                    if (data != null && data.length() > 0) {
                        val item = data.getJSONObject(0)
                        val album = item.optJSONObject("album")
                        // Deezer provides cover, cover_small, cover_medium, cover_big, cover_xl
                        val coverXl = album?.optString("cover_xl", "") ?: ""
                        val coverBig = album?.optString("cover_big", "") ?: ""
                        val coverMed = album?.optString("cover_medium", "") ?: ""
                        val cover = listOf(coverXl, coverBig, coverMed)
                            .firstOrNull { it.isNotBlank() }
                        val preview = item.optString("preview", "")
                            .takeIf { it.isNotBlank() }
                        val albumName = album?.optString("title", "")
                            .takeIf { !it.isNullOrBlank() }

                        return TrackMediaInfo(
                            coverUrl = cover,
                            previewAudioUrl = preview,
                            albumName = albumName
                        )
                    }
                }
                null
            }
        } catch (e: Exception) {
            Log.d(TAG, "Deezer lookup failed for '$artist - $title': ${e.message}")
            null
        }
    }

    /**
     * Convenience: just the cover URL (used by map overlay code).
     */
    suspend fun getCoverUrl(artist: String, title: String): String? {
        return getMediaInfo(artist, title).coverUrl
    }

    /**
     * Batch-prefetch cover URLs for a list of tracks (used to preload
     * mini covers for radar map pins). Runs all lookups on IO dispatcher.
     */
    suspend fun prefetchCovers(tracks: List<Pair<String, String>>) = withContext(Dispatchers.IO) {
        tracks.forEach { (artist, title) ->
            val key = "$artist::$title".lowercase().trim()
            if (!cache.containsKey(key)) {
                try {
                    getMediaInfo(artist, title)
                } catch (_: Exception) {}
            }
        }
    }
}
