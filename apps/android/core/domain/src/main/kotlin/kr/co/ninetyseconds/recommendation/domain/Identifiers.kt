package kr.co.ninetyseconds.recommendation.domain

private val CODE_PATTERN = Regex("[a-z][a-z0-9_-]*")

@JvmInline
value class ProjectId(val value: String) {
    init { require(value.isNotBlank()) { "ProjectId cannot be blank" } }
}

@JvmInline
value class EmotionCode(val value: String) {
    init { require(value.matches(CODE_PATTERN)) { "EmotionCode must be a lowercase identifier" } }
}

@JvmInline
value class LocationId(val value: String) {
    init { require(value.isNotBlank()) { "LocationId cannot be blank" } }
}

@JvmInline
value class RecommendationItemId(val value: String) {
    init { require(value.isNotBlank()) { "RecommendationItemId cannot be blank" } }
}

@JvmInline
value class SessionId(val value: String) {
    init { require(value.isNotBlank()) { "SessionId cannot be blank" } }
}
