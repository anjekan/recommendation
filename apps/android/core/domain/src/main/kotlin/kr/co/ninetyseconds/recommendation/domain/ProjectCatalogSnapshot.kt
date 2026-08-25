package kr.co.ninetyseconds.recommendation.domain

data class Location(
    val id: LocationId,
    val code: String,
    val title: String,
    val imageRef: String?,
    val capacity: Int?,
    val enabled: Boolean = true,
) {
    init {
        require(code.isNotBlank()) { "Location code cannot be blank" }
        require(title.isNotBlank()) { "Location title cannot be blank" }
        require(capacity == null || capacity >= 0) { "Location capacity cannot be negative" }
    }
}

data class ProjectCatalogSnapshot(
    val projectId: ProjectId,
    val configVersion: Long,
    val defaultLanguage: String,
    val locations: List<Location>,
    val items: List<RecommendationItem>,
) {
    init {
        require(configVersion >= 1) { "Config version must be positive" }
        require(defaultLanguage.isNotBlank()) { "Default language cannot be blank" }
        val locationIds = locations.map { it.id }.toSet()
        require(items.all { it.locationId in locationIds }) {
            "Every recommendation item must reference a catalog location"
        }
    }
}
