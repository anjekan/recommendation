package kr.co.ninetyseconds.recommendation.analysis.android

import android.content.Context
import android.graphics.Bitmap
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarker
import java.io.Closeable
import kr.co.ninetyseconds.recommendation.analysis.NormalizedFace
import kr.co.ninetyseconds.recommendation.analysis.NormalizedPoint
import kr.co.ninetyseconds.recommendation.analysis.PrimaryFaceSelector

interface BitmapFaceDetector {
    fun detect(bitmap: Bitmap): NormalizedFace?
}

class MediaPipeFaceDetector private constructor(
    private val landmarker: FaceLandmarker,
) : BitmapFaceDetector, Closeable {
    override fun detect(bitmap: Bitmap): NormalizedFace? {
        val result = landmarker.detect(BitmapImageBuilder(bitmap).build())
        val faces = result.faceLandmarks().map { landmarks ->
            NormalizedFace(landmarks.map { NormalizedPoint(it.x(), it.y()) })
        }
        return PrimaryFaceSelector.select(faces)
    }

    override fun close() = landmarker.close()

    companion object {
        const val MODEL_ASSET = "face_landmarker.task"

        fun create(context: Context): MediaPipeFaceDetector {
            val options = FaceLandmarker.FaceLandmarkerOptions.builder()
                .setBaseOptions(BaseOptions.builder().setModelAssetPath(MODEL_ASSET).build())
                .setRunningMode(RunningMode.IMAGE)
                .setNumFaces(2)
                .build()
            return MediaPipeFaceDetector(FaceLandmarker.createFromOptions(context, options))
        }
    }
}
