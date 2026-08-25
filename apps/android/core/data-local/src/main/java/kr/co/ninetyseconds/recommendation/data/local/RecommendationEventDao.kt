package kr.co.ninetyseconds.recommendation.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
internal interface RecommendationEventDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(event: RecommendationEventEntity): Long

    @Query("SELECT locationId FROM recommendation_events ORDER BY decidedAtEpochMillis DESC LIMIT :limit")
    suspend fun getRecentLocationIds(limit: Int): List<String>
}
