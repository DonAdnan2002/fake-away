package com.fakeaway.app.features.voice_detector.presentation

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fakeaway.app.core.domain.DispatcherProvider
import com.fakeaway.app.core.dsp.AudioPcmDecoder
import com.fakeaway.app.core.dsp.AudioResampler
import com.fakeaway.app.features.voice_detector.data.AcousticForensicsAnalyzer
import com.fakeaway.app.features.voice_detector.data.LiveMicRecorder
import com.fakeaway.app.features.voice_detector.domain.AnalyzeVoiceUseCase
import com.fakeaway.app.features.voice_detector.domain.VoiceAnalysisResult
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Abstraction for playing back 16kHz PCM samples with progress reporting.
 */
interface PcmAudioPlayer {
    suspend fun play(samples16k: FloatArray, onProgress: (Float) -> Unit)
    fun stop()
}

class AndroidPcmAudioPlayer : PcmAudioPlayer {
    @Volatile
    private var track: AudioTrack? = null
    @Volatile
    private var cancelled = false

    override suspend fun play(samples16k: FloatArray, onProgress: (Float) -> Unit) {
        if (samples16k.isEmpty()) return
        cancelled = false
        val sampleRate = AudioResampler.TARGET_SAMPLE_RATE
        val minBuf = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(4096)

        val pcmShorts = ShortArray(samples16k.size) { i ->
            (samples16k[i].coerceIn(-1f, 1f) * 32767f).toInt().toShort()
        }

        val audioTrack = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(minBuf * 2)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()

        track = audioTrack
        try {
            audioTrack.play()
            var offset = 0
            val chunk = 1024
            while (offset < pcmShorts.size && !cancelled) {
                val len = chunk.coerceAtMost(pcmShorts.size - offset)
                val written = audioTrack.write(pcmShorts, offset, len)
                if (written <= 0) break
                offset += written
                onProgress(offset.toFloat() / pcmShorts.size)
            }
        } finally {
            runCatching { audioTrack.stop() }
            runCatching { audioTrack.release() }
            track = null
            onProgress(0f)
        }
    }

    override fun stop() {
        cancelled = true
        runCatching { track?.pause() }
        runCatching { track?.flush() }
    }
}

class VoiceDetectorViewModel(
    private val analyzeVoiceUseCase: AnalyzeVoiceUseCase,
    private val dispatchers: DispatcherProvider,
    private val micRecorder: LiveMicRecorder = LiveMicRecorder(),
    private val audioPlayer: PcmAudioPlayer? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow(VoiceDetectorUiState())
    val uiState: StateFlow<VoiceDetectorUiState> = _uiState.asStateFlow()

    private var lastSamples16k: FloatArray? = null
    private var playbackJob: Job? = null
    private var recordingJob: Job? = null

    fun analyzeUri(
        context: Context,
        uri: Uri,
        sourceLabel: String,
        bannerMessage: String? = null
    ) {
        stopPlayback()
        _uiState.update {
            it.copy(
                isAnalyzing = true,
                errorType = null,
                bannerMessage = bannerMessage
            )
        }
        viewModelScope.launch {
            val decoded = runCatching {
                withContext(dispatchers.io) {
                    AudioPcmDecoder.decodeUri(context, uri)
                }
            }
            decoded.fold(
                onSuccess = { audio ->
                    analyzePcm(
                        samples16k = audio.samples16k,
                        sourceLabel = sourceLabel,
                        usedMicFallback = false,
                        bannerMessage = bannerMessage
                    )
                },
                onFailure = {
                    _uiState.update {
                        it.copy(
                            isAnalyzing = false,
                            errorType = VoiceErrorType.DECODE_FAILED
                        )
                    }
                }
            )
        }
    }

    fun analyzePcm(
        samples16k: FloatArray,
        sourceLabel: String,
        usedMicFallback: Boolean = false,
        bannerMessage: String? = null
    ) {
        stopPlayback()
        lastSamples16k = samples16k
        _uiState.update {
            it.copy(
                isAnalyzing = true,
                errorType = null,
                bannerMessage = bannerMessage
            )
        }
        viewModelScope.launch {
            val res = analyzeVoiceUseCase(samples16k, sourceLabel, usedMicFallback)
            res.fold(
                onSuccess = { result ->
                    _uiState.update {
                        it.copy(
                            isAnalyzing = false,
                            result = result,
                            errorType = null,
                            playbackProgress = 0f
                        )
                    }
                },
                onFailure = {
                    _uiState.update {
                        it.copy(
                            isAnalyzing = false,
                            errorType = VoiceErrorType.TOO_SHORT
                        )
                    }
                }
            )
        }
    }

    fun analyzeExternalResult(result: VoiceAnalysisResult) {
        stopPlayback()
        _uiState.update {
            it.copy(
                isAnalyzing = false,
                result = result,
                errorType = null,
                playbackProgress = 0f
            )
        }
    }

    fun runBuiltInSample(synthetic: Boolean) {
        val samples = AcousticForensicsAnalyzer.generateDemoPcm(synthetic = synthetic, durationSeconds = 3.2f)
        val label = if (synthetic) "Demo Synthetic Voice (AI)" else "Demo Organic Voice (Human)"
        analyzePcm(samples, label)
    }

    fun startRecording() {
        if (_uiState.value.isRecording) return
        stopPlayback()
        _uiState.update {
            it.copy(isRecording = true, recordingSeconds = 0, errorType = null)
        }
        recordingJob = viewModelScope.launch {
            val recorded = runCatching {
                micRecorder.recordMaxSeconds(maxSeconds = 15) { sec ->
                    _uiState.update { it.copy(recordingSeconds = sec) }
                }
            }.getOrElse { FloatArray(0) }

            _uiState.update { it.copy(isRecording = false) }
            if (recorded.size >= AnalyzeVoiceUseCase.MIN_SAMPLES_16K) {
                analyzePcm(recorded, "Live Microphone Clip")
            } else {
                _uiState.update { it.copy(errorType = VoiceErrorType.TOO_SHORT) }
            }
        }
    }

    fun stopRecording() {
        micRecorder.stop()
    }

    fun togglePlayback() {
        if (_uiState.value.isPlaying) {
            stopPlayback()
            return
        }
        val samples = lastSamples16k ?: return
        val player = audioPlayer ?: return
        _uiState.update { it.copy(isPlaying = true, playbackProgress = 0f) }
        playbackJob = viewModelScope.launch(dispatchers.io) {
            player.play(samples) { progress ->
                if (isActive) {
                    _uiState.update { it.copy(playbackProgress = progress) }
                }
            }
            _uiState.update { it.copy(isPlaying = false, playbackProgress = 0f) }
        }
    }

    fun stopPlayback() {
        playbackJob?.cancel()
        audioPlayer?.stop()
        _uiState.update { it.copy(isPlaying = false, playbackProgress = 0f) }
    }

    fun showMicPermissionError() {
        _uiState.update { it.copy(errorType = VoiceErrorType.MIC_PERMISSION) }
    }

    override fun onCleared() {
        super.onCleared()
        stopPlayback()
        micRecorder.stop()
    }
}
