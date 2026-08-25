package kr.co.ninetyseconds.recommendation.application

import kr.co.ninetyseconds.recommendation.domain.RecommendationDecision
import kr.co.ninetyseconds.recommendation.domain.RecommendationRequest
import kr.co.ninetyseconds.recommendation.domain.ports.RecommendationEngine
import kr.co.ninetyseconds.recommendation.domain.ports.RecommendationEventSink

class Recommend(
    private val engine: RecommendationEngine,
    private val eventSink: RecommendationEventSink,
) {
    suspend operator fun invoke(request: RecommendationRequest): RecommendationDecision {
        val decision = engine.recommend(request)
        eventSink.record(decision)
        return decision
    }
}
