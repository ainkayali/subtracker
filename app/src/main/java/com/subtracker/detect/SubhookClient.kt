package com.subtracker.detect

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
private val mediaJson = "application/json".toMediaType()

@Serializable
private data class RegisterRequest(val token: String, val device_name: String? = null)

@Serializable
data class BackfillRequest(val months: Int)

@Serializable
data class BackfillAcceptedResponse(val ok: Boolean, val job_id: Long)

@Serializable
data class BackfillJobStatusRequest(val job_id: Long)

@Serializable
data class BackfillJob(
    val id: Long,
    val months: Int,
    val status: String,
    val processed: Int = 0,
    val total: Int = 0,
    val inserted: Int = 0,
    val detections: List<DetectionPayload> = emptyList(),
    val error: String? = null
)

@Serializable
data class BackfillStatusResponse(val ok: Boolean, val job: BackfillJob)

class SubhookClient(
    private val baseUrl: String,
    private val hmacSecret: String
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()

    suspend fun registerFcmToken(token: String, deviceName: String?) = withContext(Dispatchers.IO) {
        val body = json.encodeToString(RegisterRequest.serializer(), RegisterRequest(token, deviceName))
        val req = signedRequest("/register-fcm-token", body)
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) error("register failed: ${resp.code}")
        }
    }

    suspend fun submitBackfill(months: Int): Long = withContext(Dispatchers.IO) {
        val body = json.encodeToString(BackfillRequest.serializer(), BackfillRequest(months))
        val req = signedRequest("/backfill", body)
        client.newCall(req).execute().use { resp ->
            val txt = resp.body?.string() ?: error("empty response")
            if (resp.code != 202 && !resp.isSuccessful) error("backfill submit failed: ${resp.code} $txt")
            json.decodeFromString(BackfillAcceptedResponse.serializer(), txt).job_id
        }
    }

    suspend fun backfillStatus(jobId: Long): BackfillJob = withContext(Dispatchers.IO) {
        val body = json.encodeToString(BackfillJobStatusRequest.serializer(), BackfillJobStatusRequest(jobId))
        val req = signedRequest("/backfill-status", body)
        client.newCall(req).execute().use { resp ->
            val txt = resp.body?.string() ?: error("empty response")
            if (!resp.isSuccessful) error("status failed: ${resp.code} $txt")
            json.decodeFromString(BackfillStatusResponse.serializer(), txt).job
        }
    }

    private fun signedRequest(path: String, body: String): Request {
        val ts = System.currentTimeMillis()
        val auth = SubhookAuth.sign(hmacSecret, ts, body)
        return Request.Builder()
            .url(baseUrl.trimEnd('/') + path)
            .post(body.toRequestBody(mediaJson))
            .header("Authorization", auth)
            .build()
    }
}
