package com.fakeaway.app.features.voice_detector

import com.fakeaway.app.core.domain.AnalysisMode
import com.fakeaway.app.features.voice_detector.data.AcousticForensicsAnalyzer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AcousticForensicsAnalyzerTest {

    private val analyzer = AcousticForensicsAnalyzer()

    @Test
    fun analyze_syntheticVoiceScoresHigherRiskThanOrganicVoice() {
        val syntheticPcm = AcousticForensicsAnalyzer.generateDemoPcm(synthetic = true, durationSeconds = 3.2f)
        val organicPcm = AcousticForensicsAnalyzer.generateDemoPcm(synthetic = false, durationSeconds = 3.2f)

        val syntheticResult = analyzer.analyze(syntheticPcm, "Synthetic Clip")
        val organicResult = analyzer.analyze(organicPcm, "Organic Clip")

        assertEquals(AnalysisMode.OFFLINE_FORENSIC, syntheticResult.analysisMode)
        assertEquals(64, syntheticResult.waveformAmplitudes.size)
        assertTrue("Expected >= 2 timeline segments", syntheticResult.timelineSegments.size >= 2)
        assertTrue(
            "Synthetic (${syntheticResult.overallFakeScore}) should exceed organic (${organicResult.overallFakeScore})",
            syntheticResult.overallFakeScore > organicResult.overallFakeScore + 0.20f
        )
        assertTrue(syntheticResult.overallFakeScore >= 0.55f)
        assertTrue(organicResult.overallFakeScore <= 0.45f)
    }
}
