package kr.co.ninetyseconds.recommendation.server.event

import java.time.Instant
import java.util.UUID

enum class RecommendationSource { LOCAL, REMOTE, LOCAL_FALLBACK }

data class RecommendationEvent(
    val eventId: UUID,
    val projectCode: String,
    val kioskId: String,
    val sessionId: UUID,
    val emotionCode: String,
    val itemId: UUID,
    val locationId: UUID,
    val source: RecommendationSource,
    val policyVersion: String,
    val occurredAt: Instant,
)

fun interface RecommendationEventStore {
    fun appendIfAbsent(event: RecommendationEvent): Boolean
}
