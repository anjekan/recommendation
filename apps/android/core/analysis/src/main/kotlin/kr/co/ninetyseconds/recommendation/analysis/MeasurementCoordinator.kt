package kr.co.ninetyseconds.recommendation.analysis

data class MeasurementResult(
    val emotionLabel: String,
    val stressScore: Int,
    val vital: VitalResult,
)

data class MeasurementProgress(
    val phase: MeasurementPhase,
    val secondsRemaining: Int,
    val result: MeasurementResult? = null,
)

enum class MeasurementPhase {
    WAITING_FOR_FACE,
    MEASURING,
    CALIBRATING,
    COMPLETED,
}

class MeasurementCoordinator(
    private val measurementSeconds: Int = DEFAULT_MEASUREMENT_SECONDS,
    private val calibrationExtensionSeconds: Int = DEFAULT_CALIBRATION_EXTENSION_SECONDS,
) {
    private val emotions = LegacyEmotionAccumulator()
    private var secondsRemaining = measurementSeconds
    private var completed: MeasurementResult? = null

    init {
        require(measurementSeconds > 0) { "Measurement duration must be positive" }
        require(calibrationExtensionSeconds > 0) { "Calibration extension must be positive" }
    }

    fun tick(faceDetected: Boolean, vital: VitalResult?, emotionLabel: String?): MeasurementProgress {
        completed?.let { return MeasurementProgress(MeasurementPhase.COMPLETED, 0, it) }
        if (!faceDetected) return MeasurementProgress(MeasurementPhase.WAITING_FOR_FACE, secondsRemaining)

        emotionLabel?.let(emotions::add)
        secondsRemaining--
        if (secondsRemaining > 0) return MeasurementProgress(MeasurementPhase.MEASURING, secondsRemaining)
        if (vital == null) {
            secondsRemaining = calibrationExtensionSeconds
            return MeasurementProgress(MeasurementPhase.CALIBRATING, secondsRemaining)
        }

        val label = emotions.result()
        val result = MeasurementResult(
            emotionLabel = label,
            stressScore = LegacyStressCalculator.calculate(vital.heartRateBpm, vital.respiratoryRateRpm, label),
            vital = vital,
        )
        completed = result
        return MeasurementProgress(MeasurementPhase.COMPLETED, 0, result)
    }

    fun reset(): MeasurementProgress {
        emotions.reset()
        completed = null
        secondsRemaining = measurementSeconds
        return MeasurementProgress(MeasurementPhase.WAITING_FOR_FACE, secondsRemaining)
    }

    companion object {
        const val DEFAULT_MEASUREMENT_SECONDS = 20
        const val DEFAULT_CALIBRATION_EXTENSION_SECONDS = 5
    }
}
