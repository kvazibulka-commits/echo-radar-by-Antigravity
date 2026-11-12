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
    val albumName: String? = null,
    val genre: String? = null
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
            val resolvedGenre = previewInfo?.genre ?: detectLocalGenre(cleanArtist, cleanTitle)
            info = TrackMediaInfo(
                coverUrl = directCoverUrl,
                previewAudioUrl = previewInfo?.previewAudioUrl,
                albumName = previewInfo?.albumName,
                genre = resolvedGenre
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
                        albumName = deezerInfo.albumName ?: info?.albumName,
                        genre = deezerInfo.genre ?: info?.genre ?: detectLocalGenre(cleanArtist, cleanTitle)
                    )
                }
            }
        }

        val result = (info ?: TrackMediaInfo(genre = detectLocalGenre(cleanArtist, cleanTitle))).let {
            if (it.genre.isNullOrBlank()) it.copy(genre = detectLocalGenre(cleanArtist, cleanTitle)) else it
        }
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
                            val rawGenre = item.optString("primaryGenreName", "")
                                .takeIf { it.isNotBlank() }
                            val resolvedGenre = normalizeGenre(rawGenre, artist, title)
                            return TrackMediaInfo(
                                coverUrl = art600,
                                previewAudioUrl = preview,
                                albumName = album,
                                genre = resolvedGenre
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
                        val resolvedGenre = detectLocalGenre(artist, title)

                        return TrackMediaInfo(
                            coverUrl = cover,
                            previewAudioUrl = preview,
                            albumName = albumName,
                            genre = resolvedGenre
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

    fun detectLocalGenre(artist: String, title: String): String {
        val art = artist.lowercase().trim()
        val tit = title.lowercase().trim()

        // 1. Symphonic Metal / Metal
        if (art.contains("therion") || art.contains("nightwish") || art.contains("epica") ||
            art.contains("within temptation") || art.contains("haggard") || art.contains("delain")) {
            return "Symphonic Metal"
        }
        val metalKeywords = listOf(
            "metallica", "iron maiden", "judas priest", "rammstein", "slipknot", "slayer",
            "megadeth", "pantera", "dimmu borgir", "cradle of filth", "blind guardian",
            "sabaton", "amorphis", "in flames", "arch enemy", "behemoth", "gojira",
            "sepultura", "avenged sevenfold", "ghost", "opeth", "mastodon", "meshuggah",
            "deftones", "korn", "disturbed", "system of a down", "soad", "ария", "эпидемия",
            "мастер", "чёрный обелиск", "кипелов", "северный флот", "powerwolf", "alestorm",
            "bathory", "burzum", "mayhem", "cannibal corpse", "death"
        )
        if (metalKeywords.any { art.contains(it) } || tit.contains("metal") || tit.contains("метал")) {
            return "Metal"
        }

        // 2. Post-Punk / Doomer
        val postPunkKeywords = listOf(
            "молчат дома", "ploho", "буерак", "дурной вкус", "свидание", "перемотка",
            "утро", "кино", "цой", "post-punk", "пост-панк", "joy division", "the cure",
            "bauhaus", "she past away", "lebanon hanover"
        )
        if (postPunkKeywords.any { art.contains(it) || tit.contains(it) }) {
            return "Post-Punk"
        }

        // 3. Trip-Hop / Lo-Fi / Downtempo
        val tripHopKeywords = listOf(
            "burial", "massive attack", "portishead", "tricky", "morcheeba", "bonobo",
            "trip-hop", "трип-хоп", "lo-fi", "лоу-фай", "downtempo"
        )
        if (tripHopKeywords.any { art.contains(it) || tit.contains(it) }) {
            return "Trip-Hop"
        }

        // 4. Techno / Electronic / Synthwave
        val electronicKeywords = listOf(
            "daft punk", "the prodigy", "kraftwerk", "chemical brothers", "deadmau5",
            "techno", "техно", "house", "хаус", "synthwave", "синтвейв", "carpenter brut",
            "kavinsky", "perturbator", "minimal", "minimal-techno", "ambient", "эмбиент"
        )
        if (electronicKeywords.any { art.contains(it) || tit.contains(it) }) {
            return "Electronic"
        }

        // 5. Classic / Alternative Rock
        val rockKeywords = listOf(
            "queen", "pink floyd", "led zeppelin", "deep purple", "ac/dc", "nirvana",
            "linkin park", "muse", "radiohead", "arctic monkeys", "foo fighters", "oasis",
            "би-2", "сплин", "ддт", "король и шут", "киш", "агата кристи", "наутилус",
            "земфира", "мумий тролль", "рок", "rock"
        )
        if (rockKeywords.any { art.contains(it) || tit.contains(it) }) {
            return "Rock"
        }

        // 6. Classical / Neoclassical
        val classicalKeywords = listOf(
            "hans zimmer", "ludovico einaudi", "max richter", "yiruma", "bach",
            "mozart", "beethoven", "chopin", "vivaldi", "tchaikovsky", "чайковский",
            "раманинов", "шопен", "барокко", "симфония", "orchestra"
        )
        if (classicalKeywords.any { art.contains(it) || tit.contains(it) }) {
            return "Classical"
        }

        // 7. Jazz / Blues / Dark Jazz
        val jazzKeywords = listOf(
            "miles davis", "john coltrane", "chet baker", "bill evans", "bohren",
            "the dale cooper", "kilimanjaro darkjazz", "jazz", "джаз", "blues", "блюз"
        )
        if (jazzKeywords.any { art.contains(it) || tit.contains(it) }) {
            return "Jazz"
        }

        // 8. Hip-Hop / Rap
        val hiphopKeywords = listOf(
            "eminem", "2pac", "notorious b.i.g.", "kendrick lamar", "kanye west",
            "oxxxymiron", "скриптонит", "баста", "хаски", "noize mc", "каста",
            "rap", "рэп", "hip-hop", "хип-хоп", "trap"
        )
        if (hiphopKeywords.any { art.contains(it) || tit.contains(it) }) {
            return "Hip-Hop"
        }

        // 9. Indie / Dream Pop
        val indieKeywords = listOf(
            "сироткин", "sirotkin", "pompeya", "казанский феномен", "мы", "обе две",
            "самое большое простое число", "сбпч", "дайте танк", "indie", "инди",
            "dream-pop", "дрим-поп", "shoegaze", "шугейз"
        )
        if (indieKeywords.any { art.contains(it) || tit.contains(it) }) {
            return "Indie"
        }

        return "Indie"
    }

    fun normalizeGenre(rawGenre: String?, artist: String, title: String): String {
        val art = artist.lowercase().trim()
        if (art.contains("therion") || art.contains("nightwish") || art.contains("epica") ||
            art.contains("within temptation") || art.contains("haggard") || art.contains("delain")) {
            return "Symphonic Metal"
        }

        val g = rawGenre?.lowercase()?.trim() ?: ""
        return when {
            g.contains("symphonic metal") -> "Symphonic Metal"
            g.contains("metal") || g.contains("метал") -> "Metal"
            g.contains("hard rock") || g.contains("heavy rock") -> "Hard Rock"
            g.contains("rock") || g.contains("рок") -> "Rock"
            g.contains("post-punk") || g.contains("пост-панк") -> "Post-Punk"
            g.contains("electronic") || g.contains("электроника") || g.contains("dance") || g.contains("techno") || g.contains("house") -> "Electronic"
            g.contains("trip-hop") || g.contains("трип-хоп") || g.contains("downtempo") -> "Trip-Hop"
            g.contains("hip-hop") || g.contains("хип-хоп") || g.contains("rap") || g.contains("рэп") -> "Hip-Hop"
            g.contains("jazz") || g.contains("джаз") || g.contains("blues") -> "Jazz"
            g.contains("classical") || g.contains("классик") || g.contains("soundtrack") || g.contains("score") -> "Classical"
            g.contains("pop") || g.contains("поп") -> "Pop"
            g.contains("indie") || g.contains("инди") || g.contains("alternative") || g.contains("альтернатива") -> "Indie"
            g.isNotBlank() && g != "music" && g != "музыка" -> rawGenre!!.replaceFirstChar { it.uppercase() }
            else -> detectLocalGenre(artist, title)
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
