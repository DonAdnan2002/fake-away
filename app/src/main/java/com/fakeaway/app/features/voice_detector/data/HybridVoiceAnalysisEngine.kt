package com.fakeaway.app.features.voice_detector.data

import com.fakeaway.app.core.domain.AnalysisMode
import com.fakeaway.app.core.domain.ForensicSignal
import com.fakeaway.app.core.domain.Verdict
import com.fakeaway.app.features.voice_detector.domain.VoiceAnalysisEngine
import com.fakeaway.app.features.voice_detector.domain.VoiceAnalysisResult
import kotlin.math.roundToInt

/**
 * Fuses deterministic acoustic DSP forensics with optional on-device Wav2Vec2 ONNX neural scoring.
 */
class HybridVoiceAnalysisEngine(
    private val dspAnalyzer: AcousticForensicsAnalyzer,
    private val neuralScorer: Wav2Vec2OnnxVoiceScorer?
) : VoiceAnalysisEngine {

    override suspend fun analyze(
        samples16k: FloatArray,
        sourceLabel: String,
        usedMicFallback: Boolean
    ): VoiceAnalysisResult {
        val dspResult = dspAnalyzer.analyze(samples16k, sourceLabel)
        val neural = neuralScorer?.scoreIfAvailable(samples16k)
            ?: return dspResult.copy(usedMicFallback = usedMicFallback)

        val fusedOverall = (0.70f * neural.overallFakeProb + 0.30f * dspResult.dspScore).coerceIn(0f, 1f)
        val fusedSegments = dspResult.timelineSegments.mapIndexed { idx, seg ->
            val chunkIdx = ((idx.toFloat() / dspResult.timelineSegments.size.coerceAtLeast(1)) *
                neural.chunkScores.size).toInt().coerceIn(0, neural.chunkScores.lastIndex)
            val chunkProb = neural.chunkScores[chunkIdx]
            val fusedSeg = (0.70f * chunkProb + 0.30f * seg.fakeProbability).coerceIn(0f, 1f)
            seg.copy(
                fakeProbability = fusedSeg,
                verdict = Verdict.fromFakeScore(fusedSeg)
            )
        }

        val neuralSignal = ForensicSignal(
            key = "neural_wav2vec2",
            labelKey = "sig_neural_wav2vec2",
            score = neural.overallFakeProb,
            detail = "On-device Wav2Vec2 anti-spoof confidence: ${(neural.overallFakeProb * 100f).roundToInt()}%",
            isSuspicious = neural.overallFakeProb >= 0.55f
        )

        return dspResult.copy(
            overallFakeScore = fusedOverall,
            verdict = Verdict.fromFakeScore(fusedOverall),
            analysisMode = AnalysisMode.OFFLINE_NEURAL_AND_FORENSIC,
            timelineSegments = fusedSegments,
            signals = listOf(neuralSignal) + dspResult.signals,
            neuralScore = neural.overallFakeProb,
            usedMicFallback = usedMicFallback
        )
    }
}
