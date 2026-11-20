package com.fakeaway.app.features.history.domain

import kotlinx.coroutines.flow.Flow

class ObserveHistoryUseCase(private val repository: ScanHistoryRepository) {
    operator fun invoke(): Flow<List<ScanHistoryItem>> = repository.observeAll()
}

class DeleteHistoryItemUseCase(private val repository: ScanHistoryRepository) {
    suspend operator fun invoke(id: Long) = repository.delete(id)
}

class ClearHistoryUseCase(private val repository: ScanHistoryRepository) {
    suspend operator fun invoke() = repository.clearAll()
}
