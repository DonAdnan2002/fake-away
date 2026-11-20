package com.fakeaway.app.features.fact_checker.domain

import com.fakeaway.app.core.domain.AppLanguage
import com.fakeaway.app.core.domain.DispatcherProvider
import com.fakeaway.app.core.domain.ScanType
import com.fakeaway.app.features.history.domain.ScanHistoryItem
import com.fakeaway.app.features.history.domain.ScanHistoryRepository
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/**
 * Validates claim text, runs hybrid verification, and persists the result locally.
 */
class VerifyClaimUseCase(
    private val engine: FactCheckEngine,
    private val historyRepository: ScanHistoryRepository,
    private val dispatchers: DispatcherProvider
) {
    suspend operator fun invoke(
        rawText: String,
        uiLanguage: AppLanguage
    ): Result<FactCheckResult> = withContext(dispatchers.default) {
        val cleaned = rawText.trim()
        if (cleaned.isEmpty()) {
            return@withContext Result.failure(IllegalArgumentException("EMPTY_TEXT"))
        }

        val result = engine.verify(cleaned, uiLanguage)
        val title = result.extractedClaims.firstOrNull()?.take(70) ?: cleaned.take(70)
        val pct = (result.fakeRiskScore * 100f).roundToInt()

        historyRepository.save(
            ScanHistoryItem(
                scanType = ScanType.NEWS_TEXT,
                title = title,
                verdict = result.verdict,
                fakeScore = result.fakeRiskScore,
                analysisMode = result.analysisMode,
                summary = "$pct% • ${result.detectedLanguage.displayName} • ${result.explanation.take(120)}"
            )
        )

        Result.success(result)
    }
}
