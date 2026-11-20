package com.fakeaway.app.core.dsp

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Decoded 16kHz mono PCM audio container ready for DSP and ONNX inference.
 */
data class DecodedAudio(
    val samples16k: FloatArray,
    val sampleRate: Int = AudioResampler.TARGET_SAMPLE_RATE,
    val durationSeconds: Float
)

/**
 * Decodes WAV bytes (pure Kotlin, JVM-testable) as well as compressed Android URIs
 * (WhatsApp .opus/.ogg, Telegram .mp3/.m4a, and .mp4 video audio tracks) via MediaExtractor + MediaCodec.
 */
object AudioPcmDecoder {

    private const val MAX_DECODE_SECONDS = 60
    private const val CODEC_TIMEOUT_US = 10_000L

    /**
     * Parses uncompressed RIFF/WAVE bytes (8-bit, 16-bit PCM, or 32-bit float) into 16kHz mono PCM.
     */
    fun decodeWavBytes(bytes: ByteArray): DecodedAudio {
        require(bytes.size >= 44) { "Invalid WAV: too short" }
        val riff = String(bytes, 0, 4, Charsets.US_ASCII)
        val wave = String(bytes, 8, 4, Charsets.US_ASCII)
        require(riff == "RIFF" && wave == "WAVE") { "Invalid WAV header" }

        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        var pos = 12
        var audioFormat = 1
        var channels = 1
        var sampleRate = AudioResampler.TARGET_SAMPLE_RATE
        var bitsPerSample = 16
        var dataStart = -1
        var dataSize = 0

        while (pos + 8 <= bytes.size) {
            val chunkId = String(bytes, pos, 4, Charsets.US_ASCII)
            val chunkSize = buf.getInt(pos + 4)
            val payloadPos = pos + 8
            when (chunkId) {
                "fmt " -> if (payloadPos + 16 <= bytes.size) {
                    audioFormat = buf.getShort(payloadPos).toInt() and 0xFFFF
                    channels = buf.getShort(payloadPos + 2).toInt() and 0xFFFF
                    sampleRate = buf.getInt(payloadPos + 4)
                    bitsPerSample = buf.getShort(payloadPos + 14).toInt() and 0xFFFF
                }
                "data" -> {
                    dataStart = payloadPos
                    dataSize = chunkSize.coerceAtMost(bytes.size - payloadPos).coerceAtLeast(0)
                    break
                }
            }
            val padded = if (chunkSize % 2 != 0) chunkSize + 1 else chunkSize
            pos = payloadPos + padded.coerceAtLeast(0)
        }

        require(dataStart >= 0 && dataSize > 0) { "WAV file has no audio data chunk" }

        val interleaved = decodeRawPcmSamples(
            bytes = bytes,
            dataStart = dataStart,
            dataSize = dataSize,
            audioFormat = audioFormat,
            bitsPerSample = bitsPerSample
        )
        val mono = AudioResampler.downmixToMono(interleaved, channels.coerceAtLeast(1))
        val resampled = AudioResampler.resampleLinear(mono, sampleRate, AudioResampler.TARGET_SAMPLE_RATE)
        return DecodedAudio(
            samples16k = resampled,
            sampleRate = AudioResampler.TARGET_SAMPLE_RATE,
            durationSeconds = resampled.size.toFloat() / AudioResampler.TARGET_SAMPLE_RATE
        )
    }

    /**
     * Decodes an Android content [uri] (audio or video) into 16kHz mono PCM, capped at [maxDurationSeconds].
     */
    fun decodeUri(context: Context, uri: Uri, maxDurationSeconds: Int = MAX_DECODE_SECONDS): DecodedAudio {
        tryFastWavFromUri(context, uri)?.let { return it }
        return decodeViaMediaCodec(context, uri, maxDurationSeconds)
    }

    private fun tryFastWavFromUri(context: Context, uri: Uri): DecodedAudio? {
        return runCatching {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val header = ByteArray(12)
                val read = stream.read(header)
                if (read == 12 &&
                    String(header, 0, 4, Charsets.US_ASCII) == "RIFF" &&
                    String(header, 8, 4, Charsets.US_ASCII) == "WAVE"
                ) {
                    val rest = stream.readBytes()
                    decodeWavBytes(header + rest)
                } else {
                    null
                }
            }
        }.getOrNull()
    }

    private fun decodeViaMediaCodec(
        context: Context,
        uri: Uri,
        maxDurationSeconds: Int
    ): DecodedAudio {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            extractor.setDataSource(context, uri, null)
            val audioTrack = findFirstAudioTrack(extractor)
                ?: error("No audio track found in URI")

            extractor.selectTrack(audioTrack.first)
            val format = audioTrack.second
            val mime = format.getString(MediaFormat.KEY_MIME)
                ?: error("Audio track missing MIME type")
            var srcSampleRate = format.getIntegerOrDefault(MediaFormat.KEY_SAMPLE_RATE, 16_000)
            var srcChannels = format.getIntegerOrDefault(MediaFormat.KEY_CHANNEL_COUNT, 1)

            codec = MediaCodec.createDecoderByType(mime).apply {
                configure(format, null, null, 0)
                start()
            }

            val maxSamples = maxDurationSeconds * srcSampleRate * srcChannels
            val pcmOut = ByteArrayOutputStream()
            val bufferInfo = MediaCodec.BufferInfo()
            var inputDone = false
            var outputDone = false
            var collectedBytes = 0

            while (!outputDone && collectedBytes < maxSamples * 2) {
                if (!inputDone) {
                    val inIdx = codec.dequeueInputBuffer(CODEC_TIMEOUT_US)
                    if (inIdx >= 0) {
                        val inBuf = codec.getInputBuffer(inIdx)!!
                        val sampleSize = extractor.readSampleData(inBuf, 0)
                        if (sampleSize < 0) {
                            codec.queueInputBuffer(inIdx, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            codec.queueInputBuffer(inIdx, 0, sampleSize, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }

                val outIdx = codec.dequeueOutputBuffer(bufferInfo, CODEC_TIMEOUT_US)
                when {
                    outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val outFormat = codec.outputFormat
                        srcSampleRate = outFormat.getIntegerOrDefault(MediaFormat.KEY_SAMPLE_RATE, srcSampleRate)
                        srcChannels = outFormat.getIntegerOrDefault(MediaFormat.KEY_CHANNEL_COUNT, srcChannels)
                    }
                    outIdx >= 0 -> {
                        val outBuf = codec.getOutputBuffer(outIdx)
                        if (outBuf != null && bufferInfo.size > 0) {
                            val chunk = ByteArray(bufferInfo.size)
                            outBuf.position(bufferInfo.offset)
                            outBuf.limit(bufferInfo.offset + bufferInfo.size)
                            outBuf.get(chunk)
                            pcmOut.write(chunk)
                            collectedBytes += chunk.size
                        }
                        codec.releaseOutputBuffer(outIdx, false)
                        if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                            outputDone = true
                        }
                    }
                }
            }

            val rawBytes = pcmOut.toByteArray()
            require(rawBytes.isNotEmpty()) { "Decoded audio buffer is empty" }
            val interleaved = bytesToFloat16Le(rawBytes)
            val mono = AudioResampler.downmixToMono(interleaved, srcChannels.coerceAtLeast(1))
            val resampled = AudioResampler.resampleLinear(mono, srcSampleRate, AudioResampler.TARGET_SAMPLE_RATE)
            return DecodedAudio(
                samples16k = resampled,
                sampleRate = AudioResampler.TARGET_SAMPLE_RATE,
                durationSeconds = resampled.size.toFloat() / AudioResampler.TARGET_SAMPLE_RATE
            )
        } finally {
            runCatching { codec?.stop() }
            runCatching { codec?.release() }
            runCatching { extractor.release() }
        }
    }

    private fun findFirstAudioTrack(extractor: MediaExtractor): Pair<Int, MediaFormat>? {
        for (i in 0 until extractor.trackCount) {
            val fmt = extractor.getTrackFormat(i)
            val mime = fmt.getString(MediaFormat.KEY_MIME) ?: continue
            if (mime.startsWith("audio/")) return i to fmt
        }
        return null
    }

    private fun MediaFormat.getIntegerOrDefault(key: String, default: Int): Int {
        return if (containsKey(key)) getInteger(key) else default
    }

    private fun decodeRawPcmSamples(
        bytes: ByteArray,
        dataStart: Int,
        dataSize: Int,
        audioFormat: Int,
        bitsPerSample: Int
    ): FloatArray {
        val buf = ByteBuffer.wrap(bytes, dataStart, dataSize).order(ByteOrder.LITTLE_ENDIAN)
        return when {
            audioFormat == 3 && bitsPerSample == 32 -> {
                val count = dataSize / 4
                FloatArray(count) { buf.getFloat().coerceIn(-1f, 1f) }
            }
            bitsPerSample == 8 -> FloatArray(dataSize) {
                ((bytes[dataStart + it].toInt() and 0xFF) - 128) / 128f
            }
            else -> {
                val count = dataSize / 2
                FloatArray(count) { buf.getShort() / 32768f }
            }
        }
    }

    private fun bytesToFloat16Le(bytes: ByteArray): FloatArray {
        val count = bytes.size / 2
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        return FloatArray(count) { buf.getShort() / 32768f }
    }
}
