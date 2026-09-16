package kr.co.ninetyseconds.recommendation.server.recommendation

import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kr.co.ninetyseconds.recommendation.server.project.ProjectConfiguration
import kr.co.ninetyseconds.recommendation.server.project.ProjectConfigurationStore
import kr.co.ninetyseconds.recommendation.server.event.RecommendationEventStore
import kr.co.ninetyseconds.recommendation.server.event.ConsentStatus
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
        RecommendationEventStore { true },
        Clock.fixed(Instant.parse("2026-08-27T00:00:00Z"), ZoneOffset.UTC),
        RecentRecommendationLoad { _, _, _ -> emptyMap() },
        OperationalLocationStatusLoad { _, _ -> emptyMap() },
        Duration.ofMinutes(15),
    )

    @Test
    fun `excludes previous location and returns deterministic remote result`() {
        val request = request(previousLocationId = UUID.fromString("10000000-0000-4000-8000-000000000001"))
        val first = service(request)
        val second = service(request)

        assertEquals("10000000-0000-4000-8000-000000000002", first.location.path("id").stringValue())
        assertEquals("REMOTE", first.source)
        assertEquals(first.recommendationId, second.recommendationId)
        assertEquals(
            listOf("PREVIOUS_EXCLUDED", "HIGHEST_PRIORITY", "RECENT_LOAD_BALANCED", "WEIGHTED_DETERMINISTIC"),
            first.reasons,
        )
    }

    @Test
    fun `selects the location with fewer recommendations in the recent window`() {
        val locationA = UUID.fromString("10000000-0000-4000-8000-000000000001")
        val locationB = UUID.fromString("10000000-0000-4000-8000-000000000002")
        val balanced = CreateRecommendation(
            ProjectConfigurationStore { ProjectConfiguration("EXPO", 1, config) },
            JsonMapper.builder().addModule(kotlinModule()).build(),
            RecommendationEventStore { true },
            Clock.fixed(Instant.parse("2026-08-27T00:00:00Z"), ZoneOffset.UTC),
            RecentRecommendationLoad { projectCode, locationIds, since ->
                assertEquals("EXPO", projectCode)
                assertEquals(setOf(locationA, locationB), locationIds)
                assertEquals(Instant.parse("2026-08-26T23:45:00Z"), since)
                mapOf(locationA to 9L, locationB to 2L)
            },
            OperationalLocationStatusLoad { _, _ -> emptyMap() },
            Duration.ofMinutes(15),
        )

        val result = balanced(request())

        assertEquals(locationB.toString(), result.location.path("id").stringValue())
        assertEquals("balanced-v2", result.policyVersion)
    }

    @Test
    fun `returns conflict domain error when emotion has no candidate`() {
        assertFailsWith<NoEligibleRecommendationException> { service(request(emotionCode = "UNKNOWN")) }
    }

    @Test
    fun `version two returns a journey stop with requested sense`() {
        val result = service(request(schemaVersion = 2, journeySenseCodes = listOf("INSIGHT", "ACTION", "TASTE")))

        assertEquals(1, result.journey.size)
        assertEquals(1, result.journey.single().order)
        assertEquals("INSIGHT", result.journey.single().senseCode)
        assertEquals(result.item, result.journey.single().item)
    }

    @Test
    fun `rich flow selects three confirmed active venues in requested sense order`() {
        val richConfig = """
            {
              "emotion_profiles":[{"code":"VITALITY","active":true}],
              "locations":[], "items":[], "rules":[],
              "rich_flow": {
                "journey_mappings":[{"condition_code":"JOY","sense_sequence":["INSIGHT","ACTION","TASTE"]}],
                "venue_operations":[
                  {"code":"HALL","name":{"ko":"주제관"},"active":true,"confirmation_status":"CONFIRMED","indoor":true,"alcohol":false,"sense_codes":["INSIGHT"],"marker":{"x_percent":10,"y_percent":20}},
                  {"code":"PLAY","name":{"ko":"체험장"},"active":true,"confirmation_status":"CONFIRMED","indoor":false,"alcohol":false,"sense_codes":["ACTION"],"marker":{"x_percent":30,"y_percent":40}},
                  {"code":"FOOD","name":{"ko":"먹거리"},"active":true,"confirmation_status":"CONFIRMED","indoor":true,"alcohol":false,"sense_codes":["TASTE"],"marker":{"x_percent":50,"y_percent":60}},
                  {"code":"DRAFT","name":{"ko":"미확정"},"active":true,"confirmation_status":"PENDING_CONFIRMATION","indoor":true,"alcohol":false,"sense_codes":["ACTION"]}
                ]
              }
            }
        """.trimIndent()
        val richService = CreateRecommendation(
            ProjectConfigurationStore { ProjectConfiguration("EXPO", 1, richConfig) },
            JsonMapper.builder().addModule(kotlinModule()).build(), RecommendationEventStore { true },
            Clock.fixed(Instant.parse("2026-08-27T00:00:00Z"), ZoneOffset.UTC),
            RecentRecommendationLoad { _, _, _ -> emptyMap() },
            OperationalLocationStatusLoad { _, _ -> emptyMap() }, Duration.ofMinutes(15),
        )

        val result = richService(
            request(schemaVersion = 2, journeySenseCodes = listOf("INSIGHT", "ACTION", "TASTE"))
                .copy(conditionCode = "JOY"),
        )

        assertEquals("rich-journey-v1", result.policyVersion)
        assertEquals(listOf("INSIGHT", "ACTION", "TASTE"), result.journey.map { it.senseCode })
        assertEquals(listOf("HALL", "PLAY", "FOOD"), result.journey.map { it.location.path("code").stringValue() })
        assertEquals(listOf(1, 2, 3), result.journey.map { it.order })
    }

    @Test
    fun `operational override can stop an active venue and enable a draft venue`() {
        val richConfig = """
            {
              "emotion_profiles":[{"code":"VITALITY","active":true}],
              "locations":[], "items":[], "rules":[],
              "rich_flow":{"venue_operations":[
                {"code":"ACTIVE","name":{"ko":"운영 장소"},"active":true,"confirmation_status":"CONFIRMED","indoor":true,"alcohol":false,"sense_codes":["ACTION"]},
                {"code":"DRAFT","name":{"ko":"시험 장소"},"active":false,"confirmation_status":"PENDING_CONFIRMATION","indoor":true,"alcohol":false,"sense_codes":["ACTION"]}
              ]}
            }
        """.trimIndent()
        val richService = CreateRecommendation(
            ProjectConfigurationStore { ProjectConfiguration("EXPO", 1, richConfig) },
            JsonMapper.builder().addModule(kotlinModule()).build(), RecommendationEventStore { true },
            Clock.fixed(Instant.parse("2026-08-27T00:00:00Z"), ZoneOffset.UTC),
            RecentRecommendationLoad { _, _, _ -> emptyMap() },
            OperationalLocationStatusLoad { _, _ ->
                mapOf(
                    "ACTIVE" to OperationalLocationPolicy(false, null, null),
                    "DRAFT" to OperationalLocationPolicy(true, null, null),
                )
            },
            Duration.ofMinutes(15),
        )

        val result = richService(request(schemaVersion = 2, journeySenseCodes = listOf("ACTION")))

        assertEquals("DRAFT", result.location.path("code").stringValue())
    }

    @Test
    fun `active priority policy selects an eligible venue before load balancing`() {
        val richConfig = """
            {
              "emotion_profiles":[{"code":"VITALITY","active":true}],
              "locations":[], "items":[], "rules":[],
              "rich_flow":{"venue_operations":[
                {"code":"NORMAL","name":{"ko":"일반 장소"},"active":true,"confirmation_status":"CONFIRMED","capacity":100,"indoor":true,"alcohol":false,"sense_codes":["ACTION"]},
                {"code":"PRIORITY","name":{"ko":"우선 장소"},"active":true,"confirmation_status":"CONFIRMED","capacity":100,"indoor":true,"alcohol":false,"sense_codes":["ACTION"]}
              ]}
            }
        """.trimIndent()
        val richService = CreateRecommendation(
            ProjectConfigurationStore { ProjectConfiguration("EXPO", 1, richConfig) },
            JsonMapper.builder().addModule(kotlinModule()).build(), RecommendationEventStore { true },
            Clock.fixed(Instant.parse("2026-08-27T00:00:00Z"), ZoneOffset.UTC),
            RecentRecommendationLoad { _, _, _ -> emptyMap() },
            OperationalLocationStatusLoad { _, _ ->
                mapOf(
                    "PRIORITY" to OperationalLocationPolicy(
                        true, 100, OffsetDateTime.parse("2026-08-27T01:00:00Z"),
                    ),
                )
            },
            Duration.ofMinutes(15),
        )

        val result = richService(request(schemaVersion = 2, journeySenseCodes = listOf("ACTION")))

        assertEquals("PRIORITY", result.location.path("code").stringValue())
        assertEquals(true, "OPERATOR_PRIORITY_APPLIED" in result.reasons)
    }

    @Test
    fun `rich flow rain and family filters unsafe venues`() {
        val richConfig = """
            {
              "emotion_profiles":[{"code":"VITALITY","active":true}],
              "locations":[], "items":[], "rules":[],
              "rich_flow":{"venue_operations":[
                {"code":"OUTDOOR","name":{"ko":"야외"},"active":true,"confirmation_status":"CONFIRMED","indoor":false,"alcohol":false,"sense_codes":["TASTE"]},
                {"code":"BAR","name":{"ko":"주류"},"active":true,"confirmation_status":"CONFIRMED","indoor":true,"alcohol":true,"sense_codes":["TASTE"]},
                {"code":"SAFE","name":{"ko":"실내 먹거리"},"active":true,"confirmation_status":"CONFIRMED","indoor":true,"alcohol":false,"sense_codes":["TASTE"]}
              ]}
            }
        """.trimIndent()
        val richService = CreateRecommendation(
            ProjectConfigurationStore { ProjectConfiguration("EXPO", 1, richConfig) },
            JsonMapper.builder().addModule(kotlinModule()).build(), RecommendationEventStore { true },
            Clock.fixed(Instant.parse("2026-08-27T00:00:00Z"), ZoneOffset.UTC),
            RecentRecommendationLoad { _, _, _ -> emptyMap() },
            OperationalLocationStatusLoad { _, _ -> emptyMap() }, Duration.ofMinutes(15),
        )

        val result = richService(
            request(schemaVersion = 2, journeySenseCodes = listOf("TASTE")).copy(
                operationContext = OperationContextRequest(raining = true, companionType = "FAMILY"),
            ),
        )

        assertEquals("SAFE", result.location.path("code").stringValue())
    }

    private fun request(
        emotionCode: String = "VITALITY",
        previousLocationId: UUID? = null,
        schemaVersion: Int = 1,
        journeySenseCodes: List<String> = emptyList(),
    ) = RecommendationRequest(
        schemaVersion = schemaVersion,
        projectCode = "EXPO",
        kioskId = "KIOSK-01",
        sessionId = UUID.fromString("11111111-1111-4111-8111-111111111111"),
        requestId = requestId,
        emotionCode = emotionCode,
        stressScore = 63,
        language = "ko",
        previousLocationId = previousLocationId,
        consentStatus = ConsentStatus.CONSENTED,
        journeySenseCodes = journeySenseCodes,
        requestedAt = OffsetDateTime.parse("2026-08-27T09:00:00+09:00"),
    )
}
