package kr.co.ninetyseconds.recommendation.analysis

import kotlin.math.PI
import kotlin.math.sin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class LegacyPosVitalSignalProcessorTest {
    @Test
    fun `synthetic heart and respiration frequencies remain in legacy bands`() {
        val processor = LegacyPosVitalSignalProcessor()
        val results = (0 until 300).mapNotNull { index ->
            val seconds = index / 30.0
            val heart = sin(2.0 * PI * 1.2 * seconds)
            val respiration = sin(2.0 * PI * 0.3 * seconds)
            processor.add(
                RgbSample(
                    red = 100.0 + respiration * 2.0,
                    green = 100.0 + heart,
                    blue = 100.0 - heart,
                    timestampMillis = (seconds * 1000.0).toLong(),
                ),
            )
        }

        assertEquals(10, results.size)
        assertTrue(results.last().heartRateBpm in 68..72)
        assertTrue(results.last().respiratoryRateRpm in 16..18)
        assertEquals("legacy-pos-v1", results.last().algorithmVersion)
    }

    @Test
    fun `reset requires a new minimum sample window`() {
        val processor = LegacyPosVitalSignalProcessor()
        repeat(165) { processor.add(RgbSample(100.0, 100.0, 100.0, it * 34L)) }
        processor.reset()

        val earlyResults = (0 until 150).mapNotNull {
            processor.add(RgbSample(100.0, 100.0, 100.0, it * 34L))
        }

        assertTrue(earlyResults.isEmpty())
    }

    @Test
    fun `non increasing timestamps are rejected`() {
        val processor = LegacyPosVitalSignalProcessor()
        processor.add(RgbSample(1.0, 1.0, 1.0, 100L))

        assertThrows(IllegalArgumentException::class.java) {
            processor.add(RgbSample(1.0, 1.0, 1.0, 100L))
        }
    }
}
