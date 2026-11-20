package com.fakeaway.app.core.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.OfflineBolt
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.GppBad
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.fakeaway.app.R
import com.fakeaway.app.core.domain.AnalysisMode
import com.fakeaway.app.core.domain.ForensicSignal
import com.fakeaway.app.core.domain.Verdict
import com.fakeaway.app.core.ui.theme.ForensicCyan
import com.fakeaway.app.core.ui.theme.ForensicFake
import com.fakeaway.app.core.ui.theme.ForensicReal
import com.fakeaway.app.core.ui.theme.ForensicSuspicious
import com.fakeaway.app.core.ui.theme.ForensicUnverified
import com.fakeaway.app.core.ui.theme.MidnightBorder
import com.fakeaway.app.core.ui.theme.MidnightSurface
import com.fakeaway.app.core.ui.theme.MidnightSurfaceElevated
import com.fakeaway.app.core.ui.theme.TextPrimary
import com.fakeaway.app.core.ui.theme.TextSecondary
import kotlin.math.roundToInt

fun verdictColor(verdict: Verdict): Color = when (verdict) {
    Verdict.REAL -> ForensicReal
    Verdict.SUSPICIOUS -> ForensicSuspicious
    Verdict.FAKE -> ForensicFake
    Verdict.UNVERIFIED -> ForensicUnverified
}

@Composable
fun verdictLabel(verdict: Verdict): String = when (verdict) {
    Verdict.REAL -> stringResource(R.string.verdict_real)
    Verdict.SUSPICIOUS -> stringResource(R.string.verdict_suspicious)
    Verdict.FAKE -> stringResource(R.string.verdict_fake)
    Verdict.UNVERIFIED -> stringResource(R.string.verdict_unverified)
}

@Composable
fun analysisModeLabel(mode: AnalysisMode): String = when (mode) {
    AnalysisMode.OFFLINE_FORENSIC -> stringResource(R.string.mode_offline_forensic)
    AnalysisMode.OFFLINE_NEURAL_AND_FORENSIC -> stringResource(R.string.mode_offline_neural)
    AnalysisMode.HYBRID_ONLINE -> stringResource(R.string.mode_hybrid_online)
}

@Composable
fun signalLabel(labelKey: String): String = when (labelKey) {
    "sig_hf_rolloff" -> stringResource(R.string.sig_hf_rolloff)
    "sig_spectral_flatness" -> stringResource(R.string.sig_spectral_flatness)
    "sig_zero_floor_silence" -> stringResource(R.string.sig_zero_floor_silence)
    "sig_pitch_rigidity" -> stringResource(R.string.sig_pitch_rigidity)
    "sig_mel_smoothness" -> stringResource(R.string.sig_mel_smoothness)
    "sig_neural_wav2vec2" -> stringResource(R.string.sig_neural_wav2vec2)
    "sig_metadata_ai" -> stringResource(R.string.sig_metadata_ai)
    "sig_camera_exif" -> stringResource(R.string.sig_camera_exif)
    "sig_fft_grid" -> stringResource(R.string.sig_fft_grid)
    "sig_patch_inpainting" -> stringResource(R.string.sig_patch_inpainting)
    "sig_neural_smogy" -> stringResource(R.string.sig_neural_smogy)
    "sig_clickbait_lexicon" -> stringResource(R.string.sig_clickbait_lexicon)
    "sig_urgency_punctuation" -> stringResource(R.string.sig_urgency_punctuation)
    "sig_source_attribution" -> stringResource(R.string.sig_source_attribution)
    "sig_ai_text_style" -> stringResource(R.string.sig_ai_text_style)
    "sig_online_corroboration" -> stringResource(R.string.sig_online_corroboration)
    "sig_gemini_grounding" -> stringResource(R.string.sig_gemini_grounding)
    "sig_tathabbat_newsroom" -> stringResource(R.string.sig_tathabbat_newsroom)
    else -> labelKey
}

@Composable
fun VerdictBanner(
    verdict: Verdict,
    fakeScore: Float,
    mode: AnalysisMode,
    subtitle: String? = null,
    modifier: Modifier = Modifier
) {
    val accent = verdictColor(verdict)
    val icon = when (verdict) {
        Verdict.REAL -> Icons.Filled.VerifiedUser
        Verdict.SUSPICIOUS -> Icons.Filled.Warning
        Verdict.FAKE -> Icons.Filled.GppBad
        Verdict.UNVERIFIED -> Icons.AutoMirrored.Filled.HelpOutline
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MidnightSurface),
        border = BorderStroke(1.5.dp, accent.copy(alpha = 0.65f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ConfidenceArcGauge(
                score = fakeScore,
                color = accent,
                modifier = Modifier.size(72.dp)
            )
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = verdictLabel(verdict),
                        style = MaterialTheme.typography.titleLarge,
                        color = accent,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(Modifier.height(6.dp))
                ModeStatusPill(mode = mode)
                if (!subtitle.isNullOrBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
            }
        }
    }
}

@Composable
fun ConfidenceArcGauge(
    score: Float,
    color: Color,
    modifier: Modifier = Modifier
) {
    val clamped = score.coerceIn(0f, 1f)
    val pct = (clamped * 100f).roundToInt()
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.matchParentSize().padding(4.dp)) {
            drawArc(
                color = MidnightBorder,
                startAngle = 135f,
                sweepAngle = 270f,
                useCenter = false,
                style = Stroke(width = 7.dp.toPx(), cap = StrokeCap.Round)
            )
            drawArc(
                color = color,
                startAngle = 135f,
                sweepAngle = 270f * clamped.coerceAtLeast(0.03f),
                useCenter = false,
                style = Stroke(width = 7.dp.toPx(), cap = StrokeCap.Round)
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "$pct%",
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = stringResource(R.string.risk_score_label),
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )
        }
    }
}

@Composable
fun ModeStatusPill(mode: AnalysisMode, modifier: Modifier = Modifier) {
    val (tint, icon) = when (mode) {
        AnalysisMode.OFFLINE_FORENSIC -> ForensicCyan to Icons.Filled.OfflineBolt
        AnalysisMode.OFFLINE_NEURAL_AND_FORENSIC -> ForensicReal to Icons.Filled.Shield
        AnalysisMode.HYBRID_ONLINE -> ForensicReal to Icons.Filled.CloudDone
    }
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(999.dp),
        color = tint.copy(alpha = 0.14f),
        border = BorderStroke(1.dp, tint.copy(alpha = 0.45f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(14.dp)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = analysisModeLabel(mode),
                style = MaterialTheme.typography.labelMedium,
                color = tint
            )
        }
    }
}

@Composable
fun SignalMetricRow(signal: ForensicSignal, modifier: Modifier = Modifier) {
    val barColor = when {
        signal.score >= 0.65f -> ForensicFake
        signal.score >= 0.38f -> ForensicSuspicious
        else -> ForensicReal
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MidnightSurfaceElevated)
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = signalLabel(signal.labelKey),
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "${(signal.score * 100f).roundToInt()}%",
                style = MaterialTheme.typography.labelMedium,
                color = barColor,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(Modifier.height(6.dp))
        LinearProgressIndicator(
            progress = { signal.score.coerceIn(0f, 1f) },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(999.dp)),
            color = barColor,
            trackColor = MidnightBorder
        )
        if (signal.detail.isNotBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = signal.detail,
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )
        }
    }
}

@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        color = ForensicCyan,
        fontWeight = FontWeight.Bold,
        modifier = modifier.padding(vertical = 4.dp)
    )
}
