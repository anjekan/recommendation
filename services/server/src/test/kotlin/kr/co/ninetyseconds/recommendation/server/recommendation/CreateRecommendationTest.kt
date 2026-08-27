package kr.co.ninetyseconds.recommendation.server.recommendation

import java.time.Clock
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kr.co.ninetyseconds.recommendation.server.project.ProjectConfiguration
import kr.co.ninetyseconds.recommendation.server.project.ProjectConfigurationStore
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.kotlinModule

class CreateRecommendationTest {
    private val requestId = UUID.fromString("22222222-2222-4222-8222-222222222222")
    private val config = """
        {
          "emotion_profiles":[{"code":"VITALITY","name":{"ko":"활력"},"message":{"ko":"활기찬 상태"},"color":"#FFA726","active":true}],
          "locations":[
            {"id":"10000000-0000-4000-8000-000000000001","code":"A","name":{"ko":"이전 장소"},"status":"NORMAL","marker":{"x_percent":1,"y_percent":2},"active":true},
            {"id":"10000000-0000-4000-8000-000000000002","code":"B","name":{"ko":"추천 장소"},"status":"NORMAL","marker":{"x_percent":3,"y_percent":4},"active":true}
          ],
          "items":[
            {"id":"20000000-0000-4000-8000-000000000001","type":"place","location_id":"10000000-0000-4000-8000-000000000001","name":{"ko":"이전 장소"},"description":{"ko":"설명"},"image_url":"a.webp","active":true},
            {"id":"20000000-0000-4000-8000-000000000002","type":"place","location_id":"10000000-0000-4000-8000-000000000002","name":{"ko":"추천 장소"},"description":{"ko":"설명"},"image_url":"b.webp","active":true}
          ],
          "rules":[
            {"emotion_code":"VITALITY","item_id":"20000000-0000-4000-8000-000000000001","weight":100,"priority":10,"active":true},
            {"emotion_code":"VITALITY","item_id":"20000000-0000-4000-8000-000000000002","weight":100,"priority":10,"active":true}
          ]
        }
    """.trimIndent()
    private val service = CreateRecommendation(
        ProjectConfigurationStore { ProjectConfiguration("EXPO", 1, config) },
        JsonMapper.builder().addModule(kotlinModule()).build(),
        Clock.fixed(Instant.parse("2026-08-27T00:00:00Z"), ZoneOffset.UTC),
    )

    @Test
    fun `excludes previous location and returns deterministic remote result`() {
        val request = request(previousLocationId = UUID.fromString("10000000-0000-4000-8000-000000000001"))
        val first = service(request)
        val second = service(request)

        assertEquals("10000000-0000-4000-8000-000000000002", first.location.path("id").stringValue())
        assertEquals("REMOTE", first.source)
        assertEquals(first.recommendationId, second.recommendationId)
        assertEquals(listOf("PREVIOUS_EXCLUDED", "HIGHEST_PRIORITY", "WEIGHTED_DETERMINISTIC"), first.reasons)
    }

    @Test
    fun `returns conflict domain error when emotion has no candidate`() {
        assertFailsWith<NoEligibleRecommendationException> { service(request(emotionCode = "UNKNOWN")) }
    }

    private fun request(
        emotionCode: String = "VITALITY",
        previousLocationId: UUID? = null,
    ) = RecommendationRequest(
        schemaVersion = 1,
        projectCode = "EXPO",
        kioskId = "KIOSK-01",
        sessionId = UUID.fromString("11111111-1111-4111-8111-111111111111"),
        requestId = requestId,
        emotionCode = emotionCode,
        stressScore = 63,
        language = "ko",
        previousLocationId = previousLocationId,
        requestedAt = OffsetDateTime.parse("2026-08-27T09:00:00+09:00"),
    )
}
