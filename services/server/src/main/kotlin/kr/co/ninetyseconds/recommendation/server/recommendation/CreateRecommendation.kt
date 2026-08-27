package kr.co.ninetyseconds.recommendation.server.recommendation

import java.time.Clock
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import kr.co.ninetyseconds.recommendation.server.project.ProjectConfigurationStore
import kr.co.ninetyseconds.recommendation.server.project.ProjectNotFoundException
import org.springframework.stereotype.Service
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
    val requestedAt: OffsetDateTime,
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
    val policyVersion: String = "balanced-v1",
    val reasons: List<String>,
    val createdAt: OffsetDateTime,
)

class NoEligibleRecommendationException(val requestId: UUID) :
    RuntimeException("No eligible recommendation candidate")

@Service
class CreateRecommendation(
    private val projects: ProjectConfigurationStore,
    private val objectMapper: ObjectMapper,
    private val clock: Clock,
) {
    operator fun invoke(request: RecommendationRequest): RecommendationResult {
        require(request.schemaVersion == 1) { "Unsupported schema version: ${request.schemaVersion}" }
        require(request.stressScore in 0..100) { "stress_score must be between 0 and 100" }

        val config = projects.findActiveByCode(request.projectCode)
            ?: throw ProjectNotFoundException(request.projectCode)
        val root = objectMapper.readTree(config.json)
        val emotion = root.path("emotion_profiles").firstOrNull {
            it.path("active").asBoolean(true) && it.path("code").stringValue() == request.emotionCode
        } ?: throw NoEligibleRecommendationException(request.requestId)

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
        val selected = selectWeighted(prioritized, request.requestId)
        val names = objectMapper.convertValue(selected.item.path("name"), Map::class.java)
            .entries.associate { it.key.toString() to it.value.toString() }
        val recommendationText = names.mapValues { (_, name) -> "지금의 당신에게 $name 추천합니다." }

        return RecommendationResult(
            recommendationId = UUID.nameUUIDFromBytes("${request.projectCode}:${request.requestId}".toByteArray()),
            requestId = request.requestId,
            emotionProfile = emotion,
            item = selected.item,
            location = selected.location,
            display = RecommendationDisplay(recommendationText),
            reasons = buildList {
                if (previous != null) add("PREVIOUS_EXCLUDED")
                add("HIGHEST_PRIORITY")
                add("WEIGHTED_DETERMINISTIC")
            },
            createdAt = OffsetDateTime.now(clock).withOffsetSameInstant(ZoneOffset.UTC),
        )
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

    private data class Candidate(val rule: JsonNode, val item: JsonNode, val location: JsonNode)
}
