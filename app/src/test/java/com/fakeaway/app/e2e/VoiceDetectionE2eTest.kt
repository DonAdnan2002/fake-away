package com.fakeaway.app.e2e

import com.fakeaway.app.core.domain.AnalysisMode
import com.fakeaway.app.core.domain.ScanType
import com.fakeaway.app.core.domain.Verdict
import com.fakeaway.app.core.dsp.AudioPcmDecoder
import com.fakeaway.app.features.voice_detector.data.AcousticForensicsAnalyzer
import com.fakeaway.app.features.voice_detector.data.HybridVoiceAnalysisEngine
import com.fakeaway.app.features.voice_detector.domain.AnalyzeVoiceUseCase
import com.fakeaway.app.features.voice_detector.presentation.VoiceDetectorViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class VoiceDetectionE2eTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var dispatchers: TestDispatcherProvider
    private lateinit var historyRepo: InMemoryScanHistoryRepository
    private lateinit var viewModel: VoiceDetectorViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        dispatchers = TestDispatcherProvider(testDispatcher)
        historyRepo = InMemoryScanHistoryRepository()
        val engine = HybridVoiceAnalysisEngine(
            dspAnalyzer = AcousticForensicsAnalyzer(),
            neuralScorer = null
        )
        val useCase = AnalyzeVoiceUseCase(engine, historyRepo, dispatchers)
        viewModel = VoiceDetectorViewModel(
            analyzeVoiceUseCase = useCase,
            dispatchers = dispatchers
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun fullVerticalSlice_decodesWavRunsEngineUpdatesViewModelAndPersistsHistory() = runTest(testDispatcher) {
        val syntheticSamples = AcousticForensicsAnalyzer.generateDemoPcm(synthetic = true, durationSeconds = 3.0f)
        val wavBytes = encodePcm16Wav(syntheticSamples)
        val decoded = AudioPcmDecoder.decodeWavBytes(wavBytes)

        viewModel.analyzePcm(
            samples16k = decoded.samples16k,
            sourceLabel = "WhatsApp_PTT_2026.wav"
        )

        val state = viewModel.uiState.value
        assertFalse(state.isAnalyzing)
        assertNotNull(state.result)
        val result = state.result!!
        assertEquals(AnalysisMode.OFFLINE_FORENSIC, result.analysisMode)
        assertTrue(result.overallFakeScore >= 0.55f)
        assertEquals(64, result.waveformAmplitudes.size)
        assertTrue(result.timelineSegments.isNotEmpty())

        val saved = historyRepo.currentItems()
        assertEquals(1, saved.size)
        assertEquals(ScanType.VOICE, saved.first().scanType)
        assertEquals("WhatsApp_PTT_2026.wav", saved.first().title)
        assertTrue(saved.first().verdict == Verdict.FAKE || saved.first().verdict == Verdict.SUSPICIOUS)
    }
}
