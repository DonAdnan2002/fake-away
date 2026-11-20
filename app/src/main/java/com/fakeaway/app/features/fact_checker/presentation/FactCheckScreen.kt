package com.fakeaway.app.features.fact_checker.presentation

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fakeaway.app.R
import com.fakeaway.app.core.domain.AppLanguage
import com.fakeaway.app.core.ui.components.SectionHeader
import com.fakeaway.app.core.ui.components.SignalMetricRow
import com.fakeaway.app.core.ui.components.VerdictBanner
import com.fakeaway.app.core.ui.theme.ForensicCyan
import com.fakeaway.app.core.ui.theme.ForensicFake
import com.fakeaway.app.core.ui.theme.ForensicReal
import com.fakeaway.app.core.ui.theme.MidnightBg
import com.fakeaway.app.core.ui.theme.MidnightBorder
import com.fakeaway.app.core.ui.theme.MidnightSurface
import com.fakeaway.app.core.ui.theme.MidnightSurfaceElevated
import com.fakeaway.app.core.ui.theme.TextPrimary
import com.fakeaway.app.core.ui.theme.TextSecondary

@Composable
fun FactCheckScreen(
    viewModel: FactCheckViewModel,
    onJumpToShield: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = stringResource(R.string.verify_title),
            style = MaterialTheme.typography.headlineSmall,
            color = TextPrimary
        )
        Text(
            text = stringResource(R.string.verify_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary
        )

        if (state.isFacebookLinkBanner) {
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = ForensicCyan.copy(alpha = 0.14f)),
                border = BorderStroke(1.dp, ForensicCyan)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = stringResource(R.string.shield_fb_link_banner),
                        style = MaterialTheme.typography.bodySmall,
                        color = ForensicCyan
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = onJumpToShield) {
                        Icon(Icons.Filled.Shield, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.shield_start_button))
                    }
                }
            }
        }

        OutlinedTextField(
            value = state.inputText,
            onValueChange = { viewModel.updateInput(it) },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("verify_input"),
            minLines = 3,
            maxLines = 6,
            placeholder = { Text(stringResource(R.string.verify_input_hint)) }
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = { viewModel.verify() },
                enabled = !state.isVerifying,
                modifier = Modifier
                    .weight(1f)
                    .testTag("verify_submit"),
                colors = ButtonDefaults.buttonColors(containerColor = ForensicCyan, contentColor = MidnightBg)
            ) {
                Icon(Icons.Filled.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.verify_button))
            }

            OutlinedButton(
                onClick = {
                    val clipText = clipboard.getText()?.text.orEmpty()
                    if (clipText.isNotBlank()) viewModel.updateInput(clipText)
                }
            ) {
                Icon(Icons.Filled.ContentPaste, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text(stringResource(R.string.verify_paste))
            }

            OutlinedButton(onClick = viewModel::clear) {
                Icon(Icons.Filled.Clear, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text(stringResource(R.string.verify_clear))
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = stringResource(R.string.verify_samples_label),
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(
                    onClick = { viewModel.loadSample(AppLanguage.ARABIC) },
                    label = { Text(stringResource(R.string.verify_sample_ar)) }
                )
                AssistChip(
                    onClick = { viewModel.loadSample(AppLanguage.ENGLISH) },
                    label = { Text(stringResource(R.string.verify_sample_en)) }
                )
                AssistChip(
                    onClick = { viewModel.loadSample(AppLanguage.KURDISH_SORANI) },
                    label = { Text(stringResource(R.string.verify_sample_ckb)) }
                )
            }
        }

        if (state.emptyError) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = ForensicFake.copy(alpha = 0.14f),
                border = BorderStroke(1.dp, ForensicFake)
            ) {
                Text(
                    text = stringResource(R.string.verify_empty_error),
                    style = MaterialTheme.typography.bodySmall,
                    color = ForensicFake,
                    modifier = Modifier.padding(10.dp)
                )
            }
        }

        if (state.isVerifying) {
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MidnightSurface)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = stringResource(R.string.verify_analyzing),
                        style = MaterialTheme.typography.bodyMedium,
                        color = ForensicCyan
                    )
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = ForensicCyan)
                }
            }
        }

        val res = state.result
        if (res != null) {
            VerdictBanner(
                verdict = res.verdict,
                fakeScore = res.fakeRiskScore,
                mode = res.analysisMode,
                subtitle = stringResource(R.string.verify_detected_lang, res.detectedLanguage.displayName),
                modifier = Modifier.testTag("verify_result_banner")
            )

            SectionHeader(title = stringResource(R.string.verify_explanation_title))
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MidnightSurface),
                border = BorderStroke(1.dp, MidnightBorder)
            ) {
                Text(
                    text = res.explanation,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextPrimary,
                    modifier = Modifier.padding(14.dp)
                )
            }

            if (res.extractedClaims.isNotEmpty()) {
                SectionHeader(title = stringResource(R.string.verify_claims_title))
                res.extractedClaims.forEach { claim ->
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MidnightSurfaceElevated
                    ) {
                        Text(
                            text = claim,
                            style = MaterialTheme.typography.bodySmall,
                            color = ForensicCyan,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(10.dp)
                        )
                    }
                }
            }

            SectionHeader(title = stringResource(R.string.verify_sources_title))
            if (res.sources.isEmpty()) {
                Text(
                    text = stringResource(R.string.verify_no_sources),
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            } else {
                res.sources.forEach { src ->
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MidnightSurface),
                        border = BorderStroke(1.dp, MidnightBorder),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                runCatching {
                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(src.url)))
                                }
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = src.title,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = TextPrimary,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = src.publisher,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = ForensicReal,
                                    fontWeight = FontWeight.Medium
                                )
                                if (src.snippet.isNotBlank()) {
                                    Text(
                                        text = src.snippet,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = TextSecondary,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            Spacer(Modifier.width(8.dp))
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                contentDescription = null,
                                tint = ForensicCyan,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            res.signals.forEach { signal ->
                SignalMetricRow(signal = signal)
            }

            OutlinedButton(
                onClick = viewModel::escalateToNewsroom,
                enabled = !state.isEscalating && state.escalatedClaimId == null,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(
                        if (state.isEscalating) R.string.verify_escalating else R.string.verify_escalate_button
                    )
                )
            }

            state.escalatedClaimId?.let { claimId ->
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = ForensicReal.copy(alpha = 0.14f),
                    border = BorderStroke(1.dp, ForensicReal)
                ) {
                    Text(
                        text = stringResource(R.string.verify_escalated_success, claimId),
                        style = MaterialTheme.typography.bodySmall,
                        color = ForensicReal,
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }

            state.escalationError?.let { err ->
                val msg = if (err == "NO_SERVER_URL") {
                    stringResource(R.string.verify_configure_server_hint)
                } else {
                    stringResource(R.string.verify_escalated_error)
                }
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = ForensicFake.copy(alpha = 0.14f),
                    border = BorderStroke(1.dp, ForensicFake)
                ) {
                    Text(
                        text = msg,
                        style = MaterialTheme.typography.bodySmall,
                        color = ForensicFake,
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }
        }

        Spacer(Modifier.height(24.dp))
    }
}
