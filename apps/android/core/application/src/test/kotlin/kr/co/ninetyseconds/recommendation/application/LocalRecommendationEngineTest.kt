package kr.co.ninetyseconds.recommendation.application

import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
import kr.co.ninetyseconds.recommendation.domain.EmotionCode
import kr.co.ninetyseconds.recommendation.domain.EmotionProfile
import kr.co.ninetyseconds.recommendation.domain.EmotionScore
import kr.co.ninetyseconds.recommendation.domain.LocationId
import kr.co.ninetyseconds.recommendation.domain.ProjectId
import kr.co.ninetyseconds.recommendation.domain.RecommendationItem
import kr.co.ninetyseconds.recommendation.domain.RecommendationItemId
import kr.co.ninetyseconds.recommendation.domain.RecommendationRequest
import kr.co.ninetyseconds.recommendation.domain.SessionId
import kr.co.ninetyseconds.recommendation.domain.ports.ProjectCatalog
import kr.co.ninetyseconds.recommendation.domain.ports.RecommendationHistory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class LocalRecommendationEngineTest {
    private val now = Instant.parse("2026-08-25T00:00:00Z")
    private val joy = EmotionCode("JOY")
    private val locationA = LocationId("location-a")
    private val locationB = LocationId("location-b")

    @Test
    fun `recent location is not repeated when another location is available`() = runSuspend {
        val engine = engine(items = listOf(item("a", locationA), item("b", locationB)), recent = listOf(locationA))

        val decision = engine.recommend(request("request-1"))

        assertEquals(locationB, decision.item.locationId)
    }

    @Test
    fun `recent history is relaxed when it would remove every candidate`() = runSuspend {
        val engine = engine(items = listOf(item("a", locationA)), recent = listOf(locationA))

        val decision = engine.recommend(request("request-2"))

        assertEquals(locationA, decision.item.locationId)
    }

    @Test
    fun `explicitly excluded location is never selected`() = runSuspend {
        val engine = engine(items = listOf(item("a", locationA), item("b", locationB)))

        val decision = engine.recommend(request("request-3", excluded = setOf(locationA)))

        assertEquals(locationB, decision.item.locationId)
    }

    @Test
    fun `same request and candidates produce the same decision`() = runSuspend {
        val engine = engine(items = listOf(item("a", locationA), item("b", locationB)))
        val request = request("idempotent-request")

        val first = engine.recommend(request)
        val second = engine.recommend(request)

        assertEquals(first, second)
    }

    @Test
    fun `missing emotion candidate fails explicitly`() {
        assertThrows(NoRecommendationAvailable::class.java) {
            runSuspend {
                engine(items = listOf(item("a", locationA))).recommend(
                    request("request-4", emotion = EmotionCode("SERENITY")),
                )
            }
        }
    }

    private fun engine(
        items: List<RecommendationItem>,
        recent: List<LocationId> = emptyList(),
    ) = LocalRecommendationEngine(
        catalog = object : ProjectCatalog {
            override suspend fun getRecommendationItems(projectId: ProjectId) = items
        },
        history = object : RecommendationHistory {
            override suspend fun recentLocationIds(limit: Int) = recent.take(limit)
        },
        clock = Clock.fixed(now, ZoneOffset.UTC),
    )

    private fun item(id: String, locationId: LocationId) = RecommendationItem(
        id = RecommendationItemId(id),
        locationId = locationId,
        title = id,
        imageRef = null,
        supportedEmotions = setOf(joy),
    )

    private fun request(
        id: String,
        emotion: EmotionCode = joy,
        excluded: Set<LocationId> = emptySet(),
    ) = RecommendationRequest(
        requestId = id,
        projectId = ProjectId("EXPO"),
        sessionId = SessionId("session"),
        emotionProfile = EmotionProfile(listOf(EmotionScore(emotion, 1.0))),
        excludedLocationIds = excluded,
        requestedAt = now,
    )
}

private fun <T> runSuspend(block: suspend () -> T): T {
    var outcome: Result<T>? = null
    block.startCoroutine(
        object : Continuation<T> {
            override val context = EmptyCoroutineContext
            override fun resumeWith(result: Result<T>) {
                outcome = result
            }
        },
    )
    return outcome!!.getOrThrow()
}
