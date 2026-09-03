package kr.co.ninetyseconds.recommendation.data.config

import kotlinx.serialization.json.Json
import kr.co.ninetyseconds.recommendation.domain.EmotionCode
import kr.co.ninetyseconds.recommendation.domain.EmotionDefinition
import kr.co.ninetyseconds.recommendation.domain.Location
import kr.co.ninetyseconds.recommendation.domain.LocationId
import kr.co.ninetyseconds.recommendation.domain.ProjectCatalogSnapshot
import kr.co.ninetyseconds.recommendation.domain.ProjectConfiguration
import kr.co.ninetyseconds.recommendation.domain.ProjectId
import kr.co.ninetyseconds.recommendation.domain.ProjectTheme
import kr.co.ninetyseconds.recommendation.domain.ProjectContent
import kr.co.ninetyseconds.recommendation.domain.ProjectNavigation
import kr.co.ninetyseconds.recommendation.domain.MapPoint
import kr.co.ninetyseconds.recommendation.domain.RecommendationItem
import kr.co.ninetyseconds.recommendation.domain.RecommendationItemId

class InvalidProjectConfig(message: String) : IllegalArgumentException(message)
class IncompatibleProjectConfig(message: String) : IllegalStateException(message)

class ProjectConfigImporter(
    private val currentAppVersion: Int,
    private val json: Json = Json { ignoreUnknownKeys = false },
) {
    init {
        require(currentAppVersion >= 1) { "Current app version must be positive" }
    }

    fun import(jsonText: String, preferredLanguage: String? = null): ProjectConfiguration {
        val dto = json.decodeFromString<ProjectConfigDto>(jsonText)
        validate(dto)
        if (dto.minimumAppVersion > currentAppVersion) {
            throw IncompatibleProjectConfig(
                "Config requires app version ${dto.minimumAppVersion}, current version is $currentAppVersion",
            )
        }

        val language = preferredLanguage
            ?.takeIf(dto.supportedLanguages::contains)
            ?: dto.defaultLanguage
        val activeLocations = dto.locations.filter { it.active && it.status != "PAUSED" }
        val activeLocationIds = activeLocations.map { it.id }.toSet()
        val activeRulesByItem = dto.rules
            .filter { it.active }
            .groupBy(RuleDto::itemId)
        val activeItems = dto.items.filter { it.active && it.locationId in activeLocationIds }

        val catalog = ProjectCatalogSnapshot(
            projectId = ProjectId(dto.projectCode),
            configVersion = dto.configVersion,
            defaultLanguage = dto.defaultLanguage,
            locations = activeLocations.map { location ->
                Location(
                    id = LocationId(location.id),
                    code = location.code,
                    title = location.name.resolve(language, dto.defaultLanguage),
                    imageRef = location.imageUrl,
                    capacity = location.capacity,
                    markerXPercent = location.marker.xPercent,
                    markerYPercent = location.marker.yPercent,
                )
            },
            items = activeItems.mapNotNull { item ->
                val emotions = activeRulesByItem[item.id].orEmpty()
                    .map { EmotionCode(it.emotionCode) }
                    .toSet()
                if (emotions.isEmpty()) return@mapNotNull null
                RecommendationItem(
                    id = RecommendationItemId(item.id),
                    locationId = LocationId(item.locationId),
                    title = item.name.resolve(language, dto.defaultLanguage),
                    imageRef = item.imageUrl,
                    supportedEmotions = emotions,
                )
            },
        )

        return ProjectConfiguration(
            catalog = catalog,
            theme = ProjectTheme(
                name = dto.theme.name.resolve(language, dto.defaultLanguage),
                logoRef = dto.theme.logoUrl,
                primaryColor = dto.theme.primaryColor,
                backgroundImageRef = dto.theme.backgroundImageUrl,
                mapImageRef = dto.theme.mapImageUrl,
            ),
            content = ProjectContent(
                homeIntroduction = dto.content.homeIntroduction.resolve(language, dto.defaultLanguage),
                resultItemLabel = dto.content.resultItemLabel.resolve(language, dto.defaultLanguage),
                mapButtonLabel = dto.content.mapButtonLabel.resolve(language, dto.defaultLanguage),
                currentLocationLabel = dto.content.currentLocationLabel.resolve(language, dto.defaultLanguage),
                mapGestureHint = dto.content.mapGestureHint.resolve(language, dto.defaultLanguage),
            ),
            navigation = ProjectNavigation(
                origin = MapPoint(dto.navigation.origin.xPercent, dto.navigation.origin.yPercent),
                routesByLocationCode = dto.navigation.routesByLocationCode.mapValues { (_, points) ->
                    points.map { MapPoint(it.xPercent, it.yPercent) }
                },
            ),
            emotions = dto.emotionProfiles.filter(EmotionDto::active).map { emotion ->
                EmotionDefinition(
                    code = EmotionCode(emotion.code),
                    name = emotion.name.resolve(language, dto.defaultLanguage),
                    message = emotion.message.resolve(language, dto.defaultLanguage),
                    color = emotion.color,
                    iconRef = emotion.icon,
                )
            },
            analysisEmotionMappings = dto.analysisMappings.associate { it.sourceLabel to EmotionCode(it.emotionCode) },
            selectedLanguage = language,
            supportedLanguages = dto.supportedLanguages,
        )
    }

    private fun validate(config: ProjectConfigDto) {
        if (config.schemaVersion != 1) throw IncompatibleProjectConfig("Unsupported schema version ${config.schemaVersion}")
        if (config.defaultLanguage !in config.supportedLanguages) invalid("Default language must be supported")
        unique(config.locations.map(LocationDto::id), "location id")
        unique(config.items.map(ItemDto::id), "item id")
        unique(config.emotionProfiles.map(EmotionDto::code), "emotion code")
        unique(config.analysisMappings.map(AnalysisMappingDto::sourceLabel), "analysis mapping source label")
        val locationIds = config.locations.map(LocationDto::id).toSet()
        val itemIds = config.items.map(ItemDto::id).toSet()
        val emotionCodes = config.emotionProfiles.map(EmotionDto::code).toSet()
        if (config.analysisMappings.any { it.emotionCode !in emotionCodes }) invalid("Analysis mapping references an unknown emotion")
        if (config.items.any { it.locationId !in locationIds }) invalid("Item references an unknown location")
        if (config.rules.any { it.itemId !in itemIds }) invalid("Rule references an unknown item")
        if (config.rules.any { it.emotionCode !in emotionCodes }) invalid("Rule references an unknown emotion")
        if (config.rules.any { it.weight < 0 || it.priority < 0 }) invalid("Rule weight and priority cannot be negative")
    }

    private fun unique(values: List<String>, label: String) {
        if (values.distinct().size != values.size) invalid("Duplicate $label")
    }

    private fun invalid(message: String): Nothing = throw InvalidProjectConfig(message)
}

private fun Map<String, String>.resolve(language: String, defaultLanguage: String): String =
    this[language]?.takeIf(String::isNotBlank)
        ?: this[defaultLanguage]?.takeIf(String::isNotBlank)
        ?: values.firstOrNull(String::isNotBlank)
        ?: throw InvalidProjectConfig("Localized text has no non-blank value")
