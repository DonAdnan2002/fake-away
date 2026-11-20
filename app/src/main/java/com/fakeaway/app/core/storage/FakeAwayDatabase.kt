package com.fakeaway.app.core.storage

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [ScanRecordEntity::class],
    version = 1,
    exportSchema = false
)
abstract class FakeAwayDatabase : RoomDatabase() {
    abstract fun scanRecordDao(): ScanRecordDao

    companion object {
        fun create(context: Context): FakeAwayDatabase = Room.databaseBuilder(
            context.applicationContext,
            FakeAwayDatabase::class.java,
            "fakeaway_scans.db"
        ).build()
    }
}
