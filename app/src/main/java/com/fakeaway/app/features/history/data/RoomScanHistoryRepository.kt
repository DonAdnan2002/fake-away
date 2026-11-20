package com.fakeaway.app.features.history.data

import com.fakeaway.app.core.domain.AnalysisMode
import com.fakeaway.app.core.domain.ScanType
import com.fakeaway.app.core.domain.Verdict
import com.fakeaway.app.core.storage.ScanRecordDao
import com.fakeaway.app.core.storage.ScanRecordEntity
import com.fakeaway.app.features.history.domain.ScanHistoryItem
import com.fakeaway.app.features.history.domain.ScanHistoryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomScanHistoryRepository(
    private val dao: ScanRecordDao
) : ScanHistoryRepository {

    override fun observeAll(): Flow<List<ScanHistoryItem>> =
        dao.observeAll().map { rows -> rows.map { it.toDomain() } }

    override suspend fun save(item: ScanHistoryItem): Long =
        dao.insert(item.toEntity())

    override suspend fun delete(id: Long) =
        dao.deleteById(id)

    override suspend fun clearAll() =
        dao.deleteAll()

    private fun ScanRecordEntity.toDomain() = ScanHistoryItem(
        id = id,
        scanType = runCatching { ScanType.valueOf(scanType) }.getOrDefault(ScanType.NEWS_TEXT),
        title = title,
        verdict = runCatching { Verdict.valueOf(verdict) }.getOrDefault(Verdict.UNVERIFIED),
        fakeScore = fakeScore,
        analysisMode = runCatching { AnalysisMode.valueOf(analysisMode) }.getOrDefault(AnalysisMode.OFFLINE_FORENSIC),
        summary = summary,
        detailsJson = detailsJson,
        timestampMillis = timestampMillis
    )

    private fun ScanHistoryItem.toEntity() = ScanRecordEntity(
        id = id,
        scanType = scanType.name,
        title = title,
        verdict = verdict.name,
        fakeScore = fakeScore,
        analysisMode = analysisMode.name,
        summary = summary,
        detailsJson = detailsJson,
        timestampMillis = timestampMillis
    )
}
