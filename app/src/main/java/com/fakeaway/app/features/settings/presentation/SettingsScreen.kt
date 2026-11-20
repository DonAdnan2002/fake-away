package com.fakeaway.app.features.settings.presentation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.OfflineBolt
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fakeaway.app.R
import com.fakeaway.app.core.domain.AppLanguage
import com.fakeaway.app.core.onnx.ModelDownloadProgress
import com.fakeaway.app.core.onnx.ModelSpec
import com.fakeaway.app.core.ui.components.SectionHeader
import com.fakeaway.app.core.ui.theme.ForensicCyan
import com.fakeaway.app.core.ui.theme.ForensicFake
import com.fakeaway.app.core.ui.theme.ForensicReal
import com.fakeaway.app.core.ui.theme.MidnightBg
import com.fakeaway.app.core.ui.theme.MidnightBorder
import com.fakeaway.app.core.ui.theme.MidnightSurface
import com.fakeaway.app.core.ui.theme.TextPrimary
import com.fakeaway.app.core.ui.theme.TextSecondary

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onOpenAbout: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            text = stringResource(R.string.settings_title),
            style = MaterialTheme.typography.headlineSmall,
            color = TextPrimary
        )

        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MidnightSurface),
            border = BorderStroke(1.dp, MidnightBorder)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Language, contentDescription = null, tint = ForensicCyan)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.settings_language_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary
                    )
                }
                Text(
                    text = stringResource(R.string.settings_language_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AppLanguage.entries.forEach { lang ->
                        FilterChip(
                            selected = state.language == lang,
                            onClick = { viewModel.selectLanguage(lang) },
                            label = { Text(lang.displayName) },
                            modifier = Modifier.testTag("lang_${lang.tag}"),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = ForensicCyan.copy(alpha = 0.22f),
                                selectedLabelColor = ForensicCyan
                            )
                        )
                    }
                }
            }
        }

        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MidnightSurface),
            border = BorderStroke(1.dp, MidnightBorder)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.OfflineBolt, contentDescription = null, tint = ForensicReal)
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.settings_offline_mode_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary
                    )
                    Text(
                        text = stringResource(R.string.settings_offline_mode_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
                Spacer(Modifier.width(8.dp))
                Switch(
                    checked = state.strictOfflineMode,
                    onCheckedChange = viewModel::setStrictOfflineMode
                )
            }
        }

        SectionHeader(title = stringResource(R.string.settings_models_title))

        // Bundled Offline OCR Pack
        Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = MidnightSurface),
            border = BorderStroke(1.dp, ForensicReal.copy(alpha = 0.4f))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = ForensicReal)
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.settings_model_ocr_name),
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary
                    )
                    Text(
                        text = stringResource(R.string.settings_model_ocr_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
                Text(
                    text = stringResource(R.string.settings_model_ready),
                    style = MaterialTheme.typography.labelMedium,
                    color = ForensicReal,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        OnnxModelRow(
            title = stringResource(R.string.settings_model_voice_name),
            description = stringResource(R.string.settings_model_voice_desc),
            installed = state.installedModels[ModelSpec.VOICE_WAV2VEC2] == true,
            progress = state.activeDownloads[ModelSpec.VOICE_WAV2VEC2],
            onDownload = { viewModel.downloadModel(ModelSpec.VOICE_WAV2VEC2) },
            onDelete = { viewModel.deleteModel(ModelSpec.VOICE_WAV2VEC2) }
        )

        OnnxModelRow(
            title = stringResource(R.string.settings_model_image_name),
            description = stringResource(R.string.settings_model_image_desc),
            installed = state.installedModels[ModelSpec.IMAGE_SMOGY] == true,
            progress = state.activeDownloads[ModelSpec.IMAGE_SMOGY],
            onDownload = { viewModel.downloadModel(ModelSpec.IMAGE_SMOGY) },
            onDelete = { viewModel.deleteModel(ModelSpec.IMAGE_SMOGY) }
        )

        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MidnightSurface),
            border = BorderStroke(1.dp, MidnightBorder)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.settings_tathabbat_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary
                )
                Text(
                    text = stringResource(R.string.settings_tathabbat_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
                OutlinedTextField(
                    value = state.tathabbatUrlDraft,
                    onValueChange = viewModel::updateTathabbatUrlDraft,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text(stringResource(R.string.settings_tathabbat_hint)) }
                )
                when (val conn = state.connectionStatus) {
                    is NewsroomConnectionStatus.Connected -> Text(
                        text = stringResource(R.string.settings_connection_ok, conn.claims, conn.articles),
                        style = MaterialTheme.typography.bodySmall,
                        color = ForensicReal
                    )
                    NewsroomConnectionStatus.Failed -> Text(
                        text = stringResource(R.string.settings_connection_fail),
                        style = MaterialTheme.typography.bodySmall,
                        color = ForensicFake
                    )
                    NewsroomConnectionStatus.Testing -> LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth(),
                        color = ForensicCyan
                    )
                    NewsroomConnectionStatus.Idle -> Unit
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = viewModel::testTathabbatConnection,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(stringResource(R.string.settings_test_connection))
                    }
                    Button(
                        onClick = viewModel::saveTathabbatUrl,
                        colors = ButtonDefaults.buttonColors(containerColor = ForensicCyan, contentColor = MidnightBg)
                    ) {
                        Text(stringResource(R.string.settings_save))
                    }
                }
            }
        }

        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MidnightSurface),
            border = BorderStroke(1.dp, MidnightBorder)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.settings_gemini_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary
                )
                Text(
                    text = stringResource(R.string.settings_gemini_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
                OutlinedTextField(
                    value = state.geminiKeyDraft,
                    onValueChange = viewModel::updateGeminiKeyDraft,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text(stringResource(R.string.settings_gemini_hint)) }
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (state.savedFeedbackCounter > 0) {
                        Text(
                            text = stringResource(R.string.settings_saved_toast),
                            style = MaterialTheme.typography.bodySmall,
                            color = ForensicReal
                        )
                    } else {
                        Spacer(Modifier.width(1.dp))
                    }
                    Button(
                        onClick = viewModel::saveGeminiKey,
                        colors = ButtonDefaults.buttonColors(containerColor = ForensicCyan, contentColor = MidnightBg)
                    ) {
                        Text(stringResource(R.string.settings_save))
                    }
                }
            }
        }

        Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = MidnightSurface),
            border = BorderStroke(1.dp, ForensicCyan.copy(alpha = 0.5f)),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("settings_open_about")
                .clickable { onOpenAbout() }
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.Groups, contentDescription = null, tint = ForensicCyan, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.about_team_name) + " • Mosul Space",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary
                    )
                    Text(
                        text = stringResource(R.string.about_open_team_card),
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = ForensicCyan)
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
                Icon(Icons.Filled.Security, contentDescription = null, tint = ForensicCyan, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(10.dp))
                Text(
                    text = stringResource(R.string.settings_architecture_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }
        }

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun OnnxModelRow(
    title: String,
    description: String,
    installed: Boolean,
    progress: ModelDownloadProgress?,
    onDownload: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MidnightSurface),
        border = BorderStroke(1.dp, MidnightBorder)
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(text = title, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
            Text(text = description, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            if (progress != null) {
                Text(
                    text = stringResource(R.string.settings_model_downloading, progress.percent),
                    style = MaterialTheme.typography.bodySmall,
                    color = ForensicCyan
                )
                LinearProgressIndicator(
                    progress = { progress.percent / 100f },
                    modifier = Modifier.fillMaxWidth(),
                    color = ForensicCyan
                )
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(
                            if (installed) R.string.settings_model_ready else R.string.settings_model_not_installed
                        ),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (installed) ForensicReal else TextSecondary
                    )
                    if (installed) {
                        OutlinedButton(onClick = onDelete) {
                            Icon(Icons.Filled.DeleteOutline, contentDescription = null, tint = ForensicFake, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(stringResource(R.string.settings_model_delete), color = ForensicFake)
                        }
                    } else {
                        Button(
                            onClick = onDownload,
                            colors = ButtonDefaults.buttonColors(containerColor = ForensicCyan, contentColor = MidnightBg)
                        ) {
                            Icon(Icons.Filled.CloudDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.settings_model_download))
                        }
                    }
                }
            }
        }
    }
}
