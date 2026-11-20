package com.fakeaway.app.features.voice_detector.data

import com.fakeaway.app.core.domain.AnalysisMode
import com.fakeaway.app.core.domain.ForensicSignal
import com.fakeaway.app.core.domain.Verdict
import com.fakeaway.app.core.dsp.AudioResampler
import com.fakeaway.app.core.dsp.FftMath
import com.fakeaway.app.features.voice_detector.domain.TimelineSegment
import com.fakeaway.app.features.voice_detector.domain.VoiceAnalysisResult
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Pure-Kotlin multi-signal acoustic forensics analyzer operating on 16kHz mono PCM.
 * Extracts spectral rolloff, spectral flatness, zero-floor digital silence ratio,
 * pitch/ZCR micro-jitter rigidity, and Mel trajectory smoothness across 2s sliding windows.
 */
class AcousticForensicsAnalyzer {

    private val hann512 = FftMath.hannWindow(FRAME_SIZE)

    fun analyze(samples16k: FloatArray, sourceLabel: String): VoiceAnalysisResult {
        val sampleRate = AudioResampler.TARGET_SAMPLE_RATE
        val duration = samples16k.size.toFloat() / sampleRate
        val waveform = AudioResampler.buildWaveformBars(samples16k, barCount = 64)

        val windows = sliceWindows(samples16k, sampleRate)
        val windowScores = mutableListOf<WindowFeatures>()
        val segments = mutableListOf<TimelineSegment>()

        for ((startIdx, endIdx) in windows) {
            val features = extractWindowFeatures(samples16k, startIdx, endIdx, sampleRate)
            windowScores += features
            val startSec = startIdx.toFloat() / sampleRate
            val endSec = endIdx.toFloat() / sampleRate
            segments += TimelineSegment(
                startSec = startSec,
                endSec = endSec,
                fakeProbability = features.combinedScore,
                verdict = Verdict.fromFakeScore(features.combinedScore)
            )
        }

        val avgHf = windowScores.map { it.hfRolloffScore }.average().toFloat()
        val avgFlatness = windowScores.map { it.spectralFlatnessScore }.average().toFloat()
        val avgSilence = windowScores.map { it.zeroFloorSilenceScore }.average().toFloat()
        val avgRigidity = windowScores.map { it.pitchRigidityScore }.average().toFloat()
        val avgMelSmooth = windowScores.map { it.melSmoothnessScore }.average().toFloat()

        val dspScore = combineFeatureScores(
            hfRolloff = avgHf,
            flatness = avgFlatness,
            zeroSilence = avgSilence,
            rigidity = avgRigidity,
            melSmooth = avgMelSmooth
        )

        val signals = listOf(
            ForensicSignal(
                key = "hf_rolloff",
                labelKey = "sig_hf_rolloff",
                score = avgHf,
                detail = "5.5–7.8 kHz band attenuation & cutoff index: ${(avgHf * 100).roundToInt()}%",
                isSuspicious = avgHf >= 0.55f
            ),
            ForensicSignal(
                key = "spectral_flatness",
                labelKey = "sig_spectral_flatness",
                score = avgFlatness,
                detail = "Wiener harmonic entropy anomaly: ${(avgFlatness * 100).roundToInt()}%",
                isSuspicious = avgFlatness >= 0.55f
            ),
            ForensicSignal(
                key = "zero_floor_silence",
                labelKey = "sig_zero_floor_silence",
                score = avgSilence,
                detail = "Digital zero-floor pause ratio vs organic breath floor: ${(avgSilence * 100).roundToInt()}%",
                isSuspicious = avgSilence >= 0.55f
            ),
            ForensicSignal(
                key = "pitch_rigidity",
                labelKey = "sig_pitch_rigidity",
                score = avgRigidity,
                detail = "ZCR & periodicity micro-jitter deficit: ${(avgRigidity * 100).roundToInt()}%",
                isSuspicious = avgRigidity >= 0.55f
            ),
            ForensicSignal(
                key = "mel_smoothness",
                labelKey = "sig_mel_smoothness",
                score = avgMelSmooth,
                detail = "Over-smoothed 8-band Mel transition index: ${(avgMelSmooth * 100).roundToInt()}%",
                isSuspicious = avgMelSmooth >= 0.55f
            )
        )

        return VoiceAnalysisResult(
            overallFakeScore = dspScore,
            verdict = Verdict.fromFakeScore(dspScore),
            analysisMode = AnalysisMode.OFFLINE_FORENSIC,
            durationSeconds = duration,
            sampleRate = sampleRate,
            waveformAmplitudes = waveform,
            timelineSegments = segments,
            signals = signals,
            neuralScore = null,
            dspScore = dspScore,
            sourceLabel = sourceLabel
        )
    }

    private fun sliceWindows(samples: FloatArray, sampleRate: Int): List<Pair<Int, Int>> {
        val winLen = sampleRate * 2
        val hopLen = sampleRate * 1
        if (samples.size <= winLen) {
            return listOf(0 to samples.size)
        }
        val windows = mutableListOf<Pair<Int, Int>>()
        var pos = 0
        while (pos < samples.size) {
            val end = (pos + winLen).coerceAtMost(samples.size)
            if (end - pos >= sampleRate / 2 || windows.isEmpty()) {
                windows += pos to end
            }
            if (end == samples.size) break
            pos += hopLen
        }
        return windows
    }

    private fun extractWindowFeatures(
        samples: FloatArray,
        start: Int,
        end: Int,
        sampleRate: Int
    ): WindowFeatures {
        val frame = FloatArray(FRAME_SIZE)
        var pos = start
        var totalFrames = 0
        var activeFrames = 0
        var pauseFrames = 0
        var digitalZeroFrames = 0

        val hfRatios = mutableListOf<Float>()
        val flatnessValues = mutableListOf<Float>()
        val zcrValues = mutableListOf<Float>()
        val melTrajectory = mutableListOf<FloatArray>()
        val pitchLags = mutableListOf<Int>()

        while (pos + FRAME_SIZE <= end) {
            var zcrCount = 0
            for (i in 0 until FRAME_SIZE) {
                val s = samples[pos + i]
                frame[i] = s * hann512[i]
                if (i > 0 && ((samples[pos + i - 1] >= 0f) != (s >= 0f))) {
                    zcrCount++
                }
            }
            val rms = AudioResampler.computeRms(samples, pos, pos + FRAME_SIZE)
            totalFrames++

            if (rms < 0.008f) {
                pauseFrames++
                if (rms < 0.00035f) {
                    digitalZeroFrames++
                }
            } else {
                activeFrames++
                zcrValues += zcrCount.toFloat() / FRAME_SIZE
                val spectrum = FftMath.magnitudeSpectrum(frame)
                hfRatios += computeHfEnergyRatio(spectrum, sampleRate)
                flatnessValues += computeSpectralFlatness(spectrum)
                melTrajectory += computeMelBands(spectrum, sampleRate)
                estimateAutocorrLag(samples, pos, FRAME_SIZE)?.let { pitchLags += it }
            }
            pos += FRAME_HOP
        }

        val hfScore = scoreHighFreqCutoff(hfRatios)
        val flatnessScore = scoreSpectralFlatness(flatnessValues)
        val silenceScore = scoreZeroFloorSilence(pauseFrames, digitalZeroFrames, totalFrames)
        val rigidityScore = scorePitchRigidity(zcrValues, pitchLags)
        val melSmoothScore = scoreMelSmoothness(melTrajectory)

        val combined = combineFeatureScores(
            hfRolloff = hfScore,
            flatness = flatnessScore,
            zeroSilence = silenceScore,
            rigidity = rigidityScore,
            melSmooth = melSmoothScore
        )

        return WindowFeatures(
            hfRolloffScore = hfScore,
            spectralFlatnessScore = flatnessScore,
            zeroFloorSilenceScore = silenceScore,
            pitchRigidityScore = rigidityScore,
            melSmoothnessScore = melSmoothScore,
            combinedScore = combined
        )
    }

    private fun computeHfEnergyRatio(spectrum: FloatArray, sampleRate: Int): Float {
        val binHz = sampleRate.toFloat() / FRAME_SIZE
        val lowStart = (300f / binHz).toInt().coerceIn(1, spectrum.lastIndex)
        val lowEnd = (4000f / binHz).toInt().coerceIn(lowStart + 1, spectrum.lastIndex)
        val highStart = (5500f / binHz).toInt().coerceIn(lowEnd, spectrum.lastIndex)
        val highEnd = (7800f / binHz).toInt().coerceIn(highStart, spectrum.lastIndex)

        var lowE = 1e-8
        for (b in lowStart..lowEnd) lowE += spectrum[b] * spectrum[b]
        var highE = 0.0
        for (b in highStart..highEnd) highE += spectrum[b] * spectrum[b]

        return (highE / lowE).toFloat()
    }

    private fun scoreHighFreqCutoff(ratios: List<Float>): Float {
        if (ratios.isEmpty()) return 0.35f
        val avgRatio = ratios.average().toFloat()
        // Organic wideband speech usually carries ~0.025–0.18 in 5.5–7.8kHz relative to 0.3–4kHz.
        // Neural TTS / vocoders often exhibit stark upper-band cutoff (<0.012).
        return when {
            avgRatio < 0.004f -> 0.92f
            avgRatio < 0.012f -> 0.74f
            avgRatio < 0.022f -> 0.52f
            avgRatio > 0.35f -> 0.65f
            else -> 0.18f
        }
    }

    private fun computeSpectralFlatness(spectrum: FloatArray): Float {
        val start = 4
        val end = (spectrum.size - 1).coerceAtLeast(start + 1)
        var logSum = 0.0
        var arithSum = 0.0
        val count = end - start + 1
        for (i in start..end) {
            val power = (spectrum[i] * spectrum[i]).toDouble().coerceAtLeast(1e-12)
            logSum += ln(power)
            arithSum += power
        }
        val geomMean = exp(logSum / count)
        val arithMean = (arithSum / count).coerceAtLeast(1e-12)
        return (geomMean / arithMean).toFloat().coerceIn(0f, 1f)
    }

    private fun scoreSpectralFlatness(values: List<Float>): Float {
        if (values.size < 3) return 0.4f
        val mean = values.average().toFloat()
        val std = stdDev(values, mean)
        // Natural speech alternates strongly between tonal vowels (low flatness) and noisy consonants (higher flatness).
        // Low std indicates monotonous vocoder harmonic structure.
        val lowVarianceRisk = (1f - (std / 0.06f)).coerceIn(0f, 1f)
        val unnaturalFloor = if (mean < 0.008f) 0.75f else if (mean > 0.25f) 0.60f else 0.20f
        return (0.65f * lowVarianceRisk + 0.35f * unnaturalFloor).coerceIn(0f, 1f)
    }

    private fun scoreZeroFloorSilence(pauseFrames: Int, digitalZeroFrames: Int, totalFrames: Int): Float {
        if (totalFrames == 0 || pauseFrames < 2) return 0.30f
        val zeroRatio = digitalZeroFrames.toFloat() / pauseFrames.toFloat()
        val pauseDensity = pauseFrames.toFloat() / totalFrames.toFloat()
        // Pure zero-floor pauses in TTS give zeroRatio near 1.0; organic room/mic recordings carry gentle floor noise.
        return if (pauseDensity > 0.04f) {
            (0.85f * zeroRatio + 0.15f * pauseDensity.coerceAtMost(1f)).coerceIn(0f, 1f)
        } else {
            0.28f
        }
    }

    private fun estimateAutocorrLag(samples: FloatArray, offset: Int, len: Int): Int? {
        val minLag = 40 // ~400 Hz
        val maxLag = 200 // ~80 Hz
        if (offset + len > samples.size) return null
        var bestLag = -1
        var bestCorr = 0.0
        var zeroLag = 1e-8
        for (i in 0 until len) {
            zeroLag += samples[offset + i] * samples[offset + i]
        }
        for (lag in minLag..maxLag) {
            var corr = 0.0
            for (i in 0 until len - lag) {
                corr += samples[offset + i] * samples[offset + i + lag]
            }
            val norm = corr / zeroLag
            if (norm > bestCorr) {
                bestCorr = norm
                bestLag = lag
            }
        }
        return if (bestCorr > 0.35 && bestLag > 0) bestLag else null
    }

    private fun scorePitchRigidity(zcrValues: List<Float>, pitchLags: List<Int>): Float {
        if (zcrValues.size < 4) return 0.4f
        val zcrStd = stdDev(zcrValues, zcrValues.average().toFloat())
        val zcrRigidity = (1f - (zcrStd / 0.035f)).coerceIn(0f, 1f)

        val lagRigidity = if (pitchLags.size >= 4) {
            val floats = pitchLags.map { it.toFloat() }
            val lagStd = stdDev(floats, floats.average().toFloat())
            (1f - (lagStd / 8f)).coerceIn(0f, 1f)
        } else {
            0.4f
        }
        return (0.55f * zcrRigidity + 0.45f * lagRigidity).coerceIn(0f, 1f)
    }

    private fun computeMelBands(spectrum: FloatArray, sampleRate: Int): FloatArray {
        val bands = 8
        val out = FloatArray(bands)
        val nyquist = sampleRate / 2f
        val maxMel = hzToMel(nyquist)
        val binHz = sampleRate.toFloat() / FRAME_SIZE
        for (b in 0 until bands) {
            val m0 = maxMel * b / bands
            val m1 = maxMel * (b + 1) / bands
            val f0 = melToHz(m0)
            val f1 = melToHz(m1)
            val i0 = (f0 / binHz).toInt().coerceIn(1, spectrum.lastIndex)
            val i1 = (f1 / binHz).toInt().coerceIn(i0, spectrum.lastIndex)
            var sum = 1e-8
            for (i in i0..i1) sum += spectrum[i] * spectrum[i]
            out[b] = ln(sum / (i1 - i0 + 1)).toFloat()
        }
        return out
    }

    private fun scoreMelSmoothness(trajectory: List<FloatArray>): Float {
        if (trajectory.size < 4) return 0.35f
        var deltaSum = 0.0
        var count = 0
        for (t in 1 until trajectory.size) {
            val prev = trajectory[t - 1]
            val curr = trajectory[t]
            for (b in prev.indices) {
                deltaSum += abs(curr[b] - prev[b])
                count++
            }
        }
        val meanDelta = (deltaSum / count.coerceAtLeast(1)).toFloat()
        // Natural dynamic articulation changes ~0.8–2.5 log-units per frame; over-smoothed TTS is flatter (<0.45).
        return (1f - ((meanDelta - 0.15f) / 1.15f)).coerceIn(0f, 1f)
    }

    private fun combineFeatureScores(
        hfRolloff: Float,
        flatness: Float,
        zeroSilence: Float,
        rigidity: Float,
        melSmooth: Float
    ): Float = (
        0.24f * hfRolloff +
            0.20f * flatness +
            0.22f * zeroSilence +
            0.18f * rigidity +
            0.16f * melSmooth
        ).coerceIn(0f, 1f)

    private fun stdDev(values: List<Float>, mean: Float): Float {
        if (values.size <= 1) return 0f
        var sumSq = 0.0
        for (v in values) {
            val d = v - mean
            sumSq += d * d
        }
        return sqrt(sumSq / values.size).toFloat()
    }

    private fun hzToMel(hz: Float): Float = (2595.0 * kotlin.math.log10(1.0 + hz / 700.0)).toFloat()
    private fun melToHz(mel: Float): Float = (700.0 * (10.0.pow(mel / 2595.0) - 1.0)).toFloat()

    private data class WindowFeatures(
        val hfRolloffScore: Float,
        val spectralFlatnessScore: Float,
        val zeroFloorSilenceScore: Float,
        val pitchRigidityScore: Float,
        val melSmoothnessScore: Float,
        val combinedScore: Float
    )

    companion object {
        private const val FRAME_SIZE = 512
        private const val FRAME_HOP = 256

        /**
         * Generates deterministic synthetic TTS-like vs organic-like 16kHz PCM audio clips
         * for in-app offline diagnostics and E2E testing.
         */
        fun generateDemoPcm(synthetic: Boolean, durationSeconds: Float = 3.0f): FloatArray {
            val sr = AudioResampler.TARGET_SAMPLE_RATE
            val n = (sr * durationSeconds).toInt()
            val out = FloatArray(n)
            var seed = 1337L
            fun nextRand(): Float {
                seed = (seed * 6364136223846793005L + 1442695040888963407L)
                return ((seed ushr 33).toInt() / Int.MAX_VALUE.toFloat())
            }

            if (synthetic) {
                // Steady low-jitter fundamental + harmonics cut off above 4kHz + exact digital zero pauses
                for (i in 0 until n) {
                    val t = i.toDouble() / sr
                    val cycle = t % 1.2
                    if (cycle > 0.85) {
                        out[i] = 0f // Zero-floor digital silence
                    } else {
                        val f0 = 140.0
                        var s = 0.35 * sin(2 * PI * f0 * t)
                        s += 0.22 * sin(2 * PI * f0 * 2 * t)
                        s += 0.14 * sin(2 * PI * f0 * 4 * t)
                        s += 0.08 * sin(2 * PI * f0 * 7 * t)
                        out[i] = s.toFloat()
                    }
                }
            } else {
                // Dynamic vocal vibrato, wideband harmonics + fricatives up to 7kHz, and natural ambient room breath
                for (i in 0 until n) {
                    val t = i.toDouble() / sr
                    val cycle = t % 1.35
                    val ambient = (nextRand() - 0.5f) * 0.008f
                    if (cycle > 0.95) {
                        // Natural ambient/breath pause (not zero-floor)
                        out[i] = ambient
                    } else {
                        val vibrato = 145.0 + 18.0 * sin(2 * PI * 4.5 * t) + 7.0 * sin(2 * PI * 1.1 * t)
                        val envelope = (0.55 + 0.45 * sin(2 * PI * 2.8 * t)).toFloat()
                        var harmonic = 0.0
                        for (h in 1..12) {
                            harmonic += (0.28 / h) * sin(2 * PI * vibrato * h * t)
                        }
                        val fricative = if ((t * 5.0).toInt() % 3 == 0) (nextRand() - 0.5f) * 0.16f else 0f
                        out[i] = ((harmonic.toFloat() * envelope) + fricative + ambient).coerceIn(-0.95f, 0.95f)
                    }
                }
            }
            return out
        }
    }
}
