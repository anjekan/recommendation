package kr.co.ninetyseconds.recommendation

import java.time.Instant
import kr.co.ninetyseconds.recommendation.domain.DecisionSource
import kr.co.ninetyseconds.recommendation.domain.EmotionCode
import kr.co.ninetyseconds.recommendation.domain.JourneyStop
import kr.co.ninetyseconds.recommendation.domain.LocationId
import kr.co.ninetyseconds.recommendation.domain.RecommendationDecision
import kr.co.ninetyseconds.recommendation.domain.RecommendationItem
import kr.co.ninetyseconds.recommendation.domain.RecommendationItemId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class JourneyPresentationTest {
    @Test
    fun `uses primary item when a degraded response has no journey stops`() {
        val model = decision(journeyCount = 0, expectedCount = 3).toJourneyPresentation()
        assertEquals(listOf("1. 콘서트장"), model.stopLabels)
        assertEquals(1, model.actualStopCount)
        assertEquals("현재 운영 상황에 따라 1개 장소만 안내합니다.", model.operationNotice("ko"))
    }

    @Test
    fun `shows a two stop notice when one planned stop is unavailable`() {
        val model = decision(journeyCount = 2, expectedCount = 3).toJourneyPresentation()
        assertEquals(2, model.actualStopCount)
        assertEquals("현재 운영 상황에 따라 2개 장소만 안내합니다.", model.operationNotice("ko"))
    }

    @Test
    fun `does not show an operations notice for a complete journey`() {
        assertNull(decision(journeyCount = 3, expectedCount = 3).toJourneyPresentation().operationNotice("ko"))
    }

    private fun decision(journeyCount: Int, expectedCount: Int): RecommendationDecision {
        val primary = item(1, "콘서트장")
        return RecommendationDecision(
            requestId = "request-1",
            item = primary,
            source = DecisionSource.REMOTE,
            decidedAt = Instant.parse("2026-09-17T00:00:00Z"),
            journey = (1..journeyCount).map { index -> JourneyStop(index, "SENSE_$index", item(index, "장소 $index")) },
            expectedJourneyStopCount = expectedCount,
        )
    }

    private fun item(index: Int, title: String) = RecommendationItem(
        id = RecommendationItemId("item-$index"),
        locationId = LocationId("location-$index"),
        title = title,
        imageRef = null,
        supportedEmotions = setOf(EmotionCode("HAPPY")),
    )
}
