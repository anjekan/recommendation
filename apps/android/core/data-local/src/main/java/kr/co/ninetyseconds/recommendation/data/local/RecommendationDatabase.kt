package kr.co.ninetyseconds.recommendation.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [ProjectEntity::class, LocationEntity::class, RecommendationItemEntity::class, ItemEmotionEntity::class, RecommendationEventEntity::class],
    version = 1,
    exportSchema = true,
)
internal abstract class RecommendationDatabase : RoomDatabase() {
    abstract fun catalogDao(): CatalogDao
    abstract fun recommendationEventDao(): RecommendationEventDao

    companion object {
        fun create(context: Context, databaseName: String = "recommendation.db"): RecommendationDatabase =
            Room.databaseBuilder(context, RecommendationDatabase::class.java, databaseName).build()
    }
}
