package kr.co.ninetyseconds.recommendation.domain.ports

import kr.co.ninetyseconds.recommendation.domain.RecommendationDecision

interface RecommendationEventSink {
    suspend fun record(decision: RecommendationDecision)
}
