package com.fakeaway.app.features.voice_detector.domain

import com.fakeaway.app.core.domain.AnalysisMode
import com.fakeaway.app.core.domain.ForensicSignal
import com.fakeaway.app.core.domain.Verdict

/**
 * Per-window authenticity prediction used to color the interactive waveform heatmap.
 */
data class TimelineSegment(
    val startSec: Float,
    val endSec: Float,
    val fakeProbability: Float,
    val verdict: Verdict
)

/**
 * Full result of a voice deepfake inspection.
 */
data class VoiceAnalysisResult(
    val overallFakeScore: Float,
    val verdict: Verdict,
    val analysisMode: AnalysisMode,
    val durationSeconds: Float,
    val sampleRate: Int,
    val waveformAmplitudes: List<Float>,
    val timelineSegments: List<TimelineSegment>,
    val signals: List<ForensicSignal>,
    val neuralScore: Float?,
    val dspScore: Float,
    val sourceLabel: String,
    val usedMicFallback: Boolean = false
)
