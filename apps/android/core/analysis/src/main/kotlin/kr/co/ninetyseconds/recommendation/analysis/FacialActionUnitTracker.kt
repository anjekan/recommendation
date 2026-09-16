package kr.co.ninetyseconds.recommendation.analysis

import kotlin.math.abs
import kotlin.math.roundToInt

/** Measures facial movement relative to the participant's initial relaxed expression. */
class FacialActionUnitTracker(
    private val calibrationFrames: Int = DEFAULT_CALIBRATION_FRAMES,
) {
    private val calibrationSums = RELEVANT_BLENDSHAPES.associateWith { 0f }.toMutableMap()
    private val baseline = mutableMapOf<String, Float>()
    private var collectedFrames = 0

    init { require(calibrationFrames > 0) { "Calibration frames must be positive" } }

    fun add(blendshapes: Map<String, Float>): Int? {
        if (blendshapes.isEmpty()) return null
        if (collectedFrames < calibrationFrames) {
            RELEVANT_BLENDSHAPES.forEach { name ->
                calibrationSums[name] = calibrationSums.getValue(name) + blendshapes.getOrDefault(name, 0f)
            }
            collectedFrames++
            if (collectedFrames == calibrationFrames) {
                RELEVANT_BLENDSHAPES.forEach { name ->
                    baseline[name] = calibrationSums.getValue(name) / calibrationFrames
                }
            }
            return null
        }

        val strongestChanges = RELEVANT_BLENDSHAPES
            .map { name -> abs(blendshapes.getOrDefault(name, 0f) - baseline.getOrDefault(name, 0f)) }
            .sortedDescending()
            .take(STRONGEST_UNIT_COUNT)
        if (strongestChanges.isEmpty()) return 0
        val meanChange = strongestChanges.average().toFloat()
        return (meanChange / FULL_SCALE_CHANGE * 100f).roundToInt().coerceIn(0, 100)
    }

    fun reset() {
        calibrationSums.keys.forEach { calibrationSums[it] = 0f }
        baseline.clear()
        collectedFrames = 0
    }

    companion object {
        const val DEFAULT_CALIBRATION_FRAMES = 30
        private const val STRONGEST_UNIT_COUNT = 5
        private const val FULL_SCALE_CHANGE = .35f
        val RELEVANT_BLENDSHAPES = listOf(
            "browDownLeft", "browDownRight", "browInnerUp", "browOuterUpLeft", "browOuterUpRight",
            "cheekSquintLeft", "cheekSquintRight", "eyeSquintLeft", "eyeSquintRight", "jawOpen",
            "mouthFrownLeft", "mouthFrownRight", "mouthPressLeft", "mouthPressRight",
            "mouthPucker", "mouthSmileLeft", "mouthSmileRight", "mouthStretchLeft", "mouthStretchRight",
        )
    }
}
