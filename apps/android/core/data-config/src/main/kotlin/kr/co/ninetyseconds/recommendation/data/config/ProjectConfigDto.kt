package kr.co.ninetyseconds.recommendation.data.config

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
internal data class ProjectConfigDto(
    @SerialName("schema_version") val schemaVersion: Int,
    @SerialName("config_version") val configVersion: Long,
    @SerialName("minimum_app_version") val minimumAppVersion: Int,
    @SerialName("project_code") val projectCode: String,
    @SerialName("default_language") val defaultLanguage: String,
    @SerialName("supported_languages") val supportedLanguages: List<String>,
    val theme: ThemeDto,
    val content: ContentDto,
    val navigation: NavigationDto,
    @SerialName("emotion_profiles") val emotionProfiles: List<EmotionDto>,
    @SerialName("analysis_mappings") val analysisMappings: List<AnalysisMappingDto>,
    val locations: List<LocationDto>,
    val items: List<ItemDto>,
    val rules: List<RuleDto>,
)

@Serializable
internal data class ContentDto(
    @SerialName("home_introduction") val homeIntroduction: Map<String, String>,
    @SerialName("result_item_label") val resultItemLabel: Map<String, String>,
    @SerialName("map_button_label") val mapButtonLabel: Map<String, String>,
    @SerialName("current_location_label") val currentLocationLabel: Map<String, String>,
    @SerialName("map_gesture_hint") val mapGestureHint: Map<String, String>,
)

@Serializable
internal data class NavigationDto(
    val origin: MarkerDto,
    @SerialName("routes_by_location_code") val routesByLocationCode: Map<String, List<MarkerDto>>,
)

@Serializable
internal data class AnalysisMappingDto(
    @SerialName("source_label") val sourceLabel: String,
    @SerialName("emotion_code") val emotionCode: String,
)

@Serializable
internal data class ThemeDto(
    val name: Map<String, String>,
    @SerialName("logo_url") val logoUrl: String? = null,
    @SerialName("primary_color") val primaryColor: String,
    @SerialName("background_image_url") val backgroundImageUrl: String,
    @SerialName("map_image_url") val mapImageUrl: String,
)

@Serializable
internal data class EmotionDto(
    val code: String,
    val name: Map<String, String>,
    val message: Map<String, String>,
    val color: String,
    val icon: String? = null,
    val active: Boolean,
)

@Serializable
internal data class MarkerDto(
    @SerialName("x_percent") val xPercent: Double,
    @SerialName("y_percent") val yPercent: Double,
)

@Serializable
internal data class LocationDto(
    val id: String,
    val code: String,
    val name: Map<String, String>,
    val description: Map<String, String> = emptyMap(),
    @SerialName("image_url") val imageUrl: String? = null,
    val capacity: Int? = null,
    val status: String,
    val marker: MarkerDto,
    val active: Boolean,
)

@Serializable
internal data class ItemDto(
    val id: String,
    val type: String,
    @SerialName("location_id") val locationId: String,
    val name: Map<String, String>,
    val description: Map<String, String>,
    @SerialName("image_url") val imageUrl: String,
    val attributes: JsonObject = JsonObject(emptyMap()),
    val active: Boolean,
)

@Serializable
internal data class RuleDto(
    @SerialName("emotion_code") val emotionCode: String,
    @SerialName("item_id") val itemId: String,
    val weight: Int,
    val priority: Int,
    val active: Boolean,
)
