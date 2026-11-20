package com.fakeaway.app.features.voice_detector.presentation

import com.fakeaway.app.features.voice_detector.domain.VoiceAnalysisResult

enum class VoiceErrorType {
    TOO_SHORT,
    DECODE_FAILED,
    MIC_PERMISSION
}

data class VoiceDetectorUiState(
    val isAnalyzing: Boolean = false,
    val isRecording: Boolean = false,
    val recordingSeconds: Int = 0,
    val isPlaying: Boolean = false,
    val playbackProgress: Float = 0f,
    val result: VoiceAnalysisResult? = null,
    val errorType: VoiceErrorType? = null,
    val bannerMessage: String? = null
)
