package kr.co.ninetyseconds.recommendation.data.remote

import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kr.co.ninetyseconds.recommendation.domain.RecommendationUnavailable
import kr.co.ninetyseconds.recommendation.domain.ports.OfflineRecommendationEvent
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class HttpRecommendationEventSync(
    baseUrl: String,
    private val kioskKey: String,
    private val client: OkHttpClient = OkHttpClient(),
    private val json: Json = Json { ignoreUnknownKeys = true },
) {
    private val endpoint = baseUrl.trimEnd('/') + "/api/v1/events/sync"

    suspend fun sync(events: List<OfflineRecommendationEvent>): Set<String> = withContext(Dispatchers.IO) {
        if (events.isEmpty()) return@withContext emptySet()
        val payload = SyncEventsRequest(events.map { it.toDto() })
        val request = Request.Builder()
            .url(endpoint)
            .header("X-Kiosk-Key", kioskKey)
            .post(json.encodeToString(payload).toRequestBody(JSON_MEDIA_TYPE))
            .build()
        val response = try {
            client.newCall(request).execute()
        } catch (error: IOException) {
            throw RecommendationUnavailable("Event sync API is unreachable", error)
        }
        response.use {
            if (!it.isSuccessful) throw RecommendationUnavailable("Event sync failed with HTTP ${it.code}")
            json.decodeFromString<SyncEventsResponse>(it.body.string()).acceptedEventIds.toSet()
        }
    }

    private fun OfflineRecommendationEvent.toDto() = RecommendationEventDto(
        eventId, projectCode, kioskId, sessionId, emotionCode, itemId, locationId, source,
        consentStatus.name, stressScore, policyVersion, occurredAt.toString(),
    )

    private companion object {
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}

@Serializable
private data class SyncEventsRequest(val events: List<RecommendationEventDto>)

@Serializable
private data class RecommendationEventDto(
    @SerialName("event_id") val eventId: String,
    @SerialName("project_code") val projectCode: String,
    @SerialName("kiosk_id") val kioskId: String,
    @SerialName("session_id") val sessionId: String,
    @SerialName("emotion_code") val emotionCode: String,
    @SerialName("item_id") val itemId: String,
    @SerialName("location_id") val locationId: String,
    val source: String,
    @SerialName("consent_status") val consentStatus: String,
    @SerialName("stress_score") val stressScore: Int,
    @SerialName("policy_version") val policyVersion: String,
    @SerialName("occurred_at") val occurredAt: String,
)

@Serializable
private data class SyncEventsResponse(
    @SerialName("accepted_event_ids") val acceptedEventIds: List<String>,
)
