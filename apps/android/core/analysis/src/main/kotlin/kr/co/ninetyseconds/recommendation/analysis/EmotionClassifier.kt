package kr.co.ninetyseconds.recommendation.analysis

data class GrayscaleImage(
    val width: Int,
    val height: Int,
    val pixels: FloatArray,
) {
    init {
        require(width > 0 && height > 0) { "Image dimensions must be positive" }
        require(pixels.size == width * height) { "Pixel count must match image dimensions" }
    }
}

data class EmotionPrediction(
    val label: String,
    val confidence: Float,
    val modelVersion: String,
)

fun interface EmotionClassifier {
    fun classify(image: GrayscaleImage): EmotionPrediction
}
