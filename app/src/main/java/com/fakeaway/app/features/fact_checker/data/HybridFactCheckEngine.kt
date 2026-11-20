package com.fakeaway.app.features.fact_checker.data

import com.fakeaway.app.core.domain.AnalysisMode
import com.fakeaway.app.core.domain.AppLanguage
import com.fakeaway.app.core.domain.DispatcherProvider
import com.fakeaway.app.core.domain.ForensicSignal
import com.fakeaway.app.core.domain.Verdict
import com.fakeaway.app.core.network.ConnectivityChecker
import com.fakeaway.app.features.fact_checker.domain.FactCheckEngine
import com.fakeaway.app.features.fact_checker.domain.FactCheckResult
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.roundToInt

/**
 * Hybrid trilingual fact-checking engine:
 * 1) Always executes strong offline linguistic forensics in AR/EN/CKB.
 * 2) When online and strict-offline mode is disabled, enriches with live Gemini Search
 *    grounding (if configured) and Google News RSS + Wikipedia corroboration.
 */
class HybridFactCheckEngine(
    private val offlineAnalyzer: OfflineTextForensicsAnalyzer,
    private val newsCorroborator: OnlineNewsCorroborator,
    private val geminiClient: GeminiFactCheckClient,
    private val connectivityChecker: ConnectivityChecker,
    private val dispatchers: DispatcherProvider,
    private val isStrictOffline: () -> Boolean,
    private val geminiKeyProvider: () -> String,
    private val tathabbatClient: TathabbatBackendClient? = null,
    private val tathabbatUrlProvider: () -> String = { "" }
) : FactCheckEngine {

    override suspend fun verify(rawText: String, uiLanguage: AppLanguage): FactCheckResult {
        val offline = offlineAnalyzer.analyze(rawText, uiLanguage)
        if (isStrictOffline() || !connectivityChecker.isOnline()) {
            return offline
        }

        val query = offline.extractedClaims.firstOrNull().orEmpty().ifBlank { rawText.take(120) }
        val apiKey = geminiKeyProvider()
        val newsroomUrl = tathabbatUrlProvider().trim()

        val onlineOutcome = withContext(dispatchers.io) {
            withTimeoutOrNull(8_000L) {
                val tathabbat = if (tathabbatClient != null && newsroomUrl.isNotBlank()) {
                    tathabbatClient.verifyAgainstNewsroom(newsroomUrl, query, offline.detectedLanguage)
                } else {
                    null
                }
                val gemini = if (apiKey.isNotBlank()) {
                    geminiClient.verifyWithSearch(query, apiKey, uiLanguage)
                } else {
                    null
                }
                val news = newsCorroborator.search(query, offline.detectedLanguage)
                Triple(tathabbat, gemini, news)
            }
        } ?: return offline

        val (tathabbat, gemini, news) = onlineOutcome
        val tathabbatReviewed = tathabbat?.matchedClaim?.takeIf {
            it.similarity >= 0.35f && it.status in REVIEWED_TATHABBAT_STATUSES
        }
        val tathabbatSources = tathabbat?.matchedClaim?.evidence.orEmpty() + tathabbat?.relatedArticles.orEmpty()

        if (tathabbatReviewed == null && tathabbatSources.isEmpty() && gemini == null && news.sources.isEmpty()) {
            return offline
        }

        val extraSignals = mutableListOf<ForensicSignal>()
        var combinedRisk = offline.offlineRiskScore
        var finalVerdict = offline.verdict
        var onlineConfidence = news.matchScore
        val allSources = (tathabbatSources + gemini?.sources.orEmpty() + news.sources).distinctBy { it.url }

        if (news.sources.isNotEmpty()) {
            val newsRisk = when {
                news.debunkDetected -> 0.88f
                news.matchScore >= 0.45f -> 0.22f
                news.matchScore >= 0.20f -> 0.40f
                else -> 0.55f
            }
            extraSignals += ForensicSignal(
                key = "online_corroboration",
                labelKey = "sig_online_corroboration",
                score = newsRisk,
                detail = if (news.debunkDetected) {
                    "Debunking report detected across ${news.sources.size} retrieved sources"
                } else {
                    "Overlap ${(news.matchScore * 100f).roundToInt()}% across ${news.sources.size} live sources"
                },
                isSuspicious = newsRisk >= 0.55f
            )
            combinedRisk = (0.45f * offline.offlineRiskScore + 0.55f * newsRisk).coerceIn(0f, 1f)
        }

        if (gemini != null) {
            extraSignals += ForensicSignal(
                key = "gemini_grounding",
                labelKey = "sig_gemini_grounding",
                score = gemini.riskScore,
                detail = "Gemini verdict: ${gemini.verdict.name} (${(gemini.riskScore * 100f).roundToInt()}%)",
                isSuspicious = gemini.riskScore >= 0.55f
            )
            combinedRisk = (0.25f * offline.offlineRiskScore + 0.75f * gemini.riskScore).coerceIn(0f, 1f)
            onlineConfidence = 1f - gemini.riskScore
            finalVerdict = gemini.verdict
        } else if (news.debunkDetected) {
            finalVerdict = Verdict.FAKE
        } else {
            finalVerdict = Verdict.fromFakeScore(combinedRisk)
        }

        if (tathabbatReviewed != null) {
            val (mappedVerdict, mappedScore) = when (tathabbatReviewed.status) {
                "false" -> Verdict.FAKE to 0.94f
                "misleading" -> Verdict.SUSPICIOUS to 0.72f
                "supported" -> Verdict.REAL to 0.12f
                else -> finalVerdict to combinedRisk
            }
            extraSignals.add(
                0,
                ForensicSignal(
                    key = "tathabbat_newsroom",
                    labelKey = "sig_tathabbat_newsroom",
                    score = mappedScore,
                    detail = buildString {
                        append("Status: ${tathabbatReviewed.status}")
                        append(" (${(tathabbatReviewed.similarity * 100f).roundToInt()}% match)")
                        tathabbatReviewed.reviewer?.let { append(" • Reviewer: $it") }
                    },
                    isSuspicious = mappedScore >= 0.55f
                )
            )
            finalVerdict = mappedVerdict
            combinedRisk = mappedScore
            onlineConfidence = tathabbatReviewed.similarity
        }

        val explanation = tathabbatReviewed?.rationale?.takeIf { it.isNotBlank() }
            ?: gemini?.explanation?.takeIf { it.isNotBlank() }
            ?: buildHybridExplanation(
                lang = uiLanguage,
                offlineSummary = offline.explanation,
                sourceCount = allSources.size,
                debunked = news.debunkDetected
            )

        return offline.copy(
            verdict = finalVerdict,
            fakeRiskScore = combinedRisk,
            onlineConfidence = onlineConfidence,
            analysisMode = AnalysisMode.HYBRID_ONLINE,
            signals = extraSignals + offline.signals,
            sources = allSources,
            explanation = explanation
        )
    }

    companion object {
        private val REVIEWED_TATHABBAT_STATUSES = setOf("false", "misleading", "supported")
    }

    private fun buildHybridExplanation(
        lang: AppLanguage,
        offlineSummary: String,
        sourceCount: Int,
        debunked: Boolean
    ): String = when (lang) {
        AppLanguage.ARABIC -> if (debunked) {
            "تم رصد تقارير تكذيب أو تحذير من هذا الادعاء عبر $sourceCount مصدر حي. $offlineSummary"
        } else {
            "تم فحص الادعاء ومطابقته مع $sourceCount مصدر إخباري ومعرفي حي. $offlineSummary"
        }
        AppLanguage.KURDISH_SORANI -> if (debunked) {
            "ڕاپۆرتی بەدرۆخستنەوە بۆ ئەم بانگەشەیە لە ناو $sourceCount سەرچاوەی زیندوودا دۆزرایەوە. $offlineSummary"
        } else {
            "بانگەشەکە لەگەڵ $sourceCount سەرچاوەی هەواڵ و زانیاری زیندوودا بەراورد کرا. $offlineSummary"
        }
        AppLanguage.ENGLISH -> if (debunked) {
            "Debunking coverage matched across $sourceCount live sources. $offlineSummary"
        } else {
            "Cross-checked against $sourceCount live news and reference sources. $offlineSummary"
        }
    }
}
