package kr.co.ninetyseconds.recommendation.server.recommendation

import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import kr.co.ninetyseconds.recommendation.server.project.ProjectConfigurationStore
import kr.co.ninetyseconds.recommendation.server.project.ProjectNotFoundException
import kr.co.ninetyseconds.recommendation.server.event.ConsentStatus
import kr.co.ninetyseconds.recommendation.server.event.RecommendationEvent
import kr.co.ninetyseconds.recommendation.server.event.RecommendationEventStore
import kr.co.ninetyseconds.recommendation.server.event.RecommendationSource
import org.springframework.stereotype.Service
import org.springframework.beans.factory.annotation.Value
import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper

data class RecommendationRequest(
    val schemaVersion: Int,
    val projectCode: String,
    val kioskId: String,
    val sessionId: UUID,
    val requestId: UUID,
    val emotionCode: String,
    val stressScore: Int,
    val language: String,
    val previousLocationId: UUID?,
    val consentStatus: ConsentStatus,
    val participant: ParticipantRequestBody? = null,
    val conditionCode: String? = null,
    val journeySenseCodes: List<String> = emptyList(),
    val operationContext: OperationContextRequest? = null,
    val requestedAt: OffsetDateTime,
)

data class OperationContextRequest(
    val raining: Boolean = false,
    val companionType: String? = null,
    val performanceWindowOpen: Boolean = false,
)

data class RecommendationDisplay(val recommendationText: Map<String, String>, val displaySeconds: Int = 10)

data class RecommendationResult(
    val schemaVersion: Int = 1,
    val recommendationId: UUID,
    val requestId: UUID,
    val emotionProfile: JsonNode,
    val item: JsonNode,
    val location: JsonNode,
    val display: RecommendationDisplay,
    val source: String = "REMOTE",
    val policyVersion: String = "balanced-v2",
    val reasons: List<String>,
    val journey: List<JourneyStopResult> = emptyList(),
    val createdAt: OffsetDateTime,
)

data class JourneyStopResult(
    val order: Int,
    val senseCode: String,
    val item: JsonNode,
    val location: JsonNode,
)

class NoEligibleRecommendationException(val requestId: UUID) :
    RuntimeException("No eligible recommendation candidate")

fun interface RecentRecommendationLoad {
    fun countByLocation(projectCode: String, locationIds: Set<UUID>, since: Instant): Map<UUID, Long>
}

@Service
class CreateRecommendation(
    private val projects: ProjectConfigurationStore,
    private val objectMapper: ObjectMapper,
    private val events: RecommendationEventStore,
    private val clock: Clock,
    private val recentLoad: RecentRecommendationLoad,
    @Value("\${recommendation.policy.recent-window:PT15M}")
    private val recentWindow: Duration,
) {
    operator fun invoke(request: RecommendationRequest): RecommendationResult {
        require(request.schemaVersion in 1..2) { "Unsupported schema version: ${request.schemaVersion}" }
        require(request.stressScore in 0..100) { "stress_score must be between 0 and 100" }
        require(request.journeySenseCodes.size <= 3) { "journey_sense_codes can contain at most three values" }
        require(request.participant == null || request.consentStatus == ConsentStatus.CONSENTED) {
            "participant requires CONSENTED status"
        }

        val config = projects.findActiveByCode(request.projectCode)
            ?: throw ProjectNotFoundException(request.projectCode)
        val root = objectMapper.readTree(config.json)
        val emotion = root.path("emotion_profiles").firstOrNull {
            it.path("active").asBoolean(true) && it.path("code").stringValue() == request.emotionCode
        } ?: throw NoEligibleRecommendationException(request.requestId)

        val richJourney = selectRichJourney(root, request)
        if (richJourney.isNotEmpty()) return createRichJourneyResult(request, emotion, richJourney)

        val locations = root.path("locations").associateBy { it.path("id").stringValue() }
        val items = root.path("items").associateBy { it.path("id").stringValue() }
        val previous = request.previousLocationId?.toString()
        val candidates = root.path("rules").mapNotNull { rule ->
            if (!rule.path("active").asBoolean(true) || rule.path("emotion_code").stringValue() != request.emotionCode) {
                return@mapNotNull null
            }
            val item = items[rule.path("item_id").stringValue()] ?: return@mapNotNull null
            val location = locations[item.path("location_id").stringValue()] ?: return@mapNotNull null
            if (!item.path("active").asBoolean(true) ||
                !location.path("active").asBoolean(true) ||
                location.path("status").stringValue() == "PAUSED" ||
                location.path("id").stringValue() == previous
            ) return@mapNotNull null
            Candidate(rule, item, location)
        }

        if (candidates.isEmpty()) throw NoEligibleRecommendationException(request.requestId)
        val highestPriority = candidates.maxOf { it.rule.path("priority").asInt() }
        val prioritized = candidates.filter { it.rule.path("priority").asInt() == highestPriority }
            .sortedBy { it.item.path("id").stringValue() }
        val locationIds = prioritized.map { UUID.fromString(it.location.path("id").stringValue()) }.toSet()
        val recentCounts = recentLoad.countByLocation(
            request.projectCode,
            locationIds,
            Instant.now(clock).minus(recentWindow),
        )
        val minimumCount = locationIds.minOf { recentCounts[it] ?: 0L }
        val leastLoaded = prioritized.filter {
            (recentCounts[UUID.fromString(it.location.path("id").stringValue())] ?: 0L) == minimumCount
        }
        val selected = selectWeighted(leastLoaded, request.requestId)
        val names = objectMapper.convertValue(selected.item.path("name"), Map::class.java)
            .entries.associate { it.key.toString() to it.value.toString() }
        val recommendationText = names.mapValues { (_, name) -> "지금의 당신에게 $name 추천합니다." }

        val result = RecommendationResult(
            schemaVersion = request.schemaVersion,
            recommendationId = UUID.nameUUIDFromBytes("${request.projectCode}:${request.requestId}".toByteArray()),
            requestId = request.requestId,
            emotionProfile = emotion,
            item = selected.item,
            location = selected.location,
            display = RecommendationDisplay(recommendationText),
            reasons = buildList {
                if (previous != null) add("PREVIOUS_EXCLUDED")
                add("HIGHEST_PRIORITY")
                add("RECENT_LOAD_BALANCED")
                add("WEIGHTED_DETERMINISTIC")
            },
            journey = request.journeySenseCodes.firstOrNull()?.let { senseCode ->
                listOf(JourneyStopResult(1, senseCode, selected.item, selected.location))
            }.orEmpty(),
            createdAt = OffsetDateTime.now(clock).withOffsetSameInstant(ZoneOffset.UTC),
        )
        events.appendIfAbsent(
            RecommendationEvent(
                eventId = result.recommendationId,
                projectCode = request.projectCode,
                kioskId = request.kioskId,
                sessionId = request.sessionId,
                emotionCode = request.emotionCode,
                itemId = UUID.fromString(selected.item.path("id").stringValue()),
                locationId = UUID.fromString(selected.location.path("id").stringValue()),
                source = RecommendationSource.REMOTE,
                consentStatus = request.consentStatus,
                stressScore = request.stressScore,
                participantName = request.participant?.name,
                participantPhone = request.participant?.phone,
                participantBirthDate = request.participant?.birthDate,
                participantGender = request.participant?.gender,
                policyVersion = result.policyVersion,
                occurredAt = request.requestedAt.toInstant(),
            ),
        )
        return result
    }

    private fun selectWeighted(candidates: List<Candidate>, requestId: UUID): Candidate {
        val total = candidates.sumOf { it.rule.path("weight").asInt(1).coerceAtLeast(1) }
        var position = Math.floorMod(requestId.hashCode(), total)
        for (candidate in candidates) {
            position -= candidate.rule.path("weight").asInt(1).coerceAtLeast(1)
            if (position < 0) return candidate
        }
        return candidates.last()
    }

    private fun selectRichJourney(root: JsonNode, request: RecommendationRequest): List<RichJourneySelection> {
        val richFlow = root.path("rich_flow")
        if (richFlow.isMissingNode || richFlow.isNull) return emptyList()
        val requestedSenses: List<String> = if (request.journeySenseCodes.isNotEmpty()) {
            request.journeySenseCodes.take(3)
        } else {
            val mapping = richFlow.path("journey_mappings").firstOrNull {
                it.path("condition_code").stringValue() == request.conditionCode
            }
            val mappedSenses = mutableListOf<String>()
            mapping?.path("sense_sequence")?.forEach { node ->
                if (mappedSenses.size < 3) mappedSenses += node.stringValue()
            }
            mappedSenses
        }
        if (requestedSenses.isEmpty()) return emptyList()

        val usedCodes = mutableSetOf<String>()
        return requestedSenses.mapIndexedNotNull { index, senseCode ->
            val candidates = richFlow.path("venue_operations").filter { venue ->
                val code = venue.path("code").stringValue()
                venue.path("active").asBoolean(false) &&
                    venue.path("confirmation_status").stringValue() == "CONFIRMED" &&
                    code !in usedCodes &&
                    venue.path("sense_codes").any { it.stringValue() == senseCode } &&
                    (!request.operationContext?.raining.orFalse() || venue.path("indoor").asBoolean(false)) &&
                    (!isFamily(request.operationContext?.companionType) || !venue.path("alcohol").asBoolean(false))
            }.sortedBy { it.path("code").stringValue() }
            if (candidates.isEmpty()) return@mapIndexedNotNull null
            val position = Math.floorMod("${request.requestId}:$senseCode:$index".hashCode(), candidates.size)
            val venue = candidates[position]
            usedCodes += venue.path("code").stringValue()
            RichJourneySelection(index + 1, senseCode, venue)
        }.mapIndexed { index, selection -> selection.copy(order = index + 1) }
    }

    private fun createRichJourneyResult(
        request: RecommendationRequest,
        emotion: JsonNode,
        selections: List<RichJourneySelection>,
    ): RecommendationResult {
        val stops = selections.map { selection ->
            val code = selection.venue.path("code").stringValue()
            val locationId = stableUuid(request.projectCode, "location:$code")
            val itemId = stableUuid(request.projectCode, "item:$code")
            val location = objectMapper.createObjectNode().apply {
                put("id", locationId.toString())
                put("code", code)
                set("name", selection.venue.path("name"))
                put("status", "NORMAL")
                set("marker", selection.venue.path("marker"))
                put("active", true)
            }
            val item = objectMapper.createObjectNode().apply {
                put("id", itemId.toString())
                put("type", "place")
                put("location_id", locationId.toString())
                set("name", selection.venue.path("name"))
                put("active", true)
            }
            JourneyStopResult(selection.order, selection.senseCode, item, location)
        }
        val primary = stops.first()
        val names = objectMapper.convertValue(primary.item.path("name"), Map::class.java)
            .entries.associate { it.key.toString() to it.value.toString() }
        val result = RecommendationResult(
            schemaVersion = request.schemaVersion,
            recommendationId = UUID.nameUUIDFromBytes("${request.projectCode}:${request.requestId}".toByteArray()),
            requestId = request.requestId,
            emotionProfile = emotion,
            item = primary.item,
            location = primary.location,
            display = RecommendationDisplay(names.mapValues { (_, name) -> "지금의 당신에게 $name 추천합니다." }),
            policyVersion = "rich-journey-v1",
            reasons = listOf("SENSE_SEQUENCE_MATCHED", "OPERATION_FILTERED", "DETERMINISTIC"),
            journey = stops,
            createdAt = OffsetDateTime.now(clock).withOffsetSameInstant(ZoneOffset.UTC),
        )
        events.appendIfAbsent(
            RecommendationEvent(
                eventId = result.recommendationId, projectCode = request.projectCode, kioskId = request.kioskId,
                sessionId = request.sessionId, emotionCode = request.emotionCode,
                itemId = UUID.fromString(primary.item.path("id").stringValue()),
                locationId = UUID.fromString(primary.location.path("id").stringValue()),
                source = RecommendationSource.REMOTE, consentStatus = request.consentStatus,
                stressScore = request.stressScore, participantName = request.participant?.name,
                participantPhone = request.participant?.phone, participantBirthDate = request.participant?.birthDate,
                participantGender = request.participant?.gender, policyVersion = result.policyVersion,
                occurredAt = request.requestedAt.toInstant(),
            ),
        )
        return result
    }

    private fun Boolean?.orFalse() = this ?: false
    private fun isFamily(companionType: String?): Boolean =
        companionType.equals("FAMILY", ignoreCase = true) || companionType.equals("CHILD", ignoreCase = true)
    private fun stableUuid(projectCode: String, value: String): UUID =
        UUID.nameUUIDFromBytes("$projectCode:$value".toByteArray())

    private data class Candidate(val rule: JsonNode, val item: JsonNode, val location: JsonNode)
    private data class RichJourneySelection(val order: Int, val senseCode: String, val venue: JsonNode)
}
