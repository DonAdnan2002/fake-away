package com.fakeaway.app.features.fact_checker

import com.fakeaway.app.core.domain.AppLanguage
import com.fakeaway.app.core.domain.Verdict
import com.fakeaway.app.features.fact_checker.data.OfflineTextForensicsAnalyzer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OfflineTextForensicsAnalyzerTest {

    private val analyzer = OfflineTextForensicsAnalyzer()

    @Test
    fun detectLanguage_accuratelyDistinguishesArabicEnglishAndKurdishSorani() {
        assertEquals(
            AppLanguage.ARABIC,
            analyzer.detectLanguage("أعلنت وزارة الصحة اليوم عن افتتاح مركز طبي جديد في بغداد")
        )
        assertEquals(
            AppLanguage.KURDISH_SORANI,
            analyzer.detectLanguage("بەپەلە و گرنگ لە هەولێر پێش سڕینەوە بیبینە سەرچاوەی فەرمی ڕایگەیاند")
        )
        assertEquals(
            AppLanguage.ENGLISH,
            analyzer.detectLanguage("Official statement from Reuters regarding international summit 2026")
        )
    }

    @Test
    fun analyze_flagsSensationalHoaxesAcrossAllThreeLanguages() {
        val arHoax = analyzer.analyze(
            "عاجل جداً!! شاهد قبل الحذف: تسريب خطير ومفاجأة صادمة للجميع انشرها فوراً!!!",
            AppLanguage.ARABIC
        )
        val ckbHoax = analyzer.analyze(
            "بەپەلە و گرنگ!! پێش سڕینەوە بیبینە: باوەڕ ناکەیت نهێنی گەورە ئاشکرا بوو، بڵاوی بکەنەوە پێش سڕینەوەی!!!",
            AppLanguage.KURDISH_SORANI
        )
        val enHoax = analyzer.analyze(
            "SHOCKING!! Watch before deleted: Secret they hide, forward to everyone 100% free giveaway!!!",
            AppLanguage.ENGLISH
        )

        assertEquals(Verdict.FAKE, arHoax.verdict)
        assertEquals(Verdict.FAKE, ckbHoax.verdict)
        assertEquals(Verdict.FAKE, enHoax.verdict)
        assertTrue(arHoax.fakeRiskScore >= 0.65f)
        assertTrue(ckbHoax.fakeRiskScore >= 0.65f)
        assertTrue(enHoax.fakeRiskScore >= 0.65f)
    }

    @Test
    fun analyze_scoresSourcedNeutralReportingLowAndStripsFacebookChrome() {
        val raw = """
            أعجبني
            تعليق
            مشاركة
            أعلنت وزارة التعليم العالي في بيان رسمي بتاريخ 19/11/2026 نتائج القبول المركزي بنسبة 85% عبر وكالة الأنباء الرسمية https://mohesr.gov.iq
        """.trimIndent()

        val result = analyzer.analyze(raw, AppLanguage.ARABIC)
        val claim = result.extractedClaims.firstOrNull().orEmpty()

        assertTrue(result.fakeRiskScore < 0.35f)
        assertEquals(Verdict.REAL, result.verdict)
        assertFalse(claim.contains("أعجبني"))
        assertFalse(claim.contains("مشاركة"))
    }
}
