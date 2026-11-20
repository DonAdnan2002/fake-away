package com.fakeaway.app.features.screen_shield.domain

import com.fakeaway.app.core.domain.AnalysisMode
import com.fakeaway.app.core.domain.ForensicSignal
import com.fakeaway.app.core.domain.Verdict

/**
 * Spatial and spectral forensic inspection result for an image or post media frame.
 */
data class ImageAnalysisResult(
    val aiScore: Float,
    val verdict: Verdict,
    val analysisMode: AnalysisMode,
    val metadataSignature: String?,
    val hasCameraExif: Boolean,
    val fftGridScore: Float,
    val heatmapGrid: List<List<Float>>,
    val neuralScore: Float?,
    val signals: List<ForensicSignal>
)
