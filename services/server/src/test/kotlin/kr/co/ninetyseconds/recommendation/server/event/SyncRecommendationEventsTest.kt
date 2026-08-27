package kr.co.ninetyseconds.recommendation.server.event

import java.time.Instant
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals

class SyncRecommendationEventsTest {
    @Test
    fun `duplicate events are still acknowledged for offline synchronization`() {
        val stored = mutableSetOf<UUID>()
        val store = RecommendationEventStore { event -> stored.add(event.eventId) }
        val sync = SyncRecommendationEvents(store)
        val event = event()

        val first = sync(listOf(event))
        val duplicate = sync(listOf(event))

        assertEquals(listOf(event.eventId.toString()), first)
        assertEquals(first, duplicate)
        assertEquals(1, stored.size)
    }

    private fun event() = RecommendationEvent(
        eventId = UUID.randomUUID(),
        projectCode = "EXPO",
        kioskId = "KIOSK-01",
        sessionId = UUID.randomUUID(),
        emotionCode = "VITALITY",
        itemId = UUID.randomUUID(),
        locationId = UUID.randomUUID(),
        source = RecommendationSource.LOCAL,
        policyVersion = "local-v1",
        occurredAt = Instant.parse("2026-08-27T00:00:00Z"),
    )
}
