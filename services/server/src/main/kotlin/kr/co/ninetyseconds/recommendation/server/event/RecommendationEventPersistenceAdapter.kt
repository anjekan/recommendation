package kr.co.ninetyseconds.recommendation.server.event

import java.time.Clock
import java.time.OffsetDateTime
import java.time.ZoneOffset
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository

@Repository
class RecommendationEventPersistenceAdapter(private val jdbc: JdbcClient, private val clock: Clock) : RecommendationEventStore {
    override fun appendIfAbsent(event: RecommendationEvent): Boolean = jdbc.sql(
        """
        insert into recommendation_events (
            event_id, project_code, kiosk_id, session_id, emotion_code, item_id, location_id,
            source, policy_version, occurred_at, received_at
        ) values (
            :eventId, :projectCode, :kioskId, :sessionId, :emotionCode, :itemId, :locationId,
            :source, :policyVersion, :occurredAt, :receivedAt
        ) on conflict (event_id) do nothing
        """.trimIndent(),
    ).param("eventId", event.eventId)
        .param("projectCode", event.projectCode)
        .param("kioskId", event.kioskId)
        .param("sessionId", event.sessionId)
        .param("emotionCode", event.emotionCode)
        .param("itemId", event.itemId)
        .param("locationId", event.locationId)
        .param("source", event.source.name)
        .param("policyVersion", event.policyVersion)
        .param("occurredAt", event.occurredAt.atOffset(ZoneOffset.UTC))
        .param("receivedAt", OffsetDateTime.now(clock).withOffsetSameInstant(ZoneOffset.UTC))
        .update() == 1
}
