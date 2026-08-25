package kr.co.ninetyseconds.recommendation.analysis

data class RgbSample(
    val red: Double,
    val green: Double,
    val blue: Double,
    val timestampMillis: Long,
)

data class VitalResult(
    val heartRateBpm: Int,
    val respiratoryRateRpm: Int,
    val algorithmVersion: String = LegacyPosVitalSignalProcessor.ALGORITHM_VERSION,
)

interface VitalSignalProcessor {
    fun add(sample: RgbSample): VitalResult?
    fun reset()
}
