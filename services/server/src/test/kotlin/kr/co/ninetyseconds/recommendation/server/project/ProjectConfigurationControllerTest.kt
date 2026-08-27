package kr.co.ninetyseconds.recommendation.server.project

import kotlin.test.Test
import kotlin.test.assertEquals
import org.springframework.http.HttpStatus

class ProjectConfigurationControllerTest {
    private val config = ProjectConfiguration("EXPO", 7, "{\"project_code\":\"EXPO\"}")
    private val controller = ProjectConfigurationController(GetProjectConfiguration { config })

    @Test
    fun `returns json configuration with version etag`() {
        val response = controller.getConfig("EXPO", null)

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals("\"EXPO-7\"", response.headers.eTag)
        assertEquals(config.json, response.body)
    }

    @Test
    fun `returns not modified when etag matches`() {
        val response = controller.getConfig("EXPO", "\"EXPO-7\"")

        assertEquals(HttpStatus.NOT_MODIFIED, response.statusCode)
    }
}
