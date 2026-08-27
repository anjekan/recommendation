package kr.co.ninetyseconds.recommendation.server.event

import java.time.Clock
import java.time.OffsetDateTime
import java.time.ZoneOffset
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import javax.sql.DataSource

@Repository
class RecommendationEventPersistenceAdapter(
    private val jdbc: JdbcClient,
    private val dataSource: DataSource,
    private val clock: Clock,
) : RecommendationEventStore {
    override fun appendIfAbsent(event: RecommendationEvent): Boolean {
        val isH2 = dataSource.connection.use { it.metaData.databaseProductName == "H2" }
        val sql = if (isH2) """
        merge into recommendation_events (
            event_id, project_code, kiosk_id, session_id, emotion_code, item_id, location_id,
            source, consent_status, stress_score, participant_name, participant_phone,
            participant_birth_date, participant_gender, policy_version, occurred_at, received_at
        ) key (event_id) values (
            :eventId, :projectCode, :kioskId, :sessionId, :emotionCode, :itemId, :locationId,
            :source, :consentStatus, :stressScore, :participantName, :participantPhone,
            :participantBirthDate, :participantGender, :policyVersion, :occurredAt, :receivedAt
        )
        """.trimIndent() else """
        insert into recommendation_events (
            event_id, project_code, kiosk_id, session_id, emotion_code, item_id, location_id,
            source, consent_status, stress_score, participant_name, participant_phone,
            participant_birth_date, participant_gender, policy_version, occurred_at, received_at
        ) values (
            :eventId, :projectCode, :kioskId, :sessionId, :emotionCode, :itemId, :locationId,
            :source, :consentStatus, :stressScore, :participantName, :participantPhone,
            :participantBirthDate, :participantGender, :policyVersion, :occurredAt, :receivedAt
        ) on conflict (event_id) do nothing
        """.trimIndent()
        return jdbc.sql(sql).param("eventId", event.eventId)
        .param("projectCode", event.projectCode)
        .param("kioskId", event.kioskId)
        .param("sessionId", event.sessionId)
        .param("emotionCode", event.emotionCode)
        .param("itemId", event.itemId)
        .param("locationId", event.locationId)
        .param("source", event.source.name)
        .param("consentStatus", event.consentStatus.name)
        .param("stressScore", event.stressScore)
        .param("participantName", event.participantName)
        .param("participantPhone", event.participantPhone)
        .param("participantBirthDate", event.participantBirthDate)
        .param("participantGender", event.participantGender)
        .param("policyVersion", event.policyVersion)
        .param("occurredAt", event.occurredAt.atOffset(ZoneOffset.UTC))
        .param("receivedAt", OffsetDateTime.now(clock).withOffsetSameInstant(ZoneOffset.UTC))
        .update() == 1
    }
}
