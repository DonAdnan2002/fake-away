package com.fakeaway.app.features.fact_checker.domain

import com.fakeaway.app.core.domain.AnalysisMode
import com.fakeaway.app.core.domain.AppLanguage
import com.fakeaway.app.core.domain.ForensicSignal
import com.fakeaway.app.core.domain.Verdict

/**
 * Grounded web or news source retrieved during hybrid online fact-checking.
 */
data class VerificationSource(
    val title: String,
    val publisher: String,
    val url: String,
    val snippet: String = ""
)

/**
 * Combined result of offline trilingual linguistic forensics and optional online corroboration.
 */
data class FactCheckResult(
    val originalText: String,
    val detectedLanguage: AppLanguage,
    val verdict: Verdict,
    val fakeRiskScore: Float,
    val offlineRiskScore: Float,
    val onlineConfidence: Float?,
    val analysisMode: AnalysisMode,
    val signals: List<ForensicSignal>,
    val extractedClaims: List<String>,
    val sources: List<VerificationSource>,
    val explanation: String
)
