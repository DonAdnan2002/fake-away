package com.fakeaway.app.features.voice_detector.presentation

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.fakeaway.app.R
import com.fakeaway.app.core.domain.Verdict
import com.fakeaway.app.core.ui.components.verdictColor
import com.fakeaway.app.core.ui.theme.ForensicCyan
import com.fakeaway.app.core.ui.theme.ForensicFake
import com.fakeaway.app.core.ui.theme.ForensicReal
import com.fakeaway.app.core.ui.theme.ForensicSuspicious
import com.fakeaway.app.core.ui.theme.MidnightSurfaceElevated
import com.fakeaway.app.core.ui.theme.TextPrimary
import com.fakeaway.app.core.ui.theme.TextSecondary
import com.fakeaway.app.features.voice_detector.domain.TimelineSegment
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WaveformHeatmapCard(
    amplitudes: List<Float>,
    segments: List<TimelineSegment>,
    durationSeconds: Float,
    playbackProgress: Float,
    isPlaying: Boolean,
    canPlay: Boolean,
    onTogglePlay: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MidnightSurfaceElevated)
            .padding(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.voice_heatmap_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = stringResource(R.string.voice_duration_label, durationSeconds),
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }
            if (canPlay) {
                FilledTonalIconButton(onClick = onTogglePlay) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = stringResource(if (isPlaying) R.string.voice_pause else R.string.voice_play),
                        tint = ForensicCyan
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(88.dp)
        ) {
            val count = amplitudes.size.coerceAtLeast(1)
            val totalWidth = size.width
            val totalHeight = size.height
            val slotWidth = totalWidth / count
            val barWidth = (slotWidth * 0.62f).coerceAtLeast(2f)
            val centerY = totalHeight / 2f

            for (i in 0 until count) {
                val frac = i.toFloat() / count
                val tSec = frac * durationSeconds
                val matchingSeg = segments.firstOrNull { tSec >= it.startSec && tSec <= it.endSec }
                    ?: segments.lastOrNull()
                val prob = matchingSeg?.fakeProbability ?: 0.2f
                val barColor = when {
                    prob >= 0.65f -> ForensicFake
                    prob >= 0.38f -> ForensicSuspicious
                    else -> ForensicReal
                }
                val amp = amplitudes[i].coerceIn(0.08f, 1f)
                val barHeight = (amp * totalHeight * 0.88f).coerceAtLeast(6f)
                val x = i * slotWidth + (slotWidth - barWidth) / 2f
                drawRoundRect(
                    color = barColor,
                    topLeft = Offset(x, centerY - barHeight / 2f),
                    size = Size(barWidth, barHeight),
                    cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)
                )
            }

            if (playbackProgress > 0f) {
                val cursorX = playbackProgress.coerceIn(0f, 1f) * totalWidth
                drawLine(
                    color = TextPrimary,
                    start = Offset(cursorX, 0f),
                    end = Offset(cursorX, totalHeight),
                    strokeWidth = 2.dp.toPx()
                )
            }
        }

        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.voice_timeline_legend),
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary
        )

        if (segments.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                segments.forEach { seg ->
                    val segColor = verdictColor(seg.verdict)
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = segColor.copy(alpha = 0.16f)
                    ) {
                        Text(
                            text = "${seg.startSec.roundToInt()}-${seg.endSec.roundToInt()}s: ${(seg.fakeProbability * 100f).roundToInt()}%",
                            style = MaterialTheme.typography.bodySmall,
                            color = segColor,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }
        }
    }
}
