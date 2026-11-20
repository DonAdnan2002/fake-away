package com.fakeaway.app.features.voice_detector.data

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import com.fakeaway.app.core.dsp.AudioResampler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.coroutineContext

/**
 * Clean 16kHz Mono PCM microphone recorder for live offline deepfake checks.
 */
class LiveMicRecorder {

    private val recording = AtomicBoolean(false)

    fun stop() {
        recording.set(false)
    }

    @SuppressLint("MissingPermission")
    suspend fun recordMaxSeconds(
        maxSeconds: Int = 15,
        onElapsedSecond: (Int) -> Unit
    ): FloatArray = withContext(Dispatchers.IO) {
        val sampleRate = AudioResampler.TARGET_SAMPLE_RATE
        val minBuf = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(4096)

        val recorder = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            minBuf * 2
        )

        val maxSamples = sampleRate * maxSeconds
        val out = FloatArray(maxSamples)
        val chunk = ShortArray(1024)
        var written = 0
        var lastSecond = -1

        try {
            recording.set(true)
            recorder.startRecording()
            while (recording.get() && coroutineContext.isActive && written < maxSamples) {
                val read = recorder.read(chunk, 0, chunk.size)
                if (read > 0) {
                    val count = read.coerceAtMost(maxSamples - written)
                    for (i in 0 until count) {
                        out[written + i] = chunk[i] / 32768f
                    }
                    written += count
                    val sec = written / sampleRate
                    if (sec != lastSecond) {
                        lastSecond = sec
                        onElapsedSecond(sec)
                    }
                } else if (read < 0) {
                    break
                }
            }
        } finally {
            recording.set(false)
            runCatching { recorder.stop() }
            runCatching { recorder.release() }
        }
        out.copyOf(written)
    }
}
