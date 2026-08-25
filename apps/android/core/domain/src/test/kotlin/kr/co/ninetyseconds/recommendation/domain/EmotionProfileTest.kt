package kr.co.ninetyseconds.recommendation.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class EmotionProfileTest {
    @Test
    fun `dominant returns the highest scored emotion`() {
        val profile = EmotionProfile(
            listOf(
                EmotionScore(EmotionCode("calm"), 0.3),
                EmotionScore(EmotionCode("joy"), 0.8),
            ),
        )

        assertEquals(EmotionCode("joy"), profile.dominant.emotion)
    }

    @Test
    fun `duplicate emotions are rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            EmotionProfile(
                listOf(
                    EmotionScore(EmotionCode("joy"), 0.8),
                    EmotionScore(EmotionCode("joy"), 0.2),
                ),
            )
        }
    }
}
