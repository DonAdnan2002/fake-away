package com.fakeaway.app.features.voice_detector.data

import com.fakeaway.app.core.dsp.AudioResampler
import com.fakeaway.app.core.onnx.ModelSpec
import com.fakeaway.app.core.onnx.OnnxModelStore
import com.fakeaway.app.core.onnx.OnnxRuntimeSession

/**
 * Runs optional local Wav2Vec2 ONNX anti-spoofing inference over 4s normalized chunks.
 */
class Wav2Vec2OnnxVoiceScorer(
    private val modelStore: OnnxModelStore
) {
    private var cachedSession: OnnxRuntimeSession? = null

    data class NeuralVoiceScore(
        val overallFakeProb: Float,
        val chunkScores: List<Float>
    )

    @Synchronized
    fun scoreIfAvailable(samples16k: FloatArray): NeuralVoiceScore? {
        val modelFile = modelStore.resolveModelFile(ModelSpec.VOICE_WAV2VEC2) ?: return null
        val session = runCatching {
            cachedSession ?: OnnxRuntimeSession(modelFile).also { cachedSession = it }
        }.getOrNull() ?: return null

        val chunkSize = AudioResampler.TARGET_SAMPLE_RATE * 4
        val chunks = mutableListOf<Float>()
        var pos = 0
        while (pos < samples16k.size) {
            val end = (pos + chunkSize).coerceAtMost(samples16k.size)
            val slice = samples16k.copyOfRange(pos, end)
            if (slice.size >= AudioResampler.TARGET_SAMPLE_RATE || chunks.isEmpty()) {
                val normalized = AudioResampler.normalizeWav2Vec2(slice)
                val fakeProb = runCatching {
                    val logits = session.runFloatInference(
                        inputData = normalized,
                        shape = longArrayOf(1L, normalized.size.toLong())
                    )
                    // config.json: id2label { "0": "real", "1": "fake" }
                    OnnxRuntimeSession.softmaxProbability(logits, targetIndex = 1)
                }.getOrNull() ?: return null
                chunks += fakeProb
            }
            if (end == samples16k.size) break
            pos += chunkSize
        }

        if (chunks.isEmpty()) return null
        return NeuralVoiceScore(
            overallFakeProb = chunks.average().toFloat().coerceIn(0f, 1f),
            chunkScores = chunks
        )
    }
}
