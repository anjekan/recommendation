package kr.co.ninetyseconds.recommendation.server.admin

import java.nio.charset.StandardCharsets
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.util.UUID
import kr.co.ninetyseconds.recommendation.server.project.ProjectConfigurationStore
import org.springframework.beans.factory.annotation.Value
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import tools.jackson.databind.ObjectMapper

data class DashboardSummary(
    val total: Long,
    val consented: Long,
    val declined: Long,
    val notAsked: Long,
    val averageStress: Double,
)

data class NamedCount(val name: String, val count: Long)
data class LocationCount(val locationId: String, val locationCode: String?, val count: Long)
data class HourCount(val hour: Int, val count: Long)
data class KioskStatus(val kioskId: String, val count: Long, val lastActivityAt: OffsetDateTime)
data class RecentRecommendation(
    val occurredAt: OffsetDateTime,
    val kioskId: String,
    val emotionCode: String,
    val locationCode: String?,
    val consentStatus: String,
    val stressScore: Int,
    val source: String,
    val participantName: String?,
    val participantPhone: String?,
    val participantBirthDate: String?,
    val participantGender: String?,
)

data class AdminDashboard(
    val projectCode: String,
    val date: LocalDate,
    val overallSummary: DashboardSummary,
    val summary: DashboardSummary,
    val previousSummary: DashboardSummary,
    val emotions: List<NamedCount>,
    val locations: List<LocationCount>,
    val hourly: List<HourCount>,
    val kiosks: List<KioskStatus>,
    val recent: List<RecentRecommendation>,
)

@Repository
class AdminDashboardQuery(
    private val jdbc: JdbcClient,
    private val projects: ProjectConfigurationStore,
    private val objectMapper: ObjectMapper,
) {
    fun load(projectCode: String, date: LocalDate): AdminDashboard {
        val zone = ZoneId.of("Asia/Seoul")
        val start = date.atStartOfDay(zone).toOffsetDateTime()
        val end = date.plusDays(1).atStartOfDay(zone).toOffsetDateTime()
        val previousStart = date.minusDays(1).atStartOfDay(zone).toOffsetDateTime()
        val overallSummary = loadOverallSummary(projectCode)
        val summary = loadSummary(projectCode, start, end)
        val previousSummary = loadSummary(projectCode, previousStart, start)
        val emotions = jdbc.sql(
            """select emotion_code, count(*) as count from recommendation_events
               where project_code = :projectCode and occurred_at >= :start and occurred_at < :end
               group by emotion_code order by count desc, emotion_code""",
        ).param("projectCode", projectCode).param("start", start).param("end", end)
            .query { rs, _ -> NamedCount(rs.getString(1), rs.getLong(2)) }.list()
        val locationCodesById = projectLocationCodes(projectCode)
        val locations = jdbc.sql(
            """
            select location_id, count(*) as count
            from (
                select journey.location_id
                from recommendation_events event
                join recommendation_journey_stops journey on journey.recommendation_id = event.event_id
                where event.project_code = :projectCode and event.occurred_at >= :start and event.occurred_at < :end
                union all
                select event.location_id
                from recommendation_events event
                where event.project_code = :projectCode and event.occurred_at >= :start and event.occurred_at < :end
                  and not exists (
                      select 1 from recommendation_journey_stops journey where journey.recommendation_id = event.event_id
                  )
            ) selected_locations
            group by location_id order by count desc, location_id
            """.trimIndent(),
        ).param("projectCode", projectCode).param("start", start).param("end", end)
            .query { rs, _ ->
                val locationId = rs.getString(1)
                LocationCount(locationId, locationCodesById[locationId], rs.getLong(2))
            }.list()
        val countedHours = jdbc.sql(
            """select extract(hour from occurred_at at time zone 'Asia/Seoul') as hour_value, count(*) as count
               from recommendation_events where project_code = :projectCode and occurred_at >= :start and occurred_at < :end
               group by hour_value order by hour_value""",
        ).param("projectCode", projectCode).param("start", start).param("end", end)
            .query { rs, _ -> rs.getInt("hour_value") to rs.getLong("count") }.list().toMap()
        val hourly = (0..23).map { HourCount(it, countedHours[it] ?: 0L) }
        val kiosks = jdbc.sql(
            """select kiosk_id, count(*) as count, max(occurred_at) as last_activity_at
               from recommendation_events where project_code = :projectCode and occurred_at >= :start and occurred_at < :end
               group by kiosk_id order by last_activity_at desc""",
        ).param("projectCode", projectCode).param("start", start).param("end", end)
            .query { rs, _ -> KioskStatus(rs.getString("kiosk_id"), rs.getLong("count"), rs.getObject("last_activity_at", OffsetDateTime::class.java)) }.list()
        val recent = jdbc.sql(
            """select occurred_at, kiosk_id, emotion_code, location_id, consent_status, stress_score, source,
                      participant_name, participant_phone, participant_birth_date, participant_gender
               from recommendation_events
               where project_code = :projectCode and occurred_at >= :start and occurred_at < :end
               order by occurred_at desc limit 20""",
        ).param("projectCode", projectCode).param("start", start).param("end", end).query { rs, _ ->
            RecentRecommendation(
                rs.getObject("occurred_at", OffsetDateTime::class.java), rs.getString("kiosk_id"),
                rs.getString("emotion_code"), locationCodesById[rs.getString("location_id")], rs.getString("consent_status"),
                rs.getInt("stress_score"), rs.getString("source"),
                rs.getString("participant_name"), maskPhone(rs.getString("participant_phone")),
                rs.getString("participant_birth_date"), rs.getString("participant_gender"),
            )
        }.list()
        return AdminDashboard(projectCode, date, overallSummary, summary, previousSummary, emotions, locations, hourly, kiosks, recent)
    }

    private fun loadOverallSummary(projectCode: String): DashboardSummary = jdbc.sql(
        """
        select count(*) as total,
               coalesce(sum(case when consent_status = 'CONSENTED' then 1 else 0 end), 0) as consented,
               coalesce(sum(case when consent_status = 'DECLINED' then 1 else 0 end), 0) as declined,
               coalesce(sum(case when consent_status = 'NOT_ASKED' then 1 else 0 end), 0) as not_asked,
               coalesce(avg(stress_score), 0) as average_stress
        from recommendation_events where project_code = :projectCode
        """.trimIndent(),
    ).param("projectCode", projectCode).query { rs, _ ->
        DashboardSummary(
            rs.getLong("total"), rs.getLong("consented"), rs.getLong("declined"),
            rs.getLong("not_asked"), rs.getDouble("average_stress"),
        )
    }.single()

    private fun loadSummary(projectCode: String, start: OffsetDateTime, end: OffsetDateTime): DashboardSummary {
        val summary = jdbc.sql(
            """
            select count(*) as total,
                   coalesce(sum(case when consent_status = 'CONSENTED' then 1 else 0 end), 0) as consented,
                   coalesce(sum(case when consent_status = 'DECLINED' then 1 else 0 end), 0) as declined,
                   coalesce(sum(case when consent_status = 'NOT_ASKED' then 1 else 0 end), 0) as not_asked,
                   coalesce(avg(stress_score), 0) as average_stress
            from recommendation_events
            where project_code = :projectCode and occurred_at >= :start and occurred_at < :end
            """.trimIndent(),
        ).param("projectCode", projectCode).param("start", start).param("end", end).query { rs, _ ->
            DashboardSummary(
                rs.getLong("total"), rs.getLong("consented"), rs.getLong("declined"),
                rs.getLong("not_asked"), rs.getDouble("average_stress"),
            )
        }.single()
        return summary
    }

    private fun maskPhone(phone: String?): String? = phone?.let {
        if (it.length < 7) "***" else "${it.take(3)}-****-${it.takeLast(4)}"
    }

    private fun projectLocationCodes(projectCode: String): Map<String, String> {
        val configuration = projects.findActiveByCode(projectCode) ?: return emptyMap()
        val root = objectMapper.readTree(configuration.json)
        val legacy = root.path("locations").associate { location ->
            location.path("id").stringValue() to location.path("code").stringValue()
        }
        val rich = root.path("rich_flow").path("venue_operations").associate { venue ->
            val code = venue.path("code").stringValue()
            stableLocationId(projectCode, code) to code
        }
        return legacy + rich
    }

    private fun stableLocationId(projectCode: String, code: String): String = UUID.nameUUIDFromBytes(
        "$projectCode:location:$code".toByteArray(StandardCharsets.UTF_8),
    ).toString()
}

data class AdminProjectContext(val defaultProjectCode: String, val projectCodes: List<String>)

@Repository
class AdminProjectContextQuery(
    private val jdbc: JdbcClient,
    @Value("\${platform.admin.default-project-code:}") private val configuredDefault: String,
) {
    fun load(): AdminProjectContext {
        val codes = jdbc.sql(
            "select project_code from projects where active = true order by updated_at desc, project_code",
        ).query(String::class.java).list().filterNotNull()
        val defaultCode = configuredDefault.takeIf { it in codes } ?: codes.firstOrNull().orEmpty()
        return AdminProjectContext(defaultCode, codes)
    }
}

@RestController
@RequestMapping("/api/v1/admin")
class AdminDashboardController(
    private val dashboard: AdminDashboardQuery,
    private val context: AdminProjectContextQuery,
) {
    @GetMapping("/context")
    fun context(): AdminProjectContext = context.load()

    @GetMapping("/dashboard")
    fun dashboard(
        @RequestParam projectCode: String,
        @RequestParam(required = false) date: LocalDate?,
    ): AdminDashboard = dashboard.load(projectCode, date ?: LocalDate.now(ZoneId.of("Asia/Seoul")))
}
