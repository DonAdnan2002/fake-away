package com.fakeaway.app.e2e

import com.fakeaway.app.core.domain.AnalysisMode
import com.fakeaway.app.core.domain.AppLanguage
import com.fakeaway.app.core.domain.Verdict
import com.fakeaway.app.core.network.ConnectivityChecker
import com.fakeaway.app.features.fact_checker.data.GeminiFactCheckClient
import com.fakeaway.app.features.fact_checker.data.HybridFactCheckEngine
import com.fakeaway.app.features.fact_checker.data.OfflineTextForensicsAnalyzer
import com.fakeaway.app.features.fact_checker.data.OnlineNewsCorroborator
import com.fakeaway.app.features.fact_checker.data.TathabbatBackendClient
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

class TathabbatBridgeE2eTest {

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
    fun hybridEngine_appliesHumanReviewedTathabbatVerdictAndEvidenceSources() = runBlocking {
        val baseUrl = server.url("/").toString().trimEnd('/')
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                return when {
                    request.path == "/api/mobile/verify" -> MockResponse().setBody(
                        """
                        {
                          "matched_claim": {
                            "id": 42,
                            "text": "الإنفجار في الموصل أغلق الجسر القديم",
                            "status": "false",
                            "reviewer": "د. سارة العراقي",
                            "rationale": "تم التحقق مع عمليات نينوى: الخبر مفبرك والجسر مفتوح.",
                            "similarity": 0.84,
                            "evidence": [
                              {
                                "id": 1,
                                "label": "بيان محافظة نينوى",
                                "url": "https://ninawa.gov.iq/statements/104",
                                "publisher": "محافظة نينوى",
                                "kind": "source",
                                "note": "بيان رسمي"
                              }
                            ]
                          },
                          "related_articles": [],
                          "note": "ok"
                        }
                        """.trimIndent()
                    )
                    request.path?.startsWith("/rss/search") == true ->
                        MockResponse().setBody("<rss><channel></channel></rss>")
                    request.path?.startsWith("/w/api.php") == true ->
                        MockResponse().setBody("""{"query":{"search":[]}}""")
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }

        val client = TathabbatBackendClient(okHttp)
        val engine = HybridFactCheckEngine(
            offlineAnalyzer = OfflineTextForensicsAnalyzer(),
            newsCorroborator = OnlineNewsCorroborator(okHttp, newsBaseUrl = baseUrl, wikiBaseUrlForLang = { baseUrl }),
            geminiClient = GeminiFactCheckClient(okHttp, baseUrl = baseUrl),
            connectivityChecker = object : ConnectivityChecker { override fun isOnline(): Boolean = true },
            dispatchers = ioDispatchers,
            isStrictOffline = { false },
            geminiKeyProvider = { "" },
            tathabbatClient = client,
            tathabbatUrlProvider = { baseUrl }
        )

        val res = VerifyClaimUseCase(engine, historyRepo, ioDispatchers)(
            "انفجار في موصل أغلق الجسر القديم",
            AppLanguage.ARABIC
        ).getOrThrow()

        assertEquals(AnalysisMode.HYBRID_ONLINE, res.analysisMode)
        assertEquals(Verdict.FAKE, res.verdict)
        assertTrue(res.signals.any { it.key == "tathabbat_newsroom" })
        assertTrue(res.explanation.contains("عمليات نينوى"))
        assertTrue(res.sources.any { it.url == "https://ninawa.gov.iq/statements/104" })
    }

    @Test
    fun tathabbatClient_escalatesClaimAndDegradesGracefullyOnServerError() = runBlocking {
        val baseUrl = server.url("/").toString().trimEnd('/')
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                return when (request.path) {
                    "/api/mobile/escalate" -> MockResponse().setBody(
                        """{"claim_id": 77, "status": "in_review", "message": "queued"}"""
                    )
                    "/api/health" -> MockResponse().setBody(
                        """{"status": "ok", "articles": 12, "claims": 5}"""
                    )
                    else -> MockResponse().setResponseCode(503)
                }
            }
        }

        val client = TathabbatBackendClient(okHttp)
        val health = client.checkHealth(baseUrl).getOrThrow()
        assertEquals(12, health.articles)
        assertEquals(5, health.claims)

        val escalatedId = client.escalateToNewsroom(
            baseUrl = baseUrl,
            text = "خبر مشبوه من فيسبوك يحتاج مراجعة",
            language = AppLanguage.ARABIC,
            offlineRiskScore = 0.71f,
            scanType = "NEWS_TEXT",
            summary = "Offline clickbait detected"
        ).getOrThrow()
        assertEquals(77L, escalatedId)

        // When /api/mobile/verify returns 503, HybridFactCheckEngine falls back cleanly to OFFLINE_FORENSIC
        val engine = HybridFactCheckEngine(
            offlineAnalyzer = OfflineTextForensicsAnalyzer(),
            newsCorroborator = OnlineNewsCorroborator(okHttp, newsBaseUrl = baseUrl, wikiBaseUrlForLang = { baseUrl }),
            geminiClient = GeminiFactCheckClient(okHttp, baseUrl = baseUrl),
            connectivityChecker = object : ConnectivityChecker { override fun isOnline(): Boolean = true },
            dispatchers = ioDispatchers,
            isStrictOffline = { false },
            geminiKeyProvider = { "" },
            tathabbatClient = client,
            tathabbatUrlProvider = { baseUrl }
        )

        val fallback = VerifyClaimUseCase(engine, historyRepo, ioDispatchers)(
            "SHOCKING!! Watch before deleted: forward to everyone!!!",
            AppLanguage.ENGLISH
        ).getOrThrow()

        assertEquals(AnalysisMode.OFFLINE_FORENSIC, fallback.analysisMode)
        assertEquals(Verdict.FAKE, fallback.verdict)
    }
}
