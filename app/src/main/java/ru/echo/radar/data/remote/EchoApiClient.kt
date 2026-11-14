package ru.echo.radar.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

class EchoApiClient {
    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .writeTimeout(5, TimeUnit.SECONDS)
        .build()

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    suspend fun sendSignal(baseUrl: String, req: SignalRequest): Boolean = withContext(Dispatchers.IO) {
        try {
            val body = json.encodeToString(req).toRequestBody(jsonMediaType)
            val request = Request.Builder()
                .url("${baseUrl.trimEnd('/')}/api/signal")
                .header("X-Echo-Token", req.deviceToken)
                .post(body)
                .build()

            client.newCall(request).execute().use { response ->
                response.isSuccessful
            }
        } catch (e: Exception) {
            false
        }
    }

    suspend fun fetchState(baseUrl: String, deviceToken: String, lat: Double, lon: Double): StateResponse? = withContext(Dispatchers.IO) {
        try {
            val url = "${baseUrl.trimEnd('/')}/api/state?lat=$lat&lon=$lon"
            val request = Request.Builder()
                .url(url)
                .header("X-Echo-Token", deviceToken)
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val str = response.body?.string() ?: return@withContext null
                json.decodeFromString<StateResponse>(str)
            }
        } catch (e: Exception) {
            null
        }
    }

    suspend fun createCapsule(baseUrl: String, req: CreateCapsuleRequest): Boolean = withContext(Dispatchers.IO) {
        try {
            val body = json.encodeToString(req).toRequestBody(jsonMediaType)
            val request = Request.Builder()
                .url("${baseUrl.trimEnd('/')}/api/capsule")
                .header("X-Echo-Token", req.deviceToken)
                .post(body)
                .build()

            client.newCall(request).execute().use { response ->
                response.isSuccessful
            }
        } catch (e: Exception) {
            false
        }
    }

    suspend fun sendQuietPulse(baseUrl: String, req: QuietPulseRequest): Boolean = withContext(Dispatchers.IO) {
        try {
            val body = json.encodeToString(req).toRequestBody(jsonMediaType)
            val request = Request.Builder()
                .url("${baseUrl.trimEnd('/')}/api/pulse")
                .header("X-Echo-Token", req.deviceToken)
                .post(body)
                .build()

            client.newCall(request).execute().use { response ->
                response.isSuccessful
            }
        } catch (e: Exception) {
            false
        }
    }
}
