package com.fakeaway.app.features.fact_checker.data

import com.fakeaway.app.core.domain.AnalysisMode
import com.fakeaway.app.core.domain.AppLanguage
import com.fakeaway.app.core.domain.ForensicSignal
import com.fakeaway.app.core.domain.Verdict
import com.fakeaway.app.features.fact_checker.domain.FactCheckResult
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Pure Kotlin offline linguistic forensics engine supporting Arabic, English, and Kurdish Sorani.
 */
class OfflineTextForensicsAnalyzer {

    fun analyze(rawText: String, uiLanguage: AppLanguage): FactCheckResult {
        val cleaned = stripSocialUiChrome(rawText)
        val detectedLang = detectLanguage(cleaned)
        val lower = cleaned.lowercase()

        val clickbaitScore = scoreClickbaitLexicon(lower)
        val urgencyScore = scoreUrgencyFormatting(cleaned)
        val sourceRiskScore = scoreSourceAttribution(lower)
        val aiStyleScore = scoreAiUniformityAndMarkers(cleaned, lower)

        val offlineRisk = (
            0.38f * clickbaitScore +
                0.22f * urgencyScore +
                0.22f * sourceRiskScore +
                0.18f * aiStyleScore
            ).coerceIn(0f, 1f)

        val claims = extractSearchClaims(cleaned)
        val signals = listOf(
            ForensicSignal(
                key = "clickbait_lexicon",
                labelKey = "sig_clickbait_lexicon",
                score = clickbaitScore,
                detail = "${(clickbaitScore * 100).roundToInt()}%",
                isSuspicious = clickbaitScore >= 0.55f
            ),
            ForensicSignal(
                key = "urgency_punctuation",
                labelKey = "sig_urgency_punctuation",
                score = urgencyScore,
                detail = "${(urgencyScore * 100).roundToInt()}%",
                isSuspicious = urgencyScore >= 0.55f
            ),
            ForensicSignal(
                key = "source_attribution",
                labelKey = "sig_source_attribution",
                score = sourceRiskScore,
                detail = "${(sourceRiskScore * 100).roundToInt()}%",
                isSuspicious = sourceRiskScore >= 0.55f
            ),
            ForensicSignal(
                key = "ai_text_style",
                labelKey = "sig_ai_text_style",
                score = aiStyleScore,
                detail = "${(aiStyleScore * 100).roundToInt()}%",
                isSuspicious = aiStyleScore >= 0.55f
            )
        )

        val verdict = when {
            offlineRisk >= 0.65f -> Verdict.FAKE
            offlineRisk >= 0.38f -> Verdict.SUSPICIOUS
            sourceRiskScore <= 0.30f && offlineRisk < 0.30f -> Verdict.REAL
            else -> Verdict.UNVERIFIED
        }

        return FactCheckResult(
            originalText = rawText,
            detectedLanguage = detectedLang,
            verdict = verdict,
            fakeRiskScore = offlineRisk,
            offlineRiskScore = offlineRisk,
            onlineConfidence = null,
            analysisMode = AnalysisMode.OFFLINE_FORENSIC,
            signals = signals,
            extractedClaims = claims,
            sources = emptyList(),
            explanation = buildLocalizedSummary(
                lang = uiLanguage,
                verdict = verdict,
                clickbait = clickbaitScore,
                sourceRisk = sourceRiskScore,
                aiStyle = aiStyleScore
            )
        )
    }

    fun detectLanguage(text: String): AppLanguage {
        var kurdishSpecific = 0
        var arabicScript = 0
        var latinScript = 0
        for (ch in text) {
            when {
                ch in KURDISH_SPECIFIC_CHARS -> {
                    kurdishSpecific++
                    arabicScript++
                }
                ch in '\u0600'..'\u06FF' || ch in '\u0750'..'\u077F' -> arabicScript++
                ch in 'A'..'Z' || ch in 'a'..'z' -> latinScript++
            }
        }
        val lower = text.lowercase()
        val kurdishKeywordHits = KURDISH_KEYWORDS.count { lower.contains(it) }
        return when {
            kurdishSpecific >= 2 || kurdishKeywordHits >= 2 -> AppLanguage.KURDISH_SORANI
            arabicScript >= latinScript && arabicScript > 0 -> AppLanguage.ARABIC
            else -> AppLanguage.ENGLISH
        }
    }

    fun stripSocialUiChrome(raw: String): String {
        val chromeLines = setOf(
            "like", "comment", "share", "send", "follow", "sponsored",
            "أعجبني", "تعليق", "مشاركة", "إرسال", "متابعة", "ممول",
            "بەدڵبوون", "کۆمێنت", "هاوبەشکردن", "ناردن", "سپۆنسەرکراو"
        )
        return raw.lines()
            .map { it.trim() }
            .filter { line ->
                line.length > 2 &&
                    !chromeLines.contains(line.lowercase()) &&
                    !line.matches(Regex("""^[\d.,\s]+[KMkm]?$"""))
            }
            .joinToString("\n")
            .trim()
            .ifEmpty { raw.trim() }
    }

    fun extractSearchClaims(cleanedText: String): List<String> {
        val urlStripped = cleanedText.replace(Regex("""https?://\S+"""), " ").trim()
        if (urlStripped.isBlank()) {
            return listOf(cleanedText.take(120))
        }
        val sentences = urlStripped
            .split(Regex("""[.!؟?\n]+"""))
            .map { it.trim() }
            .filter { it.length >= 12 }
        val primary = (sentences.maxByOrNull { it.length } ?: urlStripped)
            .split(Regex("""\s+"""))
            .take(16)
            .joinToString(" ")
        return listOf(primary).filter { it.isNotBlank() }
    }

    private fun scoreClickbaitLexicon(lower: String): Float {
        var hits = 0
        for (phrase in ALL_CLICKBAIT_PHRASES) {
            if (lower.contains(phrase)) hits++
        }
        return when {
            hits == 0 -> 0.12f
            hits == 1 -> 0.56f
            hits == 2 -> 0.78f
            else -> 0.94f
        }
    }

    private fun scoreUrgencyFormatting(text: String): Float {
        if (text.isEmpty()) return 0.1f
        val excl = text.count { it == '!' }
        val ques = text.count { it == '?' || it == '؟' }
        val letters = text.count { it.isLetter() }.coerceAtLeast(1)
        val upperLatin = text.count { it in 'A'..'Z' }
        val upperRatio = upperLatin.toFloat() / letters

        val repeatedPunct = if (text.contains("!!") || text.contains("؟؟") || text.contains("??")) 0.35f else 0f
        val density = ((excl + ques).toFloat() / (text.length / 25f).coerceAtLeast(1f)).coerceIn(0f, 1f)
        val capsPenalty = if (upperRatio > 0.35f && upperLatin > 8) 0.35f else 0f
        return (0.45f * density + repeatedPunct + capsPenalty).coerceIn(0.08f, 0.98f)
    }

    private fun scoreSourceAttribution(lower: String): Float {
        val officialHits = OFFICIAL_SOURCE_MARKERS.count { lower.contains(it) }
        val vagueHits = VAGUE_RUMOR_MARKERS.count { lower.contains(it) }
        val hasNumbersOrDate = Regex("""\b(20\d{2}|\d{1,2}/\d{1,2}|\d+%)\b""").containsMatchIn(lower)
        val hasUrl = lower.contains("http://") || lower.contains("https://")

        var risk = 0.58f
        risk -= (officialHits * 0.18f)
        if (hasNumbersOrDate) risk -= 0.08f
        if (hasUrl) risk -= 0.08f
        risk += (vagueHits * 0.20f)
        return risk.coerceIn(0.10f, 0.95f)
    }

    private fun scoreAiUniformityAndMarkers(text: String, lower: String): Float {
        val aiMarkerHits = AI_PHRASING_MARKERS.count { lower.contains(it) }
        val sentences = text.split(Regex("""[.!؟?\n]+"""))
            .map { it.trim() }
            .filter { it.length >= 10 }

        val uniformityScore = if (sentences.size >= 3) {
            val lengths = sentences.map { it.length.toFloat() }
            val mean = lengths.average().toFloat().coerceAtLeast(1f)
            var variance = 0.0
            for (l in lengths) {
                val d = l - mean
                variance += d * d
            }
            val cv = sqrt(variance / lengths.size).toFloat() / mean
            // Uniform sentence lengths (low coefficient of variation) are characteristic of LLM prose
            (1f - (cv / 0.45f)).coerceIn(0f, 0.7f)
        } else {
            0.22f
        }

        val markerBoost = (aiMarkerHits * 0.28f).coerceAtMost(0.65f)
        return (0.55f * uniformityScore + 0.45f * markerBoost).coerceIn(0.10f, 0.95f)
    }

    private fun buildLocalizedSummary(
        lang: AppLanguage,
        verdict: Verdict,
        clickbait: Float,
        sourceRisk: Float,
        aiStyle: Float
    ): String = when (lang) {
        AppLanguage.ARABIC -> when (verdict) {
            Verdict.FAKE -> "تحذير أوفلاين: النص يحمل مؤشرات إثارة وتضليل عالية (${(clickbait * 100).roundToInt()}%) مع غياب المصادر الموثوقة."
            Verdict.SUSPICIOUS -> "صياغة مشتبه بها: توجد إشارات ترويج غير موثقة أو أنماط آلية (${(aiStyle * 100).roundToInt()}%)."
            Verdict.REAL -> "صياغة متوازنة مدعومة بذكر مصادر وأرقام أو تواريخ محددة."
            Verdict.UNVERIFIED -> "لا توجد إشارات إثارة حادة، لكن النص يحتاج إلى مطابقة مع مصادر رسمية (نسبة نقص المصدر ${(sourceRisk * 100).roundToInt()}%)."
        }
        AppLanguage.KURDISH_SORANI -> when (verdict) {
            Verdict.FAKE -> "ئاگاداری ئۆفلاین: دەقەکە نیشانەی بەرزی هەواڵی چەواشە و سەرنجڕاکێشانی تێدایە (${(clickbait * 100).roundToInt()}%) بەبێ سەرچاوەی فەرمی."
            Verdict.SUSPICIOUS -> "دەقێکی گوماناوییە: نیشانەی بڵاوکردنەوەی ناڕوون یان شێوازی AI تێدا دۆزرایەوە (${(aiStyle * 100).roundToInt()}%)."
            Verdict.REAL -> "شێوازێکی هاوسەنگە و ئاماژەی بە سەرچاوە و ژمارەی دیاریکراو کردووە."
            Verdict.UNVERIFIED -> "دەقەکە بێ زیادەڕەوییە، بەڵام پێویستی بە پشتڕاستکردنەوەی سەرچاوەی فەرمی هەیە."
        }
        AppLanguage.ENGLISH -> when (verdict) {
            Verdict.FAKE -> "Offline warning: high sensational/hoax score (${(clickbait * 100).roundToInt()}%) and missing official attribution."
            Verdict.SUSPICIOUS -> "Suspicious framing: unverified forwarding cues or synthetic prose markers (${(aiStyle * 100).roundToInt()}%)."
            Verdict.REAL -> "Balanced phrasing with attributed official sources or verifiable figures."
            Verdict.UNVERIFIED -> "Neutral wording offline, but factual verification requires matching external sources."
        }
    }

    companion object {
        private val KURDISH_SPECIFIC_CHARS = setOf('پ', 'چ', 'ژ', 'ڤ', 'گ', 'ڵ', 'ڕ', 'ێ', 'ۆ', 'ە', 'ھ')
        private val KURDISH_KEYWORDS = listOf("بەپەلە", "هەواڵ", "ڕایگەیاند", "سەرچاوە", "کوردستان", "دەڵێت", "سڕینەوە")

        private val ALL_CLICKBAIT_PHRASES = listOf(
            // Arabic
            "عاجل جداً", "عاجل جدا", "لن تصدق", "شاهد قبل الحذف", "انشرها فوراً", "انشرها فورا",
            "مفاجأة صادمة", "كارثة حقيقية", "تسريب خطير", "يحدث الآن بالصور", "احذفوه قبل",
            "اعادة توجيه للجميع", "أمر خطير", "هدية مجانية للجميع", "سجل الآن قبل انتهاء",
            // Kurdish Sorani
            "پێش سڕینەوە بیبینە", "باوەڕ ناکەیت", "بڵاوی بکەنەوە پێش", "نهێنی گەورە",
            "شۆک و سەرسوڕمان", "دیاری بەخۆڕایی بۆ هەمووان", "فریا بکەوە پێش تەواوبوون",
            "بەپەلە و گرنگ", "ئاشکرا بوو پێش سڕینەوە",
            // English
            "shocking", "watch before deleted", "share before banned", "you won't believe",
            "forward to everyone", "secret they hide", "breaking urgent alert", "100% free giveaway",
            "doctors hate this", "miracle cure"
        )

        private val OFFICIAL_SOURCE_MARKERS = listOf(
            "أعلنت وزارة", "بيان رسمي", "وكالة الأنباء", "المرصد", "رويترز", "وكالة فرانس", "الأمم المتحدة",
            "وەزارەتی", "بەیاننامەی فەرمی", "ئاژانسی هەواڵ", "سەرچاوەی فەرمی", "پەرلەمان",
            "ministry of", "official statement", "reuters", "associated press", "united nations", "according to"
        )

        private val VAGUE_RUMOR_MARKERS = listOf(
            "مصادر مطلعة", "قال أحدهم", "منقول من كروب", "يقال أن", "أخبار متداولة",
            "سەرچاوەیەکی نادیار", "دەوترێت", "بڵاوکراوەتەوە گوایە",
            "anonymous insider", "they don't want you to know", "people are saying", "forwarded many times"
        )

        private val AI_PHRASING_MARKERS = listOf(
            "في ختام المطاف", "من الجدير بالذكر", "في عالم متسارع", "علاوة على ذلك",
            "شایانی باسە", "لە کۆتاییدا دەکرێت بڵێین", "لە جیهانێکی پڕ لە گۆڕانکاری",
            "in conclusion", "it is worth noting", "delve into", "in today's fast-paced"
        )
    }
}
