package kr.co.ninetyseconds.recommendation.server.event

import java.time.Instant
import java.util.UUID

enum class RecommendationSource { LOCAL, REMOTE, LOCAL_FALLBACK }
enum class ConsentStatus { CONSENTED, DECLINED, NOT_ASKED }

data class RecommendationEvent(
    val eventId: UUID,
    val projectCode: String,
    val kioskId: String,
    val sessionId: UUID,
    val emotionCode: String,
    val itemId: UUID,
    val locationId: UUID,
    val source: RecommendationSource,
    val consentStatus: ConsentStatus = ConsentStatus.NOT_ASKED,
    val stressScore: Int = 0,
    val participantName: String? = null,
    val participantPhone: String? = null,
    val participantBirthDate: String? = null,
    val participantGender: String? = null,
    val policyVersion: String,
    val occurredAt: Instant,
)

fun interface RecommendationEventStore {
    fun appendIfAbsent(event: RecommendationEvent): Boolean
}
