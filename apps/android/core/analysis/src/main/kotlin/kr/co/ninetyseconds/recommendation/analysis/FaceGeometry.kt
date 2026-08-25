package kr.co.ninetyseconds.recommendation.analysis

data class NormalizedPoint(val x: Float, val y: Float)

data class NormalizedFace(
    val landmarks: List<NormalizedPoint>,
) {
    init { require(landmarks.isNotEmpty()) { "Face must contain landmarks" } }

    val box: NormalizedBox by lazy {
        NormalizedBox(
            left = landmarks.minOf { it.x },
            top = landmarks.minOf { it.y },
            right = landmarks.maxOf { it.x },
            bottom = landmarks.maxOf { it.y },
        )
    }
}

data class NormalizedBox(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
    val area: Float get() = (right - left).coerceAtLeast(0f) * (bottom - top).coerceAtLeast(0f)
}

object PrimaryFaceSelector {
    fun select(faces: List<NormalizedFace>): NormalizedFace? = faces.maxByOrNull { it.box.area }
}

data class RgbFrame(
    val width: Int,
    val height: Int,
    val argbPixels: IntArray,
) {
    init {
        require(width > 0 && height > 0) { "Frame dimensions must be positive" }
        require(argbPixels.size == width * height) { "Pixel count must match frame dimensions" }
    }
}

object CheekRgbSampler {
    const val LANDMARK_INDEX = 123
    const val ROI_SIZE = 10

    fun sample(frame: RgbFrame, face: NormalizedFace, timestampMillis: Long): RgbSample? {
        val point = face.landmarks.getOrNull(LANDMARK_INDEX) ?: return null
        val centerX = (point.x * frame.width).toInt()
        val centerY = (point.y * frame.height).toInt()
        val half = ROI_SIZE / 2
        if (centerX < half || centerX >= frame.width - half || centerY < half || centerY >= frame.height - half) return null
        var red = 0L
        var green = 0L
        var blue = 0L
        for (y in centerY - half until centerY + half) {
            for (x in centerX - half until centerX + half) {
                val color = frame.argbPixels[y * frame.width + x]
                red += (color shr 16) and 0xFF
                green += (color shr 8) and 0xFF
                blue += color and 0xFF
            }
        }
        val count = ROI_SIZE * ROI_SIZE.toDouble()
        return RgbSample(red / count, green / count, blue / count, timestampMillis)
    }
}
