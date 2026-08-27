package kr.co.ninetyseconds.recommendation.server.project

import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository

@Repository
class ProjectConfigurationPersistenceAdapter(private val jdbc: JdbcClient) : ProjectConfigurationStore {
    override fun findActiveByCode(projectCode: String): ProjectConfiguration? = jdbc.sql(
        """
        select project_code, config_version, config_json::text
        from projects
        where project_code = :projectCode and active = true
        """.trimIndent(),
    ).param("projectCode", projectCode).query { result, _ ->
        ProjectConfiguration(
            projectCode = result.getString("project_code"),
            configVersion = result.getInt("config_version"),
            json = result.getString("config_json"),
        )
    }.optional().orElse(null)
}
