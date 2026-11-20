package com.fakeaway.app.features.screen_shield.presentation

import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.fakeaway.app.R
import com.fakeaway.app.core.ui.theme.ForensicFake
import com.fakeaway.app.core.ui.theme.ForensicReal
import com.fakeaway.app.core.ui.theme.ForensicSuspicious
import com.fakeaway.app.core.ui.theme.MidnightSurfaceElevated
import com.fakeaway.app.core.ui.theme.TextPrimary
import com.fakeaway.app.core.ui.theme.TextSecondary

@Composable
fun ImageHeatmapCard(
    heatmapGrid: List<List<Float>>,
    previewImagePath: String?,
    metadataSignature: String?,
    modifier: Modifier = Modifier
) {
    val previewBitmap = remember(previewImagePath) {
        previewImagePath?.let { runCatching { BitmapFactory.decodeFile(it)?.asImageBitmap() }.getOrNull() }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MidnightSurfaceElevated)
            .padding(14.dp)
    ) {
        Text(
            text = stringResource(R.string.shield_heatmap_title),
            style = MaterialTheme.typography.titleMedium,
            color = TextPrimary,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = if (metadataSignature != null) {
                stringResource(R.string.shield_metadata_found, metadataSignature)
            } else {
                stringResource(R.string.shield_no_metadata)
            },
            style = MaterialTheme.typography.bodySmall,
            color = if (metadataSignature != null) ForensicFake else TextSecondary
        )

        Spacer(Modifier.height(10.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1.35f)
                .clip(RoundedCornerShape(12.dp))
        ) {
            if (previewBitmap != null) {
                Image(
                    bitmap = previewBitmap,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }

            Canvas(modifier = Modifier.fillMaxSize()) {
                val rows = heatmapGrid.size.coerceAtLeast(1)
                val cols = heatmapGrid.firstOrNull()?.size?.coerceAtLeast(1) ?: 1
                val cellW = size.width / cols
                val cellH = size.height / rows

                for (r in 0 until rows) {
                    for (c in 0 until cols) {
                        val intensity = heatmapGrid[r][c].coerceIn(0f, 1f)
                        val color = when {
                            intensity >= 0.72f -> ForensicFake.copy(alpha = 0.45f)
                            intensity >= 0.48f -> ForensicSuspicious.copy(alpha = 0.30f)
                            else -> ForensicReal.copy(alpha = 0.12f)
                        }
                        drawRect(
                            color = color,
                            topLeft = Offset(c * cellW, r * cellH),
                            size = Size(cellW, cellH)
                        )
                        drawRect(
                            color = Color.White.copy(alpha = 0.14f),
                            topLeft = Offset(c * cellW, r * cellH),
                            size = Size(cellW, cellH),
                            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1f)
                        )
                    }
                }
            }
        }
    }
}
