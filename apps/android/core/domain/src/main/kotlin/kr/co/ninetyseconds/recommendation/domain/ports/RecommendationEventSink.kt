package kr.co.ninetyseconds.recommendation.domain.ports

import kr.co.ninetyseconds.recommendation.domain.RecommendationDecision
import kr.co.ninetyseconds.recommendation.domain.LocationId
import kr.co.ninetyseconds.recommendation.domain.RecommendationRequest
import kr.co.ninetyseconds.recommendation.domain.ConsentStatus
import java.time.Instant

interface RecommendationEventSink {
    suspend fun record(request: RecommendationRequest, decision: RecommendationDecision)
}

interface RecommendationHistory {
    suspend fun recentLocationIds(limit: Int): List<LocationId>
}

data class OfflineRecommendationEvent(
    val eventId: String,
    val projectCode: String,
    val kioskId: String,
    val sessionId: String,
    val emotionCode: String,
    val itemId: String,
    val locationId: String,
    val source: String,
    val consentStatus: ConsentStatus,
    val stressScore: Int,
    val policyVersion: String,
    val occurredAt: Instant,
)

interface RecommendationEventOutbox {
    suspend fun pending(limit: Int): List<OfflineRecommendationEvent>
    suspend fun markSynced(eventIds: Set<String>)
}
