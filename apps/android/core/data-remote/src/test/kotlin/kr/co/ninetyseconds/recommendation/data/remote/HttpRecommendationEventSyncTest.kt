package kr.co.ninetyseconds.recommendation.data.remote

import java.time.Instant
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.runBlocking
import kr.co.ninetyseconds.recommendation.domain.ConsentStatus
import kr.co.ninetyseconds.recommendation.domain.ports.OfflineRecommendationEvent
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HttpRecommendationEventSyncTest {
    @Test
    fun `uploads offline event contract and returns acknowledged ids`() = runBlocking {
        val captured = AtomicReference<Request>()
        val event = event()
        val interceptor = Interceptor { chain ->
            captured.set(chain.request())
            Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("test")
                .body("""{"accepted_event_ids":["${event.eventId}"]}""".toResponseBody())
                .build()
        }
        val sync = HttpRecommendationEventSync(
            "https://example.test",
            "secret",
            OkHttpClient.Builder().addInterceptor(interceptor).build(),
        )

        val accepted = sync.sync(listOf(event))

        assertEquals(setOf(event.eventId), accepted)
        assertEquals("secret", captured.get().header("X-Kiosk-Key"))
        val body = Buffer().also { requireNotNull(captured.get().body).writeTo(it) }.readUtf8()
        assertTrue(body.contains("\"project_code\":\"EXPO\""))
        assertTrue(body.contains("\"source\":\"LOCAL_FALLBACK\""))
    }

    private fun event() = OfflineRecommendationEvent(
        eventId = "22222222-2222-4222-8222-222222222222",
        projectCode = "EXPO",
        kioskId = "KIOSK-01",
        sessionId = "11111111-1111-4111-8111-111111111111",
        emotionCode = "JOY",
        itemId = "33333333-3333-4333-8333-333333333333",
        locationId = "44444444-4444-4444-8444-444444444444",
        source = "LOCAL_FALLBACK",
        consentStatus = ConsentStatus.DECLINED,
        stressScore = 42,
        policyVersion = "local-v1",
        occurredAt = Instant.parse("2026-09-01T00:00:00Z"),
    )
}
