package kr.co.ninetyseconds.recommendation.data.remote

import java.time.Instant
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.runBlocking
import kr.co.ninetyseconds.recommendation.domain.EmotionCode
import kr.co.ninetyseconds.recommendation.domain.EmotionProfile
import kr.co.ninetyseconds.recommendation.domain.EmotionScore
import kr.co.ninetyseconds.recommendation.domain.LocationId
import kr.co.ninetyseconds.recommendation.domain.ProjectId
import kr.co.ninetyseconds.recommendation.domain.RecommendationRejected
import kr.co.ninetyseconds.recommendation.domain.RecommendationRequest
import kr.co.ninetyseconds.recommendation.domain.RecommendationUnavailable
import kr.co.ninetyseconds.recommendation.domain.SessionId
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class HttpRecommendationEngineTest {
    @Test
    fun `maps successful contract response and sends kiosk key`() = runBlocking {
        val captured = AtomicReference<Request>()
        val engine = engine(200, successBody(), captured)

        val decision = engine.recommend(request())

        assertEquals("REMOTE", decision.source.name)
        assertEquals("장소", decision.item.title)
        assertEquals("secret", captured.get().header("X-Kiosk-Key"))
        assertEquals("request-1", captured.get().header("X-Request-Id"))
    }

    @Test
    fun `sends the explicit previous location in the remote request`() = runBlocking {
        val captured = AtomicReference<Request>()
        val engine = engine(200, successBody(), captured)

        engine.recommend(request().copy(previousLocationId = LocationId("location-previous")))

        val body = Buffer().also { requireNotNull(captured.get().body).writeTo(it) }.readUtf8()
        assertTrue(body.contains("\"previous_location_id\":\"location-previous\""))
    }

    @Test
    fun `server failure is classified as unavailable`() {
        assertThrows(RecommendationUnavailable::class.java) {
            runBlocking { engine(503, "{}").recommend(request()) }
        }
    }

    @Test
    fun `client and business error is classified as rejected`() {
        assertThrows(RecommendationRejected::class.java) {
            runBlocking { engine(409, """{"message":"no candidate"}""").recommend(request()) }
        }
    }

    private fun engine(
        status: Int,
        body: String,
        captured: AtomicReference<Request> = AtomicReference(),
    ): HttpRecommendationEngine {
        val interceptor = Interceptor { chain ->
            captured.set(chain.request())
            Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(status)
                .message("test")
                .body(body.toResponseBody())
                .build()
        }
        return HttpRecommendationEngine(
            baseUrl = "https://example.test",
            kioskKey = "secret",
            client = OkHttpClient.Builder().addInterceptor(interceptor).build(),
        )
    }

    private fun request() = RecommendationRequest(
        requestId = "request-1",
        projectId = ProjectId("EXPO"),
        sessionId = SessionId("session-1"),
        emotionProfile = EmotionProfile(listOf(EmotionScore(EmotionCode("JOY"), 1.0))),
        requestedAt = Instant.parse("2026-08-25T00:00:00Z"),
        kioskId = "KIOSK-01",
        stressScore = 20,
        language = "ko",
    )

    private fun successBody() =
        """
        {
          "schema_version": 1,
          "request_id": "request-1",
          "item": {
            "id": "item-1",
            "type": "place",
            "name": {"ko": "장소", "en": "Place"},
            "description": {"ko": "설명"},
            "image_url": "/item.webp",
            "attributes": {}
          },
          "location": {
            "id": "location-1",
            "code": "ZONE-1",
            "name": {"ko": "장소"},
            "status": "NORMAL"
          },
          "source": "REMOTE",
          "created_at": "2026-08-25T00:00:01Z"
        }
        """.trimIndent()
}
