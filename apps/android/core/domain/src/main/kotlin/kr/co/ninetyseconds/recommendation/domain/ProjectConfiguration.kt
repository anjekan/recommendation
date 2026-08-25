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
}
