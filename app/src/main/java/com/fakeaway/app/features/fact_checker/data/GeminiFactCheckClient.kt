package com.fakeaway.app.features.fact_checker.data

import com.fakeaway.app.core.domain.AppLanguage
import com.fakeaway.app.core.domain.Verdict
import com.fakeaway.app.features.fact_checker.domain.VerificationSource
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

/**
 * Optional Gemini 2.5 Flash client with Google Search grounding for live online claim verification.
 */
class GeminiFactCheckClient(
    private val httpClient: OkHttpClient,
    private val baseUrl: String = "https://generativelanguage.googleapis.com"
) {
    data class GeminiVerdict(
        val verdict: Verdict,
        val riskScore: Float,
        val explanation: String,
        val sources: List<VerificationSource>
    )

    fun verifyWithSearch(
        claimText: String,
        apiKey: String,
        replyLanguage: AppLanguage
    ): GeminiVerdict? {
        if (apiKey.isBlank()) return null

        val langName = when (replyLanguage) {
            AppLanguage.ARABIC -> "Arabic"
            AppLanguage.KURDISH_SORANI -> "Kurdish Sorani"
            AppLanguage.ENGLISH -> "English"
        }
        val prompt = """
            Fact-check the following social media claim using Google Search.
            Respond ONLY with valid JSON: {"verdict":"REAL|SUSPICIOUS|FAKE|UNVERIFIED","riskScore":0.0,"explanation":"..."}
            Write the "explanation" field concisely in $langName.
            Claim: ${claimText.take(600)}
        """.trimIndent()

        val payload = JSONObject().apply {
            put(
                "contents",
                JSONArray().put(
                    JSONObject().put(
                        "parts",
                        JSONArray().put(JSONObject().put("text", prompt))
                    )
                )
            )
            put("tools", JSONArray().put(JSONObject().put("google_search", JSONObject())))
        }

        val url = "${baseUrl.trimEnd('/')}/v1beta/models/gemini-2.5-flash:generateContent?key=$apiKey"
        val request = Request.Builder()
            .url(url)
            .post(payload.toString().toRequestBody("application/json".toMediaType()))
            .build()

        return runCatching {
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                val body = response.body?.string().orEmpty()
                parseResponse(body)
            }
        }.getOrNull()
    }

    internal fun parseResponse(rawJson: String): GeminiVerdict? {
        val root = JSONObject(rawJson)
        val candidate = root.optJSONArray("candidates")?.optJSONObject(0) ?: return null
        val text = candidate
            .optJSONObject("content")
            ?.optJSONArray("parts")
            ?.optJSONObject(0)
            ?.optString("text")
            .orEmpty()

        val jsonPayload = extractJsonBlock(text) ?: return null
        val verdictStr = jsonPayload.optString("verdict", "UNVERIFIED").uppercase()
        val verdict = runCatching { Verdict.valueOf(verdictStr) }.getOrDefault(Verdict.UNVERIFIED)
        val riskScore = jsonPayload.optDouble("riskScore", 0.5).toFloat().coerceIn(0f, 1f)
        val explanation = jsonPayload.optString("explanation").trim()

        val sources = mutableListOf<VerificationSource>()
        val chunks = candidate.optJSONObject("groundingMetadata")?.optJSONArray("groundingChunks")
        if (chunks != null) {
            for (i in 0 until chunks.length()) {
                val web = chunks.optJSONObject(i)?.optJSONObject("web") ?: continue
                val title = web.optString("title").trim()
                val uri = web.optString("uri").trim()
                if (title.isNotBlank() && uri.isNotBlank()) {
                    sources += VerificationSource(
                        title = title,
                        publisher = "Google Search",
                        url = uri
                    )
                }
            }
        }

        return GeminiVerdict(
            verdict = verdict,
            riskScore = riskScore,
            explanation = explanation,
            sources = sources
        )
    }

    private fun extractJsonBlock(text: String): JSONObject? {
        val start = text.indexOf('{')
        val end = text.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        return runCatching { JSONObject(text.substring(start, end + 1)) }.getOrNull()
    }
}
