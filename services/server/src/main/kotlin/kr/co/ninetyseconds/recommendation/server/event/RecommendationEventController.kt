package kr.co.ninetyseconds.recommendation.server.event

import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.time.Instant
import java.util.UUID
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

data class SyncEventsRequest(
    @field:Size(max = 500)
    @field:Valid
    val events: List<RecommendationEventRequest>,
)

data class RecommendationEventRequest(
    val eventId: UUID,
    @field:NotBlank val projectCode: String,
    @field:NotBlank val kioskId: String,
    val sessionId: UUID,
    @field:NotBlank val emotionCode: String,
    val itemId: UUID,
    val locationId: UUID,
    val source: RecommendationSource,
    @field:NotBlank val policyVersion: String,
    val occurredAt: Instant,
) {
    fun toDomain() = RecommendationEvent(
        eventId = eventId,
        projectCode = projectCode,
        kioskId = kioskId,
        sessionId = sessionId,
        emotionCode = emotionCode,
        itemId = itemId,
        locationId = locationId,
        source = source,
        policyVersion = policyVersion,
        occurredAt = occurredAt,
    )
}

data class SyncEventsResponse(val acceptedEventIds: List<String>)

@Validated
@RestController
@RequestMapping("/api/v1/events")
class RecommendationEventController(private val syncRecommendationEvents: SyncRecommendationEvents) {
    @PostMapping("/sync")
    fun sync(@Valid @RequestBody request: SyncEventsRequest): SyncEventsResponse =
        SyncEventsResponse(syncRecommendationEvents(request.events.map(RecommendationEventRequest::toDomain)))
}
