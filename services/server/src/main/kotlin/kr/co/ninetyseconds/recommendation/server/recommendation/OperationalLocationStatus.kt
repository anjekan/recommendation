package kr.co.ninetyseconds.recommendation.server.recommendation

import java.time.OffsetDateTime
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository

data class OperationalLocationPolicy(
    val enabled: Boolean,
    val priorityShare: Int?,
    val priorityUntil: OffsetDateTime?,
)

fun interface OperationalLocationStatusLoad {
    fun policiesByCode(projectCode: String, locationCodes: Set<String>): Map<String, OperationalLocationPolicy>
}

@Repository
class OperationalLocationStatusPersistenceAdapter(private val jdbc: JdbcClient) : OperationalLocationStatusLoad {
    override fun policiesByCode(projectCode: String, locationCodes: Set<String>): Map<String, OperationalLocationPolicy> {
        if (locationCodes.isEmpty()) return emptyMap()
        return jdbc.sql(
            """
            select location_code, enabled, priority_share, priority_until from location_operational_status
            where project_code = :projectCode and location_code in (:locationCodes)
            """.trimIndent(),
        ).param("projectCode", projectCode).param("locationCodes", locationCodes)
            .query { result, _ ->
                result.getString("location_code") to OperationalLocationPolicy(
                    enabled = result.getBoolean("enabled"),
                    priorityShare = result.getInt("priority_share").let { if (result.wasNull()) null else it },
                    priorityUntil = result.getObject("priority_until", OffsetDateTime::class.java),
                )
            }
            .list().toMap()
    }
}
