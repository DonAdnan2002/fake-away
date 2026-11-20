package com.fakeaway.app.features.screen_shield.presentation

import com.fakeaway.app.features.screen_shield.domain.ImageAnalysisResult
import com.fakeaway.app.features.screen_shield.domain.ScreenScanResult

data class ScreenShieldUiState(
    val isShieldActive: Boolean = false,
    val isAnalyzing: Boolean = false,
    val latestScreenResult: ScreenScanResult? = null,
    val latestImageResult: ImageAnalysisResult? = null,
    val selectedPreviewPath: String? = null,
    val bannerMessage: String? = null
)
