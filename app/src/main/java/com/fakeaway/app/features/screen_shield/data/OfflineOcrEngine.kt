package com.fakeaway.app.features.screen_shield.data

import android.content.Context
import android.graphics.Bitmap
import com.fakeaway.app.features.screen_shield.domain.ScreenTextExtractor
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.googlecode.tesseract.android.TessBaseAPI
import kotlinx.coroutines.tasks.await
import java.io.File
import java.io.FileOutputStream

/**
 * Fully offline OCR engine combining bundled Tesseract ("Arabic+eng" covering Arabic,
 * Kurdish Sorani, and English) with ML Kit Latin fallback for crisp English/URL extraction.
 */
class AndroidOfflineOcrEngine(
    private val context: Context
) : ScreenTextExtractor {

    private val latinRecognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    @Volatile
    private var tessDataPrepared = false

    override suspend fun extractText(
        argbPixels: IntArray,
        width: Int,
        height: Int
    ): String {
        val bitmap = Bitmap.createBitmap(argbPixels, width, height, Bitmap.Config.ARGB_8888)
        return try {
            extractFromBitmap(bitmap)
        } finally {
            bitmap.recycle()
        }
    }

    suspend fun extractFromBitmap(bitmap: Bitmap): String {
        val tessText = runTesseractOcr(bitmap)
        val hasArabicScript = tessText.any { it in '\u0600'..'\u06FF' }

        // If Tesseract read substantial Arabic/Kurdish text cleanly, use it directly
        if (hasArabicScript && tessText.length >= 16) {
            return tessText
        }

        // Otherwise supplement with ML Kit Latin (very fast and accurate on English UI/links)
        val latinText = runMlKitLatin(bitmap)
        return when {
            tessText.isBlank() -> latinText
            latinText.isBlank() -> tessText
            hasArabicScript -> "$tessText\n$latinText".trim()
            latinText.length > tessText.length -> latinText
            else -> tessText
        }
    }

    @Synchronized
    private fun ensureTessDataCopied(): File? {
        val dataDir = context.filesDir
        val tessDir = File(dataDir, "tessdata").apply { mkdirs() }
        if (tessDataPrepared) return dataDir

        val files = listOf("Arabic.traineddata", "eng.traineddata")
        val ok = runCatching {
            for (name in files) {
                val outFile = File(tessDir, name)
                if (!outFile.exists() || outFile.length() < 1024L) {
                    context.assets.open("tessdata/$name").use { input ->
                        FileOutputStream(outFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                }
            }
            true
        }.getOrDefault(false)

        if (ok) {
            tessDataPrepared = true
            return dataDir
        }
        return null
    }

    private fun runTesseractOcr(bitmap: Bitmap): String {
        val dataPath = ensureTessDataCopied() ?: return ""
        val api = TessBaseAPI()
        return try {
            if (!api.init(dataPath.absolutePath, "Arabic+eng")) {
                return ""
            }
            api.setPageSegMode(TessBaseAPI.PageSegMode.PSM_AUTO)
            api.setImage(bitmap)
            cleanExtractedLines(api.utF8Text.orEmpty())
        } catch (_: Throwable) {
            ""
        } finally {
            runCatching { api.recycle() }
        }
    }

    private suspend fun runMlKitLatin(bitmap: Bitmap): String {
        return runCatching {
            val image = InputImage.fromBitmap(bitmap, 0)
            val result = latinRecognizer.process(image).await()
            val joined = result.textBlocks
                .map { it.text.trim() }
                .filter { it.length >= 4 }
                .joinToString("\n")
            cleanExtractedLines(joined)
        }.getOrDefault("")
    }

    private fun cleanExtractedLines(raw: String): String {
        return raw.lines()
            .map { it.trim() }
            .filter { it.length >= 3 }
            .distinct()
            .joinToString("\n")
    }
}
