package kr.co.ninetyseconds.recommendation.data.remote

import java.io.IOException
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kr.co.ninetyseconds.recommendation.domain.DecisionSource
import kr.co.ninetyseconds.recommendation.domain.LocationId
import kr.co.ninetyseconds.recommendation.domain.RecommendationDecision
import kr.co.ninetyseconds.recommendation.domain.RecommendationItem
import kr.co.ninetyseconds.recommendation.domain.RecommendationItemId
import kr.co.ninetyseconds.recommendation.domain.RecommendationRejected
import kr.co.ninetyseconds.recommendation.domain.RecommendationRequest
import kr.co.ninetyseconds.recommendation.domain.RecommendationUnavailable
import kr.co.ninetyseconds.recommendation.domain.ports.RecommendationEngine
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class HttpRecommendationEngine(
    baseUrl: String,
    private val kioskKey: String,
    private val client: OkHttpClient = OkHttpClient(),
    private val json: Json = Json { ignoreUnknownKeys = true },
) : RecommendationEngine {
    private val endpoint = baseUrl.trimEnd('/') + "/api/v1/recommendations"

    init {
        require(baseUrl.startsWith("http://") || baseUrl.startsWith("https://")) { "Base URL must use HTTP(S)" }
        require(kioskKey.isNotBlank()) { "Kiosk key cannot be blank" }
    }

    override suspend fun recommend(request: RecommendationRequest): RecommendationDecision = withContext(Dispatchers.IO) {
        val payload = ApiRecommendationRequest(
            schemaVersion = 1,
            projectCode = request.projectId.value,
            kioskId = request.kioskId,
            sessionId = request.sessionId.value,
            requestId = request.requestId,
            emotionCode = request.emotionProfile.dominant.emotion.value,
            stressScore = request.stressScore,
            language = request.language,
            previousLocationId = request.previousLocationId?.value,
            consentStatus = request.consentStatus.name,
            participant = request.participant?.let { ApiParticipant(it.name, it.phone, it.birthDate, it.gender) },
            requestedAt = request.requestedAt.toString(),
        )
        val httpRequest = Request.Builder()
            .url(endpoint)
            .header("X-Kiosk-Key", kioskKey)
            .header("X-Request-Id", request.requestId)
            .post(json.encodeToString(payload).toRequestBody(JSON_MEDIA_TYPE))
            .build()

        val response = try {
            client.newCall(httpRequest).execute()
        } catch (error: IOException) {
            throw RecommendationUnavailable("Recommendation API is unreachable", error)
        }

        response.use {
            val body = it.body.string()
            when {
                it.isSuccessful -> parseDecision(body, request)
                it.code >= 500 -> throw RecommendationUnavailable("Recommendation API failed with HTTP ${it.code}")
                else -> throw RecommendationRejected(parseError(body, it.code))
            }
        }
    }

    private fun parseDecision(body: String, request: RecommendationRequest): RecommendationDecision {
        val result = try {
            json.decodeFromString<ApiRecommendationResult>(body)
        } catch (error: SerializationException) {
            throw RecommendationUnavailable("Recommendation API returned an invalid response", error)
        }
        if (result.schemaVersion != 1) throw RecommendationUnavailable("Unsupported response schema ${result.schemaVersion}")
        if (result.requestId != request.requestId) throw RecommendationUnavailable("Response request id does not match")
        return RecommendationDecision(
            requestId = result.requestId,
            item = RecommendationItem(
                id = RecommendationItemId(result.item.id),
                locationId = LocationId(result.location.id),
                title = result.item.name.resolve(request.language),
                imageRef = result.item.imageUrl,
                supportedEmotions = setOf(request.emotionProfile.dominant.emotion),
            ),
            source = DecisionSource.REMOTE,
            decidedAt = parseInstant(result.createdAt),
        )
    }

    private fun parseInstant(value: String): Instant = try {
        Instant.parse(value)
    } catch (error: Exception) {
        throw RecommendationUnavailable("Response created_at is invalid", error)
    }

    private fun parseError(body: String, status: Int): String = try {
        json.decodeFromString<ApiError>(body).message ?: "Recommendation rejected with HTTP $status"
    } catch (_: SerializationException) {
        "Recommendation rejected with HTTP $status"
    }

    private fun Map<String, String>.resolve(language: String): String =
        this[language]?.takeIf(String::isNotBlank)
            ?: values.firstOrNull(String::isNotBlank)
            ?: throw RecommendationUnavailable("Response item name is empty")

    private companion object {
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}
