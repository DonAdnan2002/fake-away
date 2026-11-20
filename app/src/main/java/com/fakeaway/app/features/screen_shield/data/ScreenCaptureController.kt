package com.fakeaway.app.features.screen_shield.data

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.ImageReader
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.os.Build
import android.os.Handler
import android.os.Looper
import com.fakeaway.app.core.dsp.AudioResampler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

/**
 * Android 14/15/16-safe MediaProjection controller:
 * - Registers MediaProjection.Callback before creating a single persistent VirtualDisplay + ImageReader.
 * - Captures clean screen Bitmaps on demand.
 * - Captures internal playing audio via AudioPlaybackCapture (API 29+) with automatic
 *   microphone fallback if the foreground app forbids internal audio capture.
 */
class ScreenCaptureController(
    private val context: Context,
    private val mediaProjection: MediaProjection,
    private val onProjectionStopped: () -> Unit
) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var captureWidth: Int = 0
    private var captureHeight: Int = 0

    private val projectionCallback = object : MediaProjection.Callback() {
        override fun onStop() {
            release()
            onProjectionStopped()
        }
    }

    init {
        mediaProjection.registerCallback(projectionCallback, mainHandler)
        initPersistentVirtualDisplay()
    }

    private fun initPersistentVirtualDisplay() {
        val metrics = context.resources.displayMetrics
        // Scale down very high-res screens by 2x for fast OCR + low RAM usage
        val scale = if (metrics.widthPixels > 1200) 2 else 1
        captureWidth = (metrics.widthPixels / scale).coerceAtLeast(360)
        captureHeight = (metrics.heightPixels / scale).coerceAtLeast(640)

        val reader = ImageReader.newInstance(
            captureWidth,
            captureHeight,
            PixelFormat.RGBA_8888,
            2
        )
        imageReader = reader
        virtualDisplay = mediaProjection.createVirtualDisplay(
            "FakeAwayShieldDisplay",
            captureWidth,
            captureHeight,
            metrics.densityDpi / scale,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            reader.surface,
            null,
            mainHandler
        )
    }

    suspend fun captureFrame(): Bitmap? = withContext(Dispatchers.Default) {
        val reader = imageReader ?: return@withContext null
        // Drain stale buffer and wait briefly for a clean frame after overlay hides
        runCatching { reader.acquireLatestImage()?.close() }
        delay(220)

        var image = reader.acquireLatestImage()
        if (image == null) {
            delay(150)
            image = reader.acquireLatestImage()
        }
        image ?: return@withContext null

        try {
            val plane = image.planes[0]
            val buffer = plane.buffer
            val pixelStride = plane.pixelStride
            val rowStride = plane.rowStride
            val rowPadding = rowStride - pixelStride * captureWidth
            val paddedWidth = captureWidth + rowPadding / pixelStride

            val full = Bitmap.createBitmap(paddedWidth, captureHeight, Bitmap.Config.ARGB_8888)
            full.copyPixelsFromBuffer(buffer)
            if (paddedWidth == captureWidth) {
                full
            } else {
                Bitmap.createBitmap(full, 0, 0, captureWidth, captureHeight).also {
                    full.recycle()
                }
            }
        } finally {
            image.close()
        }
    }

    data class CapturedAudio(
        val samples16k: FloatArray,
        val usedMicFallback: Boolean
    )

    @SuppressLint("MissingPermission")
    suspend fun capturePlayingAudio(
        durationSeconds: Int = 6,
        canUseMicFallback: Boolean,
        onSecondTick: suspend (Int) -> Unit
    ): CapturedAudio = withContext(Dispatchers.IO) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val internal = runCatching {
                recordWithPlaybackCapture(durationSeconds, onSecondTick)
            }.getOrNull()

            if (internal != null && AudioResampler.computeRms(internal) >= 0.00025f) {
                return@withContext CapturedAudio(internal, usedMicFallback = false)
            }
        }

        if (canUseMicFallback) {
            val micSamples = recordWithMic(durationSeconds, onSecondTick)
            return@withContext CapturedAudio(micSamples, usedMicFallback = true)
        }

        CapturedAudio(FloatArray(0), usedMicFallback = false)
    }

    @SuppressLint("MissingPermission")
    private suspend fun recordWithPlaybackCapture(
        durationSeconds: Int,
        onSecondTick: suspend (Int) -> Unit
    ): FloatArray {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return FloatArray(0)
        val sr = AudioResampler.TARGET_SAMPLE_RATE
        val config = AudioPlaybackCaptureConfiguration.Builder(mediaProjection)
            .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
            .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
            .addMatchingUsage(AudioAttributes.USAGE_GAME)
            .build()

        val format = AudioFormat.Builder()
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setSampleRate(sr)
            .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
            .build()

        val minBuf = AudioRecord.getMinBufferSize(
            sr,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(4096)

        val record = AudioRecord.Builder()
            .setAudioFormat(format)
            .setBufferSizeInBytes(minBuf * 2)
            .setAudioPlaybackCaptureConfig(config)
            .build()

        return readAudioLoop(record, sr, durationSeconds, onSecondTick)
    }

    @SuppressLint("MissingPermission")
    private suspend fun recordWithMic(
        durationSeconds: Int,
        onSecondTick: suspend (Int) -> Unit
    ): FloatArray {
        val sr = AudioResampler.TARGET_SAMPLE_RATE
        val minBuf = AudioRecord.getMinBufferSize(
            sr,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(4096)

        val record = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            sr,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            minBuf * 2
        )
        return readAudioLoop(record, sr, durationSeconds, onSecondTick)
    }

    private suspend fun readAudioLoop(
        record: AudioRecord,
        sampleRate: Int,
        durationSeconds: Int,
        onSecondTick: suspend (Int) -> Unit
    ): FloatArray {
        val targetSamples = sampleRate * durationSeconds
        val out = FloatArray(targetSamples)
        val chunk = ShortArray(1024)
        var written = 0
        var lastTick = -1

        try {
            record.startRecording()
            while (coroutineContext.isActive && written < targetSamples) {
                val read = record.read(chunk, 0, chunk.size)
                if (read <= 0) break
                val count = read.coerceAtMost(targetSamples - written)
                for (i in 0 until count) {
                    out[written + i] = chunk[i] / 32768f
                }
                written += count
                val remaining = ((targetSamples - written) / sampleRate).coerceAtLeast(0)
                if (remaining != lastTick) {
                    lastTick = remaining
                    onSecondTick(remaining)
                }
            }
        } finally {
            runCatching { record.stop() }
            runCatching { record.release() }
        }
        return out.copyOf(written)
    }

    fun release() {
        runCatching { mediaProjection.unregisterCallback(projectionCallback) }
        runCatching { virtualDisplay?.release() }
        runCatching { imageReader?.close() }
        virtualDisplay = null
        imageReader = null
        runCatching { mediaProjection.stop() }
    }
}
