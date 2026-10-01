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
import kr.co.ninetyseconds.recommendation.server.event.RecommendationJourneyStore
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
        RecentSenseRecommendationLoad { _, _, _ -> emptyMap() },
        OperationalLocationStatusLoad { _, _ -> emptyMap() },
        Duration.ofMinutes(15),
        RecommendationJourneyStore { _, _ -> },
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
            RecentSenseRecommendationLoad { _, _, _ -> emptyMap() },
            OperationalLocationStatusLoad { _, _ -> emptyMap() },
            Duration.ofMinutes(15),
            RecommendationJourneyStore { _, _ -> },
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
        var persistedJourneyStopCount = 0
        val richService = CreateRecommendation(
            ProjectConfigurationStore { ProjectConfiguration("EXPO", 1, richConfig) },
            JsonMapper.builder().addModule(kotlinModule()).build(), RecommendationEventStore { true },
            Clock.fixed(Instant.parse("2026-08-27T00:00:00Z"), ZoneOffset.UTC),
            RecentRecommendationLoad { _, _, _ -> emptyMap() },
            RecentSenseRecommendationLoad { _, _, _ -> emptyMap() },
            OperationalLocationStatusLoad { _, _ -> emptyMap() }, Duration.ofMinutes(15),
            RecommendationJourneyStore { _, stops -> persistedJourneyStopCount = stops.size },
        )

        val result = richService(
            request(schemaVersion = 2, journeySenseCodes = listOf("INSIGHT", "ACTION", "TASTE"))
                .copy(conditionCode = "JOY"),
        )

        assertEquals("rich-journey-v3", result.policyVersion)
        assertEquals(setOf("INSIGHT", "ACTION", "TASTE"), result.journey.map { it.senseCode }.toSet())
        assertEquals(setOf("HALL", "PLAY", "FOOD"), result.journey.map { it.location.path("code").stringValue() }.toSet())
        assertEquals(listOf(1, 2, 3), result.journey.map { it.order })
        assertEquals(3, persistedJourneyStopCount)
    }

    @Test
    fun `rich flow replaces an overexposed sense with less exposed senses`() {
        val richConfig = """
            {
              "emotion_profiles":[{"code":"VITALITY","active":true}],
              "locations":[], "items":[], "rules":[],
              "rich_flow": {
                "senses":[
                  {"code":"INSIGHT","active":true},{"code":"SCENT","active":true},
                  {"code":"TASTE","active":true},{"code":"LISTENING","active":true},
                  {"code":"ACTION","active":true},{"code":"INTUITION","active":true}
                ],
                "venue_operations":[
                  {"code":"V1","name":{"ko":"안목"},"active":true,"confirmation_status":"CONFIRMED","indoor":true,"alcohol":false,"sense_codes":["INSIGHT"]},
                  {"code":"V2","name":{"ko":"향기"},"active":true,"confirmation_status":"CONFIRMED","indoor":true,"alcohol":false,"sense_codes":["SCENT"]},
                  {"code":"V3","name":{"ko":"미식"},"active":true,"confirmation_status":"CONFIRMED","indoor":true,"alcohol":false,"sense_codes":["TASTE"]},
                  {"code":"V4","name":{"ko":"경청"},"active":true,"confirmation_status":"CONFIRMED","indoor":true,"alcohol":false,"sense_codes":["LISTENING"]},
                  {"code":"V5","name":{"ko":"실천"},"active":true,"confirmation_status":"CONFIRMED","indoor":true,"alcohol":false,"sense_codes":["ACTION"]},
                  {"code":"V6","name":{"ko":"통찰"},"active":true,"confirmation_status":"CONFIRMED","indoor":true,"alcohol":false,"sense_codes":["INTUITION"]}
                ]
              }
            }
        """.trimIndent()
        val richService = CreateRecommendation(
            ProjectConfigurationStore { ProjectConfiguration("EXPO", 1, richConfig) },
            JsonMapper.builder().addModule(kotlinModule()).build(), RecommendationEventStore { true },
            Clock.fixed(Instant.parse("2026-08-27T00:00:00Z"), ZoneOffset.UTC),
            RecentRecommendationLoad { _, _, _ -> emptyMap() },
            RecentSenseRecommendationLoad { projectCode, senseCodes, since ->
                assertEquals("EXPO", projectCode)
                assertEquals(setOf("INSIGHT", "SCENT", "TASTE", "LISTENING", "ACTION", "INTUITION"), senseCodes)
                assertEquals(Instant.parse("2026-08-26T23:45:00Z"), since)
                mapOf("TASTE" to 20L, "INSIGHT" to 8L, "ACTION" to 5L)
            },
            OperationalLocationStatusLoad { _, _ -> emptyMap() }, Duration.ofMinutes(15),
            RecommendationJourneyStore { _, _ -> },
        )

        val result = richService(
            request(schemaVersion = 2, journeySenseCodes = listOf("TASTE", "INSIGHT", "ACTION")),
        )

        assertEquals(setOf("SCENT", "LISTENING", "INTUITION"), result.journey.map { it.senseCode }.toSet())
        assertEquals(true, "SENSE_LOAD_BALANCED" in result.reasons)
    }

    @Test
    fun `rich flow fills three unique stops when a requested sense has no eligible venue`() {
        val richConfig = """
            {
              "emotion_profiles":[{"code":"VITALITY","active":true}],
              "locations":[], "items":[], "rules":[],
              "rich_flow": {
                "senses":[
                  {"code":"INSIGHT","active":true},{"code":"ACTION","active":true}
                ],
                "venue_operations":[
                  {"code":"VIEW","name":{"ko":"볼거리"},"active":true,"confirmation_status":"CONFIRMED","indoor":true,"alcohol":false,"sense_codes":["INSIGHT"]},
                  {"code":"PHOTO","name":{"ko":"사진 명소"},"active":true,"confirmation_status":"CONFIRMED","indoor":true,"alcohol":false,"sense_codes":["INSIGHT"]},
                  {"code":"PLAY","name":{"ko":"체험"},"active":true,"confirmation_status":"CONFIRMED","indoor":true,"alcohol":false,"sense_codes":["ACTION"]}
                ]
              }
            }
        """.trimIndent()
        val richService = CreateRecommendation(
            ProjectConfigurationStore { ProjectConfiguration("EXPO", 1, richConfig) },
            JsonMapper.builder().addModule(kotlinModule()).build(), RecommendationEventStore { true },
            Clock.fixed(Instant.parse("2026-08-27T00:00:00Z"), ZoneOffset.UTC),
            RecentRecommendationLoad { _, _, _ -> emptyMap() },
            RecentSenseRecommendationLoad { _, _, _ -> emptyMap() },
            OperationalLocationStatusLoad { _, _ -> emptyMap() }, Duration.ofMinutes(15),
            RecommendationJourneyStore { _, _ -> },
        )

        val result = richService(
            request(schemaVersion = 2, journeySenseCodes = listOf("SCENT", "INSIGHT", "ACTION")),
        )

        assertEquals(3, result.journey.size)
        assertEquals(3, result.journey.map { it.location.path("code").stringValue() }.distinct().size)
        assertEquals(true, "JOURNEY_SIZE_FILLED" in result.reasons)
    }

    @Test
    fun `rich flow prefers three different physical zones`() {
        val richConfig = """
            {
              "emotion_profiles":[{"code":"VITALITY","active":true}],
              "locations":[], "items":[], "rules":[],
              "rich_flow": {
                "senses":[
                  {"code":"INSIGHT","active":true},{"code":"SCENT","active":true},{"code":"ACTION","active":true}
                ],
                "venue_operations":[
                  {"code":"VIEW","zone_code":"1","name":{"ko":"볼거리"},"active":true,"confirmation_status":"CONFIRMED","indoor":true,"alcohol":false,"sense_codes":["INSIGHT"]},
                  {"code":"SCENT_SAME","zone_code":"1","name":{"ko":"같은 존 향기"},"active":true,"confirmation_status":"CONFIRMED","indoor":true,"alcohol":false,"sense_codes":["SCENT"]},
                  {"code":"SCENT_OTHER","zone_code":"2","name":{"ko":"다른 존 향기"},"active":true,"confirmation_status":"CONFIRMED","indoor":true,"alcohol":false,"sense_codes":["SCENT"]},
                  {"code":"PLAY","zone_code":"3","name":{"ko":"체험"},"active":true,"confirmation_status":"CONFIRMED","indoor":true,"alcohol":false,"sense_codes":["ACTION"]}
                ]
              }
            }
        """.trimIndent()
        val richService = CreateRecommendation(
            ProjectConfigurationStore { ProjectConfiguration("EXPO", 1, richConfig) },
            JsonMapper.builder().addModule(kotlinModule()).build(), RecommendationEventStore { true },
            Clock.fixed(Instant.parse("2026-08-27T00:00:00Z"), ZoneOffset.UTC),
            RecentRecommendationLoad { _, _, _ -> emptyMap() },
            RecentSenseRecommendationLoad { _, _, _ -> mapOf("INSIGHT" to 0L, "SCENT" to 1L, "ACTION" to 2L) },
            OperationalLocationStatusLoad { _, _ -> emptyMap() }, Duration.ofMinutes(15),
            RecommendationJourneyStore { _, _ -> },
        )

        val result = richService(
            request(schemaVersion = 2, journeySenseCodes = listOf("INSIGHT", "SCENT", "ACTION")),
        )

        assertEquals(setOf("VIEW", "SCENT_OTHER", "PLAY"), result.journey.map { it.location.path("code").stringValue() }.toSet())
    }

    @Test
    fun `rich flow excludes the kiosk own measurement installation`() {
        val richConfig = """
            {
              "emotion_profiles":[{"code":"VITALITY","active":true}],
              "locations":[], "items":[], "rules":[],
              "rich_flow": {
                "senses":[{"code":"INTUITION","active":true}],
                "venue_operations":[
                  {"code":"AI_A","installation_group":"A","name":{"ko":"A 측정소"},"active":true,"confirmation_status":"CONFIRMED","indoor":true,"alcohol":false,"sense_codes":["INTUITION"]},
                  {"code":"AI_B","installation_group":"B","name":{"ko":"B 측정소"},"active":true,"confirmation_status":"CONFIRMED","indoor":true,"alcohol":false,"sense_codes":["INTUITION"]}
                ]
              }
            }
        """.trimIndent()
        val richService = CreateRecommendation(
            ProjectConfigurationStore { ProjectConfiguration("EXPO", 1, richConfig) },
            JsonMapper.builder().addModule(kotlinModule()).build(), RecommendationEventStore { true },
            Clock.fixed(Instant.parse("2026-08-27T00:00:00Z"), ZoneOffset.UTC),
            RecentRecommendationLoad { _, _, _ -> emptyMap() },
            RecentSenseRecommendationLoad { _, _, _ -> emptyMap() },
            OperationalLocationStatusLoad { _, _ -> emptyMap() }, Duration.ofMinutes(15),
            RecommendationJourneyStore { _, _ -> },
        )

        val fromA = richService(
            request(schemaVersion = 2, journeySenseCodes = listOf("INTUITION")).copy(kioskId = "LOCAL-KIOSK-A-3"),
        )
        val fromB = richService(
            request(schemaVersion = 2, journeySenseCodes = listOf("INTUITION")).copy(kioskId = "LOCAL-KIOSK-B-13"),
        )

        assertEquals("AI_B", fromA.location.path("code").stringValue())
        assertEquals("AI_A", fromB.location.path("code").stringValue())
    }

    @Test
    fun `operational override can stop an active venue and enable a draft venue`() {
        val richConfig = """
            {
              "emotion_profiles":[{"code":"VITALITY","active":true}],
              "locations":[], "items":[], "rules":[],
              "rich_flow":{"venue_operations":[
                {"code":"ACTIVE","name":{"ko":"운영 장소"},"active":true,"confirmation_status":"CONFIRMED","indoor":true,"alcohol":false,"sense_codes":["ACTION"]},
                {"code":"DRAFT","name":{"ko":"시험 장소"},"active":false,"confirmation_status":"PENDING_CONFIRMATION","indoor":true,"alcohol":false,"sense_codes":["ACTION"]},
                {"code":"BLOCKED","name":{"ko":"영구 제외 장소"},"active":false,"recommendation_eligible":false,"confirmation_status":"CONFIRMED","indoor":true,"alcohol":false,"sense_codes":["ACTION"]}
              ]}
            }
        """.trimIndent()
        val richService = CreateRecommendation(
            ProjectConfigurationStore { ProjectConfiguration("EXPO", 1, richConfig) },
            JsonMapper.builder().addModule(kotlinModule()).build(), RecommendationEventStore { true },
            Clock.fixed(Instant.parse("2026-08-27T00:00:00Z"), ZoneOffset.UTC),
            RecentRecommendationLoad { _, _, _ -> emptyMap() },
            RecentSenseRecommendationLoad { _, _, _ -> emptyMap() },
            OperationalLocationStatusLoad { _, _ ->
                mapOf(
                    "ACTIVE" to OperationalLocationPolicy(false, null, null),
                    "DRAFT" to OperationalLocationPolicy(true, null, null),
                    "BLOCKED" to OperationalLocationPolicy(true, null, null),
                )
            },
            Duration.ofMinutes(15),
            RecommendationJourneyStore { _, _ -> },
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
            RecentSenseRecommendationLoad { _, _, _ -> emptyMap() },
            OperationalLocationStatusLoad { _, _ ->
                mapOf(
                    "PRIORITY" to OperationalLocationPolicy(
                        true, 100, OffsetDateTime.parse("2026-08-27T01:00:00Z"),
                    ),
                )
            },
            Duration.ofMinutes(15),
            RecommendationJourneyStore { _, _ -> },
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
            RecentSenseRecommendationLoad { _, _, _ -> emptyMap() },
            OperationalLocationStatusLoad { _, _ -> emptyMap() }, Duration.ofMinutes(15),
            RecommendationJourneyStore { _, _ -> },
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
