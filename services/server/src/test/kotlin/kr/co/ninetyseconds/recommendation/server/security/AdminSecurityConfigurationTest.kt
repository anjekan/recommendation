package kr.co.ninetyseconds.recommendation.server.security

import kotlin.test.Test
import kotlin.test.assertTrue
import org.springframework.security.core.userdetails.UserDetailsService

class AdminSecurityConfigurationTest {
    @Test
    fun `creates configured admin with encoded password and admin role`() {
        val configuration = AdminSecurityConfiguration("operator", "change-me")
        val encoder = configuration.passwordEncoder()
        val users: UserDetailsService = configuration.adminUsers(encoder)
        val admin = users.loadUserByUsername("operator")

        assertTrue(encoder.matches("change-me", admin.password))
        assertTrue(admin.authorities.any { it.authority == "ROLE_ADMIN" })
    }
}
