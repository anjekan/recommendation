package kr.co.ninetyseconds.recommendation.analysis.android

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import java.io.Closeable
import java.nio.FloatBuffer
import kr.co.ninetyseconds.recommendation.analysis.EmotionClassifier
import kr.co.ninetyseconds.recommendation.analysis.EmotionPrediction
import kr.co.ninetyseconds.recommendation.analysis.GrayscaleImage

class OnnxEmotionClassifier private constructor(
    private val environment: OrtEnvironment,
    private val session: OrtSession,
) : EmotionClassifier, Closeable {
    override fun classify(image: GrayscaleImage): EmotionPrediction {
        require(image.width == INPUT_SIZE && image.height == INPUT_SIZE) { "FER+ input must be 64x64" }
        OnnxTensor.createTensor(environment, FloatBuffer.wrap(image.pixels), INPUT_SHAPE).use { tensor ->
            session.run(mapOf(INPUT_NAME to tensor)).use { output ->
                @Suppress("UNCHECKED_CAST")
                val scores = (output[0].value as Array<FloatArray>)[0]
                require(scores.size == LABELS.size) { "FER+ output must contain ${LABELS.size} scores" }
                val best = scores.indices.maxBy { scores[it] }
                return EmotionPrediction(LABELS[best], scores[best], MODEL_VERSION)
            }
        }
    }

    override fun close() = session.close()

    companion object {
        const val MODEL_ASSET = "emotion-ferplus-8.onnx"
        const val MODEL_VERSION = "ferplus-8-a2a2ba6a"
        const val INPUT_NAME = "Input3"
        const val INPUT_SIZE = 64
        val INPUT_SHAPE = longArrayOf(1, 1, INPUT_SIZE.toLong(), INPUT_SIZE.toLong())
        val LABELS = listOf("Neutral", "Happy", "Surprise", "Sad", "Anger", "Disgust", "Fear", "Contempt")

        fun create(context: Context): OnnxEmotionClassifier {
            val environment = OrtEnvironment.getEnvironment()
            val bytes = context.assets.open(MODEL_ASSET).use { it.readBytes() }
            val session = environment.createSession(bytes)
            require(session.inputNames == setOf(INPUT_NAME)) { "Unexpected FER+ input names: ${session.inputNames}" }
            return OnnxEmotionClassifier(environment, session)
        }
    }
}
