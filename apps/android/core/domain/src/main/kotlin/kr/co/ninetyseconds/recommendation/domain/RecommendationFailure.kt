package kr.co.ninetyseconds.recommendation.domain

sealed class RecommendationFailure(message: String, cause: Throwable? = null) : Exception(message, cause)

class RecommendationUnavailable(message: String, cause: Throwable? = null) :
    RecommendationFailure(message, cause)

class RecommendationRejected(message: String) : RecommendationFailure(message)
