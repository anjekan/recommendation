package kr.co.ninetyseconds.recommendation.data.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ProjectConfigImporterTest {
    @Test
    fun `imports localized active catalog and theme`() {
        val config = ProjectConfigImporter(currentAppVersion = 1).import(validJson(), preferredLanguage = "en")

        assertEquals("EXPO", config.catalog.projectId.value)
        assertEquals("Generic Expo", config.theme.name)
        assertEquals("Rest Zone", config.catalog.locations.single().title)
        assertEquals(setOf("SERENITY"), config.catalog.items.single().supportedEmotions.map { it.value }.toSet())
        assertEquals("SERENITY", config.mapAnalysisLabel("Neutral").value)
    }

    @Test
    fun `unsupported preferred language falls back to default`() {
        val config = ProjectConfigImporter(currentAppVersion = 1).import(validJson(), preferredLanguage = "ja")

        assertEquals("ko", config.selectedLanguage)
        assertEquals("범용 박람회", config.theme.name)
    }

    @Test
    fun `unknown rule item is rejected`() {
        val invalid = validJson().replace("\"item_id\": \"item-1\"", "\"item_id\": \"missing\"")

        assertThrows(InvalidProjectConfig::class.java) {
            ProjectConfigImporter(currentAppVersion = 1).import(invalid)
        }
    }

    @Test
    fun `newer minimum app version is rejected`() {
        val newer = validJson().replace("\"minimum_app_version\": 1", "\"minimum_app_version\": 2")

        assertThrows(IncompatibleProjectConfig::class.java) {
            ProjectConfigImporter(currentAppVersion = 1).import(newer)
        }
    }

    private fun validJson() =
        """
        {
          "schema_version": 1,
          "config_version": 3,
          "minimum_app_version": 1,
          "project_code": "EXPO",
          "default_language": "ko",
          "supported_languages": ["ko", "en"],
          "theme": {
            "name": {"ko": "범용 박람회", "en": "Generic Expo"},
            "logo_url": null,
            "primary_color": "#112233",
            "background_image_url": "/background.webp",
            "map_image_url": "/map.webp"
          },
          "content": {
            "home_introduction": {"ko":"추천을 시작합니다","en":"Start recommendation"},
            "result_item_label": {"ko":"추천 항목","en":"Recommendation"},
            "map_button_label": {"ko":"지도 보기","en":"View map"},
            "current_location_label": {"ko":"현재 위치","en":"You are here"},
            "map_gesture_hint": {"ko":"지도를 이동하세요","en":"Move the map"}
          },
          "navigation": {
            "origin": {"x_percent": 1.0, "y_percent": 2.0},
            "routes_by_location_code": {"ZONE-1": [{"x_percent": 5.0, "y_percent": 6.0}]}
          },
          "emotion_profiles": [{
            "code": "SERENITY",
            "name": {"ko": "평안", "en": "Serenity"},
            "message": {"ko": "차분합니다", "en": "You are calm"},
            "color": "#445566",
            "icon": "serenity",
            "active": true
          }],
          "analysis_mappings": [
            {"source_label": "Neutral", "emotion_code": "SERENITY"},
            {"source_label": "Happy", "emotion_code": "SERENITY"}
          ],
          "locations": [{
            "id": "location-1",
            "code": "ZONE-1",
            "name": {"ko": "휴식 공간", "en": "Rest Zone"},
            "description": {"ko": "휴식", "en": "Rest"},
            "image_url": "/location.webp",
            "capacity": 100,
            "status": "NORMAL",
            "marker": {"x_percent": 10.0, "y_percent": 20.0},
            "active": true
          }],
          "items": [{
            "id": "item-1",
            "type": "program",
            "location_id": "location-1",
            "name": {"ko": "마음 쉼", "en": "Mindful Rest"},
            "description": {"ko": "설명", "en": "Description"},
            "image_url": "/item.webp",
            "attributes": {},
            "active": true
          }],
          "rules": [{
            "emotion_code": "SERENITY",
            "item_id": "item-1",
            "weight": 100,
            "priority": 10,
            "active": true
          }]
        }
        """.trimIndent()
}
