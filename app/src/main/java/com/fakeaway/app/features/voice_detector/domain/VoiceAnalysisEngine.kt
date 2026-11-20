package com.fakeaway.app.features.voice_detector.domain

/**
 * Abstraction for voice deepfake scoring over 16kHz mono PCM audio.
 */
interface VoiceAnalysisEngine {
    suspend fun analyze(
        samples16k: FloatArray,
        sourceLabel: String,
        usedMicFallback: Boolean = false
    ): VoiceAnalysisResult
}
