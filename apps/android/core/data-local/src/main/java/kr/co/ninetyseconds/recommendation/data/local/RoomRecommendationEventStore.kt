package kr.co.ninetyseconds.recommendation.data.local

import kr.co.ninetyseconds.recommendation.domain.LocationId
import kr.co.ninetyseconds.recommendation.domain.RecommendationDecision
import kr.co.ninetyseconds.recommendation.domain.ports.RecommendationEventSink
import kr.co.ninetyseconds.recommendation.domain.ports.RecommendationHistory

class RoomRecommendationEventStore internal constructor(
    private val dao: RecommendationEventDao,
) : RecommendationEventSink, RecommendationHistory {
    override suspend fun record(decision: RecommendationDecision) {
        dao.insert(
            RecommendationEventEntity(
                requestId = decision.requestId,
                itemId = decision.item.id.value,
                locationId = decision.item.locationId.value,
                source = decision.source.name,
                decidedAtEpochMillis = decision.decidedAt.toEpochMilli(),
            ),
        )
    }

    override suspend fun recentLocationIds(limit: Int): List<LocationId> {
        require(limit > 0) { "Recent location limit must be positive" }
        return dao.getRecentLocationIds(limit).map(::LocationId)
    }
}
