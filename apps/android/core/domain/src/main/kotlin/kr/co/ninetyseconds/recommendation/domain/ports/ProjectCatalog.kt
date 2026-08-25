package kr.co.ninetyseconds.recommendation.domain.ports

import kr.co.ninetyseconds.recommendation.domain.ProjectId
import kr.co.ninetyseconds.recommendation.domain.RecommendationItem

interface ProjectCatalog {
    suspend fun getRecommendationItems(projectId: ProjectId): List<RecommendationItem>
}
