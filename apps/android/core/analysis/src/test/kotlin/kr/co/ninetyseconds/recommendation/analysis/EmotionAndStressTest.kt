package kr.co.ninetyseconds.recommendation.analysis

import org.junit.Assert.assertEquals
import org.junit.Test

class EmotionAndStressTest {
    @Test
    fun `neutral and non-result labels are excluded from majority`() {
        val accumulator = LegacyEmotionAccumulator()
        listOf("Neutral", "Error", "Calibrating...", "Happy", "Sad", "Happy").forEach(accumulator::add)

        assertEquals("Happy", accumulator.result())
    }

    @Test
    fun `empty accumulator preserves legacy neutral fallback`() {
        assertEquals("Neutral", LegacyEmotionAccumulator().result())
    }

    @Test
    fun `negative emotion adds legacy stress penalty`() {
        assertEquals(41, LegacyStressCalculator.calculate(70, 17, "Happy"))
        assertEquals(56, LegacyStressCalculator.calculate(70, 17, "Anger"))
    }

    @Test
    fun `stress is clamped to percentage range`() {
        assertEquals(100, LegacyStressCalculator.calculate(170, 35, "Fear"))
        assertEquals(0, LegacyStressCalculator.calculate(0, 0, "Neutral"))
    }
}
