package kr.co.ninetyseconds.recommendation.analysis

class LegacyEmotionAccumulator {
    private val accepted = mutableListOf<String>()

    fun add(label: String) {
        if (label !in IGNORED_LABELS) accepted += label
    }

    fun result(): String = accepted
        .groupingBy { it }
        .eachCount()
        .maxByOrNull { it.value }
        ?.key
        ?: "Neutral"

    fun reset() = accepted.clear()

    private companion object {
        val IGNORED_LABELS = setOf("Neutral", "Error", "Calibrating...")
    }
}

object LegacyStressCalculator {
    const val ALGORITHM_VERSION = "legacy-stress-v1"

    fun calculate(heartRateBpm: Int, respiratoryRateRpm: Int, emotion: String): Int {
        var score = ((heartRateBpm + respiratoryRateRpm * 2) / 2.5).toInt()
        if (emotion in NEGATIVE_EMOTIONS) score += 15
        return score.coerceIn(0, 100)
    }

    private val NEGATIVE_EMOTIONS = setOf("Anger", "Fear", "Sad")
}
