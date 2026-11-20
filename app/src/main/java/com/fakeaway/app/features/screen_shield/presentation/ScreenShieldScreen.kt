package com.fakeaway.app.features.screen_shield.presentation

import android.provider.Settings
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
import androidx.compose.material.icons.filled.ImageSearch
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.PlayArrow
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

@Composable
fun ScreenShieldScreen(
    viewModel: ScreenShieldViewModel,
    onRequestStartShield: () -> Unit,
    onRequestStopShield: () -> Unit,
    onRequestOverlayPermission: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val hasOverlayPermission = Settings.canDrawOverlays(context)

    val pickImageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.inspectImageUri(context, uri, runFullScreenOcrToo = true)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = stringResource(R.string.shield_title),
            style = MaterialTheme.typography.headlineSmall,
            color = TextPrimary
        )
        Text(
            text = stringResource(R.string.shield_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary
        )

        state.bannerMessage?.let { msg ->
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = ForensicCyan.copy(alpha = 0.14f),
                border = BorderStroke(1.dp, ForensicCyan)
            ) {
                Text(
                    text = msg,
                    style = MaterialTheme.typography.bodySmall,
                    color = ForensicCyan,
                    modifier = Modifier.padding(12.dp)
                )
            }
        }

        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MidnightSurface),
            border = BorderStroke(
                1.5.dp,
                if (state.isShieldActive) ForensicReal else MidnightBorder
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.Layers,
                        contentDescription = null,
                        tint = if (state.isShieldActive) ForensicReal else ForensicCyan,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = stringResource(
                            if (state.isShieldActive) R.string.shield_status_active else R.string.shield_status_inactive
                        ),
                        style = MaterialTheme.typography.titleMedium,
                        color = if (state.isShieldActive) ForensicReal else TextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                }

                if (!hasOverlayPermission) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = ForensicSuspicious.copy(alpha = 0.14f),
                        border = BorderStroke(1.dp, ForensicSuspicious)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = stringResource(R.string.shield_overlay_permission_title),
                                style = MaterialTheme.typography.titleMedium,
                                color = ForensicSuspicious
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = stringResource(R.string.shield_overlay_permission_msg),
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                            Spacer(Modifier.height(8.dp))
                            Button(
                                onClick = onRequestOverlayPermission,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = ForensicSuspicious,
                                    contentColor = MidnightBg
                                )
                            ) {
                                Text(stringResource(R.string.shield_grant_overlay))
                            }
                        }
                    }
                }

                Button(
                    onClick = {
                        if (state.isShieldActive) onRequestStopShield() else onRequestStartShield()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (state.isShieldActive) ForensicFake else ForensicReal,
                        contentColor = MidnightBg
                    )
                ) {
                    Icon(
                        imageVector = if (state.isShieldActive) Icons.Filled.Stop else Icons.Filled.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        stringResource(
                            if (state.isShieldActive) R.string.shield_stop_button else R.string.shield_start_button
                        )
                    )
                }

                OutlinedButton(
                    onClick = { pickImageLauncher.launch(arrayOf("image/*")) },
                    enabled = !state.isAnalyzing,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Filled.ImageSearch, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.shield_pick_image))
                }
            }
        }

        if (state.isAnalyzing) {
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MidnightSurface)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = stringResource(R.string.shield_analyzing),
                        style = MaterialTheme.typography.bodyMedium,
                        color = ForensicCyan
                    )
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = ForensicCyan)
                }
            }
        }

        val screenRes = state.latestScreenResult
        val imageRes = state.latestImageResult ?: screenRes?.imageResult

        if (screenRes != null) {
            SectionHeader(title = stringResource(R.string.shield_last_capture_title))
            VerdictBanner(
                verdict = screenRes.overallVerdict,
                fakeScore = screenRes.overallRiskScore,
                mode = screenRes.analysisMode
            )

            SectionHeader(title = stringResource(R.string.shield_ocr_extracted_title))
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MidnightSurface),
                border = BorderStroke(1.dp, MidnightBorder)
            ) {
                Text(
                    text = screenRes.extractedText.ifBlank { stringResource(R.string.shield_no_text_detected) },
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextPrimary,
                    modifier = Modifier.padding(14.dp)
                )
            }

            screenRes.factCheckResult?.let { fc ->
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MidnightSurface),
                    border = BorderStroke(1.dp, MidnightBorder)
                ) {
                    Text(
                        text = fc.explanation,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        modifier = Modifier.padding(14.dp)
                    )
                }
            }
        }

        if (imageRes != null) {
            SectionHeader(title = stringResource(R.string.shield_image_forensics_title))
            ImageHeatmapCard(
                heatmapGrid = imageRes.heatmapGrid,
                previewImagePath = state.selectedPreviewPath,
                metadataSignature = imageRes.metadataSignature
            )
            imageRes.signals.forEach { signal ->
                SignalMetricRow(signal = signal)
            }
        } else if (screenRes == null && !state.isAnalyzing) {
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MidnightSurface),
                border = BorderStroke(1.dp, MidnightBorder)
            ) {
                Text(
                    text = stringResource(R.string.shield_empty_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                    modifier = Modifier.padding(18.dp)
                )
            }
        }

        Spacer(Modifier.height(24.dp))
    }
}
