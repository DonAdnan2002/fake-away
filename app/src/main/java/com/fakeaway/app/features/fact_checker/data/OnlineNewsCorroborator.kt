package com.fakeaway.app.features.fact_checker.data

import com.fakeaway.app.core.domain.AppLanguage
import com.fakeaway.app.features.fact_checker.domain.VerificationSource
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import kotlin.math.max

/**
 * Zero-API-key live corroboration client using Google News RSS Search and Wikipedia Search.
 * Endpoints are injectable so tests can drive deterministic MockWebServer verification.
 */
class OnlineNewsCorroborator(
    private val httpClient: OkHttpClient,
    private val newsBaseUrl: String = "https://news.google.com",
    private val wikiBaseUrlForLang: (AppLanguage) -> String = { lang ->
        val sub = when (lang) {
            AppLanguage.ARABIC -> "ar"
            AppLanguage.KURDISH_SORANI -> "ckb"
            AppLanguage.ENGLISH -> "en"
        }
        "https://$sub.wikipedia.org"
    }
) {
    data class CorroborationReport(
        val sources: List<VerificationSource>,
        val matchScore: Float,
        val debunkDetected: Boolean
    )

    fun search(claim: String, language: AppLanguage): CorroborationReport {
        val query = claim.trim().take(120)
        if (query.isBlank()) return CorroborationReport(emptyList(), 0f, false)

        val newsSources = fetchGoogleNewsRss(query, language)
        val wikiSources = fetchWikipedia(query, language)
        val combined = (newsSources + wikiSources).distinctBy { it.title.lowercase() }.take(6)

        if (combined.isEmpty()) {
            return CorroborationReport(emptyList(), 0f, false)
        }

        val queryTokens = tokenize(query)
        var bestOverlap = 0f
        var debunkDetected = false

        for (src in combined) {
            val hay = "${src.title} ${src.snippet}".lowercase()
            if (DEBUNK_KEYWORDS.any { hay.contains(it) }) {
                debunkDetected = true
            }
            val srcTokens = tokenize(hay)
            if (queryTokens.isNotEmpty() && srcTokens.isNotEmpty()) {
                val overlap = queryTokens.count { srcTokens.contains(it) }.toFloat() / queryTokens.size
                bestOverlap = max(bestOverlap, overlap)
            }
        }

        return CorroborationReport(
            sources = combined,
            matchScore = bestOverlap.coerceIn(0f, 1f),
            debunkDetected = debunkDetected
        )
    }

    private fun fetchGoogleNewsRss(query: String, language: AppLanguage): List<VerificationSource> {
        val encoded = URLEncoder.encode(query, "UTF-8")
        val (hl, gl, ceid) = when (language) {
            AppLanguage.ARABIC, AppLanguage.KURDISH_SORANI -> Triple("ar", "IQ", "IQ:ar")
            AppLanguage.ENGLISH -> Triple("en-US", "US", "US:en")
        }
        val url = "${newsBaseUrl.trimEnd('/')}/rss/search?q=$encoded&hl=$hl&gl=$gl&ceid=$ceid"
        val req = Request.Builder()
            .url(url)
            .header("User-Agent", "FakeAway/1.0")
            .build()

        return runCatching {
            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use emptyList()
                val xml = resp.body?.string().orEmpty()
                parseRssItems(xml)
            }
        }.getOrDefault(emptyList())
    }

    private fun fetchWikipedia(query: String, language: AppLanguage): List<VerificationSource> {
        val encoded = URLEncoder.encode(query, "UTF-8")
        val base = wikiBaseUrlForLang(language).trimEnd('/')
        val url = "$base/w/api.php?action=query&list=search&format=json&srlimit=3&srsearch=$encoded"
        val req = Request.Builder()
            .url(url)
            .header("User-Agent", "FakeAway/1.0")
            .build()

        return runCatching {
            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use emptyList()
                val body = resp.body?.string().orEmpty()
                val json = JSONObject(body)
                val items = json.optJSONObject("query")?.optJSONArray("search") ?: return@use emptyList()
                val out = mutableListOf<VerificationSource>()
                for (i in 0 until items.length()) {
                    val item = items.optJSONObject(i) ?: continue
                    val title = item.optString("title").trim()
                    if (title.isBlank()) continue
                    val snippet = stripHtml(item.optString("snippet"))
                    val pageUrl = "$base/wiki/${URLEncoder.encode(title.replace(' ', '_'), "UTF-8")}"
                    out += VerificationSource(
                        title = title,
                        publisher = "Wikipedia",
                        url = pageUrl,
                        snippet = snippet
                    )
                }
                out
            }
        }.getOrDefault(emptyList())
    }

    internal fun parseRssItems(xml: String): List<VerificationSource> {
        if (xml.isBlank()) return emptyList()
        val itemRegex = Regex("""<item>(.*?)</item>""", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
        val out = mutableListOf<VerificationSource>()
        for (match in itemRegex.findAll(xml)) {
            val block = match.groupValues[1]
            val title = extractTag(block, "title")
            val link = extractTag(block, "link")
            val source = extractTag(block, "source").ifBlank { "Google News" }
            val desc = stripHtml(extractTag(block, "description"))
            if (title.isNotBlank() && link.isNotBlank()) {
                out += VerificationSource(
                    title = title,
                    publisher = source,
                    url = link,
                    snippet = desc.take(160)
                )
            }
            if (out.size >= 4) break
        }
        return out
    }

    private fun extractTag(block: String, tag: String): String {
        val regex = Regex("""<$tag[^>]*>(.*?)</$tag>""", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
        val raw = regex.find(block)?.groupValues?.get(1).orEmpty()
        return unescapeXml(raw.removePrefix("<![CDATA[").removeSuffix("]]>")).trim()
    }

    private fun stripHtml(s: String): String =
        unescapeXml(s.replace(Regex("""<[^>]+>"""), " ")).replace(Regex("""\s+"""), " ").trim()

    private fun unescapeXml(s: String): String = s
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")

    private fun tokenize(text: String): Set<String> =
        text.lowercase()
            .split(Regex("""[^\p{L}\p{N}]+"""))
            .filter { it.length >= 3 }
            .toSet()

    companion object {
        private val DEBUNK_KEYWORDS = listOf(
            "مفبرك", "إشاعة", "زائف", "غير صحيح", "كذب", "تكذيب", "حقيقة خبر",
            "درۆ", "ناڕاست", "چەواشە", "هەڵە",
            "debunk", "hoax", "fact check", "misleading", "false claim", "fake news"
        )
    }
}
