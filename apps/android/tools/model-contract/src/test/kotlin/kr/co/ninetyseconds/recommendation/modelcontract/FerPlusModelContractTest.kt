package kr.co.ninetyseconds.recommendation.modelcontract

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.TensorInfo
import java.nio.FloatBuffer
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FerPlusModelContractTest {
    @Test
    fun `model checksum input and output contracts match legacy asset`() {
        val model = modelPath()
        assertEquals(EXPECTED_SHA256, sha256(model))
        val environment = OrtEnvironment.getEnvironment()
        environment.createSession(Files.readAllBytes(model)).use { session ->
            assertEquals(setOf(INPUT_NAME), session.inputNames)
            val input = session.inputInfo.getValue(INPUT_NAME).info as TensorInfo
            assertArrayEquals(INPUT_SHAPE, input.shape)
            assertEquals(1, session.outputInfo.size)
            val output = session.outputInfo.values.single().info as TensorInfo
            assertArrayEquals(longArrayOf(1, 8), output.shape)

            OnnxTensor.createTensor(environment, FloatBuffer.wrap(FloatArray(64 * 64) { 127f }), INPUT_SHAPE).use { tensor ->
                session.run(mapOf(INPUT_NAME to tensor)).use { result ->
                    @Suppress("UNCHECKED_CAST")
                    val scores = (result[0].value as Array<FloatArray>)[0]
                    assertEquals(8, scores.size)
                    assertTrue(scores.all(Float::isFinite))
                }
            }
        }
    }

    private fun modelPath(): Path {
        val root = Path.of(System.getProperty("user.dir"))
        val candidates = listOf(
            root.resolve("core/analysis-android/src/main/assets/$MODEL_ASSET"),
            root.resolve("../../core/analysis-android/src/main/assets/$MODEL_ASSET").normalize(),
        )
        return candidates.firstOrNull(Files::exists) ?: error("FER+ model asset not found from $root")
    }

    private fun sha256(path: Path): String = MessageDigest.getInstance("SHA-256")
        .digest(Files.readAllBytes(path))
        .joinToString("") { "%02X".format(it) }

    private companion object {
        const val MODEL_ASSET = "emotion-ferplus-8.onnx"
        const val INPUT_NAME = "Input3"
        val INPUT_SHAPE = longArrayOf(1, 1, 64, 64)
        const val EXPECTED_SHA256 = "A2A2BA6A335A3B29C21ACB6272F962BD3D47F84952AAFFA03B60986E04EFA61C"
    }
}
