package com.fakeaway.app.features.history.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fakeaway.app.core.domain.ScanType
import com.fakeaway.app.features.history.domain.ClearHistoryUseCase
import com.fakeaway.app.features.history.domain.DeleteHistoryItemUseCase
import com.fakeaway.app.features.history.domain.ObserveHistoryUseCase
import com.fakeaway.app.features.history.domain.ScanHistoryItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class HistoryFilter {
    ALL,
    VOICE,
    SCREEN_AND_IMAGE,
    NEWS
}

data class HistoryUiState(
    val filter: HistoryFilter = HistoryFilter.ALL,
    val items: List<ScanHistoryItem> = emptyList(),
    val totalCount: Int = 0
)

class HistoryViewModel(
    observeHistory: ObserveHistoryUseCase,
    private val deleteItem: DeleteHistoryItemUseCase,
    private val clearHistory: ClearHistoryUseCase
) : ViewModel() {

    private val selectedFilter = MutableStateFlow(HistoryFilter.ALL)

    val uiState: StateFlow<HistoryUiState> = combine(
        observeHistory(),
        selectedFilter
    ) { allItems, filter ->
        val filtered = when (filter) {
            HistoryFilter.ALL -> allItems
            HistoryFilter.VOICE -> allItems.filter { it.scanType == ScanType.VOICE }
            HistoryFilter.SCREEN_AND_IMAGE -> allItems.filter {
                it.scanType == ScanType.SCREEN || it.scanType == ScanType.IMAGE
            }
            HistoryFilter.NEWS -> allItems.filter { it.scanType == ScanType.NEWS_TEXT }
        }
        HistoryUiState(
            filter = filter,
            items = filtered,
            totalCount = allItems.size
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = HistoryUiState()
    )

    fun selectFilter(filter: HistoryFilter) {
        selectedFilter.value = filter
    }

    fun onDeleteItem(id: Long) {
        viewModelScope.launch {
            deleteItem(id)
        }
    }

    fun onClearAll() {
        viewModelScope.launch {
            clearHistory()
        }
    }
}
