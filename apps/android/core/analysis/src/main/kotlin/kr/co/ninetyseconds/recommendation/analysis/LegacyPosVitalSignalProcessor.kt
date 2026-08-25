package kr.co.ninetyseconds.recommendation.analysis

import java.util.ArrayDeque
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

class LegacyPosVitalSignalProcessor : VitalSignalProcessor {
    private val samples = ArrayDeque<RgbSample>(BUFFER_SIZE)
    private val bpmHistory = ArrayDeque<Int>(HISTORY_SIZE)
    private val respiratoryHistory = ArrayDeque<Int>(HISTORY_SIZE)

    override fun reset() {
        samples.clear()
        bpmHistory.clear()
        respiratoryHistory.clear()
    }

    override fun add(sample: RgbSample): VitalResult? {
        require(sample.red.isFinite() && sample.green.isFinite() && sample.blue.isFinite()) {
            "RGB values must be finite"
        }
        require(samples.lastOrNull()?.timestampMillis?.let { sample.timestampMillis > it } != false) {
            "Sample timestamps must be strictly increasing"
        }
        if (samples.size >= BUFFER_SIZE) samples.removeFirst()
        samples.addLast(sample)
        return if (samples.size > MINIMUM_SAMPLES && samples.size % CALCULATION_INTERVAL == 0) calculate() else null
    }

    private fun calculate(): VitalResult {
        val values = samples.toList()
        val durationSeconds = (values.last().timestampMillis - values.first().timestampMillis) / 1000.0
        val framesPerSecond = values.size / durationSeconds
        if (framesPerSecond !in 10.0..60.0) return VitalResult(0, 0)

        val redMean = values.sumOf(RgbSample::red) / values.size
        val greenMean = values.sumOf(RgbSample::green) / values.size
        val blueMean = values.sumOf(RgbSample::blue) / values.size
        val x = DoubleArray(values.size)
        val y = DoubleArray(values.size)
        val normalizedRed = DoubleArray(values.size)

        values.forEachIndexed { index, sample ->
            val red = sample.red / (redMean + EPSILON)
            val green = sample.green / (greenMean + EPSILON)
            val blue = sample.blue / (blueMean + EPSILON)
            x[index] = green - blue
            y[index] = green + blue - 2.0 * red
            normalizedRed[index] = red - 1.0
        }

        val alpha = standardDeviation(x) / (standardDeviation(y) + EPSILON)
        val heartSignal = DoubleArray(values.size) { x[it] + alpha * y[it] }
        removeMean(heartSignal)
        removeMean(normalizedRed)
        val (frequencies, heartMagnitudes) = fft(movingAverage(heartSignal), framesPerSecond)
        val (_, respiratoryMagnitudes) = fft(movingAverage(normalizedRed), framesPerSecond)
        val rawHeartRate = peakRate(frequencies, heartMagnitudes, HEART_HZ_RANGE)
        val rawRespiratoryRate = peakRate(frequencies, respiratoryMagnitudes, RESPIRATORY_HZ_RANGE)

        if (rawHeartRate in 55..170) bpmHistory.addBounded(rawHeartRate)
        if (rawRespiratoryRate in 10..35) respiratoryHistory.addBounded(rawRespiratoryRate)
        return VitalResult(
            heartRateBpm = bpmHistory.averageOrZero(),
            respiratoryRateRpm = respiratoryHistory.averageOrZero(),
        )
    }

    private fun peakRate(frequencies: DoubleArray, magnitudes: DoubleArray, range: ClosedFloatingPointRange<Double>): Int {
        var maximum = -1.0
        var peak = 0.0
        frequencies.indices.forEach { index ->
            if (frequencies[index] in range && magnitudes[index] > maximum) {
                maximum = magnitudes[index]
                peak = frequencies[index]
            }
        }
        return (peak * 60.0).toInt()
    }

    private fun standardDeviation(data: DoubleArray): Double {
        val mean = data.average()
        return sqrt(data.sumOf { (it - mean) * (it - mean) } / data.size)
    }

    private fun removeMean(data: DoubleArray) {
        val mean = data.average()
        data.indices.forEach { data[it] -= mean }
    }

    private fun movingAverage(data: DoubleArray, window: Int = 5): DoubleArray = DoubleArray(data.size) { index ->
        val start = (index - window / 2).coerceAtLeast(0)
        val end = (index + window / 2).coerceAtMost(data.lastIndex)
        (start..end).sumOf { data[it] } / (end - start + 1)
    }

    private fun fft(data: DoubleArray, framesPerSecond: Double): Pair<DoubleArray, DoubleArray> {
        var size = 1
        while (size < data.size) size *= 2
        val real = DoubleArray(size)
        val imaginary = DoubleArray(size)
        data.copyInto(real)
        var reversed = 0
        for (index in 0 until size - 1) {
            if (index < reversed) {
                real.swap(index, reversed)
                imaginary.swap(index, reversed)
            }
            var half = size / 2
            while (half <= reversed) {
                reversed -= half
                half /= 2
            }
            reversed += half
        }
        var length = 1
        while (length < size) {
            val step = length * 2
            val cosine = cos(-Math.PI / length)
            val sine = sin(-Math.PI / length)
            var weightReal = 1.0
            var weightImaginary = 0.0
            for (offset in 0 until length) {
                for (index in offset until size step step) {
                    val paired = index + length
                    val transformedReal = weightReal * real[paired] - weightImaginary * imaginary[paired]
                    val transformedImaginary = weightReal * imaginary[paired] + weightImaginary * real[paired]
                    real[paired] = real[index] - transformedReal
                    imaginary[paired] = imaginary[index] - transformedImaginary
                    real[index] += transformedReal
                    imaginary[index] += transformedImaginary
                }
                val previousReal = weightReal
                weightReal = weightReal * cosine - weightImaginary * sine
                weightImaginary = previousReal * sine + weightImaginary * cosine
            }
            length = step
        }
        val frequencies = DoubleArray(size / 2) { it * framesPerSecond / size }
        val magnitudes = DoubleArray(size / 2) { sqrt(real[it] * real[it] + imaginary[it] * imaginary[it]) }
        return frequencies to magnitudes
    }

    private fun DoubleArray.swap(first: Int, second: Int) {
        val value = this[first]
        this[first] = this[second]
        this[second] = value
    }

    private fun ArrayDeque<Int>.addBounded(value: Int) {
        addLast(value)
        if (size > HISTORY_SIZE) removeFirst()
    }

    private fun ArrayDeque<Int>.averageOrZero(): Int = if (isEmpty()) 0 else average().toInt()

    companion object {
        const val ALGORITHM_VERSION = "legacy-pos-v1"
        private const val BUFFER_SIZE = 300
        private const val MINIMUM_SAMPLES = 150
        private const val CALCULATION_INTERVAL = 15
        private const val HISTORY_SIZE = 10
        private const val EPSILON = 1e-6
        private val HEART_HZ_RANGE = 0.91..2.83
        private val RESPIRATORY_HZ_RANGE = 0.16..0.58
    }
}
