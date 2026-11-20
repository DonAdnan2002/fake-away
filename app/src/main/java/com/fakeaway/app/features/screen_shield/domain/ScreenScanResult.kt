package com.fakeaway.app.features.screen_shield.domain

import com.fakeaway.app.core.domain.AnalysisMode
import com.fakeaway.app.core.domain.Verdict
import com.fakeaway.app.features.fact_checker.domain.FactCheckResult

/**
 * Unified result produced by a single tap of the Floating Screen Shield (OCR + Image + Fact Check).
 */
data class ScreenScanResult(
    val extractedText: String,
    val imageResult: ImageAnalysisResult?,
    val factCheckResult: FactCheckResult?,
    val overallVerdict: Verdict,
    val overallRiskScore: Float,
    val analysisMode: AnalysisMode,
    val previewImagePath: String? = null
)
