package com.fakeaway.app.features.voice_detector.domain

import com.fakeaway.app.core.domain.DispatcherProvider
import com.fakeaway.app.core.domain.ScanType
import com.fakeaway.app.core.dsp.AudioResampler
import com.fakeaway.app.features.history.domain.ScanHistoryItem
import com.fakeaway.app.features.history.domain.ScanHistoryRepository
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/**
 * Validates audio duration, runs the offline/neural voice engine, and stores history locally.
 */
class AnalyzeVoiceUseCase(
    private val engine: VoiceAnalysisEngine,
    private val historyRepository: ScanHistoryRepository,
    private val dispatchers: DispatcherProvider
) {
    suspend operator fun invoke(
        samples16k: FloatArray,
        sourceLabel: String,
        usedMicFallback: Boolean = false
    ): Result<VoiceAnalysisResult> = withContext(dispatchers.default) {
        if (samples16k.size < MIN_SAMPLES_16K) {
            return@withContext Result.failure(IllegalArgumentException("AUDIO_TOO_SHORT"))
        }

        val result = engine.analyze(
            samples16k = samples16k,
            sourceLabel = sourceLabel,
            usedMicFallback = usedMicFallback
        )

        val suspiciousWindows = result.timelineSegments.count { it.fakeProbability >= 0.65f }
        val pct = (result.overallFakeScore * 100f).roundToInt()
        val summary = buildString {
            append("${result.sourceLabel} • ${pct}% (${result.durationSeconds.format1()}s)")
            if (suspiciousWindows > 0) {
                append(" • $suspiciousWindows flagged segments")
            }
        }

        historyRepository.save(
            ScanHistoryItem(
                scanType = ScanType.VOICE,
                title = result.sourceLabel,
                verdict = result.verdict,
                fakeScore = result.overallFakeScore,
                analysisMode = result.analysisMode,
                summary = summary
            )
        )

        Result.success(result)
    }

    private fun Float.format1(): String = ((this * 10f).roundToInt() / 10f).toString()

    companion object {
        const val MIN_SAMPLES_16K = (AudioResampler.TARGET_SAMPLE_RATE * 0.4f).toInt()
    }
}
