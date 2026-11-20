package com.fakeaway.app.features.fact_checker.data

import com.fakeaway.app.core.domain.AppLanguage
import com.fakeaway.app.features.fact_checker.domain.VerificationSource
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

/**
 * Client bridging Fake Away mobile scans to the Tathabbat (تثبّت) Newsroom FastAPI backend.
 */
class TathabbatBackendClient(
    private val httpClient: OkHttpClient
) {
    data class HealthStatus(
        val articles: Int,
        val claims: Int
    )

    data class MatchedNewsroomClaim(
        val id: Long,
        val text: String,
        val status: String,
        val reviewer: String?,
        val rationale: String?,
        val similarity: Float,
        val evidence: List<VerificationSource>
    )

    data class NewsroomVerification(
        val matchedClaim: MatchedNewsroomClaim?,
        val relatedArticles: List<VerificationSource>
    )

    fun checkHealth(baseUrl: String): Result<HealthStatus> {
        val cleanBase = normalizeBaseUrl(baseUrl)
        if (cleanBase.isEmpty()) {
            return Result.failure(IllegalArgumentException("Empty Tathabbat URL"))
        }
        val req = Request.Builder().url("$cleanBase/api/health").get().build()
        return runCatching {
            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) error("HTTP ${resp.code}")
                val json = JSONObject(resp.body?.string().orEmpty())
                HealthStatus(
                    articles = json.optInt("articles", 0),
                    claims = json.optInt("claims", 0)
                )
            }
        }
    }

    fun verifyAgainstNewsroom(
        baseUrl: String,
        claimText: String,
        language: AppLanguage
    ): NewsroomVerification? {
        val cleanBase = normalizeBaseUrl(baseUrl)
        if (cleanBase.isEmpty()) return null

        val payload = JSONObject().apply {
            put("text", claimText.take(2000))
            put("language", language.tag)
        }
        val req = Request.Builder()
            .url("$cleanBase/api/mobile/verify")
            .post(payload.toString().toRequestBody("application/json".toMediaType()))
            .build()

        return runCatching {
            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use null
                parseVerifyResponse(resp.body?.string().orEmpty())
            }
        }.getOrNull()
    }

    fun escalateToNewsroom(
        baseUrl: String,
        text: String,
        language: AppLanguage,
        offlineRiskScore: Float,
        scanType: String = "NEWS_TEXT",
        summary: String = ""
    ): Result<Long> {
        val cleanBase = normalizeBaseUrl(baseUrl)
        if (cleanBase.isEmpty()) {
            return Result.failure(IllegalStateException("SERVER_URL_NOT_CONFIGURED"))
        }

        val payload = JSONObject().apply {
            put("text", text.take(4000))
            put("language", language.tag)
            put("offline_risk_score", offlineRiskScore.toDouble())
            put("scan_type", scanType)
            if (summary.isNotBlank()) put("summary", summary.take(500))
        }

        val req = Request.Builder()
            .url("$cleanBase/api/mobile/escalate")
            .post(payload.toString().toRequestBody("application/json".toMediaType()))
            .build()

        return runCatching {
            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) error("HTTP ${resp.code}")
                val json = JSONObject(resp.body?.string().orEmpty())
                json.getLong("claim_id")
            }
        }
    }

    private fun parseVerifyResponse(raw: String): NewsroomVerification {
        val root = JSONObject(raw)
        val mc = root.optJSONObject("matched_claim")?.let { obj ->
            val evArray = obj.optJSONArray("evidence")
            val evidenceList = buildList {
                if (evArray != null) {
                    for (i in 0 until evArray.length()) {
                        val e = evArray.optJSONObject(i) ?: continue
                        add(
                            VerificationSource(
                                title = e.optString("label"),
                                publisher = e.optString("publisher").ifBlank { "Tathabbat Evidence" },
                                url = e.optString("url"),
                                snippet = e.optString("note")
                            )
                        )
                    }
                }
            }
            MatchedNewsroomClaim(
                id = obj.optLong("id"),
                text = obj.optString("text"),
                status = obj.optString("status"),
                reviewer = obj.optString("reviewer").takeIf { it.isNotBlank() && it != "null" },
                rationale = obj.optString("rationale").takeIf { it.isNotBlank() && it != "null" },
                similarity = obj.optDouble("similarity", 0.0).toFloat(),
                evidence = evidenceList
            )
        }

        val related = buildList {
            val arr = root.optJSONArray("related_articles")
            if (arr != null) {
                for (i in 0 until arr.length()) {
                    val a = arr.optJSONObject(i) ?: continue
                    add(
                        VerificationSource(
                            title = a.optString("title"),
                            publisher = a.optString("domain").ifBlank { a.optString("provider", "Tathabbat Archive") },
                            url = a.optString("url"),
                            snippet = a.optString("published_at")
                        )
                    )
                }
            }
        }

        return NewsroomVerification(matchedClaim = mc, relatedArticles = related)
    }

    private fun normalizeBaseUrl(raw: String): String {
        val trimmed = raw.trim().trimEnd('/')
        if (trimmed.isEmpty()) return ""
        return if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            trimmed
        } else {
            "http://$trimmed"
        }
    }
}
