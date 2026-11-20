package com.fakeaway.app.core.dsp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin

class FftMathTest {

    @Test
    fun magnitudeSpectrum_identifiesExactSineBin() {
        val sampleRate = 16_000
        val size = 512
        val targetBin = 32 // 32 * 16000 / 512 = 1000 Hz
        val freq = targetBin * sampleRate.toDouble() / size
        val window = FftMath.hannWindow(size)
        val signal = FloatArray(size) { i ->
            (sin(2.0 * PI * freq * i / sampleRate) * window[i]).toFloat()
        }

        val spectrum = FftMath.magnitudeSpectrum(signal)
        val peakBin = spectrum.indices.maxByOrNull { spectrum[it] } ?: -1

        assertEquals(targetBin, peakBin)
        assertTrue(spectrum[peakBin] > 100f)
    }

    @Test
    fun fft2dMagnitude_detectsPeriodicGridEnergy() {
        val n = 32
        val smooth = Array(n) { r -> FloatArray(n) { c -> (r + c) / (2f * n) } }
        val checkerboard = Array(n) { r -> FloatArray(n) { c -> if ((r + c) % 2 == 0) 1f else -1f } }

        val smoothMag = FftMath.fft2dMagnitude(smooth)
        val checkerMag = FftMath.fft2dMagnitude(checkerboard)

        val cornerSmooth = smoothMag[0][0]
        val cornerChecker = checkerMag[0][0]
        assertTrue("Checkerboard high-freq corner should dominate smooth ramp", cornerChecker > cornerSmooth * 10f)
    }
}
