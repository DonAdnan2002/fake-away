package com.fakeaway.app.core.dsp

import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Pure Kotlin audio resampling, normalization, and waveform visualization helpers.
 */
object AudioResampler {

    const val TARGET_SAMPLE_RATE = 16_000

    /**
     * Converts interleaved multi-channel PCM samples to mono by averaging channels.
     */
    fun downmixToMono(samples: FloatArray, channels: Int): FloatArray {
        if (channels <= 1) return samples
        val frames = samples.size / channels
        val mono = FloatArray(frames)
        for (f in 0 until frames) {
            var sum = 0f
            val base = f * channels
            for (c in 0 until channels) {
                sum += samples[base + c]
            }
            mono[f] = sum / channels
        }
        return mono
    }

    /**
     * Linearly resamples mono audio from [srcRate] to [dstRate].
     */
    fun resampleLinear(
        samples: FloatArray,
        srcRate: Int,
        dstRate: Int = TARGET_SAMPLE_RATE
    ): FloatArray {
        if (samples.isEmpty() || srcRate <= 0 || srcRate == dstRate) return samples
        val ratio = srcRate.toDouble() / dstRate.toDouble()
        val outSize = (samples.size / ratio).toInt().coerceAtLeast(1)
        val out = FloatArray(outSize)
        val lastIdx = samples.lastIndex
        for (i in 0 until outSize) {
            val pos = i * ratio
            val idx0 = pos.toInt().coerceIn(0, lastIdx)
            val idx1 = (idx0 + 1).coerceIn(0, lastIdx)
            val frac = (pos - idx0).toFloat()
            out[i] = samples[idx0] * (1f - frac) + samples[idx1] * frac
        }
        return out
    }

    /**
     * Applies Wav2Vec2 zero-mean unit-variance standardization.
     */
    fun normalizeWav2Vec2(samples: FloatArray): FloatArray {
        if (samples.isEmpty()) return samples
        var mean = 0.0
        for (s in samples) mean += s
        mean /= samples.size

        var variance = 0.0
        for (s in samples) {
            val d = s - mean
            variance += d * d
        }
        variance /= samples.size
        val std = sqrt(variance + 1e-7)
        return FloatArray(samples.size) { i -> ((samples[i] - mean) / std).toFloat() }
    }

    /**
     * Computes Root-Mean-Square energy in the slice [[start], [end]).
     */
    fun computeRms(samples: FloatArray, start: Int = 0, end: Int = samples.size): Float {
        val safeStart = start.coerceIn(0, samples.size)
        val safeEnd = end.coerceIn(safeStart, samples.size)
        val count = safeEnd - safeStart
        if (count <= 0) return 0f
        var sumSq = 0.0
        for (i in safeStart until safeEnd) {
            val v = samples[i].toDouble()
            sumSq += v * v
        }
        return sqrt(sumSq / count).toFloat()
    }

    /**
     * Extracts [barCount] normalized peak amplitudes in [0.05f..1f] for waveform rendering.
     */
    fun buildWaveformBars(samples: FloatArray, barCount: Int = 64): List<Float> {
        if (samples.isEmpty() || barCount <= 0) return List(barCount.coerceAtLeast(0)) { 0.1f }
        val step = (samples.size / barCount).coerceAtLeast(1)
        val raw = FloatArray(barCount)
        var maxPeak = 1e-4f
        for (b in 0 until barCount) {
            val start = (b * samples.size) / barCount
            val end = ((b + 1) * samples.size / barCount).coerceAtMost(samples.size).coerceAtLeast(start + 1)
            var peak = 0f
            var idx = start
            val stride = (step / 16).coerceAtLeast(1)
            while (idx < end) {
                val a = abs(samples[idx])
                if (a > peak) peak = a
                idx += stride
            }
            raw[b] = peak
            if (peak > maxPeak) maxPeak = peak
        }
        return raw.map { (it / maxPeak).coerceIn(0.06f, 1f) }
    }
}
