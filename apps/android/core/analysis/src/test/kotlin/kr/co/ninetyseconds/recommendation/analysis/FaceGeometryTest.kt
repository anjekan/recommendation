package kr.co.ninetyseconds.recommendation.analysis

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FaceGeometryTest {
    @Test
    fun `largest face is selected independent of detector order`() {
        val small = faceWithBox(0.2f, 0.2f, 0.4f, 0.4f)
        val large = faceWithBox(0.1f, 0.1f, 0.8f, 0.8f)

        assertEquals(large, PrimaryFaceSelector.select(listOf(small, large)))
    }

    @Test
    fun `cheek roi averages all rgb pixels`() {
        val color = (255 shl 24) or (120 shl 16) or (80 shl 8) or 40
        val frame = RgbFrame(20, 20, IntArray(400) { color })
        val face = faceWithCheek(0.5f, 0.5f)

        val sample = CheekRgbSampler.sample(frame, face, 1000L)!!

        assertEquals(120.0, sample.red, 0.0)
        assertEquals(80.0, sample.green, 0.0)
        assertEquals(40.0, sample.blue, 0.0)
    }

    @Test
    fun `cheek roi outside frame is ignored`() {
        val frame = RgbFrame(20, 20, IntArray(400))

        assertNull(CheekRgbSampler.sample(frame, faceWithCheek(0.0f, 0.0f), 1L))
    }

    private fun faceWithBox(left: Float, top: Float, right: Float, bottom: Float): NormalizedFace {
        val landmarks = MutableList(124) { NormalizedPoint((left + right) / 2, (top + bottom) / 2) }
        landmarks[0] = NormalizedPoint(left, top)
        landmarks[1] = NormalizedPoint(right, bottom)
        return NormalizedFace(landmarks)
    }

    private fun faceWithCheek(x: Float, y: Float): NormalizedFace {
        val landmarks = MutableList(124) { NormalizedPoint(0.5f, 0.5f) }
        landmarks[CheekRgbSampler.LANDMARK_INDEX] = NormalizedPoint(x, y)
        return NormalizedFace(landmarks)
    }
}
