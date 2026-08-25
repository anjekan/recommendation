package kr.co.ninetyseconds.recommendation.analysis

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MeasurementCoordinatorTest {
    @Test
    fun `time does not advance without a face`() {
        val coordinator = MeasurementCoordinator(measurementSeconds = 2)

        val progress = coordinator.tick(faceDetected = false, vital = null, emotionLabel = null)

        assertEquals(MeasurementPhase.WAITING_FOR_FACE, progress.phase)
        assertEquals(2, progress.secondsRemaining)
    }

    @Test
    fun `calibration extends when vital result is not ready`() {
        val coordinator = MeasurementCoordinator(measurementSeconds = 1, calibrationExtensionSeconds = 3)

        val progress = coordinator.tick(faceDetected = true, vital = null, emotionLabel = "Happy")

        assertEquals(MeasurementPhase.CALIBRATING, progress.phase)
        assertEquals(3, progress.secondsRemaining)
        assertNull(progress.result)
    }

    @Test
    fun `completed result uses accumulated emotion and vital signs`() {
        val coordinator = MeasurementCoordinator(measurementSeconds = 2)
        val vital = VitalResult(heartRateBpm = 75, respiratoryRateRpm = 15)

        coordinator.tick(faceDetected = true, vital = null, emotionLabel = "Happy")
        val progress = coordinator.tick(faceDetected = true, vital = vital, emotionLabel = "Happy")

        assertEquals(MeasurementPhase.COMPLETED, progress.phase)
        assertEquals("Happy", progress.result?.emotionLabel)
        assertEquals(42, progress.result?.stressScore)
        assertEquals(vital, progress.result?.vital)
    }

    @Test
    fun `completed state is idempotent until reset`() {
        val coordinator = MeasurementCoordinator(measurementSeconds = 1)
        val vital = VitalResult(70, 12)

        val first = coordinator.tick(true, vital, "Sad")
        val second = coordinator.tick(false, null, "Happy")

        assertEquals(first, second)
        assertEquals(1, coordinator.reset().secondsRemaining)
    }
}
