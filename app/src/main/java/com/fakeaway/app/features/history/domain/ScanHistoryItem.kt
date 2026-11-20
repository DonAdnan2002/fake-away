package com.fakeaway.app.features.history.domain

import com.fakeaway.app.core.domain.AnalysisMode
import com.fakeaway.app.core.domain.ScanType
import com.fakeaway.app.core.domain.Verdict

/**
 * Domain model for a locally stored verification scan record.
 */
data class ScanHistoryItem(
    val id: Long = 0L,
    val scanType: ScanType,
    val title: String,
    val verdict: Verdict,
    val fakeScore: Float,
    val analysisMode: AnalysisMode,
    val summary: String,
    val detailsJson: String = "{}",
    val timestampMillis: Long = System.currentTimeMillis()
)
