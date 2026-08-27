package kr.co.ninetyseconds.recommendation.server.project

import java.nio.file.Files
import java.nio.file.Path
import java.time.Clock
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.ObjectMapper

@Component
class ProjectConfigurationSeeder(
    @Value("\${platform.seed.config-path:}") private val configPath: String,
    private val objectMapper: ObjectMapper,
    private val jdbc: JdbcClient,
    private val clock: Clock,
) : ApplicationRunner {
    @Transactional
    override fun run(args: ApplicationArguments) {
        if (configPath.isBlank()) return
        val json = Files.readString(Path.of(configPath))
        val root = objectMapper.readTree(json)
        val projectCode = root.path("project_code").stringValue().orEmpty().also {
            require(it.isNotBlank()) { "Seed project_code is required" }
        }
        val configVersion = root.path("config_version").asInt().also {
            require(it > 0) { "Seed config_version must be positive" }
        }
        val now = OffsetDateTime.now(clock).withOffsetSameInstant(ZoneOffset.UTC)
        jdbc.sql(
            """
            insert into projects (id, project_code, config_version, config_json, active, created_at, updated_at)
            values (:id, :projectCode, :configVersion, cast(:configJson as jsonb), true, :now, :now)
            on conflict (project_code) do update
            set config_version = excluded.config_version,
                config_json = excluded.config_json,
                active = true,
                updated_at = excluded.updated_at
            where excluded.config_version > projects.config_version
            """.trimIndent(),
        ).param("id", UUID.randomUUID())
            .param("projectCode", projectCode)
            .param("configVersion", configVersion)
            .param("configJson", json)
            .param("now", now)
            .update()
    }
}
