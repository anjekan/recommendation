package kr.co.ninetyseconds.recommendation.server.recommendation

import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository

fun interface OperationalLocationStatusLoad {
    fun enabledByCode(projectCode: String, locationCodes: Set<String>): Map<String, Boolean>
}

@Repository
class OperationalLocationStatusPersistenceAdapter(private val jdbc: JdbcClient) : OperationalLocationStatusLoad {
    override fun enabledByCode(projectCode: String, locationCodes: Set<String>): Map<String, Boolean> {
        if (locationCodes.isEmpty()) return emptyMap()
        return jdbc.sql(
            """
            select location_code, enabled from location_operational_status
            where project_code = :projectCode and location_code in (:locationCodes)
            """.trimIndent(),
        ).param("projectCode", projectCode).param("locationCodes", locationCodes)
            .query { result, _ -> result.getString("location_code") to result.getBoolean("enabled") }
            .list().toMap()
    }
}
