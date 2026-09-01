package kr.co.ninetyseconds.recommendation.application

import java.time.Instant
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
import kr.co.ninetyseconds.recommendation.domain.DecisionSource
import kr.co.ninetyseconds.recommendation.domain.EmotionCode
import kr.co.ninetyseconds.recommendation.domain.EmotionProfile
import kr.co.ninetyseconds.recommendation.domain.EmotionScore
import kr.co.ninetyseconds.recommendation.domain.LocationId
import kr.co.ninetyseconds.recommendation.domain.ProjectId
import kr.co.ninetyseconds.recommendation.domain.RecommendationDecision
import kr.co.ninetyseconds.recommendation.domain.RecommendationItem
import kr.co.ninetyseconds.recommendation.domain.RecommendationItemId
import kr.co.ninetyseconds.recommendation.domain.RecommendationRequest
import kr.co.ninetyseconds.recommendation.domain.SessionId
import kr.co.ninetyseconds.recommendation.domain.ports.RecommendationEngine
import kr.co.ninetyseconds.recommendation.domain.ports.RecommendationEventSink
import kr.co.ninetyseconds.recommendation.domain.ports.RecommendationHistory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecommendTest {
    @Test
    fun `latest local location is attached to engine request and excluded`() = awaitResult {
        val previous = LocationId("location-a")
        var received: RecommendationRequest? = null
        var recorded: RecommendationDecision? = null
        val expected = decision(LocationId("location-b"))
        val recommend = Recommend(
            engine = RecommendationEngine { request ->
                received = request
                expected
            },
            eventSink = object : RecommendationEventSink {
                override suspend fun record(decision: RecommendationDecision) {
                    recorded = decision
                }
            },
            history = object : RecommendationHistory {
                override suspend fun recentLocationIds(limit: Int) = listOf(previous).take(limit)
            },
        )

        val result = recommend(request())

        assertEquals(previous, received?.previousLocationId)
        assertTrue(previous in requireNotNull(received).excludedLocationIds)
        assertEquals(expected, result)
        assertEquals(expected, recorded)
    }

    @Test
    fun `explicit previous location takes precedence over stored history`() = awaitResult {
        val explicit = LocationId("location-explicit")
        var received: RecommendationRequest? = null
        val expected = decision(LocationId("location-b"))
        val recommend = Recommend(
            engine = RecommendationEngine { request ->
                received = request
                expected
            },
            eventSink = object : RecommendationEventSink {
                override suspend fun record(decision: RecommendationDecision) = Unit
            },
            history = object : RecommendationHistory {
                override suspend fun recentLocationIds(limit: Int) = listOf(LocationId("location-stored")).take(limit)
            },
        )

        recommend(request().copy(previousLocationId = explicit))

        assertEquals(explicit, received?.previousLocationId)
        assertTrue(explicit in requireNotNull(received).excludedLocationIds)
    }

    private fun request() = RecommendationRequest(
        requestId = "request-1",
        projectId = ProjectId("EXPO"),
        sessionId = SessionId("session-1"),
        emotionProfile = EmotionProfile(listOf(EmotionScore(EmotionCode("JOY"), 1.0))),
        requestedAt = Instant.parse("2026-09-01T00:00:00Z"),
    )

    private fun decision(locationId: LocationId) = RecommendationDecision(
        requestId = "request-1",
        item = RecommendationItem(
            id = RecommendationItemId("item-1"),
            locationId = locationId,
            title = "Recommendation",
            imageRef = null,
            supportedEmotions = setOf(EmotionCode("JOY")),
        ),
        source = DecisionSource.REMOTE,
        decidedAt = Instant.parse("2026-09-01T00:00:01Z"),
    )

    private fun <T> awaitResult(block: suspend () -> T): T {
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
}
