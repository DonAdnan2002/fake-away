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
import com.fakeaway.app.features.fact_checker.domain.VerifyClaimUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit

class FactCheckHybridE2eTest {

    private lateinit var server: MockWebServer
    private lateinit var historyRepo: InMemoryScanHistoryRepository
    private val ioDispatchers = TestDispatcherProvider(Dispatchers.IO)
    private val okHttp = OkHttpClient.Builder()
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(3, TimeUnit.SECONDS)
        .build()

    @Before
    fun setup() {
        server = MockWebServer()
        server.start()
        historyRepo = InMemoryScanHistoryRepository()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun strictOfflineMode_makesZeroNetworkRequestsAndPersistsArabicResult() = runBlocking {
        val engine = buildEngine(online = true, strictOffline = true, apiKey = "fake-key")
        val useCase = VerifyClaimUseCase(engine, historyRepo, ioDispatchers)

        val res = useCase(
            "عاجل جداً!! شاهد قبل الحذف تسريب خطير ومفاجأة صادمة انشرها فوراً!!!",
            AppLanguage.ARABIC
        ).getOrThrow()

        assertEquals(0, server.requestCount)
        assertEquals(AnalysisMode.OFFLINE_FORENSIC, res.analysisMode)
        assertEquals(AppLanguage.ARABIC, res.detectedLanguage)
        assertEquals(Verdict.FAKE, res.verdict)
        assertEquals(1, historyRepo.currentItems().size)
        assertEquals(ScanType.NEWS_TEXT, historyRepo.currentItems().first().scanType)
    }

    @Test
    fun hybridOnline_parsesGoogleNewsRssAndWikipediaDebunk() = runBlocking {
        val baseUrl = server.url("/").toString().trimEnd('/')
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty()
                return when {
                    path.startsWith("/rss/search") -> MockResponse().setBody(
                        """
                        <rss><channel>
                          <item>
                            <title>حقيقة خبر إغلاق المدارس غداً - تقرير مفبرك وإشاعة قديمة</title>
                            <link>https://news.example.iq/factcheck-schools</link>
                            <source>مسبار لتدقيق الحقائق</source>
                            <description>الخبر المتداول غير صحيح وإشاعة مكررة</description>
                          </item>
                        </channel></rss>
                        """.trimIndent()
                    )
                    path.startsWith("/w/api.php") -> MockResponse().setBody(
                        """{"query":{"search":[{"title":"العراق","snippet":"بيانات التعليم"}]}}"""
                    )
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }

        val engine = buildEngine(
            online = true,
            strictOffline = false,
            apiKey = "",
            baseUrl = baseUrl
        )
        val useCase = VerifyClaimUseCase(engine, historyRepo, ioDispatchers)

        val res = useCase("إغلاق المدارس غداً في بغداد", AppLanguage.ARABIC).getOrThrow()

        assertEquals(AnalysisMode.HYBRID_ONLINE, res.analysisMode)
        assertEquals(Verdict.FAKE, res.verdict)
        assertTrue(res.sources.any { it.publisher.contains("مسبار") })
    }

    @Test
    fun hybridOnline_parsesGeminiSearchGroundingInKurdishSorani() = runBlocking {
        val baseUrl = server.url("/").toString().trimEnd('/')
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty()
                return when {
                    path.contains("generateContent") -> MockResponse().setBody(
                        """
                        {
                          "candidates": [{
                            "content": {
                              "parts": [{
                                "text": "{\"verdict\":\"FAKE\",\"riskScore\":0.91,\"explanation\":\"ئەم هەواڵە ساختەیە و هیچ بنەمایەکی فەرمی نییە.\"}"
                              }]
                            },
                            "groundingMetadata": {
                              "groundingChunks": [
                                {"web": {"uri": "https://gov.krd/statement", "title": "Official KRG Clarification"}}
                              ]
                            }
                          }]
                        }
                        """.trimIndent()
                    )
                    path.startsWith("/rss/search") -> MockResponse().setBody("<rss><channel></channel></rss>")
                    path.startsWith("/w/api.php") -> MockResponse().setBody("""{"query":{"search":[]}}""")
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }

        val engine = buildEngine(
            online = true,
            strictOffline = false,
            apiKey = "test-gemini-key",
            baseUrl = baseUrl
        )
        val useCase = VerifyClaimUseCase(engine, historyRepo, ioDispatchers)

        val res = useCase(
            "بەپەلە لە هەولێر هەواڵێکی نوێ بڵاوکرایەوە",
            AppLanguage.KURDISH_SORANI
        ).getOrThrow()

        assertEquals(AnalysisMode.HYBRID_ONLINE, res.analysisMode)
        assertEquals(Verdict.FAKE, res.verdict)
        assertEquals("ئەم هەواڵە ساختەیە و هیچ بنەمایەکی فەرمی نییە.", res.explanation)
        assertTrue(res.sources.any { it.url == "https://gov.krd/statement" })
    }

    @Test
    fun hybridOnline_gracefullyFallsBackToOfflineWhenServerFails() = runBlocking {
        val baseUrl = server.url("/").toString().trimEnd('/')
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse =
                MockResponse().setResponseCode(500)
        }

        val engine = buildEngine(
            online = true,
            strictOffline = false,
            apiKey = "test-key",
            baseUrl = baseUrl
        )
        val useCase = VerifyClaimUseCase(engine, historyRepo, ioDispatchers)

        val res = useCase(
            "SHOCKING!! Watch before deleted: forward to everyone!!!",
            AppLanguage.ENGLISH
        ).getOrThrow()

        assertEquals(AnalysisMode.OFFLINE_FORENSIC, res.analysisMode)
        assertEquals(Verdict.FAKE, res.verdict)
    }

    private fun buildEngine(
        online: Boolean,
        strictOffline: Boolean,
        apiKey: String,
        baseUrl: String = server.url("/").toString().trimEnd('/')
    ): HybridFactCheckEngine = HybridFactCheckEngine(
        offlineAnalyzer = OfflineTextForensicsAnalyzer(),
        newsCorroborator = OnlineNewsCorroborator(
            httpClient = okHttp,
            newsBaseUrl = baseUrl,
            wikiBaseUrlForLang = { baseUrl }
        ),
        geminiClient = GeminiFactCheckClient(okHttp, baseUrl = baseUrl),
        connectivityChecker = object : ConnectivityChecker {
            override fun isOnline(): Boolean = online
        },
        dispatchers = ioDispatchers,
        isStrictOffline = { strictOffline },
        geminiKeyProvider = { apiKey }
    )
}
