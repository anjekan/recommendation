package kr.co.ninetyseconds.recommendation.server.project

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class GetProjectConfigurationTest {
    @Test
    fun `returns active project configuration`() {
        val expected = ProjectConfiguration("EXPO", 3, "{\"config_version\":3}")
        val query = GetProjectConfiguration { expected }

        assertEquals(expected, query("EXPO"))
    }

    @Test
    fun `throws typed error when project does not exist`() {
        val query = GetProjectConfiguration { null }

        assertFailsWith<ProjectNotFoundException> { query("MISSING") }
    }
}
