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
    val excludedLocationIds: Set<LocationId> = emptySet(),
    val requestedAt: Instant,
) {
    init { require(requestId.isNotBlank()) { "Request id cannot be blank" } }
}

enum class DecisionSource { LOCAL, REMOTE }

data class RecommendationDecision(
    val requestId: String,
    val item: RecommendationItem,
    val source: DecisionSource,
    val decidedAt: Instant,
)
