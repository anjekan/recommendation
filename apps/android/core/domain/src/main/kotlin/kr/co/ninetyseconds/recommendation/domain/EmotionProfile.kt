package kr.co.ninetyseconds.recommendation.domain

data class EmotionScore(val emotion: EmotionCode, val score: Double) {
    init { require(score in 0.0..1.0) { "Emotion score must be between 0 and 1" } }
}

data class EmotionProfile(val scores: List<EmotionScore>) {
    init {
        require(scores.isNotEmpty()) { "Emotion profile cannot be empty" }
        require(scores.map { it.emotion }.distinct().size == scores.size) {
            "Emotion profile cannot contain duplicate emotions"
        }
    }

    val dominant: EmotionScore
        get() = scores.maxBy { it.score }
}
