package com.fakeaway.app.core.storage

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room entity representing one persisted verification scan on device.
 */
@Entity(tableName = "scan_records")
data class ScanRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val scanType: String,
    val title: String,
    val verdict: String,
    val fakeScore: Float,
    val analysisMode: String,
    val summary: String,
    val detailsJson: String,
    val timestampMillis: Long
)
