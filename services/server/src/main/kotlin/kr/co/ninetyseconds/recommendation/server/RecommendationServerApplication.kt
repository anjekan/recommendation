package kr.co.ninetyseconds.recommendation.server

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class RecommendationServerApplication

fun main(args: Array<String>) {
	runApplication<RecommendationServerApplication>(*args)
}
