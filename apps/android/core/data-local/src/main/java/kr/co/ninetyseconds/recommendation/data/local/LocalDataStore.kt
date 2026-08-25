package kr.co.ninetyseconds.recommendation.data.local

import android.content.Context

class LocalDataStore private constructor(
    val projectCatalog: RoomProjectCatalog,
    val recommendationEvents: RoomRecommendationEventStore,
) {
    companion object {
        fun create(context: Context, databaseName: String = "recommendation.db"): LocalDataStore {
            val database = RecommendationDatabase.create(context.applicationContext, databaseName)
            return LocalDataStore(RoomProjectCatalog(database.catalogDao()), RoomRecommendationEventStore(database.recommendationEventDao()))
        }
    }
}
