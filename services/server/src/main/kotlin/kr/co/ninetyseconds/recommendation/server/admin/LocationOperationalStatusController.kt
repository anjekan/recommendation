package kr.co.ninetyseconds.recommendation.server.admin

import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import java.security.Principal
import java.time.Clock
import java.time.OffsetDateTime
import java.time.ZoneOffset
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

data class LocationOperationalStatus(
    val locationCode: String,
    val enabled: Boolean,
    val priorityShare: Int?,
    val priorityUntil: OffsetDateTime?,
    val updatedAt: OffsetDateTime,
    val updatedBy: String,
)

data class UpdateLocationOperationalStatusRequest(
    val enabled: Boolean,
    val priorityShare: Int? = null,
    val priorityUntil: OffsetDateTime? = null,
)

@Repository
class LocationOperationalStatusRepository(private val jdbc: JdbcClient, private val clock: Clock) {
    fun list(projectCode: String): List<LocationOperationalStatus> = jdbc.sql(
        """
        select location_code, enabled, priority_share, priority_until, updated_at, updated_by
        from location_operational_status where project_code = :projectCode
        order by location_code
        """.trimIndent(),
    ).param("projectCode", projectCode).query { result, _ ->
        LocationOperationalStatus(
            result.getString("location_code"), result.getBoolean("enabled"),
            result.getInt("priority_share").let { if (result.wasNull()) null else it },
            result.getObject("priority_until", OffsetDateTime::class.java),
            result.getObject("updated_at", OffsetDateTime::class.java), result.getString("updated_by"),
        )
    }.list()

    @Transactional
    fun save(
        projectCode: String,
        locationCode: String,
        enabled: Boolean,
        priorityShare: Int?,
        priorityUntil: OffsetDateTime?,
        updatedBy: String,
    ): LocationOperationalStatus {
        val now = OffsetDateTime.now(clock).withOffsetSameInstant(ZoneOffset.UTC)
        require(priorityShare == null || priorityShare in 1..100) { "priority_share must be between 1 and 100" }
        require((priorityShare == null) == (priorityUntil == null)) {
            "priority_share and priority_until must both be set or both be null"
        }
        require(priorityUntil == null || priorityUntil.isAfter(now)) { "priority_until must be in the future" }
        require(enabled || priorityShare == null) { "a stopped location cannot be prioritized" }
        val updated = jdbc.sql(
            """
            update location_operational_status
            set enabled = :enabled, priority_share = :priorityShare, priority_until = :priorityUntil,
                updated_at = :updatedAt, updated_by = :updatedBy
            where project_code = :projectCode and location_code = :locationCode
            """.trimIndent(),
        ).param("enabled", enabled).param("priorityShare", priorityShare).param("priorityUntil", priorityUntil)
            .param("updatedAt", now).param("updatedBy", updatedBy)
            .param("projectCode", projectCode).param("locationCode", locationCode).update()
        if (updated == 0) {
            jdbc.sql(
                """
                insert into location_operational_status
                    (project_code, location_code, enabled, priority_share, priority_until, updated_at, updated_by)
                values (:projectCode, :locationCode, :enabled, :priorityShare, :priorityUntil, :updatedAt, :updatedBy)
                """.trimIndent(),
            ).param("projectCode", projectCode).param("locationCode", locationCode)
                .param("enabled", enabled).param("priorityShare", priorityShare).param("priorityUntil", priorityUntil)
                .param("updatedAt", now).param("updatedBy", updatedBy).update()
        }
        return LocationOperationalStatus(locationCode, enabled, priorityShare, priorityUntil, now, updatedBy)
    }
}

@RestController
@RequestMapping("/api/v1/admin/location-statuses")
class LocationOperationalStatusController(private val statuses: LocationOperationalStatusRepository) {
    @GetMapping
    fun list(@RequestParam @NotBlank projectCode: String): List<LocationOperationalStatus> = statuses.list(projectCode)

    @PutMapping("/{locationCode}")
    fun update(
        @RequestParam @NotBlank projectCode: String,
        @PathVariable @NotBlank locationCode: String,
        @Valid @RequestBody request: UpdateLocationOperationalStatusRequest,
        principal: Principal,
    ): LocationOperationalStatus = statuses.save(
        projectCode, locationCode, request.enabled, request.priorityShare, request.priorityUntil, principal.name,
    )
}
