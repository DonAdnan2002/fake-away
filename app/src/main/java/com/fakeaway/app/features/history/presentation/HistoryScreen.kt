package com.fakeaway.app.features.history.presentation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Screenshot
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fakeaway.app.R
import com.fakeaway.app.core.domain.ScanType
import com.fakeaway.app.core.ui.components.ModeStatusPill
import com.fakeaway.app.core.ui.components.verdictColor
import com.fakeaway.app.core.ui.components.verdictLabel
import com.fakeaway.app.core.ui.theme.ForensicCyan
import com.fakeaway.app.core.ui.theme.MidnightBorder
import com.fakeaway.app.core.ui.theme.MidnightSurface
import com.fakeaway.app.core.ui.theme.TextPrimary
import com.fakeaway.app.core.ui.theme.TextSecondary
import com.fakeaway.app.features.history.domain.ScanHistoryItem
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun HistoryScreen(
    viewModel: HistoryViewModel,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.history_title),
                style = MaterialTheme.typography.headlineSmall,
                color = TextPrimary
            )
            if (state.totalCount > 0) {
                TextButton(onClick = viewModel::onClearAll) {
                    Text(
                        text = stringResource(R.string.history_clear_all),
                        color = ForensicCyan
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            HistoryFilterChip(
                selected = state.filter == HistoryFilter.ALL,
                label = stringResource(R.string.history_filter_all),
                onClick = { viewModel.selectFilter(HistoryFilter.ALL) }
            )
            HistoryFilterChip(
                selected = state.filter == HistoryFilter.VOICE,
                label = stringResource(R.string.history_filter_voice),
                onClick = { viewModel.selectFilter(HistoryFilter.VOICE) }
            )
            HistoryFilterChip(
                selected = state.filter == HistoryFilter.SCREEN_AND_IMAGE,
                label = stringResource(R.string.history_filter_screen),
                onClick = { viewModel.selectFilter(HistoryFilter.SCREEN_AND_IMAGE) }
            )
            HistoryFilterChip(
                selected = state.filter == HistoryFilter.NEWS,
                label = stringResource(R.string.history_filter_news),
                onClick = { viewModel.selectFilter(HistoryFilter.NEWS) }
            )
        }

        Spacer(Modifier.height(12.dp))
        if (state.items.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Filled.History,
                        contentDescription = null,
                        tint = TextSecondary,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = stringResource(R.string.history_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                }
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(state.items, key = { it.id }) { item ->
                    HistoryRecordCard(
                        item = item,
                        onDelete = { viewModel.onDeleteItem(item.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun HistoryFilterChip(
    selected: Boolean,
    label: String,
    onClick: () -> Unit
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = ForensicCyan.copy(alpha = 0.2f),
            selectedLabelColor = ForensicCyan
        )
    )
}

@Composable
private fun HistoryRecordCard(
    item: ScanHistoryItem,
    onDelete: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val accent = verdictColor(item.verdict)
    val typeIcon = when (item.scanType) {
        ScanType.VOICE -> Icons.Filled.GraphicEq
        ScanType.SCREEN, ScanType.IMAGE -> Icons.Filled.Screenshot
        ScanType.NEWS_TEXT -> Icons.AutoMirrored.Filled.Article
    }
    val formattedDate = remember(item.timestampMillis) {
        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(item.timestampMillis))
    }

    Card(
        onClick = { expanded = !expanded },
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MidnightSurface),
        border = BorderStroke(1.dp, MidnightBorder)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = typeIcon,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "$formattedDate  •  ${verdictLabel(item.verdict)} (${(item.fakeScore * 100f).roundToInt()}%)",
                        style = MaterialTheme.typography.bodySmall,
                        color = accent,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = Icons.Filled.DeleteOutline,
                        contentDescription = stringResource(R.string.history_delete),
                        tint = TextSecondary
                    )
                }
            }
            if (item.summary.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = item.summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    maxLines = if (expanded) 10 else 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (expanded) {
                Spacer(Modifier.height(8.dp))
                ModeStatusPill(mode = item.analysisMode)
            }
        }
    }
}
