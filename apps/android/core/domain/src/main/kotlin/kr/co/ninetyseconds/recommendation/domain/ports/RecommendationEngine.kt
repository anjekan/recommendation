package kr.co.ninetyseconds.recommendation.domain.ports

import kr.co.ninetyseconds.recommendation.domain.RecommendationDecision
import kr.co.ninetyseconds.recommendation.domain.RecommendationRequest

interface RecommendationEngine {
    suspend fun recommend(request: RecommendationRequest): RecommendationDecision
}
