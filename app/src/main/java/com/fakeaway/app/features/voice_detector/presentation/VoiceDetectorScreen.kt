package com.fakeaway.app.features.voice_detector.presentation

import android.Manifest
import android.content.pm.PackageManager
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fakeaway.app.R
import com.fakeaway.app.core.ui.components.SectionHeader
import com.fakeaway.app.core.ui.components.SignalMetricRow
import com.fakeaway.app.core.ui.components.VerdictBanner
import com.fakeaway.app.core.ui.theme.ForensicCyan
import com.fakeaway.app.core.ui.theme.ForensicFake
import com.fakeaway.app.core.ui.theme.ForensicReal
import com.fakeaway.app.core.ui.theme.ForensicSuspicious
import com.fakeaway.app.core.ui.theme.MidnightBg
import com.fakeaway.app.core.ui.theme.MidnightBorder
import com.fakeaway.app.core.ui.theme.MidnightSurface
import com.fakeaway.app.core.ui.theme.TextPrimary
import com.fakeaway.app.core.ui.theme.TextSecondary
import kotlin.math.roundToInt

@Composable
fun VoiceDetectorScreen(
    viewModel: VoiceDetectorViewModel,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val pickMediaLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            val label = resolveDisplayName(context, uri) ?: "Audio Clip"
            viewModel.analyzeUri(context, uri, label)
        }
    }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) viewModel.startRecording() else viewModel.showMicPermissionError()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = stringResource(R.string.voice_title),
            style = MaterialTheme.typography.headlineSmall,
            color = TextPrimary
        )
        Text(
            text = stringResource(R.string.voice_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary
        )

        state.bannerMessage?.let { msg ->
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = ForensicCyan.copy(alpha = 0.14f),
                border = BorderStroke(1.dp, ForensicCyan.copy(alpha = 0.4f))
            ) {
                Text(
                    text = msg,
                    style = MaterialTheme.typography.bodySmall,
                    color = ForensicCyan,
                    modifier = Modifier.padding(12.dp)
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Button(
                onClick = { pickMediaLauncher.launch(arrayOf("audio/*", "video/*")) },
                enabled = !state.isRecording && !state.isAnalyzing,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = ForensicCyan, contentColor = MidnightBg)
            ) {
                Icon(Icons.Filled.AudioFile, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.voice_pick_audio))
            }

            Button(
                onClick = {
                    if (state.isRecording) {
                        viewModel.stopRecording()
                    } else {
                        val granted = ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.RECORD_AUDIO
                        ) == PackageManager.PERMISSION_GRANTED
                        if (granted) {
                            viewModel.startRecording()
                        } else {
                            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    }
                },
                enabled = !state.isAnalyzing,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (state.isRecording) ForensicFake else ForensicReal,
                    contentColor = MidnightBg
                )
            ) {
                Icon(
                    imageVector = if (state.isRecording) Icons.Filled.Stop else Icons.Filled.Mic,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    if (state.isRecording) {
                        stringResource(R.string.voice_stop_recording, state.recordingSeconds)
                    } else {
                        stringResource(R.string.voice_record_mic)
                    }
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedButton(
                onClick = { viewModel.runBuiltInSample(synthetic = true) },
                enabled = !state.isRecording && !state.isAnalyzing,
                modifier = Modifier.weight(1f)
            ) {
                Text(stringResource(R.string.voice_sample_synthetic), color = ForensicFake)
            }
            OutlinedButton(
                onClick = { viewModel.runBuiltInSample(synthetic = false) },
                enabled = !state.isRecording && !state.isAnalyzing,
                modifier = Modifier.weight(1f)
            ) {
                Text(stringResource(R.string.voice_sample_organic), color = ForensicReal)
            }
        }

        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MidnightSurface),
            border = BorderStroke(1.dp, MidnightBorder)
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.Info, contentDescription = null, tint = ForensicCyan, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(10.dp))
                Text(
                    text = stringResource(R.string.voice_share_tip),
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }
        }

        if (state.isAnalyzing) {
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MidnightSurface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = stringResource(R.string.voice_analyzing),
                        style = MaterialTheme.typography.bodyMedium,
                        color = ForensicCyan
                    )
                    Spacer(Modifier.height(10.dp))
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = ForensicCyan)
                }
            }
        }

        state.errorType?.let { err ->
            val errMsg = when (err) {
                VoiceErrorType.TOO_SHORT -> stringResource(R.string.voice_error_too_short)
                VoiceErrorType.DECODE_FAILED -> stringResource(R.string.voice_error_decode)
                VoiceErrorType.MIC_PERMISSION -> stringResource(R.string.voice_mic_permission_needed)
            }
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = ForensicFake.copy(alpha = 0.14f),
                border = BorderStroke(1.dp, ForensicFake)
            ) {
                Text(
                    text = errMsg,
                    style = MaterialTheme.typography.bodyMedium,
                    color = ForensicFake,
                    modifier = Modifier.padding(12.dp)
                )
            }
        }

        val result = state.result
        if (result != null) {
            VerdictBanner(
                verdict = result.verdict,
                fakeScore = result.overallFakeScore,
                mode = result.analysisMode,
                subtitle = result.sourceLabel
            )

            if (result.usedMicFallback) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = ForensicSuspicious.copy(alpha = 0.16f),
                    border = BorderStroke(1.dp, ForensicSuspicious.copy(alpha = 0.5f))
                ) {
                    Text(
                        text = stringResource(R.string.overlay_mic_fallback_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = ForensicSuspicious,
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    color = MidnightSurface,
                    border = BorderStroke(1.dp, MidnightBorder)
                ) {
                    Text(
                        text = stringResource(R.string.voice_dsp_score, (result.dspScore * 100f).roundToInt()),
                        style = MaterialTheme.typography.labelMedium,
                        color = ForensicCyan,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(12.dp)
                    )
                }
                result.neuralScore?.let { neural ->
                    Surface(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        color = MidnightSurface,
                        border = BorderStroke(1.dp, MidnightBorder)
                    ) {
                        Text(
                            text = stringResource(R.string.voice_neural_score, (neural * 100f).roundToInt()),
                            style = MaterialTheme.typography.labelMedium,
                            color = ForensicReal,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
            }

            WaveformHeatmapCard(
                amplitudes = result.waveformAmplitudes,
                segments = result.timelineSegments,
                durationSeconds = result.durationSeconds,
                playbackProgress = state.playbackProgress,
                isPlaying = state.isPlaying,
                canPlay = result.sourceLabel.isNotBlank(),
                onTogglePlay = viewModel::togglePlayback
            )

            SectionHeader(title = stringResource(R.string.voice_signals_title))
            result.signals.forEach { signal ->
                SignalMetricRow(signal = signal)
            }
        } else if (!state.isAnalyzing) {
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MidnightSurface),
                border = BorderStroke(1.dp, MidnightBorder)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Filled.GraphicEq,
                        contentDescription = null,
                        tint = ForensicCyan,
                        modifier = Modifier.size(44.dp)
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = stringResource(R.string.voice_empty_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                }
            }
        }

        Spacer(Modifier.height(24.dp))
    }
}

private fun resolveDisplayName(context: android.content.Context, uri: android.net.Uri): String? {
    return runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
    }.getOrNull() ?: uri.lastPathSegment
}
