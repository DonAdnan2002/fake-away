package com.fakeaway.app.features.screen_shield

import com.fakeaway.app.core.domain.Verdict
import com.fakeaway.app.features.screen_shield.data.ImageForensicsAnalyzer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ImageForensicsAnalyzerTest {

    private val analyzer = ImageForensicsAnalyzer()

    @Test
    fun analyzePixels_detectsMidjourneyAndC2paMetadataImmediately() {
        val width = 64
        val height = 64
        val pixels = IntArray(width * height) { 0xFF446688.toInt() }
        val fakeHeader = "PNG\r\n...parameters: cyber portrait --v 6 Midjourney...".toByteArray(Charsets.ISO_8859_1)

        val result = analyzer.analyzePixels(pixels, width, height, fakeHeader)

        assertNotNull(result.metadataSignature)
        assertTrue(result.aiScore >= 0.95f)
        assertEquals(Verdict.FAKE, result.verdict)
    }

    @Test
    fun analyzePixels_recognizesCameraExifProvenance() {
        val width = 64
        val height = 64
        val pixels = naturalTexturePixels(width, height)
        val exifBytes = "JFIF...Exif..Canon EOS R5..2026:11:19".toByteArray(Charsets.ISO_8859_1)

        val result = analyzer.analyzePixels(pixels, width, height, exifBytes)

        assertTrue(result.hasCameraExif)
        assertEquals(null, result.metadataSignature)
    }

    @Test
    fun analyzePixels_localizesInpaintedPatchInHeatmapAndFlagsFftGrid() {
        val width = 64
        val height = 64
        val pixels = naturalTexturePixels(width, height)

        // Overwrite top-left 16x16 quadrant (heatmap cells 0..1, 0..1) with flat smooth inpainted patch
        for (y in 0 until 16) {
            for (x in 0 until 16) {
                pixels[y * width + x] = 0xFF808080.toInt()
            }
        }

        val gridPixels = IntArray(width * height) { idx ->
            val x = idx % width
            val y = idx / width
            if ((x + y) % 2 == 0) 0xFFFAFAFA.toInt() else 0xFF101010.toInt()
        }

        val patchResult = analyzer.analyzePixels(pixels, width, height, null)
        val gridResult = analyzer.analyzePixels(gridPixels, width, height, null)

        val topLeftIntensity = patchResult.heatmapGrid[0][0]
        val bottomRightIntensity = patchResult.heatmapGrid[6][6]
        assertFalse(patchResult.hasCameraExif)
        assertTrue(
            "Top-left smoothed patch ($topLeftIntensity) should stand out from natural bottom-right ($bottomRightIntensity)",
            topLeftIntensity > bottomRightIntensity
        )
        assertTrue(
            "Periodic grid (${gridResult.fftGridScore}) should score higher FFT grid anomaly",
            gridResult.fftGridScore > 0.35f
        )
    }

    private fun naturalTexturePixels(width: Int, height: Int): IntArray {
        var seed = 42L
        return IntArray(width * height) { i ->
            seed = seed * 6364136223846793005L + 1442695040888963407L
            val r = (((seed ushr 16) and 0x7F) + (i % 60)).toInt().coerceIn(0, 255)
            val g = (((seed ushr 24) and 0x7F) + ((i / width) % 60)).toInt().coerceIn(0, 255)
            val b = ((seed ushr 32) and 0xFF).toInt()
            (0xFF shl 24) or (r shl 16) or (g shl 8) or b
        }
    }
}
