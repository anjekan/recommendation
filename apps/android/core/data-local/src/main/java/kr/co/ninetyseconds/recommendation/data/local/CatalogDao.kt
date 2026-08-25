package kr.co.ninetyseconds.recommendation.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

@Dao
internal abstract class CatalogDao {
    @Query("SELECT * FROM recommendation_items WHERE projectId = :projectId AND enabled = 1")
    abstract suspend fun getEnabledItems(projectId: String): List<RecommendationItemEntity>

    @Query("SELECT item_emotions.* FROM item_emotions INNER JOIN recommendation_items ON item_emotions.itemId = recommendation_items.itemId WHERE recommendation_items.projectId = :projectId")
    abstract suspend fun getItemEmotions(projectId: String): List<ItemEmotionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun insertProject(project: ProjectEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun insertLocations(locations: List<LocationEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun insertItems(items: List<RecommendationItemEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun insertItemEmotions(emotions: List<ItemEmotionEntity>)

    @Query("DELETE FROM projects WHERE projectId = :projectId")
    protected abstract suspend fun deleteProject(projectId: String)

    @Transaction
    open suspend fun replace(project: ProjectEntity, locations: List<LocationEntity>, items: List<RecommendationItemEntity>, emotions: List<ItemEmotionEntity>) {
        deleteProject(project.projectId)
        insertProject(project)
        insertLocations(locations)
        insertItems(items)
        insertItemEmotions(emotions)
    }
}
