package com.fakeaway.app.features.screen_shield.data

import com.fakeaway.app.core.domain.AnalysisMode
import com.fakeaway.app.core.domain.ForensicSignal
import com.fakeaway.app.core.domain.Verdict
import com.fakeaway.app.core.dsp.FftMath
import com.fakeaway.app.core.onnx.ModelSpec
import com.fakeaway.app.core.onnx.OnnxModelStore
import com.fakeaway.app.core.onnx.OnnxRuntimeSession
import com.fakeaway.app.features.screen_shield.domain.ImageAnalysisResult
import com.fakeaway.app.features.screen_shield.domain.ImageForensicsEngine
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Pure Kotlin image forensics engine combining:
 * 1) Header metadata / C2PA / AI generator provenance scan
 * 2) 2D FFT spectral grid & radial rolloff analysis (128x128)
 * 3) 8x8 spatial Laplacian noise inconsistency heatmap (inpainting localization)
 */
class ImageForensicsAnalyzer {

    fun analyzePixels(
        argbPixels: IntArray,
        width: Int,
        height: Int,
        rawBytes: ByteArray? = null
    ): ImageAnalysisResult {
        require(width >= 16 && height >= 16 && argbPixels.size >= width * height) {
            "Image too small for forensic analysis"
        }

        val metadata = inspectMetadata(rawBytes)
        val fftGridScore = compute2dFftAnomaly(argbPixels, width, height)
        val heatmap = compute8x8LaplacianHeatmap(argbPixels, width, height)
        val maxPatchAnomaly = heatmap.flatten().maxOrNull() ?: 0f
        val highAnomalyCells = heatmap.flatten().count { it >= 0.72f }

        val patchScore = (0.60f * maxPatchAnomaly + 0.40f * (highAnomalyCells / 12f).coerceIn(0f, 1f))
            .coerceIn(0f, 1f)

        var forensicScore = (
            0.46f * fftGridScore +
                0.34f * patchScore +
                (if (metadata.hasCameraExif) 0.08f else 0.20f)
            ).coerceIn(0f, 1f)

        if (metadata.aiMarker != null) {
            forensicScore = maxOf(forensicScore, 0.95f)
        }

        val signals = buildList {
            if (metadata.aiMarker != null) {
                add(
                    ForensicSignal(
                        key = "metadata_ai",
                        labelKey = "sig_metadata_ai",
                        score = 0.96f,
                        detail = "Header signature matched: ${metadata.aiMarker}",
                        isSuspicious = true
                    )
                )
            }
            add(
                ForensicSignal(
                    key = "camera_exif",
                    labelKey = "sig_camera_exif",
                    score = if (metadata.hasCameraExif) 0.15f else 0.62f,
                    detail = if (metadata.hasCameraExif) "Camera EXIF present" else "No camera EXIF provenance",
                    isSuspicious = !metadata.hasCameraExif
                )
            )
            add(
                ForensicSignal(
                    key = "fft_grid",
                    labelKey = "sig_fft_grid",
                    score = fftGridScore,
                    detail = "2D FFT spectral grid anomaly: ${(fftGridScore * 100).roundToInt()}%",
                    isSuspicious = fftGridScore >= 0.55f
                )
            )
            add(
                ForensicSignal(
                    key = "patch_inpainting",
                    labelKey = "sig_patch_inpainting",
                    score = patchScore,
                    detail = "$highAnomalyCells localized high-deviation patches",
                    isSuspicious = patchScore >= 0.55f
                )
            )
        }

        return ImageAnalysisResult(
            aiScore = forensicScore,
            verdict = Verdict.fromFakeScore(forensicScore),
            analysisMode = AnalysisMode.OFFLINE_FORENSIC,
            metadataSignature = metadata.aiMarker,
            hasCameraExif = metadata.hasCameraExif,
            fftGridScore = fftGridScore,
            heatmapGrid = heatmap,
            neuralScore = null,
            signals = signals
        )
    }

    internal data class MetadataScan(
        val aiMarker: String?,
        val hasCameraExif: Boolean
    )

    internal fun inspectMetadata(rawBytes: ByteArray?): MetadataScan {
        if (rawBytes == null || rawBytes.isEmpty()) return MetadataScan(null, false)
        // Inspect up to first 64KB + last 32KB (headers & trailing XMP/PNG chunks)
        val headEnd = rawBytes.size.coerceAtMost(65_536)
        val headStr = String(rawBytes, 0, headEnd, Charsets.ISO_8859_1)
        val tailStr = if (rawBytes.size > headEnd) {
            val tailStart = (rawBytes.size - 32_768).coerceAtLeast(headEnd)
            String(rawBytes, tailStart, rawBytes.size - tailStart, Charsets.ISO_8859_1)
        } else {
            ""
        }
        val combined = "$headStr\n$tailStr"
        val lower = combined.lowercase()

        val matchedAi = AI_METADATA_MARKERS.firstOrNull { lower.contains(it.lowercase()) }
        val hasExif = EXIF_CAMERA_MARKERS.any { combined.contains(it, ignoreCase = true) }
        return MetadataScan(matchedAi, hasExif)
    }

    internal fun compute2dFftAnomaly(pixels: IntArray, width: Int, height: Int): Float {
        val size = 64
        val gray = Array(size) { FloatArray(size) }
        for (r in 0 until size) {
            val sy = (r * height) / size
            for (c in 0 until size) {
                val sx = (c * width) / size
                gray[r][c] = luminance(pixels[sy * width + sx])
            }
        }
        // Remove DC mean
        var mean = 0f
        for (r in 0 until size) for (c in 0 until size) mean += gray[r][c]
        mean /= (size * size)
        for (r in 0 until size) for (c in 0 until size) gray[r][c] -= mean

        val mag = FftMath.fft2dMagnitude(gray)
        val center = size / 2
        var totalMidHigh = 0.0
        var countMidHigh = 0
        var peakVal = 0.0
        var highBandEnergy = 0.0
        var lowBandEnergy = 1e-6

        for (r in 0 until size) {
            for (c in 0 until size) {
                val dr = r - center
                val dc = c - center
                val radius = sqrt((dr * dr + dc * dc).toDouble())
                val valMag = mag[r][c].toDouble()
                if (radius in 3.0..12.0) {
                    lowBandEnergy += valMag
                } else if (radius > 12.0) {
                    highBandEnergy += valMag
                    totalMidHigh += valMag
                    countMidHigh++
                    if (valMag > peakVal) peakVal = valMag
                }
            }
        }

        val meanMidHigh = (totalMidHigh / countMidHigh.coerceAtLeast(1)).coerceAtLeast(1e-6)
        val peakToMean = (peakVal / meanMidHigh).toFloat()
        val hfRatio = (highBandEnergy / lowBandEnergy).toFloat()

        // Periodic diffusion / upsampling grids create stark peaks in mid-high 2D spectrum
        val peakRisk = ((peakToMean - 4.0f) / 14.0f).coerceIn(0f, 1f)
        // Over-smooth synthetic backgrounds drop high-band ratio
        val smoothRisk = if (hfRatio < 0.7f) 0.55f else if (hfRatio > 8.0f) 0.65f else 0.18f
        return (0.65f * peakRisk + 0.35f * smoothRisk).coerceIn(0f, 1f)
    }

    internal fun compute8x8LaplacianHeatmap(pixels: IntArray, width: Int, height: Int): List<List<Float>> {
        val grid = 8
        val rawNoise = Array(grid) { FloatArray(grid) }
        val flatValues = mutableListOf<Float>()

        for (gr in 0 until grid) {
            val yStart = ((gr * height) / grid).coerceAtLeast(1)
            val yEnd = (((gr + 1) * height) / grid).coerceAtMost(height - 1)
            for (gc in 0 until grid) {
                val xStart = ((gc * width) / grid).coerceAtLeast(1)
                val xEnd = (((gc + 1) * width) / grid).coerceAtMost(width - 1)
                var energySum = 0.0
                var count = 0
                val stepY = ((yEnd - yStart) / 12).coerceAtLeast(1)
                val stepX = ((xEnd - xStart) / 12).coerceAtLeast(1)
                var y = yStart
                while (y < yEnd) {
                    var x = xStart
                    while (x < xEnd) {
                        val center = luminance(pixels[y * width + x])
                        val lap = abs(
                            4f * center -
                                luminance(pixels[(y - 1) * width + x]) -
                                luminance(pixels[(y + 1) * width + x]) -
                                luminance(pixels[y * width + (x - 1)]) -
                                luminance(pixels[y * width + (x + 1)])
                        )
                        energySum += ln(1.0 + lap * 255.0)
                        count++
                        x += stepX
                    }
                    y += stepY
                }
                val cellVal = (energySum / count.coerceAtLeast(1)).toFloat()
                rawNoise[gr][gc] = cellVal
                flatValues += cellVal
            }
        }

        val sorted = flatValues.sorted()
        val median = sorted[sorted.size / 2]
        val deviations = flatValues.map { abs(it - median) }.sorted()
        val mad = deviations[deviations.size / 2].coerceAtLeast(0.12f)

        return List(grid) { r ->
            List(grid) { c ->
                val z = abs(rawNoise[r][c] - median) / (mad * 2.8f)
                z.coerceIn(0.05f, 1f)
            }
        }
    }

    private fun luminance(argb: Int): Float {
        val r = (argb ushr 16) and 0xFF
        val g = (argb ushr 8) and 0xFF
        val b = argb and 0xFF
        return (0.299f * r + 0.587f * g + 0.114f * b) / 255f
    }

    companion object {
        private val AI_METADATA_MARKERS = listOf(
            "Midjourney", "DALL-E", "DALL·E", "Stable Diffusion", "ComfyUI",
            "Automatic1111", "Adobe Firefly", "Google Imagen", "DreamStudio",
            "c2pa", "jumd", "jumb", "trainedAlgorithmicMedia", "AI generated"
        )
        private val EXIF_CAMERA_MARKERS = listOf(
            "Exif", "Canon", "Nikon", "Sony", "Apple", "Samsung", "Pixel", "Xiaomi", "Huawei"
        )
    }
}

/**
 * Runs optional local SMOGY Swin Vision ONNX model (224x224 ImageNet NCHW, logits [artificial=0, human=1]).
 */
class SmogyOnnxImageScorer(
    private val modelStore: OnnxModelStore
) {
    private var cachedSession: OnnxRuntimeSession? = null

    @Synchronized
    fun scoreIfAvailable(argbPixels: IntArray, width: Int, height: Int): Float? {
        val modelFile = modelStore.resolveModelFile(ModelSpec.IMAGE_SMOGY) ?: return null
        val session = runCatching {
            cachedSession ?: OnnxRuntimeSession(modelFile).also { cachedSession = it }
        }.getOrNull() ?: return null

        val target = 224
        val planeSize = target * target
        val nchw = FloatArray(3 * planeSize)
        val mean = floatArrayOf(0.485f, 0.456f, 0.406f)
        val std = floatArrayOf(0.229f, 0.224f, 0.225f)

        for (y in 0 until target) {
            val sy = (y * height) / target
            for (x in 0 until target) {
                val sx = (x * width) / target
                val p = argbPixels[sy * width + sx]
                val r = ((p ushr 16) and 0xFF) / 255f
                val g = ((p ushr 8) and 0xFF) / 255f
                val b = (p and 0xFF) / 255f
                val idx = y * target + x
                nchw[idx] = (r - mean[0]) / std[0]
                nchw[planeSize + idx] = (g - mean[1]) / std[1]
                nchw[2 * planeSize + idx] = (b - mean[2]) / std[2]
            }
        }

        return runCatching {
            val logits = session.runFloatInference(nchw, longArrayOf(1L, 3L, 224L, 224L))
            // config.json: id2label { "0": "artificial", "1": "human" }
            OnnxRuntimeSession.softmaxProbability(logits, targetIndex = 0)
        }.getOrNull()
    }
}

/**
 * Fuses deterministic pixel/FFT/metadata forensics with optional SMOGY Swin ONNX classification.
 */
class HybridImageAnalysisEngine(
    private val forensicsAnalyzer: ImageForensicsAnalyzer,
    private val neuralScorer: SmogyOnnxImageScorer?
) : ImageForensicsEngine {

    override suspend fun analyzePixels(
        argbPixels: IntArray,
        width: Int,
        height: Int,
        rawBytes: ByteArray?
    ): ImageAnalysisResult {
        val forensic = forensicsAnalyzer.analyzePixels(argbPixels, width, height, rawBytes)
        val neural = neuralScorer?.scoreIfAvailable(argbPixels, width, height) ?: return forensic

        val fusedScore = if (forensic.metadataSignature != null) {
            maxOf(forensic.aiScore, neural)
        } else {
            (0.72f * neural + 0.28f * forensic.aiScore).coerceIn(0f, 1f)
        }

        val neuralSignal = ForensicSignal(
            key = "neural_smogy",
            labelKey = "sig_neural_smogy",
            score = neural,
            detail = "On-device SMOGY Swin neural confidence: ${(neural * 100f).roundToInt()}%",
            isSuspicious = neural >= 0.55f
        )

        return forensic.copy(
            aiScore = fusedScore,
            verdict = Verdict.fromFakeScore(fusedScore),
            analysisMode = AnalysisMode.OFFLINE_NEURAL_AND_FORENSIC,
            neuralScore = neural,
            signals = listOf(neuralSignal) + forensic.signals
        )
    }
}
