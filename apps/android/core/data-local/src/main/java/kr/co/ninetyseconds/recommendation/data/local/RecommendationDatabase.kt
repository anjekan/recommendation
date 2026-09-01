package kr.co.ninetyseconds.recommendation.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [ProjectEntity::class, LocationEntity::class, RecommendationItemEntity::class, ItemEmotionEntity::class, RecommendationEventEntity::class],
    version = 2,
    exportSchema = true,
)
internal abstract class RecommendationDatabase : RoomDatabase() {
    abstract fun catalogDao(): CatalogDao
    abstract fun recommendationEventDao(): RecommendationEventDao

    companion object {
        fun create(context: Context, databaseName: String = "recommendation.db"): RecommendationDatabase =
            Room.databaseBuilder(context, RecommendationDatabase::class.java, databaseName)
                .addMigrations(MIGRATION_1_2)
                .build()

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE recommendation_events ADD COLUMN eventId TEXT NOT NULL DEFAULT ''")
                database.execSQL("ALTER TABLE recommendation_events ADD COLUMN projectCode TEXT NOT NULL DEFAULT ''")
                database.execSQL("ALTER TABLE recommendation_events ADD COLUMN kioskId TEXT NOT NULL DEFAULT ''")
                database.execSQL("ALTER TABLE recommendation_events ADD COLUMN sessionId TEXT NOT NULL DEFAULT ''")
                database.execSQL("ALTER TABLE recommendation_events ADD COLUMN emotionCode TEXT NOT NULL DEFAULT ''")
                database.execSQL("ALTER TABLE recommendation_events ADD COLUMN consentStatus TEXT NOT NULL DEFAULT 'NOT_ASKED'")
                database.execSQL("ALTER TABLE recommendation_events ADD COLUMN stressScore INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE recommendation_events ADD COLUMN policyVersion TEXT NOT NULL DEFAULT 'local-v1'")
                database.execSQL("ALTER TABLE recommendation_events ADD COLUMN synced INTEGER NOT NULL DEFAULT 1")
                database.execSQL("UPDATE recommendation_events SET eventId = requestId")
            }
        }
    }
}
