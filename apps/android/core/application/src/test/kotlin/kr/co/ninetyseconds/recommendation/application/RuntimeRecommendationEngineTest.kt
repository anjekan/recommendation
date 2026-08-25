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
import kr.co.ninetyseconds.recommendation.domain.RecommendationRejected
import kr.co.ninetyseconds.recommendation.domain.RecommendationRequest
import kr.co.ninetyseconds.recommendation.domain.RecommendationUnavailable
import kr.co.ninetyseconds.recommendation.domain.RuntimeMode
import kr.co.ninetyseconds.recommendation.domain.SessionId
import kr.co.ninetyseconds.recommendation.domain.ports.RecommendationEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class RuntimeRecommendationEngineTest {
    @Test
    fun `hybrid falls back only when remote is unavailable`() = runRuntimeSuspend {
        val engine = router(remote = failing(RecommendationUnavailable("offline")))

        val decision = engine.recommend(request())

        assertEquals(DecisionSource.LOCAL_FALLBACK, decision.source)
    }

    @Test
    fun `hybrid does not hide rejected remote request`() {
        assertThrows(RecommendationRejected::class.java) {
            runRuntimeSuspend {
                router(remote = failing(RecommendationRejected("bad request"))).recommend(request())
            }
        }
    }

    @Test
    fun `remote mode never falls back`() {
        assertThrows(RecommendationUnavailable::class.java) {
            runRuntimeSuspend {
                router(RuntimeMode.REMOTE, failing(RecommendationUnavailable("offline"))).recommend(request())
            }
        }
    }

    private fun router(
        mode: RuntimeMode = RuntimeMode.HYBRID,
        remote: RecommendationEngine,
    ) = RuntimeRecommendationEngine(
        modeProvider = RuntimeModeProvider { mode },
        local = RecommendationEngine { decision(DecisionSource.LOCAL) },
        remote = remote,
    )

    private fun failing(error: Exception) = RecommendationEngine { throw error }

    private fun decision(source: DecisionSource) = RecommendationDecision(
        requestId = "request",
        item = RecommendationItem(
            RecommendationItemId("item"),
            LocationId("location"),
            "Item",
            null,
            setOf(EmotionCode("JOY")),
        ),
        source = source,
        decidedAt = Instant.EPOCH,
    )

    private fun request() = RecommendationRequest(
        requestId = "request",
        projectId = ProjectId("EXPO"),
        sessionId = SessionId("session"),
        emotionProfile = EmotionProfile(listOf(EmotionScore(EmotionCode("JOY"), 1.0))),
        requestedAt = Instant.EPOCH,
    )
}

private fun <T> runRuntimeSuspend(block: suspend () -> T): T {
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
