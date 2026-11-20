package com.fakeaway.app.core.dsp

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * Pure Kotlin iterative Cooley-Tukey Radix-2 1D and 2D Fast Fourier Transform utilities.
 */
object FftMath {

    /**
     * Generates a symmetric Hann window of [size] samples.
     */
    fun hannWindow(size: Int): FloatArray {
        if (size <= 1) return FloatArray(size) { 1f }
        val window = FloatArray(size)
        val denom = (size - 1).toDouble()
        for (i in 0 until size) {
            window[i] = (0.5 * (1.0 - cos(2.0 * PI * i / denom))).toFloat()
        }
        return window
    }

    /**
     * Performs in-place Radix-2 FFT on [real] and [imag] arrays whose length must be a power of 2.
     */
    fun fft(real: FloatArray, imag: FloatArray) {
        val n = real.size
        require(n == imag.size) { "Real and imag arrays must have identical length" }
        require(n > 0 && (n and (n - 1)) == 0) { "FFT size must be a power of 2, got $n" }

        bitReversePermute(real, imag, n)

        var len = 2
        while (len <= n) {
            val halfLen = len ushr 1
            val angleStep = -2.0 * PI / len
            for (start in 0 until n step len) {
                for (k in 0 until halfLen) {
                    val angle = angleStep * k
                    val wr = cos(angle).toFloat()
                    val wi = sin(angle).toFloat()
                    val evenIdx = start + k
                    val oddIdx = evenIdx + halfLen
                    val tr = wr * real[oddIdx] - wi * imag[oddIdx]
                    val ti = wr * imag[oddIdx] + wi * real[oddIdx]
                    real[oddIdx] = real[evenIdx] - tr
                    imag[oddIdx] = imag[evenIdx] - ti
                    real[evenIdx] += tr
                    imag[evenIdx] += ti
                }
            }
            len = len shl 1
        }
    }

    /**
     * Computes single-sided magnitude spectrum (size n/2 + 1) of a real-valued [frame].
     * Automatically zero-pads to the next power of 2 if needed.
     */
    fun magnitudeSpectrum(frame: FloatArray): FloatArray {
        val n = nextPowerOfTwo(frame.size.coerceAtLeast(2))
        val real = FloatArray(n)
        val imag = FloatArray(n)
        frame.copyInto(real)
        fft(real, imag)
        val bins = n / 2 + 1
        return FloatArray(bins) { idx -> hypot(real[idx], imag[idx]) }
    }

    /**
     * Computes shifted 2D magnitude spectrum over a power-of-2 matrix [matrix] (rows x cols).
     */
    fun fft2dMagnitude(matrix: Array<FloatArray>): Array<FloatArray> {
        val rows = matrix.size
        require(rows > 0) { "Matrix cannot be empty" }
        val cols = matrix[0].size
        require((rows and (rows - 1)) == 0 && (cols and (cols - 1)) == 0) {
            "2D FFT dimensions must be powers of 2"
        }

        val real = Array(rows) { r -> matrix[r].copyOf() }
        val imag = Array(rows) { FloatArray(cols) }

        for (r in 0 until rows) {
            fft(real[r], imag[r])
        }

        val colReal = FloatArray(rows)
        val colImag = FloatArray(rows)
        for (c in 0 until cols) {
            for (r in 0 until rows) {
                colReal[r] = real[r][c]
                colImag[r] = imag[r][c]
            }
            fft(colReal, colImag)
            for (r in 0 until rows) {
                real[r][c] = colReal[r]
                imag[r][c] = colImag[r]
            }
        }

        val halfR = rows / 2
        val halfC = cols / 2
        return Array(rows) { r ->
            val sr = (r + halfR) % rows
            FloatArray(cols) { c ->
                val sc = (c + halfC) % cols
                hypot(real[sr][sc], imag[sr][sc])
            }
        }
    }

    private fun bitReversePermute(real: FloatArray, imag: FloatArray, n: Int) {
        var j = 0
        for (i in 1 until n) {
            var bit = n ushr 1
            while (j and bit != 0) {
                j = j xor bit
                bit = bit ushr 1
            }
            j = j xor bit
            if (i < j) {
                val tmpR = real[i]
                real[i] = real[j]
                real[j] = tmpR
                val tmpI = imag[i]
                imag[i] = imag[j]
                imag[j] = tmpI
            }
        }
    }

    private fun nextPowerOfTwo(v: Int): Int {
        var p = 1
        while (p < v) p = p shl 1
        return p
    }
}
