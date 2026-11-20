package com.fakeaway.app.features.history.domain

import kotlinx.coroutines.flow.Flow

/**
 * Persistence boundary for local scan history across all detector slices.
 */
interface ScanHistoryRepository {
    fun observeAll(): Flow<List<ScanHistoryItem>>
    suspend fun save(item: ScanHistoryItem): Long
    suspend fun delete(id: Long)
    suspend fun clearAll()
}
