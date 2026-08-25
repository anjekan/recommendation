package kr.co.ninetyseconds.recommendation.application

import java.time.Clock
import java.util.SortedMap
import kr.co.ninetyseconds.recommendation.domain.DecisionSource
import kr.co.ninetyseconds.recommendation.domain.RecommendationDecision
import kr.co.ninetyseconds.recommendation.domain.RecommendationItem
import kr.co.ninetyseconds.recommendation.domain.RecommendationRequest
import kr.co.ninetyseconds.recommendation.domain.ports.ProjectCatalog
import kr.co.ninetyseconds.recommendation.domain.ports.RecommendationEngine
import kr.co.ninetyseconds.recommendation.domain.ports.RecommendationHistory

class NoRecommendationAvailable(message: String) : IllegalStateException(message)

class LocalRecommendationEngine(
    private val catalog: ProjectCatalog,
    private val history: RecommendationHistory,
    private val clock: Clock = Clock.systemUTC(),
    private val recentHistoryLimit: Int = 1,
) : RecommendationEngine {
    init {
        require(recentHistoryLimit > 0) { "Recent history limit must be positive" }
    }

    override suspend fun recommend(request: RecommendationRequest): RecommendationDecision {
        val emotion = request.emotionProfile.dominant.emotion
        val eligible = catalog.getRecommendationItems(request.projectId)
            .filter { item ->
                item.enabled &&
                    emotion in item.supportedEmotions &&
                    item.locationId !in request.excludedLocationIds
            }

        if (eligible.isEmpty()) {
            throw NoRecommendationAvailable("No enabled item is available for emotion ${emotion.value}")
        }

        val recentLocations = history.recentLocationIds(recentHistoryLimit).toSet()
        val nonRepeated = eligible.filterNot { it.locationId in recentLocations }
        val candidates = nonRepeated.ifEmpty { eligible }
        val selected = selectDeterministically(candidates, request.requestId)

        return RecommendationDecision(
            requestId = request.requestId,
            item = selected,
            source = DecisionSource.LOCAL,
            decidedAt = clock.instant(),
        )
    }

    private fun selectDeterministically(
        candidates: List<RecommendationItem>,
        requestId: String,
    ): RecommendationItem {
        val byLocation: SortedMap<String, List<RecommendationItem>> = candidates
            .groupBy { it.locationId.value }
            .toSortedMap()
        val locations = byLocation.keys.toList()
        val locationIndex = Math.floorMod(requestId.hashCode(), locations.size)
        val items = byLocation.getValue(locations[locationIndex]).sortedBy { it.id.value }
        val itemIndex = Math.floorMod(requestId.reversed().hashCode(), items.size)
        return items[itemIndex]
    }
}
