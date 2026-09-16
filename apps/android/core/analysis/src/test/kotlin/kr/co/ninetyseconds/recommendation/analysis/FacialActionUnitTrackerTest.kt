package kr.co.ninetyseconds.recommendation.analysis

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FacialActionUnitTrackerTest {
    @Test
    fun `calibrates a relaxed baseline before reporting facial movement`() {
        val tracker = FacialActionUnitTracker(calibrationFrames = 2)
        val relaxed = FacialActionUnitTracker.RELEVANT_BLENDSHAPES.associateWith { .10f }
        val expressive = FacialActionUnitTracker.RELEVANT_BLENDSHAPES.associateWith { .275f }

        assertNull(tracker.add(relaxed))
        assertNull(tracker.add(relaxed))
        assertEquals(50, tracker.add(expressive))
    }

    @Test
    fun `reset requires a fresh calibration`() {
        val tracker = FacialActionUnitTracker(calibrationFrames = 1)
        val relaxed = FacialActionUnitTracker.RELEVANT_BLENDSHAPES.associateWith { 0f }

        assertNull(tracker.add(relaxed))
        tracker.reset()

        assertNull(tracker.add(relaxed))
    }
}
