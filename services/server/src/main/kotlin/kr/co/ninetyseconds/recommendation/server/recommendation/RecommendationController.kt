package kr.co.ninetyseconds.recommendation.server.recommendation

import jakarta.validation.Valid
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
import java.time.OffsetDateTime
import java.util.UUID
import kr.co.ninetyseconds.recommendation.server.event.ConsentStatus
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.bind.annotation.RestControllerAdvice

data class ParticipantRequestBody(
    @field:NotBlank @field:Size(max = 50) val name: String,
    @field:Pattern(regexp = "^[0-9]{10,11}$") val phone: String,
    @field:Pattern(regexp = "^[0-9]{8}$") val birthDate: String,
    @field:NotBlank @field:Size(max = 20) val gender: String,
)

data class RecommendationRequestBody(
    val schemaVersion: Int,
    @field:NotBlank val projectCode: String,
    @field:NotBlank val kioskId: String,
    val sessionId: UUID,
    val requestId: UUID,
    @field:NotBlank val emotionCode: String,
    @field:Min(0) @field:Max(100) val stressScore: Int,
    @field:NotBlank val language: String,
    val previousLocationId: UUID?,
    val consentStatus: ConsentStatus = ConsentStatus.NOT_ASKED,
    @field:Valid val participant: ParticipantRequestBody? = null,
    val requestedAt: OffsetDateTime,
) {
    fun toCommand() = RecommendationRequest(
        schemaVersion, projectCode, kioskId, sessionId, requestId, emotionCode,
        stressScore, language, previousLocationId, consentStatus, participant, requestedAt,
    )
}

@RestController
@RequestMapping("/api/v1/recommendations")
class RecommendationController(private val createRecommendation: CreateRecommendation) {
    @PostMapping
    fun create(
        @RequestHeader("X-Kiosk-Key") kioskKey: String,
        @Valid @RequestBody body: RecommendationRequestBody,
    ): RecommendationResult {
        require(kioskKey.isNotBlank()) { "X-Kiosk-Key must not be blank" }
        return createRecommendation(body.toCommand())
    }
}

data class RecommendationApiError(val code: String, val message: String, val requestId: String)

@RestControllerAdvice
class RecommendationErrorHandler {
    @ExceptionHandler(NoEligibleRecommendationException::class)
    fun noCandidate(error: NoEligibleRecommendationException): ResponseEntity<RecommendationApiError> =
        ResponseEntity.status(HttpStatus.CONFLICT).body(
            RecommendationApiError("NO_ELIGIBLE_CANDIDATE", error.message.orEmpty(), error.requestId.toString()),
        )
}
