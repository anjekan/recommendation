package kr.co.ninetyseconds.recommendation.server.event

import java.util.UUID
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import javax.sql.DataSource

data class RecommendationJourneyStopEvent(
    val order: Int,
    val senseCode: String,
    val itemId: UUID,
    val locationId: UUID,
)

fun interface RecommendationJourneyStore {
    fun appendIfAbsent(recommendationId: UUID, stops: List<RecommendationJourneyStopEvent>)
}

@Repository
class RecommendationJourneyPersistenceAdapter(
    private val jdbc: JdbcClient,
    private val dataSource: DataSource,
) : RecommendationJourneyStore {
    override fun appendIfAbsent(recommendationId: UUID, stops: List<RecommendationJourneyStopEvent>) {
        if (stops.isEmpty()) return
        val isH2 = dataSource.connection.use { it.metaData.databaseProductName == "H2" }
        val sql = if (isH2) {
            """
            merge into recommendation_journey_stops
                (recommendation_id, stop_order, sense_code, item_id, location_id)
            key (recommendation_id, stop_order)
            values (:recommendationId, :stopOrder, :senseCode, :itemId, :locationId)
            """.trimIndent()
        } else {
            """
            insert into recommendation_journey_stops
                (recommendation_id, stop_order, sense_code, item_id, location_id)
            values (:recommendationId, :stopOrder, :senseCode, :itemId, :locationId)
            on conflict (recommendation_id, stop_order) do nothing
            """.trimIndent()
        }
        stops.forEach { stop ->
            jdbc.sql(sql)
                .param("recommendationId", recommendationId)
                .param("stopOrder", stop.order)
                .param("senseCode", stop.senseCode)
                .param("itemId", stop.itemId)
                .param("locationId", stop.locationId)
                .update()
        }
    }
}
