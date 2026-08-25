package kr.co.ninetyseconds.recommendation.application

import kr.co.ninetyseconds.recommendation.domain.DecisionSource
import kr.co.ninetyseconds.recommendation.domain.RecommendationDecision
import kr.co.ninetyseconds.recommendation.domain.RecommendationRequest
import kr.co.ninetyseconds.recommendation.domain.RecommendationUnavailable
import kr.co.ninetyseconds.recommendation.domain.RuntimeMode
import kr.co.ninetyseconds.recommendation.domain.ports.RecommendationEngine

fun interface RuntimeModeProvider {
    fun currentMode(): RuntimeMode
}

class RuntimeRecommendationEngine(
    private val modeProvider: RuntimeModeProvider,
    private val local: RecommendationEngine,
    private val remote: RecommendationEngine,
) : RecommendationEngine {
    override suspend fun recommend(request: RecommendationRequest): RecommendationDecision =
        when (modeProvider.currentMode()) {
            RuntimeMode.LOCAL -> local.recommend(request)
            RuntimeMode.REMOTE -> remote.recommend(request)
            RuntimeMode.HYBRID -> try {
                remote.recommend(request)
            } catch (_: RecommendationUnavailable) {
                local.recommend(request).copy(source = DecisionSource.LOCAL_FALLBACK)
            }
        }
}
