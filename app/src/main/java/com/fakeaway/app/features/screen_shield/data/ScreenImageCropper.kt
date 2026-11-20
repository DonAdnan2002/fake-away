package com.fakeaway.app.features.screen_shield.data

import android.graphics.Bitmap
import kotlin.math.abs

/**
 * Crops mobile system bars from screen captures and isolates media regions for pixel forensics.
 */
object ScreenImageCropper {

    data class CroppedRegions(
        val ocrBitmap: Bitmap,
        val mediaBitmap: Bitmap
    )

    fun extractRegions(fullScreen: Bitmap): CroppedRegions {
        val w = fullScreen.width
        val h = fullScreen.height
        val topCut = (h * 0.11f).toInt().coerceIn(0, h / 4)
        val bottomCut = (h * 0.09f).toInt().coerceIn(0, h / 4)
        val contentHeight = (h - topCut - bottomCut).coerceAtLeast(32)

        val ocrBitmap = Bitmap.createBitmap(fullScreen, 0, topCut, w, contentHeight)
        val mediaBitmap = findHighVarianceBand(ocrBitmap) ?: ocrBitmap
        return CroppedRegions(ocrBitmap = ocrBitmap, mediaBitmap = mediaBitmap)
    }

    fun toArgbPixels(bitmap: Bitmap): IntArray {
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        return pixels
    }

    /**
     * Scans horizontal strips inside a feed screenshot to isolate the photo/video frame
     * (highest local color/edge variance compared to flat text backgrounds).
     */
    private fun findHighVarianceBand(content: Bitmap): Bitmap? {
        val w = content.width
        val h = content.height
        if (w < 64 || h < 128) return null

        val bands = 6
        val bandH = h / bands
        val energies = FloatArray(bands)
        val rowBuf = IntArray(w)

        for (b in 0 until bands) {
            val yStart = b * bandH
            var diffSum = 0.0
            var samples = 0
            var y = yStart
            val yStep = (bandH / 8).coerceAtLeast(1)
            while (y < yStart + bandH && y < h) {
                content.getPixels(rowBuf, 0, w, 0, y, w, 1)
                for (x in 4 until w step 8) {
                    val p0 = rowBuf[x - 4]
                    val p1 = rowBuf[x]
                    val dr = abs(((p1 ushr 16) and 0xFF) - ((p0 ushr 16) and 0xFF))
                    val dg = abs(((p1 ushr 8) and 0xFF) - ((p0 ushr 8) and 0xFF))
                    val db = abs((p1 and 0xFF) - (p0 and 0xFF))
                    diffSum += (dr + dg + db)
                    samples++
                }
                y += yStep
            }
            energies[b] = (diffSum / samples.coerceAtLeast(1)).toFloat()
        }

        // Select contiguous 2..3 bands around peak visual variance
        val peakBand = energies.indices.maxByOrNull { energies[it] } ?: return null
        val startBand = (peakBand - 1).coerceAtLeast(0)
        val endBand = (peakBand + 1).coerceAtMost(bands - 1)
        val cropY = startBand * bandH
        val cropH = ((endBand - startBand + 1) * bandH).coerceAtMost(h - cropY)
        return if (cropH >= 48) {
            Bitmap.createBitmap(content, 0, cropY, w, cropH)
        } else {
            null
        }
    }
}
