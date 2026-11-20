package com.fakeaway.app.core.storage

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ScanRecordDao {

    @Query("SELECT * FROM scan_records ORDER BY timestampMillis DESC")
    fun observeAll(): Flow<List<ScanRecordEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: ScanRecordEntity): Long

    @Query("DELETE FROM scan_records WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM scan_records")
    suspend fun deleteAll()
}
