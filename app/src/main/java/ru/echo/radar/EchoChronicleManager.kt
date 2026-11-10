package ru.echo.radar

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import org.json.JSONArray
import org.json.JSONObject

/**
 * Запись сохраненного трека в ночную летопись.
 */
data class ChronicleTrackRecord(
    val id: String,
    val artist: String,
    val title: String,
    val coverUrl: String?,
    val distanceLabel: String,
    val poeticPersona: String,
    val resonancePercent: Int,
    val timestampMillis: Long,
    val sourceApp: String
)

/**
 * Запись взаимного отклика / импульса созвучия.
 */
data class EchoReactionRecord(
    val id: Long,
    val senderId: String,
    val symbol: String,
    val message: String,
    val timeSec: Long
)

/**
 * Менеджер персональной летописи ночных находок и откликов созвучия.
 */
object EchoChronicleManager {
    private const val PREFS_NAME = "echo_chronicle_prefs"
    private const val KEY_SAVED_TRACKS = "saved_tracks_json"
    private const val KEY_LAST_READ_REACTION_ID = "last_read_reaction_id"

    private var prefs: SharedPreferences? = null

    val savedTracks = mutableStateListOf<ChronicleTrackRecord>()
    val incomingEchoes = mutableStateListOf<EchoReactionRecord>()
    val hasUnread = mutableStateOf(false)

    fun init(context: Context) {
        if (prefs != null) return
        prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        loadSavedTracks()
    }

    private fun loadSavedTracks() {
        val raw = prefs?.getString(KEY_SAVED_TRACKS, null) ?: return
        try {
            val arr = JSONArray(raw)
            val list = mutableListOf<ChronicleTrackRecord>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(
                    ChronicleTrackRecord(
                        id = obj.optString("id", "${obj.optString("artist")}_${obj.optString("title")}"),
                        artist = obj.optString("artist", ""),
                        title = obj.optString("title", ""),
                        coverUrl = obj.optString("coverUrl").takeIf { it.isNotBlank() },
                        distanceLabel = obj.optString("distanceLabel", "~75 м"),
                        poeticPersona = obj.optString("poeticPersona", "Слушатель"),
                        resonancePercent = obj.optInt("resonancePercent", 80),
                        timestampMillis = obj.optLong("timestampMillis", System.currentTimeMillis()),
                        sourceApp = obj.optString("sourceApp", "Музыка")
                    )
                )
            }
            savedTracks.clear()
            savedTracks.addAll(list)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun saveTrack(track: VinylTrack): Boolean {
        // Проверяем, не сохранен ли уже такой трек
        if (savedTracks.any { it.artist.equals(track.artist, ignoreCase = true) && it.title.equals(track.title, ignoreCase = true) }) {
            return false
        }
        val record = ChronicleTrackRecord(
            id = "${track.id}_${System.currentTimeMillis()}",
            artist = track.artist,
            title = track.title,
            coverUrl = track.coverUrl,
            distanceLabel = track.distanceLabel,
            poeticPersona = track.poeticPersona,
            resonancePercent = track.compatibility,
            timestampMillis = System.currentTimeMillis(),
            sourceApp = track.sourceApp
        )
        savedTracks.add(0, record)
        persistTracks()
        return true
    }

    fun removeTrack(id: String) {
        savedTracks.removeAll { it.id == id }
        persistTracks()
    }

    private fun persistTracks() {
        val arr = JSONArray()
        for (item in savedTracks) {
            val obj = JSONObject().apply {
                put("id", item.id)
                put("artist", item.artist)
                put("title", item.title)
                put("coverUrl", item.coverUrl ?: "")
                put("distanceLabel", item.distanceLabel)
                put("poeticPersona", item.poeticPersona)
                put("resonancePercent", item.resonancePercent)
                put("timestampMillis", item.timestampMillis)
                put("sourceApp", item.sourceApp)
            }
            arr.put(obj)
        }
        prefs?.edit()?.putString(KEY_SAVED_TRACKS, arr.toString())?.apply()
    }

    fun isTrackSaved(artist: String, title: String): Boolean {
        return savedTracks.any { it.artist.equals(artist, ignoreCase = true) && it.title.equals(title, ignoreCase = true) }
    }

    fun updateReactionsHistory(history: List<EchoReactionRecord>) {
        incomingEchoes.clear()
        incomingEchoes.addAll(history.reversed()) // Свежие в начале

        val lastReadId = prefs?.getLong(KEY_LAST_READ_REACTION_ID, 0L) ?: 0L
        val maxId = history.maxOfOrNull { it.id } ?: 0L
        hasUnread.value = maxId > lastReadId
    }

    fun markAllRead() {
        val maxId = incomingEchoes.maxOfOrNull { it.id } ?: 0L
        if (maxId > 0) {
            prefs?.edit()?.putLong(KEY_LAST_READ_REACTION_ID, maxId)?.apply()
        }
        hasUnread.value = false
    }
}
