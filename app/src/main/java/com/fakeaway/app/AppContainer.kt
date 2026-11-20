package com.fakeaway.app

import android.content.Context
import com.fakeaway.app.core.domain.DefaultDispatcherProvider
import com.fakeaway.app.core.domain.DispatcherProvider
import com.fakeaway.app.core.network.AndroidConnectivityChecker
import com.fakeaway.app.core.network.ConnectivityChecker
import com.fakeaway.app.core.onnx.OnnxModelStore
import com.fakeaway.app.core.storage.AppPreferences
import com.fakeaway.app.core.storage.FakeAwayDatabase
import com.fakeaway.app.features.fact_checker.data.GeminiFactCheckClient
import com.fakeaway.app.features.fact_checker.data.HybridFactCheckEngine
import com.fakeaway.app.features.fact_checker.data.OfflineTextForensicsAnalyzer
import com.fakeaway.app.features.fact_checker.data.OnlineNewsCorroborator
import com.fakeaway.app.features.fact_checker.data.TathabbatBackendClient
import com.fakeaway.app.features.fact_checker.domain.FactCheckEngine
import com.fakeaway.app.features.fact_checker.domain.VerifyClaimUseCase
import com.fakeaway.app.features.history.data.RoomScanHistoryRepository
import com.fakeaway.app.features.history.domain.ClearHistoryUseCase
import com.fakeaway.app.features.history.domain.DeleteHistoryItemUseCase
import com.fakeaway.app.features.history.domain.ObserveHistoryUseCase
import com.fakeaway.app.features.history.domain.ScanHistoryRepository
import com.fakeaway.app.features.screen_shield.data.AndroidOfflineOcrEngine
import com.fakeaway.app.features.screen_shield.data.HybridImageAnalysisEngine
import com.fakeaway.app.features.screen_shield.data.ImageForensicsAnalyzer
import com.fakeaway.app.features.screen_shield.data.ShieldRuntimeState
import com.fakeaway.app.features.screen_shield.data.SmogyOnnxImageScorer
import com.fakeaway.app.features.screen_shield.domain.AnalyzeImageUseCase
import com.fakeaway.app.features.screen_shield.domain.AnalyzeScreenUseCase
import com.fakeaway.app.features.screen_shield.domain.ImageForensicsEngine
import com.fakeaway.app.features.screen_shield.domain.ScreenTextExtractor
import com.fakeaway.app.features.share_router.ParseShareIntentUseCase
import com.fakeaway.app.features.voice_detector.data.AcousticForensicsAnalyzer
import com.fakeaway.app.features.voice_detector.data.HybridVoiceAnalysisEngine
import com.fakeaway.app.features.voice_detector.data.Wav2Vec2OnnxVoiceScorer
import com.fakeaway.app.features.voice_detector.domain.AnalyzeVoiceUseCase
import com.fakeaway.app.features.voice_detector.domain.VoiceAnalysisEngine
import com.fakeaway.app.features.voice_detector.presentation.AndroidPcmAudioPlayer
import com.fakeaway.app.features.voice_detector.presentation.PcmAudioPlayer
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * Lightweight manual dependency injection container wiring Fake Away's vertical slices.
 */
class AppContainer(context: Context) {

    private val appContext = context.applicationContext

    val dispatchers: DispatcherProvider = DefaultDispatcherProvider()
    val preferences: AppPreferences = AppPreferences(appContext)
    val connectivityChecker: ConnectivityChecker = AndroidConnectivityChecker(appContext)

    val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()

    private val database: FakeAwayDatabase by lazy { FakeAwayDatabase.create(appContext) }

    val historyRepository: ScanHistoryRepository by lazy {
        RoomScanHistoryRepository(database.scanRecordDao())
    }

    val modelStore: OnnxModelStore by lazy {
        OnnxModelStore(appContext, httpClient)
    }

    val shieldRuntimeState: ShieldRuntimeState = ShieldRuntimeState()

    val voiceEngine: VoiceAnalysisEngine by lazy {
        HybridVoiceAnalysisEngine(
            dspAnalyzer = AcousticForensicsAnalyzer(),
            neuralScorer = Wav2Vec2OnnxVoiceScorer(modelStore)
        )
    }

    val analyzeVoiceUseCase: AnalyzeVoiceUseCase by lazy {
        AnalyzeVoiceUseCase(voiceEngine, historyRepository, dispatchers)
    }

    val pcmAudioPlayer: PcmAudioPlayer by lazy { AndroidPcmAudioPlayer() }

    val tathabbatClient: TathabbatBackendClient by lazy { TathabbatBackendClient(httpClient) }

    val factCheckEngine: FactCheckEngine by lazy {
        HybridFactCheckEngine(
            offlineAnalyzer = OfflineTextForensicsAnalyzer(),
            newsCorroborator = OnlineNewsCorroborator(httpClient),
            geminiClient = GeminiFactCheckClient(httpClient),
            connectivityChecker = connectivityChecker,
            dispatchers = dispatchers,
            isStrictOffline = { preferences.strictOfflineMode.value },
            geminiKeyProvider = { preferences.geminiApiKey.value },
            tathabbatClient = tathabbatClient,
            tathabbatUrlProvider = { preferences.tathabbatBaseUrl.value }
        )
    }

    val verifyClaimUseCase: VerifyClaimUseCase by lazy {
        VerifyClaimUseCase(factCheckEngine, historyRepository, dispatchers)
    }

    val imageEngine: ImageForensicsEngine by lazy {
        HybridImageAnalysisEngine(
            forensicsAnalyzer = ImageForensicsAnalyzer(),
            neuralScorer = SmogyOnnxImageScorer(modelStore)
        )
    }

    val ocrExtractor: ScreenTextExtractor by lazy {
        AndroidOfflineOcrEngine(appContext)
    }

    val analyzeImageUseCase: AnalyzeImageUseCase by lazy {
        AnalyzeImageUseCase(imageEngine, historyRepository, dispatchers)
    }

    val analyzeScreenUseCase: AnalyzeScreenUseCase by lazy {
        AnalyzeScreenUseCase(
            textExtractor = ocrExtractor,
            imageEngine = imageEngine,
            factCheckEngine = factCheckEngine,
            historyRepository = historyRepository,
            dispatchers = dispatchers
        )
    }

    val observeHistoryUseCase: ObserveHistoryUseCase by lazy { ObserveHistoryUseCase(historyRepository) }
    val deleteHistoryItemUseCase: DeleteHistoryItemUseCase by lazy { DeleteHistoryItemUseCase(historyRepository) }
    val clearHistoryUseCase: ClearHistoryUseCase by lazy { ClearHistoryUseCase(historyRepository) }

    val parseShareIntentUseCase: ParseShareIntentUseCase = ParseShareIntentUseCase()
}
