package kr.co.ninetyseconds.recommendation.server.admin

import java.time.OffsetDateTime
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

data class DashboardSummary(
    val total: Long,
    val consented: Long,
    val declined: Long,
    val notAsked: Long,
    val averageStress: Double,
)

data class NamedCount(val name: String, val count: Long)
data class RecentRecommendation(
    val occurredAt: OffsetDateTime,
    val kioskId: String,
    val emotionCode: String,
    val consentStatus: String,
    val stressScore: Int,
    val source: String,
)

data class AdminDashboard(
    val projectCode: String,
    val summary: DashboardSummary,
    val emotions: List<NamedCount>,
    val recent: List<RecentRecommendation>,
)

@Repository
class AdminDashboardQuery(private val jdbc: JdbcClient) {
    fun load(projectCode: String): AdminDashboard {
        val summary = jdbc.sql(
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
        val emotions = jdbc.sql(
            """select emotion_code, count(*) as count from recommendation_events
               where project_code = :projectCode group by emotion_code order by count desc, emotion_code""",
        ).param("projectCode", projectCode).query { rs, _ -> NamedCount(rs.getString(1), rs.getLong(2)) }.list()
        val recent = jdbc.sql(
            """select occurred_at, kiosk_id, emotion_code, consent_status, stress_score, source
               from recommendation_events where project_code = :projectCode
               order by occurred_at desc limit 20""",
        ).param("projectCode", projectCode).query { rs, _ ->
            RecentRecommendation(
                rs.getObject("occurred_at", OffsetDateTime::class.java), rs.getString("kiosk_id"),
                rs.getString("emotion_code"), rs.getString("consent_status"),
                rs.getInt("stress_score"), rs.getString("source"),
            )
        }.list()
        return AdminDashboard(projectCode, summary, emotions, recent)
    }
}

@RestController
@RequestMapping("/api/v1/admin")
class AdminDashboardController(private val dashboard: AdminDashboardQuery) {
    @GetMapping("/dashboard")
    fun dashboard(@RequestParam projectCode: String): AdminDashboard = dashboard.load(projectCode)
}
