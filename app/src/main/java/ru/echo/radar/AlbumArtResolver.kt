package ru.echo.radar

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

object AlbumArtResolver {
    private val cache = ConcurrentHashMap<String, String>()
    private val client = OkHttpClient.Builder()
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(4, TimeUnit.SECONDS)
        .build()

    suspend fun getCoverUrl(artist: String, title: String): String? = withContext(Dispatchers.IO) {
        val cleanArtist = artist.trim()
        val cleanTitle = title.trim()
        if (cleanArtist.isBlank() || cleanTitle.isBlank() || cleanArtist.startsWith("Ожидание") || cleanTitle.startsWith("Включите")) {
            return@withContext null
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
                    val body = resp.body?.string() ?: return@withContext null
                    val json = JSONObject(body)
                    if (json.optInt("resultCount", 0) > 0) {
                        val results = json.optJSONArray("results")
                        if (results != null && results.length() > 0) {
                            val item = results.getJSONObject(0)
                            val art100 = item.optString("artworkUrl100", "")
                            val art600 = art100.replace("100x100bb.jpg", "600x600bb.jpg")
                            if (art600.isNotBlank()) {
                                cache[key] = art600
                                return@withContext art600
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {}
        null
    }
}
