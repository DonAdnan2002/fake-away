package com.fakeaway.app.features.screen_shield.domain

import com.fakeaway.app.core.domain.AppLanguage
import com.fakeaway.app.core.domain.DispatcherProvider
import com.fakeaway.app.core.domain.ScanType
import com.fakeaway.app.core.domain.Verdict
import com.fakeaway.app.features.fact_checker.domain.FactCheckEngine
import com.fakeaway.app.features.history.domain.ScanHistoryItem
import com.fakeaway.app.features.history.domain.ScanHistoryRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Abstraction for image pixel + metadata + optional ONNX inspection.
 */
interface ImageForensicsEngine {
    suspend fun analyzePixels(
        argbPixels: IntArray,
        width: Int,
        height: Int,
        rawBytes: ByteArray? = null
    ): ImageAnalysisResult
}

/**
 * Abstraction for offline OCR extraction across Arabic, Kurdish Sorani, and English.
 */
interface ScreenTextExtractor {
    suspend fun extractText(
        argbPixels: IntArray,
        width: Int,
        height: Int
    ): String
}

/**
 * Analyzes an individual image/screenshot via pixel forensics and stores local history.
 */
class AnalyzeImageUseCase(
    private val imageEngine: ImageForensicsEngine,
    private val historyRepository: ScanHistoryRepository,
    private val dispatchers: DispatcherProvider
) {
    suspend operator fun invoke(
        argbPixels: IntArray,
        width: Int,
        height: Int,
        rawBytes: ByteArray? = null,
        title: String = "Image Scan"
    ): ImageAnalysisResult = withContext(dispatchers.default) {
        val result = imageEngine.analyzePixels(argbPixels, width, height, rawBytes)
        val pct = (result.aiScore * 100f).roundToInt()
        historyRepository.save(
            ScanHistoryItem(
                scanType = ScanType.IMAGE,
                title = title,
                verdict = result.verdict,
                fakeScore = result.aiScore,
                analysisMode = result.analysisMode,
                summary = "AI Score: $pct% • FFT: ${(result.fftGridScore * 100).roundToInt()}%" +
                    (result.metadataSignature?.let { " • Marker: $it" } ?: "")
            )
        )
        result
    }
}

/**
 * End-to-end Screen Shield use case: extracts text (OCR) + checks post pixels + verifies claims in parallel.
 */
class AnalyzeScreenUseCase(
    private val textExtractor: ScreenTextExtractor,
    private val imageEngine: ImageForensicsEngine,
    private val factCheckEngine: FactCheckEngine,
    private val historyRepository: ScanHistoryRepository,
    private val dispatchers: DispatcherProvider
) {
    suspend operator fun invoke(
        ocrPixels: IntArray,
        ocrWidth: Int,
        ocrHeight: Int,
        mediaPixels: IntArray,
        mediaWidth: Int,
        mediaHeight: Int,
        uiLanguage: AppLanguage,
        previewImagePath: String? = null
    ): ScreenScanResult = withContext(dispatchers.default) {
        val textDeferred = async {
            runCatching { textExtractor.extractText(ocrPixels, ocrWidth, ocrHeight) }.getOrDefault("")
        }
        val imageDeferred = async {
            if (mediaWidth >= 16 && mediaHeight >= 16) {
                runCatching { imageEngine.analyzePixels(mediaPixels, mediaWidth, mediaHeight, null) }.getOrNull()
            } else {
                null
            }
        }

        val extractedText = textDeferred.await().trim()
        val imageResult = imageDeferred.await()
        val factResult = if (extractedText.length >= 12) {
            runCatching { factCheckEngine.verify(extractedText, uiLanguage) }.getOrNull()
        } else {
            null
        }

        val imgScore = imageResult?.aiScore ?: 0f
        val textScore = factResult?.fakeRiskScore ?: 0f
        val combinedRisk = when {
            imageResult != null && factResult != null -> max(0.55f * imgScore + 0.45f * textScore, max(imgScore, textScore) * 0.90f)
            imageResult != null -> imgScore
            factResult != null -> textScore
            else -> 0f
        }.coerceIn(0f, 1f)

        val overallVerdict = when {
            combinedRisk >= 0.65f -> Verdict.FAKE
            combinedRisk >= 0.38f -> Verdict.SUSPICIOUS
            extractedText.isBlank() && imageResult == null -> Verdict.UNVERIFIED
            factResult?.verdict == Verdict.UNVERIFIED && imgScore < 0.38f -> Verdict.UNVERIFIED
            else -> Verdict.REAL
        }

        val mode = factResult?.analysisMode ?: imageResult?.analysisMode ?: com.fakeaway.app.core.domain.AnalysisMode.OFFLINE_FORENSIC

        val title = factResult?.extractedClaims?.firstOrNull()?.take(60)
            ?: extractedText.lineSequence().firstOrNull()?.take(60)
            ?: "Screen Capture"

        val summary = buildString {
            append("Overall ${(combinedRisk * 100).roundToInt()}%")
            imageResult?.let { append(" • Image ${(it.aiScore * 100).roundToInt()}%") }
            if (extractedText.isNotBlank()) {
                append(" • OCR: ${extractedText.take(80)}")
            }
        }

        historyRepository.save(
            ScanHistoryItem(
                scanType = ScanType.SCREEN,
                title = title,
                verdict = overallVerdict,
                fakeScore = combinedRisk,
                analysisMode = mode,
                summary = summary
            )
        )

        ScreenScanResult(
            extractedText = extractedText,
            imageResult = imageResult,
            factCheckResult = factResult,
            overallVerdict = overallVerdict,
            overallRiskScore = combinedRisk,
            analysisMode = mode,
            previewImagePath = previewImagePath
        )
    }
}
