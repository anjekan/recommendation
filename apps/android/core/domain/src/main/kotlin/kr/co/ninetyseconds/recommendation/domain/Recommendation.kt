package kr.co.ninetyseconds.recommendation.domain

import java.time.Instant

data class RecommendationItem(
    val id: RecommendationItemId,
    val locationId: LocationId,
    val title: String,
    val imageRef: String?,
    val supportedEmotions: Set<EmotionCode>,
    val enabled: Boolean = true,
) {
    init {
        require(title.isNotBlank()) { "Recommendation item title cannot be blank" }
        require(supportedEmotions.isNotEmpty()) { "Recommendation item must support an emotion" }
    }
}

data class RecommendationRequest(
    val requestId: String,
    val projectId: ProjectId,
    val sessionId: SessionId,
    val emotionProfile: EmotionProfile,
    val previousLocationId: LocationId? = null,
    val excludedLocationIds: Set<LocationId> = emptySet(),
    val requestedAt: Instant,
    val kioskId: String = "UNASSIGNED",
    val stressScore: Int = 0,
    val language: String = "ko",
    val consentStatus: ConsentStatus = ConsentStatus.NOT_ASKED,
    val participant: ParticipantProfile? = null,
    val conditionCode: String? = null,
    val journeySenseCodes: List<String> = emptyList(),
    val operationContext: RecommendationOperationContext? = null,
) {
    init {
        require(requestId.isNotBlank()) { "Request id cannot be blank" }
        require(kioskId.isNotBlank()) { "Kiosk id cannot be blank" }
        require(stressScore in 0..100) { "Stress score must be between 0 and 100" }
        require(language.isNotBlank()) { "Language cannot be blank" }
        require(participant == null || consentStatus == ConsentStatus.CONSENTED) {
            "Participant information requires consent"
        }
        require(journeySenseCodes.size <= 3) { "Journey can contain at most three senses" }
        require(journeySenseCodes.none(String::isBlank)) { "Journey sense code cannot be blank" }
    }
}

data class RecommendationOperationContext(
    val raining: Boolean = false,
    val companionType: String? = null,
    val performanceWindowOpen: Boolean = false,
)

enum class ConsentStatus { CONSENTED, DECLINED, NOT_ASKED }
data class ParticipantProfile(val name: String, val phone: String, val birthDate: String, val gender: String)
enum class DecisionSource { LOCAL, REMOTE, LOCAL_FALLBACK }

data class RecommendationDecision(
    val requestId: String,
    val item: RecommendationItem,
    val source: DecisionSource,
    val decidedAt: Instant,
    val journey: List<JourneyStop> = emptyList(),
)

data class JourneyStop(
    val order: Int,
    val senseCode: String,
    val item: RecommendationItem,
) {
    init {
        require(order in 1..3) { "Journey stop order must be between 1 and 3" }
        require(senseCode.isNotBlank()) { "Journey stop sense code cannot be blank" }
    }
}
