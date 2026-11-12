package ru.echo.radar

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

data class PlayingTrack(
    val artist: String,
    val title: String,
    val sourceApp: String,
    val poeticPersona: String,
    val mood: String
)

data class NearbyEchoSignal(
    val id: String,
    val artist: String,
    val title: String,
    val sourceApp: String,
    val distanceLabel: String,
    val poeticName: String,
    val aiObservation: String,
    val resonanceStory: String = "",
    val compatibility: Int,
    val resonanceDays: String?,
    val encountersCount: Int = 1,
    val angleDeg: Float,
    val ringFactor: Float,
    val colorHex: Long = 0xFF38BDF8L,
    val isGhost: Boolean = false,
    val coverUrl: String? = null,
    val bpm: Int = 120,
    val camelotKey: String = "8A",
    val genre: String = "indie"
)

data class ThermalPatch(
    val id: String,
    val targetId: String,
    val senderId: String,
    val x: Float,
    val y: Float,
    val ageSec: Float
)

data class LagrangePointInfo(
    val active: Boolean,
    val angleDegrees: Float,
    val distanceRatio: Float,
    val secretTrack: String,
    val secretMessage: String,
    val participants: List<String>
)

data class EchoCapsule(
    val id: String,
    val creatorId: String,
    val artist: String,
    val title: String,
    val moodLabel: String,
    val personaTitle: String,
    val zoneName: String,
    val secretMessage: String,
    val distanceLabel: String,
    val angleDeg: Float,
    val ringFactor: Float,
    val compatibility: Int,
    val isVenue: Boolean = false,
    val venueName: String = "",
    val minResonance: Int = 0,
    val secretPerk: String = "",
    val isUnlocked: Boolean = true,
    val createdAt: Long = 0L
)

data class IncomingReaction(
    val id: Long,
    val symbol: String,
    val senderId: String,
    val message: String,
    val timestamp: Long
)

object EchoStateRepository {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .writeTimeout(5, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    // ── Адрес сервера и токен доступа ──
    private val _serverHost = MutableStateFlow("http://192.168.0.214:8080")
    val serverHost: StateFlow<String> = _serverHost.asStateFlow()

    // Токен не хардкодится: его выдают в консоли echo_server.py и вводят в ⚙ Сервер.
    private val _echoToken = MutableStateFlow("")
    val echoToken: StateFlow<String> = _echoToken.asStateFlow()

    private var _appContext: Context? = null

    // ── Текущий трек ──
    private val _currentTrack = MutableStateFlow(
        PlayingTrack(
            artist = "Ожидание музыки...",
            title = "Включите трек в Яндекс Музыке / VK / Плеере",
            sourceApp = "Системный перехват Android",
            poeticPersona = "Архивариус ночных шорохов",
            mood = "Ночная тишина"
        )
    )
    val currentTrack: StateFlow<PlayingTrack> = _currentTrack.asStateFlow()

    // ── Присутствие ──
    private val _presenceActive = MutableStateFlow(true)
    val presenceActive: StateFlow<Boolean> = _presenceActive.asStateFlow()

    // ── Сигналы с сервера ──
    private val _nearbySignals = MutableStateFlow<List<NearbyEchoSignal>>(emptyList())
    val nearbySignals: StateFlow<List<NearbyEchoSignal>> = _nearbySignals.asStateFlow()

    // ── Музыкальные капсулы (маячки-тайники) ──
    private val _capsules = MutableStateFlow<List<EchoCapsule>>(emptyList())
    val capsules: StateFlow<List<EchoCapsule>> = _capsules.asStateFlow()

    // ── АРГОН: Тепловые пятна на карте (Призрачное тепло / дыхание на стекле) ──
    private val _thermalPatches = MutableStateFlow<List<ThermalPatch>>(emptyList())
    val thermalPatches: StateFlow<List<ThermalPatch>> = _thermalPatches.asStateFlow()

    // ── АРГОН: Синхронизация иглы (Drop-in синхрон) ──
    private val _needleSyncedTargets = MutableStateFlow<Set<String>>(emptySet())
    val needleSyncedTargets: StateFlow<Set<String>> = _needleSyncedTargets.asStateFlow()

    // ── АРГОН: Точка Лагранжа (Акустическое затмение при 3+ слушателях) ──
    private val _lagrangePoint = MutableStateFlow<LagrangePointInfo?>(null)
    val lagrangePoint: StateFlow<LagrangePointInfo?> = _lagrangePoint.asStateFlow()

    // ── АРГОН: Шум рассвета (Dawn Noise / пленочное зерно) ──
    private val _isDawnMode = MutableStateFlow(false)
    val isDawnMode: StateFlow<Boolean> = _isDawnMode.asStateFlow()

    // ── Входящие отклики (импульсы) от других слушателей/ПК ──
    private val _incomingReaction = MutableStateFlow<IncomingReaction?>(null)
    val incomingReaction: StateFlow<IncomingReaction?> = _incomingReaction.asStateFlow()

    // ── Доступ к уведомлениям (без него ЭХО не видит Яндекс Музыку) ──
    private val _notificationAccess = MutableStateFlow(false)
    val notificationAccess: StateFlow<Boolean> = _notificationAccess.asStateFlow()

    // ── Доступ к геолокации (нужен для поиска музыки рядом) ──
    private val _locationAccess = MutableStateFlow(false)
    val locationAccess: StateFlow<Boolean> = _locationAccess.asStateFlow()

    // ── Обратная связь для ручной трансляции / шаринга ──
    private val _broadcastFeedback = MutableStateFlow<String?>(null)
    val broadcastFeedback: StateFlow<String?> = _broadcastFeedback.asStateFlow()

    fun clearBroadcastFeedback() {
        _broadcastFeedback.value = null
    }

    private var lastHandledBannerId: Long = 0L

    fun dismissIncomingReaction() {
        _incomingReaction.value = null
    }

    // ── Статус подключения ──
    private val _connectionStatus = MutableStateFlow("Не подключено")
    val connectionStatus: StateFlow<String> = _connectionStatus.asStateFlow()

    // ── Идентификатор устройства (стабильный UUID в SharedPreferences) ──
    private var deviceId: String = "echo_device_uninitialized"

    private var pollingJob: Job? = null
    private var lastSentTrack: Pair<String, String>? = null
    private var consecutiveErrors = 0

    fun init(context: Context) {
        _appContext = context.applicationContext
        EchoChronicleManager.init(context)
        val prefs = context.getSharedPreferences("echo_prefs", Context.MODE_PRIVATE)
        deviceId = prefs.getString("device_id", null) ?: run {
            val newId = "android_" + java.util.UUID.randomUUID().toString().replace("-", "").take(12)
            prefs.edit().putString("device_id", newId).apply()
            newId
        }
        val savedHost = prefs.getString("server_host", "http://192.168.0.214:8080") ?: "http://192.168.0.214:8080"
        val savedToken = prefs.getString("echo_token", "") ?: ""
        _serverHost.value = savedHost
        _echoToken.value = savedToken
        refreshNotificationAccess(context)
        refreshLocationAccess(context)
        startWidgetSync()
        startPolling()
    }

    /** Домашний виджет «Сейчас играет» повторяет текущий трек без сети и сервисов. */
    private fun startWidgetSync() {
        val ctx = _appContext ?: return
        scope.launch {
            _currentTrack.collect { track ->
                EchoWidgetProvider.save(
                    ctx,
                    track.title,
                    track.artist,
                    track.poeticPersona,
                    0xFF38BDF8.toInt()
                )
            }
        }
    }

    /**
     * Проверяет, выдан ли ЭХО доступ к уведомлениям.
     * Без него Android не отдаёт медиа-сессии Яндекс Музыки / VK / Spotify.
     */
    fun refreshNotificationAccess(context: Context) {
        val enabled = try {
            Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners") ?: ""
        } catch (e: Exception) {
            ""
        }
        _notificationAccess.value = enabled.split(":").any { it.contains(context.packageName) }
    }

    /** Разрешение на геолокацию: пересчитывается при каждом возврате в приложение. */
    fun refreshLocationAccess(context: Context) {
        val fine = context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        val coarse = context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        _locationAccess.value = fine || coarse
    }

    fun setServerHost(host: String, token: String = "") {
        val trimmedHost = host.trim().trimEnd('/')
        val trimmedToken = token.trim()
        _serverHost.value = trimmedHost
        _echoToken.value = trimmedToken
        _appContext?.let { ctx ->
            ctx.getSharedPreferences("echo_prefs", Context.MODE_PRIVATE)
                .edit()
                .putString("server_host", trimmedHost)
                .putString("echo_token", trimmedToken)
                .apply()
        }
        consecutiveErrors = 0
        if (trimmedHost.isNotBlank()) {
            startPolling()
        } else {
            stopPolling()
            _connectionStatus.value = "Не подключено"
        }
    }

    fun startPolling() {
        pollingJob?.cancel()
        pollingJob = scope.launch {
            while (isActive) {
                syncWithServer()
                val delayMs = if (consecutiveErrors > 2) 7000L else 3500L
                delay(delayMs)
            }
        }
    }

    fun stopPolling() {
        pollingJob?.cancel()
        pollingJob = null
    }

    fun togglePresence() {
        _presenceActive.value = !_presenceActive.value
        if (!_presenceActive.value) {
            scope.launch { deleteSignal() }
        } else {
            val cur = _currentTrack.value
            if (cur.artist != "Ожидание музыки...") {
                scope.launch { sendSignalToServer(cur.artist, cur.title, cur.sourceApp) }
            }
        }
    }

    fun updateDetectedMusic(artist: String, title: String, packageName: String) {
        val readableSource = when {
            packageName.contains("yandex", ignoreCase = true) -> "Яндекс Музыка"
            packageName.contains("vkontakte", ignoreCase = true) || packageName.contains("vk.music", ignoreCase = true) -> "VK Музыка"
            packageName.contains("zvuk", ignoreCase = true) || packageName.contains("sber", ignoreCase = true) -> "Звук"
            packageName.contains("spotify", ignoreCase = true) -> "Spotify"
            packageName.contains("telegram", ignoreCase = true) -> "Telegram Плеер"
            packageName.contains("aimp", ignoreCase = true) -> "AIMP"
            packageName.contains("poweramp", ignoreCase = true) -> "Poweramp"
            packageName.contains("apple", ignoreCase = true) -> "Apple Music"
            packageName.contains("youtube", ignoreCase = true) || packageName.contains("music.youtube", ignoreCase = true) -> "YouTube Music"
            else -> "Плеер Android"
        }

        val newArtist = artist.ifBlank { "Неизвестный исполнитель" }
        val newTitle = title.ifBlank { "Без названия" }

        // Поллинг медиа-сессий приходит часто: не сбрасываем образ и не шлём дубли
        val current = _currentTrack.value
        if (current.artist == newArtist && current.title == newTitle) return

        _currentTrack.value = PlayingTrack(
            artist = newArtist,
            title = newTitle,
            sourceApp = readableSource,
            poeticPersona = "Генерируется куратором...",
            mood = "Живое присутствие"
        )

        if (lastSentTrack != Pair(newArtist, newTitle)) {
            lastSentTrack = Pair(newArtist, newTitle)
            scope.launch { sendSignalToServer(newArtist, newTitle, readableSource) }
        }
    }

    // ── Ручная трансляция трека (в т.ч. из Яндекс Музыки) ──

    fun broadcastTrack(
        artist: String,
        title: String,
        sourceName: String = "Яндекс Музыка",
        onResult: ((Boolean) -> Unit)? = null
    ) {
        val cleanArtist = artist.trim().ifBlank { "Неизвестный исполнитель" }.take(120)
        val cleanTitle = title.trim().ifBlank { "Без названия" }.take(160)

        _currentTrack.value = PlayingTrack(
            artist = cleanArtist,
            title = cleanTitle,
            sourceApp = sourceName,
            poeticPersona = "Генерируется куратором...",
            mood = "Живое присутствие"
        )
        lastSentTrack = Pair(cleanArtist, cleanTitle)

        scope.launch {
            val ok = sendSignalToServer(cleanArtist, cleanTitle, sourceName)
            withContext(Dispatchers.Main) { onResult?.invoke(ok) }
        }
    }

    /**
     * Понимает текст, которым поделились из Яндекс Музыки:
     * «Artist — Track», ссылку на трек, строку вида «Слушай «Artist — Track» на Яндекс Музыке».
     */
    fun broadcastFromSharedText(raw: String, onResult: ((Boolean, String) -> Unit)? = null) {
        val parsed = parseArtistTitle(raw)
        if (parsed == null) {
            _broadcastFeedback.value = "Не удалось разобрать трек. Вставьте «Исполнитель — Название»."
            onResult?.invoke(false, "parse_failed")
            return
        }
        val (artist, title) = parsed
        broadcastTrack(artist, title, "Яндекс Музыка") { ok ->
            _broadcastFeedback.value = if (ok) {
                "В эфире: $artist — $title"
            } else {
                "Не удалось отправить трек — проверьте соединение и токен"
            }
            onResult?.invoke(ok, "$artist — $title")
        }
    }

    private fun cleanTrackPart(value: String): String {
        return value
            .replace(Regex("\\s+"), " ")
            .replace(Regex("^(слушай|слушайте|трек|песня)[:\\s]+", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\s+(на|в)\\s+(яндекс\\s*музыке|yandex\\s*music).*$", RegexOption.IGNORE_CASE), "")
            .trim()
            .trim('«', '»', '"', '“', '”', '\'', '`')
            .trim()
    }

    private fun parseArtistTitle(raw: String): Pair<String, String>? {
        if (raw.isBlank()) return null
        val withoutUrls = raw
            .replace(Regex("https?://\\S+", RegexOption.IGNORE_CASE), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

        for (text in listOf(withoutUrls, raw)) {
            if (text.isBlank()) continue
            for (separator in listOf(" — ", " – ", " - ", "—", "–")) {
                val index = text.indexOf(separator)
                if (index <= 0) continue
                val artist = cleanTrackPart(text.substring(0, index))
                val title = cleanTrackPart(text.substring(index + separator.length))
                if (artist.isNotBlank() && title.isNotBlank()) {
                    return artist.take(120) to title.take(160)
                }
            }
        }
        return null
    }

    // ── Построение URL с автоопределением http/https ──
    private fun buildUrl(endpoint: String): String {
        val host = _serverHost.value.trim().trimEnd('/')
        if (host.isBlank()) return ""
        val fullHost = when {
            host.startsWith("http://") || host.startsWith("https://") -> host
            host.contains("localhost") || host.contains("127.0.0.1") ||
            host.startsWith("192.168.") || host.startsWith("10.") ||
            host.contains(":8080") || host.contains(":80") -> "http://$host"
            else -> "https://$host"
        }
        val ep = if (endpoint.startsWith("/")) endpoint else "/$endpoint"
        return "$fullHost$ep"
    }

    private suspend fun syncWithServer() {
        val url = buildUrl("/api/state?deviceId=$deviceId")
        if (url.isBlank()) return

        val token = _echoToken.value
        val requestBuilder = Request.Builder().url(url).get()
        if (token.isNotBlank()) requestBuilder.addHeader("X-Echo-Token", token)
        requestBuilder.addHeader("X-Device-Id", deviceId)
        val request = requestBuilder.build()

        try {
            val response = withContext(Dispatchers.IO) {
                httpClient.newCall(request).execute()
            }
            if (response.code == 401) {
                _connectionStatus.value = "✗ Неверный токен (401)"
                consecutiveErrors++
                response.close()
                return
            }
            val body = response.body?.string() ?: return
            if (!response.isSuccessful) {
                consecutiveErrors++
                return
            }

            consecutiveErrors = 0
            val json = JSONObject(body)

            // 1. Парсинг живых сигналов слушателей
            val signalsArray = json.optJSONArray("signals")
            val signals = mutableListOf<NearbyEchoSignal>()
            if (signalsArray != null) {
                for (i in 0 until signalsArray.length()) {
                    val s = signalsArray.getJSONObject(i)
                    val id = s.optString("id", "")
                    if (id == deviceId) continue

                    val distM = s.optInt("distanceMeters", 100)
                    val distLabel = "~$distM м"
                    val resDays = s.optInt("resonanceDays", 0)

                    val rawCover = s.optString("coverUrl", "")
                    val parsedCover = if (rawCover.isNotBlank()) {
                        val full = if (rawCover.startsWith("http://") || rawCover.startsWith("https://")) rawCover else buildUrl(rawCover)
                        val trackKey = "${s.optString("artist", "")}_${s.optString("title", "")}".hashCode()
                        if (full.contains("/api/cover/")) "$full?v=$trackKey" else full
                    } else null

                    signals.add(
                        NearbyEchoSignal(
                            id = id,
                            artist = s.optString("artist", "Неизвестный исполнитель"),
                            title = s.optString("title", "Без названия"),
                            sourceApp = s.optString("sourceName", "ЭХО"),
                            distanceLabel = distLabel,
                            poeticName = s.optString("personaTitle", "Архивариус забытых кассет"),
                            aiObservation = s.optString("personaObservation", ""),
                            resonanceStory = s.optString("resonanceStory", "Тёплый вечерний резонанс в ночном городе."),
                            compatibility = s.optInt("compatibilityPercent", 80),
                            resonanceDays = if (resDays > 1) "$resDays дн." else null,
                            encountersCount = s.optInt("encountersCount", if (resDays > 0) resDays else 1).coerceAtLeast(1),
                            angleDeg = s.optDouble("angleDegrees", 45.0).toFloat(),
                            ringFactor = s.optDouble("distanceRatio", 0.5).toFloat(),
                            colorHex = s.optLong("colorHex", 0xFF38BDF8L),
                            isGhost = s.optBoolean("isGhost", false),
                            coverUrl = parsedCover,
                            bpm = s.optInt("bpm", 120),
                            camelotKey = s.optString("camelotKey", "8A"),
                            genre = s.optString("genre", "indie")
                        )
                    )
                }
            }
            _nearbySignals.value = signals

            // 2. Парсинг музыкальных тайников (капсул)
            val capsArray = json.optJSONArray("capsules")
            val capsList = mutableListOf<EchoCapsule>()
            if (capsArray != null) {
                for (i in 0 until capsArray.length()) {
                    val c = capsArray.getJSONObject(i)
                    val distM = c.optInt("distanceMeters", 65)
                    val distLabel = when {
                        distM < 50 -> "~$distM м (рядом)"
                        distM < 120 -> "~$distM м"
                        else -> "~$distM м (размыто)"
                    }
                    val compPercent = c.optInt("compatibilityPercent", 90)
                    val minRes = c.optInt("minResonance", 0)
                    val perk = c.optString("secretPerk", "")
                    val isUnl = minRes <= 0 || compPercent >= minRes

                    capsList.add(
                        EchoCapsule(
                            id = c.optString("id", ""),
                            creatorId = c.optString("creatorId", ""),
                            artist = c.optString("artist", "Неизвестный автор"),
                            title = c.optString("title", "Без названия"),
                            moodLabel = c.optString("moodLabel", "Теплый тайник"),
                            personaTitle = c.optString("personaTitle", "Музыкальный тайник"),
                            zoneName = c.optString("zoneName", "Вечерняя улица"),
                            secretMessage = c.optString("secretMessage", "Здесь оставлена музыкальная память."),
                            distanceLabel = distLabel,
                            angleDeg = c.optDouble("angleDegrees", 120.0).toFloat(),
                            ringFactor = c.optDouble("distanceRatio", 0.5).toFloat(),
                            compatibility = compPercent,
                            isVenue = c.optBoolean("isVenue", false),
                            venueName = c.optString("venueName", ""),
                            minResonance = minRes,
                            secretPerk = perk,
                            isUnlocked = isUnl,
                            createdAt = c.optLong("createdAt", 0L)
                        )
                    )
                }
            }
            _capsules.value = capsList

            val totalFound = signals.size + capsList.size
            _connectionStatus.value = "● Онлайн ($totalFound объектов)"

            // 3. Персональный ИИ-образ слушателя
            val myPersona = json.optString("myCuratedPersona", "")
            val myMood = json.optString("myRefinedMood", "")
            if (myPersona.isNotBlank()) {
                val cur = _currentTrack.value
                _currentTrack.value = cur.copy(poeticPersona = myPersona, mood = myMood.ifBlank { cur.mood })
            }

            // 4. Проверка входящих откликов (баннеров)
            val bannerObj = json.optJSONObject("incomingBanner")
            if (bannerObj != null) {
                val bannerId = bannerObj.optLong("id", 0L)
                val targetId = bannerObj.optString("targetId", "")
                val senderId = bannerObj.optString("senderId", "")
                // Публичные цели (сигнал/тайник/широковещательный импульс с ПК) касаются всех,
                // личные — только адресата. Раньше телефон отбрасывал sig_*/broadcast.
                val isPublicTarget = targetId == "broadcast" ||
                    targetId == "all" ||
                    targetId == "pc_studio_node" ||
                    targetId.startsWith("sig_") ||
                    targetId.startsWith("cap_")
                val isForThisDevice = targetId == deviceId || targetId.isEmpty() || isPublicTarget
                if (bannerId != 0L && bannerId > lastHandledBannerId && isForThisDevice && senderId != deviceId) {
                    lastHandledBannerId = bannerId
                    _incomingReaction.value = IncomingReaction(
                        id = bannerId,
                        symbol = bannerObj.optString("symbol", "✦"),
                        senderId = senderId.ifBlank { "Слушатель" },
                        message = bannerObj.optString("message", "Вам отправлен импульс"),
                        timestamp = bannerObj.optLong("time", System.currentTimeMillis())
                    )
                }
            }

            // 5. История откликов эфира для Летописи созвучий
            val histArr = json.optJSONArray("reactionsHistory")
            if (histArr != null) {
                val histList = mutableListOf<EchoReactionRecord>()
                for (i in 0 until histArr.length()) {
                    val item = histArr.getJSONObject(i)
                    histList.add(
                        EchoReactionRecord(
                            id = item.optLong("id", 0L),
                            senderId = item.optString("senderId", "Слушатель"),
                            symbol = item.optString("symbol", "✦"),
                            message = item.optString("message", "Спасибо за созвучие"),
                            timeSec = item.optLong("time", System.currentTimeMillis() / 1000)
                        )
                    )
                }
                EchoChronicleManager.updateReactionsHistory(histList)
            }

            // 6. АРГОН: Тепловые пятна
            val patchesArray = json.optJSONArray("thermalPatches")
            val patches = mutableListOf<ThermalPatch>()
            if (patchesArray != null) {
                for (i in 0 until patchesArray.length()) {
                    val p = patchesArray.getJSONObject(i)
                    patches.add(
                        ThermalPatch(
                            id = p.optString("id", ""),
                            targetId = p.optString("targetId", ""),
                            senderId = p.optString("senderId", ""),
                            x = p.optDouble("x", 0.5).toFloat(),
                            y = p.optDouble("y", 0.5).toFloat(),
                            ageSec = p.optDouble("ageSec", 0.0).toFloat()
                        )
                    )
                }
            }
            _thermalPatches.value = patches

            // 7. АРГОН: Синхронизированные цели
            val syncsArray = json.optJSONArray("needleSyncedTargets")
            val syncSet = mutableSetOf<String>()
            if (syncsArray != null) {
                for (i in 0 until syncsArray.length()) {
                    syncSet.add(syncsArray.getString(i))
                }
            }
            _needleSyncedTargets.value = syncSet

            // 8. АРГОН: Точка Лагранжа
            val lagObj = json.optJSONObject("lagrangePoint")
            if (lagObj != null && lagObj.optBoolean("active", false)) {
                val partsArr = lagObj.optJSONArray("participants")
                val parts = mutableListOf<String>()
                if (partsArr != null) {
                    for (i in 0 until partsArr.length()) parts.add(partsArr.getString(i))
                }
                _lagrangePoint.value = LagrangePointInfo(
                    active = true,
                    angleDegrees = lagObj.optDouble("angleDegrees", 0.0).toFloat(),
                    distanceRatio = lagObj.optDouble("distanceRatio", 0.5).toFloat(),
                    secretTrack = lagObj.optString("secretTrack", "Burial — Archangel (Nocturnal Re-edit)"),
                    secretMessage = lagObj.optString("secretMessage", "Точка Лагранжа: акустическое затмение"),
                    participants = parts
                )
            } else {
                _lagrangePoint.value = null
            }

            // 9. АРГОН: Шум рассвета
            _isDawnMode.value = json.optBoolean("isDawnMode", false)

        } catch (e: IOException) {
            consecutiveErrors++
            _connectionStatus.value = "✗ Нет связи с сервером"
            Log.w("EchoRepo", "Sync failed: ${e.message}")
        } catch (e: Exception) {
            Log.w("EchoRepo", "Parse error: ${e.message}")
        }
    }

    private suspend fun sendSignalToServer(artist: String, title: String, sourceName: String): Boolean {
        val url = buildUrl("/api/signal")
        if (url.isBlank() || !_presenceActive.value) return false

        val body = JSONObject().apply {
            put("id", deviceId)
            put("artist", artist)
            put("title", title)
            put("sourceName", sourceName)
        }.toString()

        val token = _echoToken.value
        val requestBuilder = Request.Builder()
            .url(url)
            .post(body.toRequestBody("application/json".toMediaType()))
        if (token.isNotBlank()) requestBuilder.addHeader("X-Echo-Token", token)
        requestBuilder.addHeader("X-Device-Id", deviceId)
        val request = requestBuilder.build()

        return try {
            withContext(Dispatchers.IO) {
                httpClient.newCall(request).execute()
            }.use { response ->
                when {
                    response.code == 401 -> {
                        _connectionStatus.value = "✗ Неверный токен (401)"
                        false
                    }
                    response.isSuccessful -> {
                        val respBody = response.body?.string()
                        if (!respBody.isNullOrBlank()) {
                            try {
                                val json = JSONObject(respBody)
                                val myPersona = json.optString("myCuratedPersona", "")
                                val myMood = json.optString("myRefinedMood", "")
                                if (myPersona.isNotBlank()) {
                                    val cur = _currentTrack.value
                                    _currentTrack.value = cur.copy(
                                        poeticPersona = myPersona,
                                        mood = myMood.ifBlank { cur.mood }
                                    )
                                }
                            } catch (e: Exception) {
                                Log.w("EchoRepo", "Signal response parse: ${e.message}")
                            }
                        }
                        true
                    }
                    else -> false
                }
            }
        } catch (e: Exception) {
            Log.w("EchoRepo", "Signal send failed: ${e.message}")
            false
        }
    }

    // ── Оставить музыкальный тайник (капсулу) ──
    fun dropCapsule(
        artist: String,
        title: String,
        secretMessage: String,
        zoneName: String = "Вечерняя улица",
        isVenue: Boolean = false,
        venueName: String = "",
        minResonance: Int = 0,
        secretPerk: String = "",
        onComplete: (Boolean) -> Unit = {}
    ) {
        val url = buildUrl("/api/drop_capsule")
        if (url.isBlank()) {
            onComplete(false)
            return
        }

        scope.launch {
            val body = JSONObject().apply {
                put("creatorId", deviceId)
                put("artist", artist)
                put("title", title)
                put("secretMessage", secretMessage)
                put("zoneName", zoneName)
                put("isVenue", isVenue)
                put("venueName", venueName)
                put("minResonance", minResonance)
                put("secretPerk", secretPerk)
            }.toString()

            val token = _echoToken.value
            val requestBuilder = Request.Builder()
                .url(url)
                .post(body.toRequestBody("application/json".toMediaType()))
            if (token.isNotBlank()) requestBuilder.addHeader("X-Echo-Token", token)
            requestBuilder.addHeader("X-Device-Id", deviceId)

            try {
                val success = withContext(Dispatchers.IO) {
                    httpClient.newCall(requestBuilder.build()).execute().use { it.isSuccessful }
                }
                withContext(Dispatchers.Main) {
                    onComplete(success)
                }
                syncWithServer()
            } catch (e: Exception) {
                Log.w("EchoRepo", "Drop capsule failed: ${e.message}")
                withContext(Dispatchers.Main) {
                    onComplete(false)
                }
            }
        }
    }

    // ── Отправка тихих реакций / настроений на сервер ──
    fun sendReaction(targetId: String, symbol: String, message: String = "Спасибо за созвучие!", onResult: ((Boolean) -> Unit)? = null) {
        scope.launch {
            val url = buildUrl("/api/react")
            if (url.isBlank()) {
                withContext(Dispatchers.Main) { onResult?.invoke(false) }
                return@launch
            }
            val body = JSONObject().apply {
                put("targetId", targetId)
                put("senderId", deviceId)
                put("symbol", symbol)
                put("message", message)
            }.toString()

            val token = _echoToken.value
            val requestBuilder = Request.Builder()
                .url(url)
                .post(body.toRequestBody("application/json".toMediaType()))
            if (token.isNotBlank()) requestBuilder.addHeader("X-Echo-Token", token)
            requestBuilder.addHeader("X-Device-Id", deviceId)
            val request = requestBuilder.build()

            try {
                val ok = withContext(Dispatchers.IO) {
                    httpClient.newCall(request).execute().use { it.isSuccessful }
                }
                withContext(Dispatchers.Main) { onResult?.invoke(ok) }
            } catch (e: Exception) {
                Log.w("EchoRepo", "Reaction failed: ${e.message}")
                withContext(Dispatchers.Main) { onResult?.invoke(false) }
            }
        }
    }

    // ── АРГОН: Синхронизация иглы (Drop-in синхрон) ──
    fun syncNeedle(targetId: String, onResult: ((Boolean) -> Unit)? = null) {
        scope.launch {
            val url = buildUrl("/api/needle_sync")
            if (url.isBlank()) {
                withContext(Dispatchers.Main) { onResult?.invoke(false) }
                return@launch
            }
            val body = JSONObject().apply {
                put("targetId", targetId)
                put("senderId", deviceId)
            }.toString()

            val token = _echoToken.value
            val requestBuilder = Request.Builder()
                .url(url)
                .post(body.toRequestBody("application/json".toMediaType()))
            if (token.isNotBlank()) requestBuilder.addHeader("X-Echo-Token", token)
            requestBuilder.addHeader("X-Device-Id", deviceId)

            try {
                val ok = withContext(Dispatchers.IO) {
                    httpClient.newCall(requestBuilder.build()).execute().use { it.isSuccessful }
                }
                withContext(Dispatchers.Main) { onResult?.invoke(ok) }
                syncWithServer()
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { onResult?.invoke(false) }
            }
        }
    }

    // ── АРГОН: Передача призрачного тепла (Thermal Breath) ──
    fun sendWarmth(targetId: String, x: Float, y: Float, onResult: ((Boolean) -> Unit)? = null) {
        scope.launch {
            val url = buildUrl("/api/send_warmth")
            if (url.isBlank()) {
                withContext(Dispatchers.Main) { onResult?.invoke(false) }
                return@launch
            }
            val body = JSONObject().apply {
                put("targetId", targetId)
                put("senderId", deviceId)
                put("x", x)
                put("y", y)
            }.toString()

            val token = _echoToken.value
            val requestBuilder = Request.Builder()
                .url(url)
                .post(body.toRequestBody("application/json".toMediaType()))
            if (token.isNotBlank()) requestBuilder.addHeader("X-Echo-Token", token)
            requestBuilder.addHeader("X-Device-Id", deviceId)

            try {
                val ok = withContext(Dispatchers.IO) {
                    httpClient.newCall(requestBuilder.build()).execute().use { it.isSuccessful }
                }
                withContext(Dispatchers.Main) { onResult?.invoke(ok) }
                syncWithServer()
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { onResult?.invoke(false) }
            }
        }
    }

    // ── АРГОН: Звуковой импульс «Камень в воду» ──
    fun sendWaveImpulse(targetId: String, onResult: ((Boolean) -> Unit)? = null) {
        scope.launch {
            val url = buildUrl("/api/wave_impulse")
            if (url.isBlank()) {
                withContext(Dispatchers.Main) { onResult?.invoke(false) }
                return@launch
            }
            val body = JSONObject().apply {
                put("targetId", targetId)
                put("senderId", deviceId)
            }.toString()

            val token = _echoToken.value
            val requestBuilder = Request.Builder()
                .url(url)
                .post(body.toRequestBody("application/json".toMediaType()))
            if (token.isNotBlank()) requestBuilder.addHeader("X-Echo-Token", token)
            requestBuilder.addHeader("X-Device-Id", deviceId)

            try {
                val ok = withContext(Dispatchers.IO) {
                    httpClient.newCall(requestBuilder.build()).execute().use { it.isSuccessful }
                }
                withContext(Dispatchers.Main) { onResult?.invoke(ok) }
                syncWithServer()
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { onResult?.invoke(false) }
            }
        }
    }

    private suspend fun deleteSignal() {
        val url = buildUrl("/api/delete_signal")
        if (url.isBlank()) return

        val body = JSONObject().apply { put("targetId", deviceId) }.toString()
        val token = _echoToken.value
        val requestBuilder = Request.Builder()
            .url(url)
            .post(body.toRequestBody("application/json".toMediaType()))
        if (token.isNotBlank()) requestBuilder.addHeader("X-Echo-Token", token)
        requestBuilder.addHeader("X-Device-Id", deviceId)
        val request = requestBuilder.build()

        try {
            withContext(Dispatchers.IO) { httpClient.newCall(request).execute() }.close()
        } catch (e: Exception) { /* ignore */ }
    }
}
