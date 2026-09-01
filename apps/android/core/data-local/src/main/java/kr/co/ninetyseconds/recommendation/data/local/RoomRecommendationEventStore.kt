package kr.co.ninetyseconds.recommendation.data.local

import kr.co.ninetyseconds.recommendation.domain.LocationId
import kr.co.ninetyseconds.recommendation.domain.RecommendationDecision
import kr.co.ninetyseconds.recommendation.domain.RecommendationRequest
import kr.co.ninetyseconds.recommendation.domain.DecisionSource
import kr.co.ninetyseconds.recommendation.domain.ports.RecommendationEventSink
import kr.co.ninetyseconds.recommendation.domain.ports.RecommendationHistory
import kr.co.ninetyseconds.recommendation.domain.ports.RecommendationEventOutbox
import kr.co.ninetyseconds.recommendation.domain.ports.OfflineRecommendationEvent
import java.time.Instant

class RoomRecommendationEventStore internal constructor(
    private val dao: RecommendationEventDao,
) : RecommendationEventSink, RecommendationHistory, RecommendationEventOutbox {
    override suspend fun record(request: RecommendationRequest, decision: RecommendationDecision) {
        dao.insert(
            RecommendationEventEntity(
                eventId = request.requestId,
                requestId = decision.requestId,
                projectCode = request.projectId.value,
                kioskId = request.kioskId,
                sessionId = request.sessionId.value,
                emotionCode = request.emotionProfile.dominant.emotion.value,
                itemId = decision.item.id.value,
                locationId = decision.item.locationId.value,
                source = decision.source.name,
                consentStatus = request.consentStatus.name,
                stressScore = request.stressScore,
                policyVersion = if (decision.source == DecisionSource.REMOTE) "balanced-v2" else "local-v1",
                decidedAtEpochMillis = decision.decidedAt.toEpochMilli(),
                synced = decision.source == DecisionSource.REMOTE,
            ),
        )
    }

    override suspend fun recentLocationIds(limit: Int): List<LocationId> {
        require(limit > 0) { "Recent location limit must be positive" }
        return dao.getRecentLocationIds(limit).map(::LocationId)
    }

    override suspend fun pending(limit: Int): List<OfflineRecommendationEvent> {
        require(limit in 1..500) { "Pending event limit must be between 1 and 500" }
        return dao.getPending(limit).map { event ->
            OfflineRecommendationEvent(
                event.eventId, event.projectCode, event.kioskId, event.sessionId, event.emotionCode,
                event.itemId, event.locationId, event.source,
                kr.co.ninetyseconds.recommendation.domain.ConsentStatus.valueOf(event.consentStatus),
                event.stressScore, event.policyVersion, Instant.ofEpochMilli(event.decidedAtEpochMillis),
            )
        }
    }

    override suspend fun markSynced(eventIds: Set<String>) {
        if (eventIds.isNotEmpty()) dao.markSynced(eventIds)
    }
}
