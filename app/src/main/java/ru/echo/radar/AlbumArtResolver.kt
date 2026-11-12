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

    /**
     * Всегда уникальная, поэтичная генерация архетипа и наблюдения
     * на основе артиста, названия песни и жанра. Работает мгновенно и офлайн.
     */
    fun generatePoeticArchetype(artist: String, title: String, genre: String): Pair<String, String> {
        val cleanArt = artist.trim()
        val cleanTit = title.trim()
        val g = genre.trim()

        if (cleanArt.startsWith("Ожидание") || cleanArt.isBlank()) {
            return Pair("Архивариус ночных шорохов", "Вслушивается в тишину спящего города, ожидая первых звуков музыки.")
        }

        val seed = "$cleanArt|$cleanTit|$g".lowercase()
        val h = Math.abs(seed.hashCode())

        return when {
            g.contains("Symphonic Metal", ignoreCase = true) || cleanArt.contains("therion", ignoreCase = true) -> {
                val titles = listOf(
                    "Алхимик симфонического шторма",
                    "Страж древних рун",
                    "Мастер нордического эпоса",
                    "Владыка грозового хорала"
                )
                val obs = listOf(
                    "Вслушивается в раскаты нордического хорала и эпический гул струнных в ночном воздухе.",
                    "Превращает городские переулки в оживший скандинавский миф под тяжелые симфонические риффы.",
                    "Ловит мерцание древних созвездий сквозь мощь хоровых партий и раскатистых гитар.",
                    "Хранит звуковую память саг и легенд, оживающих в вечерней тишине мегаполиса."
                )
                Pair(titles[h % titles.size], obs[h % obs.size])
            }
            g.contains("Metal", ignoreCase = true) || g.contains("Метал", ignoreCase = true) -> {
                val titles = listOf(
                    "Страж грозового шторма",
                    "Инженер стального монолита",
                    "Пилигрим тяжелых риффов",
                    "Хранитель железного пламени"
                )
                val obs = listOf(
                    "Встречает ночной ветер под бескомпромиссную мощь барабанов и раскатистые гитарные залпы.",
                    "Разрезает ночную мглу плотной стеной гитарного звука и несокрушимой энергии.",
                    "Синхронизирует пульс с тяжелым качающим ритмом, рассеивающим дневную суету."
                )
                Pair(titles[h % titles.size], obs[h % obs.size])
            }
            g.contains("Post-Punk", ignoreCase = true) || cleanArt.contains("молчат дома", ignoreCase = true) || cleanArt.contains("ploho", ignoreCase = true) -> {
                val titles = listOf(
                    "Пилигрим неоновых панелек",
                    "Страж полуночного инея",
                    "Странник бетонных лабиринтов",
                    "Созерцатель холодных витрин"
                )
                val obs = listOf(
                    "Вслушивается в упругий бас пустых дворов, храня верность честному и чистому звуку.",
                    "Шагает по ночным проспектам под холодные барабанные драм-машины и колючие гитарные аккорды.",
                    "Ловит ледяные синтезаторные отражения в темных витринах засыпающего квартала."
                )
                Pair(titles[h % titles.size], obs[h % obs.size])
            }
            g.contains("Trip-Hop", ignoreCase = true) || cleanArt.contains("burial", ignoreCase = true) -> {
                val titles = listOf(
                    "Штурман полуночных мостов",
                    "Ловец дымного полумрака",
                    "Алхимик замедленного времени",
                    "Созерцатель ночного дождя"
                )
                val obs = listOf(
                    "Синхронизирует дыхание с плотным кинематографичным битом и потрескиванием старой виниловой дорожки.",
                    "Растворяется в густых басовых волнах, наблюдая за медленным скольжением мокрых фар по набережной.",
                    "Замедляет городской ритм под томные джазовые сэмплы и мягкий полуночный грув."
                )
                Pair(titles[h % titles.size], obs[h % obs.size])
            }
            g.contains("Electronic", ignoreCase = true) || g.contains("Techno", ignoreCase = true) -> {
                val titles = listOf(
                    "Инженер ночного монолита",
                    "Навигатор неоновых трасс",
                    "Архитектор цифровых сфер",
                    "Пилигрим аналогового пульса"
                )
                val obs = listOf(
                    "Синхронизирует шаг с железным гипнотическим ритмом полуночных подземных галерей.",
                    "Мчится по ночной автостраде под раскаты аналоговых секвенций и неоновый пульс города.",
                    "Управляет пространством квартала через математически выверенные звуковые волны."
                )
                Pair(titles[h % titles.size], obs[h % obs.size])
            }
            g.contains("Classical", ignoreCase = true) -> {
                val titles = listOf(
                    "Архитектор вечных форм",
                    "Мастер акустической тишины",
                    "Ловец симфонической глубины",
                    "Созерцатель полифонии"
                )
                val obs = listOf(
                    "Слушает прикосновение пальцев к клавишам рояля и трепетное дыхание струнного квартета.",
                    "Созерцает величие замысла вселенной через совершенство полифонии великих мастеров."
                )
                Pair(titles[h % titles.size], obs[h % obs.size])
            }
            g.contains("Jazz", ignoreCase = true) -> {
                val titles = listOf(
                    "Ловец полуночных синкоп",
                    "Созерцатель бархатного нуара",
                    "Хранитель медных импровизаций",
                    "Ловец виниловых гармоний"
                )
                val obs = listOf(
                    "Вслушивается в живую импровизацию контрабаса и мягкий шепот медных тарелок под теплый свет фонаря.",
                    "Слушает медленное биение сердца ночного города под звуки томного саксофона."
                )
                Pair(titles[h % titles.size], obs[h % obs.size])
            }
            g.contains("Hip-Hop", ignoreCase = true) || g.contains("Rap", ignoreCase = true) -> {
                val titles = listOf(
                    "Поэт ночных кварталов",
                    "Чеканщик честного ритма",
                    "Летописец полуночных дворов",
                    "Хранитель уличного слога"
                )
                val obs = listOf(
                    "Чеканит ритм шагов по ночному асфальту под плотный качающий бит и искреннее слово.",
                    "Слушает пульс спальных районов, сплетая истории улиц в единую звуковую рифму."
                )
                Pair(titles[h % titles.size], obs[h % obs.size])
            }
            g.contains("Rock", ignoreCase = true) -> {
                val titles = listOf(
                    "Ловец гитарных штормов",
                    "Романтик аналоговой искренности",
                    "Пилигрим стадионных гимнов",
                    "Хранитель живого звука"
                )
                val obs = listOf(
                    "Несет в наушниках живую энергию перегруженных ламп и драйв настоящего рока.",
                    "Шагает сквозь ночной город под раскатистый припев, разгоняющий усталость дня."
                )
                Pair(titles[h % titles.size], obs[h % obs.size])
            }
            else -> {
                val titles = listOf(
                    "Архивариус забытых кассет",
                    "Ловец виниловых ветров",
                    "Романтик вечерних набережных",
                    "Странник теплых дворов"
                )
                val obs = listOf(
                    "Превращает прогулку по переулкам в искреннее кинематографичное переживание.",
                    "Несет в наушниках свет уходящего дня, согревая холодный вечер теплым звуком."
                )
                Pair(titles[h % titles.size], obs[h % obs.size])
            }
        }
    }
}
