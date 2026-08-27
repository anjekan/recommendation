package kr.co.ninetyseconds.recommendation.domain

data class ProjectTheme(
    val name: String,
    val logoRef: String?,
    val primaryColor: String,
    val backgroundImageRef: String,
    val mapImageRef: String,
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
    val emotions: List<EmotionDefinition>,
    val analysisEmotionMappings: Map<String, EmotionCode>,
    val selectedLanguage: String,
) {
    fun mapAnalysisLabel(label: String): EmotionCode = analysisEmotionMappings[label]
        ?: throw IllegalArgumentException("No project emotion mapping for analysis label: $label")

    fun mapAnalysisLabel(label: String, stressScore: Int): EmotionCode {
        require(stressScore in 0..100) { "Stress score must be between 0 and 100" }
        val available = emotions.map { it.code.value }.toSet()
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

    private companion object {
        val POSITIVE_LABELS = setOf("Happy", "Surprise")
    }
}
