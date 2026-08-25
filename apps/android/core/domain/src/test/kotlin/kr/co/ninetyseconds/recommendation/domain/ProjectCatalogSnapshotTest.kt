package kr.co.ninetyseconds.recommendation.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ProjectCatalogSnapshotTest {
    @Test
    fun `contract style uppercase emotion codes are valid`() {
        assertEquals("SERENITY", EmotionCode("SERENITY").value)
    }

    @Test
    fun `item cannot reference a location outside its catalog`() {
        assertThrows(IllegalArgumentException::class.java) {
            ProjectCatalogSnapshot(
                projectId = ProjectId("EXPO"),
                configVersion = 1,
                defaultLanguage = "ko",
                locations = emptyList(),
                items = listOf(
                    RecommendationItem(
                        id = RecommendationItemId("item-1"),
                        locationId = LocationId("missing"),
                        title = "Item",
                        imageRef = null,
                        supportedEmotions = setOf(EmotionCode("SERENITY")),
                    ),
                ),
            )
        }
    }
}
