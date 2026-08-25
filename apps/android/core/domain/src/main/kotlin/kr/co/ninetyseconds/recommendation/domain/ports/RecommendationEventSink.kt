package kr.co.ninetyseconds.recommendation.domain.ports

import kr.co.ninetyseconds.recommendation.domain.RecommendationDecision
import kr.co.ninetyseconds.recommendation.domain.LocationId

interface RecommendationEventSink {
    suspend fun record(decision: RecommendationDecision)
}

interface RecommendationHistory {
    suspend fun recentLocationIds(limit: Int): List<LocationId>
}
