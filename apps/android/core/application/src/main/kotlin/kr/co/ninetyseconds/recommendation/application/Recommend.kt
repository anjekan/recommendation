package kr.co.ninetyseconds.recommendation.application

import kr.co.ninetyseconds.recommendation.domain.RecommendationDecision
import kr.co.ninetyseconds.recommendation.domain.RecommendationRequest
import kr.co.ninetyseconds.recommendation.domain.ports.RecommendationEngine
import kr.co.ninetyseconds.recommendation.domain.ports.RecommendationEventSink
import kr.co.ninetyseconds.recommendation.domain.ports.RecommendationHistory

class Recommend(
    private val engine: RecommendationEngine,
    private val eventSink: RecommendationEventSink,
    private val history: RecommendationHistory,
) {
    suspend operator fun invoke(request: RecommendationRequest): RecommendationDecision {
        val previousLocationId = request.previousLocationId ?: history.recentLocationIds(limit = 1).firstOrNull()
        val enrichedRequest = request.copy(
            previousLocationId = previousLocationId,
            excludedLocationIds = previousLocationId?.let(request.excludedLocationIds::plus)
                ?: request.excludedLocationIds,
        )
        val decision = engine.recommend(enrichedRequest)
        eventSink.record(enrichedRequest, decision)
        return decision
    }
}
