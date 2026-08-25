package kr.co.ninetyseconds.recommendation.analysis.android

import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import java.io.Closeable
import kr.co.ninetyseconds.recommendation.analysis.CheekRgbSampler
import kr.co.ninetyseconds.recommendation.analysis.EmotionClassifier
import kr.co.ninetyseconds.recommendation.analysis.EmotionPrediction
import kr.co.ninetyseconds.recommendation.analysis.LegacyPosVitalSignalProcessor
import kr.co.ninetyseconds.recommendation.analysis.NormalizedBox
import kr.co.ninetyseconds.recommendation.analysis.NormalizedFace
import kr.co.ninetyseconds.recommendation.analysis.RgbFrame
import kr.co.ninetyseconds.recommendation.analysis.VitalResult
import kr.co.ninetyseconds.recommendation.analysis.VitalSignalProcessor

data class MeasurementSnapshot(
    val faceDetected: Boolean,
    val faceBox: NormalizedBox?,
    val vital: VitalResult?,
    val emotion: EmotionPrediction?,
    val timestampMillis: Long,
)

class MeasurementFrameAnalyzer(
    private val faceDetector: BitmapFaceDetector,
    private val emotionClassifier: EmotionClassifier,
    private val listener: (MeasurementSnapshot) -> Unit,
    private val vitalProcessor: VitalSignalProcessor = LegacyPosVitalSignalProcessor(),
) : ImageAnalysis.Analyzer, Closeable {
    private var frameCount = 0
    private var lastVital: VitalResult? = null
    private var lastEmotion: EmotionPrediction? = null

    override fun analyze(image: ImageProxy) {
        var rotated: Bitmap? = null
        try {
            val source = image.toBitmap()
            rotated = source.rotate(image.imageInfo.rotationDegrees)
            if (rotated !== source) source.recycle()
            val timestampMillis = image.imageInfo.timestamp / 1_000_000L
            val face = faceDetector.detect(rotated)
            if (face == null) {
                listener(MeasurementSnapshot(false, null, lastVital, lastEmotion, timestampMillis))
                return
            }

            val pixels = IntArray(rotated.width * rotated.height)
            rotated.getPixels(pixels, 0, rotated.width, 0, 0, rotated.width, rotated.height)
            CheekRgbSampler.sample(RgbFrame(rotated.width, rotated.height, pixels), face, timestampMillis)
                ?.let(vitalProcessor::add)
                ?.let { lastVital = it }

            frameCount = (frameCount + 1) % FRAME_COUNTER_LIMIT
            if (frameCount % EMOTION_INTERVAL == 0) {
                cropLegacyFace(rotated, face)?.useBitmap { crop ->
                    lastEmotion = emotionClassifier.classify(BitmapGrayscalePreprocessor.preprocess(crop))
                }
            }
            listener(MeasurementSnapshot(true, face.box, lastVital, lastEmotion, timestampMillis))
        } finally {
            rotated?.recycle()
            image.close()
        }
    }

    fun reset() {
        frameCount = 0
        lastVital = null
        lastEmotion = null
        vitalProcessor.reset()
    }

    override fun close() {
        (faceDetector as? Closeable)?.close()
        (emotionClassifier as? Closeable)?.close()
    }

    private fun Bitmap.rotate(degrees: Int): Bitmap {
        if (degrees == 0) return this
        val matrix = Matrix().apply { postRotate(degrees.toFloat()) }
        return Bitmap.createBitmap(this, 0, 0, width, height, matrix, true)
    }

    private fun cropLegacyFace(bitmap: Bitmap, face: NormalizedFace): Bitmap? {
        val cheek = face.landmarks.getOrNull(CheekRgbSampler.LANDMARK_INDEX) ?: return null
        val centerX = (cheek.x * bitmap.width).toInt()
        val centerY = (cheek.y * bitmap.height).toInt()
        val left = (centerX - FACE_CROP_SIZE / 2).coerceAtLeast(0)
        val top = (centerY - FACE_CROP_SIZE / 2).coerceAtLeast(0)
        val right = (left + FACE_CROP_SIZE).coerceAtMost(bitmap.width)
        val bottom = (top + FACE_CROP_SIZE).coerceAtMost(bitmap.height)
        if (right <= left || bottom <= top) return null
        return Bitmap.createBitmap(bitmap, left, top, right - left, bottom - top)
    }

    private inline fun Bitmap.useBitmap(block: (Bitmap) -> Unit) {
        try { block(this) } finally { recycle() }
    }

    private companion object {
        const val EMOTION_INTERVAL = 30
        const val FRAME_COUNTER_LIMIT = 10_000
        const val FACE_CROP_SIZE = 300
    }
}
