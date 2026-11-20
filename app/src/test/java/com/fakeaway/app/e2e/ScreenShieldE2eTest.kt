package com.fakeaway.app.e2e

import com.fakeaway.app.core.domain.AnalysisMode
import com.fakeaway.app.core.domain.AppLanguage
import com.fakeaway.app.core.domain.ScanType
import com.fakeaway.app.core.domain.Verdict
import com.fakeaway.app.core.network.ConnectivityChecker
import com.fakeaway.app.features.fact_checker.data.GeminiFactCheckClient
import com.fakeaway.app.features.fact_checker.data.HybridFactCheckEngine
import com.fakeaway.app.features.fact_checker.data.OfflineTextForensicsAnalyzer
import com.fakeaway.app.features.fact_checker.data.OnlineNewsCorroborator
import com.fakeaway.app.features.screen_shield.data.HybridImageAnalysisEngine
import com.fakeaway.app.features.screen_shield.data.ImageForensicsAnalyzer
import com.fakeaway.app.features.screen_shield.domain.AnalyzeScreenUseCase
import com.fakeaway.app.features.screen_shield.domain.ScreenTextExtractor
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ScreenShieldE2eTest {

    @Test
    fun analyzeScreen_combinesOcrImageForensicsAndFactCheckAndSavesHistory() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val dispatchers = TestDispatcherProvider(dispatcher)
        val historyRepo = InMemoryScanHistoryRepository()

        val fakePostText = """
            أعجبني
            تعليق
            عاجل جداً!! شاهد قبل الحذف تسريب خطير ومفاجأة صادمة انشرها فوراً!!!
        """.trimIndent()

        val ocrExtractor = object : ScreenTextExtractor {
            override suspend fun extractText(argbPixels: IntArray, width: Int, height: Int): String = fakePostText
        }

        val imageEngine = HybridImageAnalysisEngine(
            forensicsAnalyzer = ImageForensicsAnalyzer(),
            neuralScorer = null
        )

        val factEngine = HybridFactCheckEngine(
            offlineAnalyzer = OfflineTextForensicsAnalyzer(),
            newsCorroborator = OnlineNewsCorroborator(OkHttpClient()),
            geminiClient = GeminiFactCheckClient(OkHttpClient()),
            connectivityChecker = object : ConnectivityChecker {
                override fun isOnline(): Boolean = false
            },
            dispatchers = dispatchers,
            isStrictOffline = { true },
            geminiKeyProvider = { "" }
        )

        val useCase = AnalyzeScreenUseCase(
            textExtractor = ocrExtractor,
            imageEngine = imageEngine,
            factCheckEngine = factEngine,
            historyRepository = historyRepo,
            dispatchers = dispatchers
        )

        val width = 64
        val height = 64
        val pixels = IntArray(width * height) { idx ->
            val x = idx % width
            val y = idx / width
            if ((x + y) % 2 == 0) 0xFFF5F5F5.toInt() else 0xFF151515.toInt()
        }

        val result = useCase(
            ocrPixels = pixels,
            ocrWidth = width,
            ocrHeight = height,
            mediaPixels = pixels,
            mediaWidth = width,
            mediaHeight = height,
            uiLanguage = AppLanguage.ARABIC
        )

        assertTrue(result.extractedText.contains("عاجل جداً"))
        assertNotNull(result.imageResult)
        assertNotNull(result.factCheckResult)
        assertEquals(AnalysisMode.OFFLINE_FORENSIC, result.analysisMode)
        assertTrue(result.overallVerdict == Verdict.FAKE || result.overallVerdict == Verdict.SUSPICIOUS)

        val saved = historyRepo.currentItems()
        assertEquals(1, saved.size)
        assertEquals(ScanType.SCREEN, saved.first().scanType)
    }
}
