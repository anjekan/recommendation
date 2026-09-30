package kr.co.ninetyseconds.recommendation.server.recommendation

import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository

@Repository
class RecentRecommendationLoadPersistenceAdapter(
    private val jdbc: JdbcClient,
) : RecentRecommendationLoad, RecentSenseRecommendationLoad {
    override fun countByLocation(
        projectCode: String,
        locationIds: Set<UUID>,
        since: Instant,
    ): Map<UUID, Long> {
        if (locationIds.isEmpty()) return emptyMap()
        return jdbc.sql(
            """
            select location_id, count(*) as recommendation_count
            from recommendation_events
            where project_code = :projectCode
              and occurred_at >= :since
              and location_id in (:locationIds)
            group by location_id
            """.trimIndent(),
        )
            .param("projectCode", projectCode)
            .param("since", since.atOffset(ZoneOffset.UTC))
            .param("locationIds", locationIds)
            .query { resultSet, _ ->
                UUID.fromString(resultSet.getString("location_id")) to resultSet.getLong("recommendation_count")
            }
            .list()
            .toMap()
    }

    override fun countBySense(
        projectCode: String,
        senseCodes: Set<String>,
        since: Instant,
    ): Map<String, Long> {
        if (senseCodes.isEmpty()) return emptyMap()
        return jdbc.sql(
            """
            select journey.sense_code, count(*) as recommendation_count
            from recommendation_journey_stops journey
            join recommendation_events event on event.event_id = journey.recommendation_id
            where event.project_code = :projectCode
              and event.occurred_at >= :since
              and journey.sense_code in (:senseCodes)
            group by journey.sense_code
            """.trimIndent(),
        )
            .param("projectCode", projectCode)
            .param("since", since.atOffset(ZoneOffset.UTC))
            .param("senseCodes", senseCodes)
            .query { resultSet, _ ->
                resultSet.getString("sense_code") to resultSet.getLong("recommendation_count")
            }
            .list()
            .toMap()
    }
}
