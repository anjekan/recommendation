package kr.co.ninetyseconds.recommendation.data.local

import kr.co.ninetyseconds.recommendation.domain.EmotionCode
import kr.co.ninetyseconds.recommendation.domain.LocationId
import kr.co.ninetyseconds.recommendation.domain.ProjectCatalogSnapshot
import kr.co.ninetyseconds.recommendation.domain.ProjectId
import kr.co.ninetyseconds.recommendation.domain.RecommendationItem
import kr.co.ninetyseconds.recommendation.domain.RecommendationItemId
import kr.co.ninetyseconds.recommendation.domain.ports.ProjectCatalogStore

class RoomProjectCatalog internal constructor(private val dao: CatalogDao) : ProjectCatalogStore {
    override suspend fun getRecommendationItems(projectId: ProjectId): List<RecommendationItem> {
        val emotionsByItem = dao.getItemEmotions(projectId.value).groupBy(ItemEmotionEntity::itemId, ItemEmotionEntity::emotionCode)
        return dao.getEnabledItems(projectId.value).map { entity ->
            RecommendationItem(
                id = RecommendationItemId(entity.itemId),
                locationId = LocationId(entity.locationId),
                title = entity.title,
                imageRef = entity.imageRef,
                supportedEmotions = emotionsByItem[entity.itemId].orEmpty().map(::EmotionCode).toSet(),
                enabled = entity.enabled,
            )
        }
    }

    override suspend fun replace(snapshot: ProjectCatalogSnapshot) {
        dao.replace(
            ProjectEntity(snapshot.projectId.value, snapshot.configVersion, snapshot.defaultLanguage),
            snapshot.locations.map { LocationEntity(it.id.value, snapshot.projectId.value, it.code, it.title, it.imageRef, it.capacity, it.enabled) },
            snapshot.items.map { RecommendationItemEntity(it.id.value, snapshot.projectId.value, it.locationId.value, it.title, it.imageRef, it.enabled) },
            snapshot.items.flatMap { item -> item.supportedEmotions.map { ItemEmotionEntity(item.id.value, it.value) } },
        )
    }
}
