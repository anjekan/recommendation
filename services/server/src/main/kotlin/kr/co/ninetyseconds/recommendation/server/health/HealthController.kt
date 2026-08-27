package kr.co.ninetyseconds.recommendation.server.health

import java.time.Clock
import java.time.Instant
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

data class HealthResponse(val status: String, val time: Instant)

@RestController
@RequestMapping("/api/v1/health")
class HealthController(private val clock: Clock) {
    @GetMapping
    fun health() = HealthResponse(status = "UP", time = Instant.now(clock))
}

@Configuration
class TimeConfiguration {
    @Bean
    fun systemClock(): Clock = Clock.systemUTC()
}
