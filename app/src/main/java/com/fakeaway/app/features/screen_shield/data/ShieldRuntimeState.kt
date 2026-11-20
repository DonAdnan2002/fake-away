package com.fakeaway.app.features.screen_shield.data

import com.fakeaway.app.features.screen_shield.domain.ScreenScanResult
import com.fakeaway.app.features.voice_detector.domain.VoiceAnalysisResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Reactive bridge between FloatingShieldService and ScreenShield / VoiceDetector UI screens.
 */
class ShieldRuntimeState {
    private val _isShieldRunning = MutableStateFlow(false)
    val isShieldRunning: StateFlow<Boolean> = _isShieldRunning.asStateFlow()

    private val _latestScreenScan = MutableStateFlow<ScreenScanResult?>(null)
    val latestScreenScan: StateFlow<ScreenScanResult?> = _latestScreenScan.asStateFlow()

    private val _latestOverlayVoiceScan = MutableStateFlow<VoiceAnalysisResult?>(null)
    val latestOverlayVoiceScan: StateFlow<VoiceAnalysisResult?> = _latestOverlayVoiceScan.asStateFlow()

    fun setShieldRunning(running: Boolean) {
        _isShieldRunning.value = running
    }

    fun publishScreenScan(result: ScreenScanResult) {
        _latestScreenScan.value = result
    }

    fun publishOverlayVoiceScan(result: VoiceAnalysisResult) {
        _latestOverlayVoiceScan.value = result
    }
}
