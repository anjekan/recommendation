package kr.co.ninetyseconds.recommendation.analysis.android

import android.graphics.Bitmap
import kr.co.ninetyseconds.recommendation.analysis.GrayscaleImage

object BitmapGrayscalePreprocessor {
    const val TARGET_SIZE = 64

    fun preprocess(bitmap: Bitmap): GrayscaleImage {
        val scaled = Bitmap.createScaledBitmap(bitmap, TARGET_SIZE, TARGET_SIZE, true)
        val colors = IntArray(TARGET_SIZE * TARGET_SIZE)
        scaled.getPixels(colors, 0, TARGET_SIZE, 0, 0, TARGET_SIZE, TARGET_SIZE)
        val grayscale = FloatArray(colors.size) { index ->
            val color = colors[index]
            val red = (color shr 16) and 0xFF
            val green = (color shr 8) and 0xFF
            val blue = color and 0xFF
            0.299f * red + 0.587f * green + 0.114f * blue
        }
        if (scaled !== bitmap) scaled.recycle()
        return GrayscaleImage(TARGET_SIZE, TARGET_SIZE, grayscale)
    }
}
