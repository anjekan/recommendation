package kr.co.ninetyseconds.recommendation.server

import org.junit.jupiter.api.Test
import org.springframework.modulith.core.ApplicationModules

class ApplicationModulesTest {
    @Test
    fun `module dependencies remain valid`() {
        ApplicationModules.of(RecommendationServerApplication::class.java).verify()
    }
}
