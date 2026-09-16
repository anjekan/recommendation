package kr.co.ninetyseconds.recommendation.analysis

data class MeasurementResult(
    val emotionLabel: String,
    val stressScore: Int,
    val vital: VitalResult,
    val actionUnitPercent: Int,
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
    private val actionUnitSamples = mutableListOf<Int>()
    private var secondsRemaining = measurementSeconds
    private var completed: MeasurementResult? = null

    init {
        require(measurementSeconds > 0) { "Measurement duration must be positive" }
        require(calibrationExtensionSeconds > 0) { "Calibration extension must be positive" }
    }

    fun tick(
        faceDetected: Boolean,
        vital: VitalResult?,
        emotionLabel: String?,
        actionUnitPercent: Int? = null,
    ): MeasurementProgress {
        completed?.let { return MeasurementProgress(MeasurementPhase.COMPLETED, 0, it) }
        if (!faceDetected) return MeasurementProgress(MeasurementPhase.WAITING_FOR_FACE, secondsRemaining)

        emotionLabel?.let(emotions::add)
        actionUnitPercent?.let { actionUnitSamples += it.coerceIn(0, 100) }
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
            actionUnitPercent = actionUnitSamples.medianOrZero(),
        )
        completed = result
        return MeasurementProgress(MeasurementPhase.COMPLETED, 0, result)
    }

    fun reset(): MeasurementProgress {
        emotions.reset()
        actionUnitSamples.clear()
        completed = null
        secondsRemaining = measurementSeconds
        return MeasurementProgress(MeasurementPhase.WAITING_FOR_FACE, secondsRemaining)
    }

    private fun List<Int>.medianOrZero(): Int {
        if (isEmpty()) return 0
        val sorted = sorted()
        val middle = sorted.size / 2
        return if (sorted.size % 2 == 0) (sorted[middle - 1] + sorted[middle]) / 2 else sorted[middle]
    }

    companion object {
        const val DEFAULT_MEASUREMENT_SECONDS = 20
        const val DEFAULT_CALIBRATION_EXTENSION_SECONDS = 5
    }
}
