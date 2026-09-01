package kr.co.ninetyseconds.recommendation.server.security

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.Customizer.withDefaults
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.core.userdetails.User
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.provisioning.InMemoryUserDetailsManager
import org.springframework.security.web.SecurityFilterChain

@Configuration
class AdminSecurityConfiguration(
    @Value("\${platform.admin.username}") private val username: String,
    @Value("\${platform.admin.password}") private val password: String,
) {
    @Bean
    fun passwordEncoder(): PasswordEncoder = BCryptPasswordEncoder()

    @Bean
    fun adminUsers(passwordEncoder: PasswordEncoder): UserDetailsService {
        require(username.isNotBlank()) { "Admin username must not be blank" }
        require(password.isNotBlank()) { "Admin password must not be blank" }
        val admin = User.withUsername(username)
            .password(passwordEncoder.encode(password))
            .roles("ADMIN")
            .build()
        return InMemoryUserDetailsManager(admin)
    }

    @Bean
    fun adminSecurity(http: HttpSecurity): SecurityFilterChain {
        http.authorizeHttpRequests {
            it.requestMatchers("/admin/**", "/api/v1/admin/**").hasRole("ADMIN")
                .anyRequest().permitAll()
        }
        http.csrf { it.ignoringRequestMatchers("/api/v1/**") }
        http.formLogin { it.defaultSuccessUrl("/admin/index.html", true) }
        http.httpBasic(withDefaults())
        return http.build()
    }
}
