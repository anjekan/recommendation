package kr.co.ninetyseconds.recommendation.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
internal data class ApiRecommendationRequest(
    @SerialName("schema_version") val schemaVersion: Int = 1,
    @SerialName("project_code") val projectCode: String,
    @SerialName("kiosk_id") val kioskId: String,
    @SerialName("session_id") val sessionId: String,
    @SerialName("request_id") val requestId: String,
    @SerialName("emotion_code") val emotionCode: String,
    @SerialName("stress_score") val stressScore: Int,
    val language: String,
    @SerialName("previous_location_id") val previousLocationId: String?,
    @SerialName("consent_status") val consentStatus: String,
    @SerialName("requested_at") val requestedAt: String,
)

@Serializable
internal data class ApiRecommendationResult(
    @SerialName("schema_version") val schemaVersion: Int,
    @SerialName("request_id") val requestId: String,
    val item: ApiItem,
    val location: ApiLocation,
    val source: String,
    @SerialName("created_at") val createdAt: String,
)

@Serializable
internal data class ApiItem(
    val id: String,
    val type: String,
    val name: Map<String, String>,
    val description: Map<String, String>,
    @SerialName("image_url") val imageUrl: String,
    val attributes: JsonObject = JsonObject(emptyMap()),
)

@Serializable
internal data class ApiLocation(
    val id: String,
    val code: String,
    val name: Map<String, String>,
    val status: String,
)

@Serializable
internal data class ApiError(
    val code: String? = null,
    val message: String? = null,
    @SerialName("request_id") val requestId: String? = null,
)
