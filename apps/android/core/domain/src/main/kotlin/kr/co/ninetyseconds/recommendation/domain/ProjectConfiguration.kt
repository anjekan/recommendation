package kr.co.ninetyseconds.recommendation.domain

data class ProjectTheme(
    val name: String,
    val logoRef: String?,
    val primaryColor: String,
    val backgroundImageRef: String,
    val mapImageRef: String,
)

data class ProjectContent(
    val homeIntroduction: String,
    val resultItemLabel: String,
    val mapButtonLabel: String,
    val currentLocationLabel: String,
    val mapGestureHint: String,
)

data class MapPoint(val xPercent: Double, val yPercent: Double)

data class ProjectNavigation(
    val origin: MapPoint,
    val routesByLocationCode: Map<String, List<MapPoint>>,
)

data class EmotionDefinition(
    val code: EmotionCode,
    val name: String,
    val message: String,
    val color: String,
    val iconRef: String?,
)

data class ProjectConfiguration(
    val catalog: ProjectCatalogSnapshot,
    val theme: ProjectTheme,
    val content: ProjectContent,
    val navigation: ProjectNavigation,
    val emotions: List<EmotionDefinition>,
    val analysisEmotionMappings: Map<String, EmotionCode>,
    val selectedLanguage: String,
    val supportedLanguages: List<String>,
) {
    fun mapAnalysisLabel(label: String): EmotionCode = analysisEmotionMappings[label]
        ?: throw IllegalArgumentException("No project emotion mapping for analysis label: $label")

    fun mapAnalysisLabel(label: String, stressScore: Int): EmotionCode {
        require(stressScore in 0..100) { "Stress score must be between 0 and 100" }
        val available = emotions.map { it.code.value }.toSet()

        if (available.containsAll(RICH_CONDITION_CODES)) {
            val stressBand = when {
                stressScore <= 35 -> StressBand.LOW
                stressScore <= 55 -> StressBand.NORMAL
                stressScore <= 70 -> StressBand.MODERATELY_HIGH
                else -> StressBand.HIGH
            }
            val tensionBand = when (label) {
                "Happy" -> TensionBand.LOW
                "Neutral", "Surprise" -> TensionBand.MEDIUM
                else -> TensionBand.HIGH
            }
            return EmotionCode(RICH_CONDITION_BY_BANDS.getValue(stressBand to tensionBand))
        }

        val flowerCode = when {
            stressScore <= 35 && label == "Neutral" -> "SERENITY"
            stressScore <= 35 && label == "Happy" -> "RELAXED"
            stressScore <= 35 -> "STABILITY"
            stressScore <= 55 && label in POSITIVE_LABELS -> "JOY"
            stressScore <= 55 -> "CALM"
            stressScore <= 70 && label in POSITIVE_LABELS -> "VITALITY"
            stressScore <= 70 && label == "Neutral" -> "FOCUS"
            stressScore <= 70 -> "IMMERSION"
            label in POSITIVE_LABELS -> "PASSION"
            else -> "ELEVATION"
        }
        return if (flowerCode in available) EmotionCode(flowerCode) else mapAnalysisLabel(label)
    }

    private enum class StressBand { LOW, NORMAL, MODERATELY_HIGH, HIGH }
    private enum class TensionBand { LOW, MEDIUM, HIGH }

    private companion object {
        val POSITIVE_LABELS = setOf("Happy", "Surprise")
        val RICH_CONDITION_CODES = setOf(
            "JOY", "EXCITED", "THRILL", "INTEREST", "TENSION", "HEAVINESS",
            "LOW_ENERGY", "DROWSY", "LOOSE", "COMFORT", "STABLE", "LEISURE",
        )
        val RICH_CONDITION_BY_BANDS = mapOf(
            (StressBand.LOW to TensionBand.LOW) to "LOOSE",
            (StressBand.LOW to TensionBand.MEDIUM) to "COMFORT",
            (StressBand.LOW to TensionBand.HIGH) to "DROWSY",
            (StressBand.NORMAL to TensionBand.LOW) to "STABLE",
            (StressBand.NORMAL to TensionBand.MEDIUM) to "INTEREST",
            (StressBand.NORMAL to TensionBand.HIGH) to "LOW_ENERGY",
            (StressBand.MODERATELY_HIGH to TensionBand.LOW) to "LEISURE",
            (StressBand.MODERATELY_HIGH to TensionBand.MEDIUM) to "THRILL",
            (StressBand.MODERATELY_HIGH to TensionBand.HIGH) to "HEAVINESS",
            (StressBand.HIGH to TensionBand.LOW) to "JOY",
            (StressBand.HIGH to TensionBand.MEDIUM) to "EXCITED",
            (StressBand.HIGH to TensionBand.HIGH) to "TENSION",
        )
    }
}
