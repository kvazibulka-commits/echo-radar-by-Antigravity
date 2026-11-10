package ru.echo.radar

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

object AlbumArtResolver {
    private val cache = ConcurrentHashMap<String, TrackMediaInfo>()
    private val client = OkHttpClient.Builder()
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(4, TimeUnit.SECONDS)
        .build()

    suspend fun getMediaInfo(artist: String, title: String): TrackMediaInfo = withContext(Dispatchers.IO) {
        val cleanArtist = artist.trim()
        val cleanTitle = title.trim()
        if (cleanArtist.isBlank() || cleanTitle.isBlank() || cleanArtist.startsWith("Ожидание") || cleanTitle.startsWith("Включите")) {
            return@withContext TrackMediaInfo()
        }
        val key = "$cleanArtist::$cleanTitle".lowercase()
        cache[key]?.let { return@withContext it }

        try {
            val query = URLEncoder.encode("$cleanArtist $cleanTitle", "UTF-8")
            val url = "https://itunes.apple.com/search?term=$query&entity=song&limit=1"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "EchoRadar/2.0")
                .build()

            client.newCall(request).execute().use { resp ->
                if (resp.isSuccessful) {
                    val body = resp.body?.string() ?: return@withContext TrackMediaInfo()
                    val json = JSONObject(body)
                    if (json.optInt("resultCount", 0) > 0) {
                        val results = json.optJSONArray("results")
                        if (results != null && results.length() > 0) {
                            val item = results.getJSONObject(0)
                            val art100 = item.optString("artworkUrl100", "")
                            val art600 = art100.replace("100x100bb.jpg", "600x600bb.jpg").takeIf { it.isNotBlank() }
                            val preview = item.optString("previewUrl", "").takeIf { it.isNotBlank() }
                            val album = item.optString("collectionName", "").takeIf { it.isNotBlank() }
                            val info = TrackMediaInfo(
                                coverUrl = art600,
                                previewAudioUrl = preview,
                                albumName = album
                            )
                            cache[key] = info
                            return@withContext info
                        }
                    }
                }
            }
        } catch (e: Exception) {}
        val fallback = TrackMediaInfo()
        cache[key] = fallback
        fallback
    }

    suspend fun getCoverUrl(artist: String, title: String): String? {
        return getMediaInfo(artist, title).coverUrl
    }
}
