package ru.echo.radar.data.local

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID

private val Context.dataStore by preferencesDataStore(name = "echo_settings")

class EchoPreferences(private val context: Context) {
    companion object {
        private val KEY_SERVER_URL = stringPreferencesKey("server_url")
        private val KEY_DEVICE_TOKEN = stringPreferencesKey("device_token")
        private val KEY_THEME_EVENING = booleanPreferencesKey("theme_evening")
    }

    val serverUrlFlow: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_SERVER_URL] ?: "http://10.0.2.2:8000"
    }

    val deviceTokenFlow: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_DEVICE_TOKEN] ?: UUID.randomUUID().toString().also { newToken ->
            // Save on first read
        }
    }

    val isEveningModeFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_THEME_EVENING] ?: false
    }

    suspend fun setServerUrl(url: String) {
        context.dataStore.edit { it[KEY_SERVER_URL] = url }
    }

    suspend fun setEveningMode(isEvening: Boolean) {
        context.dataStore.edit { it[KEY_THEME_EVENING] = isEvening }
    }
}
